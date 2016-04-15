/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2012 ChemVantage LLC
*   
*	This servlet file is adapted from an open-source Java servlet 
*	LTIProviderServlet written by Charles Severance at imsglobal.org
*
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

public class LTILaunch extends HttpServlet {

	private static final long serialVersionUID = 137L;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null) user = Nonce.getUser(request.getParameter("Nonce"));
			if (user==null) throw new Exception();  // unauthenticated user
			if (request.getParameter("resource_link_id")==null) throw new Exception(); // missing required LTI parameter
			
			if ("pick".equals(request.getParameter("UserRequest")))
				out.println(pickResourceForm(user,request));
			else response.sendRedirect(resourceUrlFinder(user,request));
		} catch (Exception e) {
			response.sendRedirect("/lti/registration");
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		StringBuffer debug = new StringBuffer();
		if (Login.lockedDown) doError(request,response,"ChemVantage is temporarily unavailable, sorry.",null,null);

		// check for minimum required elements for a basic-lti-launch-request
		String lti_message_type=request.getParameter("lti_message_type");
		
		if ("ToolProxyRegistrationRequest".equals(lti_message_type)) {
			doError(request,response,"Tool proxy registration request failed. The correct URL for LTI2 registration of ChemVantage is " + request.getServerName() + "/lti/registration/",null,null);
			return;
		}
		
		if (!"basic-lti-launch-request".equals(lti_message_type)) {
			doError(request,response,"Missing or invalid lti_message_type parameter.",null,null);
			return;
		}
		
		// only basic lti launch requests should reach this point
		
		try {
			String lti_version = request.getParameter("lti_version");
			if (lti_version==null) {
				doError(request,response,"Missing lti_version parameter.",null,null);
				return;
			}
			switch (lti_version) {
			case "LTI-1p0": break;
			case "LTI-2p0": break;
			default: doError(request,response,"Invalid lti_version parameter.",null,null);
			return;
			}

			String oauth_consumer_key = request.getParameter("oauth_consumer_key");
			if (oauth_consumer_key==null) {
				doError(request,response,"Missing oauth_consumer_key.",null,null);
				return;
			}
			String resource_link_id = request.getParameter("resource_link_id");
			if (resource_link_id==null) {
				doError(request,response,"Missing resource_link_id.",null,null);
				return;
			}
			
			BLTIConsumer tc;
			try {
				tc = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).safe();
				if (tc.secret==null) throw new Exception();
			} catch (Exception e) {
				throw new Exception ("Invalid oauth_consumer_key. Please verify that the oauth_consumer_key is entered into your LMS exactly as you are registered with ChemVantage.");
			}

			String tool_consumer_guid = request.getParameter("tool_consumer_guid");
			if (tool_consumer_guid != null && !tool_consumer_guid.equals(tc.tool_consumer_guid)) {
				// this place is reached if the launch sends a guid that does not match the guid for the BLTIConsumer
				if (tc.tool_consumer_guid==null) {  // record a previously unrecorded guid
					tc.tool_consumer_guid = tool_consumer_guid;
					ofy().save().entity(tc).now();
				} else doError(request,response,"Invalid tool_consumer_guid.",null,null);
			}

			OAuthMessage oam = OAuthServlet.getMessage(request, null);
			OAuthValidator oav = new SimpleOAuthValidator();
			OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", 
					oauth_consumer_key, tc.secret, null);

			OAuthAccessor acc = new OAuthAccessor(cons);

			String base_string = null;
			try {
				base_string = OAuthSignatureMethod.getBaseString(oam);
			} catch (Exception e) {
				base_string = null;
			}
			
			try {
				if (!Nonce.isUnique(request.getParameter("oauth_nonce"), request.getParameter("oauth_timestamp"))) throw new Exception("Bad nonce or timestamp.");
				oav.validateMessage(oam,acc);
			} catch(Exception e) {
				System.out.println("Provider failed to validate message");
				System.out.println(e.getMessage());
				if ( base_string != null ) System.out.println(base_string);
				throw new Exception("OAuth validation failed. The most likely cause is the shared_secret value was entered into your LMS incorrectly, possibly with leading or trailing blank spaces. Please enter the value again, exactly as you are registered with ChemVantage.");
			}
			// BLTI Launch message was validated successfully. 
			
			// Gather some information about the user
			String userId = request.getParameter("user_id");
			userId = oauth_consumer_key + (userId==null?"":":"+userId);

			// Provision a new user account if necessary, and store the userId in the user's session
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId",userId);
			User user = User.getInstance(session);
			if (user==null) user = User.createBLTIUser(request); // first-ever login for this user
			
			// ensure the proper authDomain value
			if (user.authDomain == null || !user.authDomain.equals("BLTI")) {
				user.authDomain = "BLTI";
				ofy().save().entity(user).now();
			}

			// Create the domain if it doesn't already exist
			Domain domain = ofy().load().type(Domain.class).filter("domainName",oauth_consumer_key).first().now();
			Date now = new Date();
			if (domain == null) {
				domain = new Domain(oauth_consumer_key);
				if (tc.supportsResultService()) {
					domain.supportsResultService = true;
					domain.resultServiceEndpoint = tc.getResultServiceEndpoint();
					domain.resultServiceFormat = tc.getResultServiceFormat();
				}
			} else domain.setLastLogin(now);
			ofy().save().entity(domain).now();
			
			String lisOutcomeServiceURL = request.getParameter("lis_outcome_service_url");
			
			if (lisOutcomeServiceURL!=null && !lisOutcomeServiceURL.equals(domain.resultServiceEndpoint)) {
				domain.resultServiceEndpoint = lisOutcomeServiceURL;
				if (domain.resultServiceFormat==null || domain.resultServiceFormat.isEmpty()) domain.resultServiceFormat = "application/xml";
				domain.supportsResultService = true;
				ofy().save().entity(domain).now();
			} 
			
			// ensure that this user is associated with the LTI domain
			if (user.domain == null || !user.domain.equals(domain.domainName)) {
				user.domain = domain.domainName;
				ofy().save().entity(user).now();
			}

			// check if user has Instructor or Administrator role
			String roles = request.getParameter("roles");
			if (roles != null) {
				roles = roles.toLowerCase();
				if (roles.contains("instructor") && user.setIsInstructor(true)) ofy().save().entity(user).now(); // saves user if role is changed
				if (roles.contains("administrator")) {
					if (user.setIsAdministrator(true)) ofy().save().entity(user).now();  // saves user if role is changed
					if (domain.addAdmin(user.id)) ofy().save().entity(domain).now();  // saves domain object if new admin is added
				}
			}

			if (!user.hasPremiumAccount()) {
				user.setPremium(true);  // All LTI users have free premium accounts
				ofy().save().entity(user).now();	
			}
			
			String context_id = request.getParameter("context_id");
			String context_title = request.getParameter("context_title");
			
			if (context_id==null && tc.getCapabilities().contains("CourseSection.sourcedId")) context_id = request.getParameter("lis_course_section_sourcedid");
			else if (context_id==null) {
				context_id = oauth_consumer_key + ":defaultGroup";
				context_title = "default group";
			}
			if (context_title==null) context_title = context_id; // missing course title

			// Provision a new context (group), if necessary and put the user into it
			Group g = ofy().load().type(Group.class).filter("domain",oauth_consumer_key).filter("context_id",context_id).first().now();	
			if (g == null) { // create this new group
				g = new Group("BLTI",context_id,context_title);
				g.domain = domain.domainName;
				ofy().save().entity(g).now();
			}
			user.changeGroups(g.id);
			
			// Add user to the approved TA list, if necessary
			if (roles.contains("teachingassistant") && g.addTA(userId)) ofy().save().entity(g);
			
			// update the LIS result outcome service URL, if necessary
			if (domain.supportsResultService && domain.resultServiceEndpoint!=null && !domain.resultServiceEndpoint.equals(g.lis_outcome_service_url)) {  // update the URL and format as Group properties
				g.lis_outcome_service_url=domain.resultServiceEndpoint;
				g.lis_outcome_service_format = domain.resultServiceFormat;
				g.isUsingLisOutcomeService = true;
				ofy().save().entity(g).now();
			}							
			//debug.append("LIS services OK. ");
			
			if (user.isInstructor()) {
				if (g.instructorId.equals("unknown")) {  // assign the instructor to this group
					g.instructorId = user.id;
					ofy().save().entity(g).now();
				}
			}

			// Use the resourceUrlFinder method to discover the URL for the assignment associated with this link
			String redirectUrl = resourceUrlFinder(user,request);
			
			response.sendRedirect(redirectUrl);
		} catch (Exception e) {
			doError(request, response,"LTI Launch failed. " + e.getMessage() + debug.toString(), null, null);
		}
	}		
	
	String resourceUrlFinder(User user, HttpServletRequest request) {
		/* 
		 * This method searches for an assignment containing the resource_link_id for this LTI launch
		 * and redirects the user to the correct assignment. If no assignment can be found, the method 
		 * tries to validate the assignmentType and topicId or topicIds. If the user is the instructor
		 * then a new assignment is created and the resource link added. Then the user is redirected 
		 * to the correct assignment.  If the assignment cannot be validated, the user is sent to the 
		 * resourcePicker page to choose a valid assignmentId and topicId or topicIds.
		 */
		String redirectUrl = "";
		Assignment myAssignment = null;
		String assignmentType = request.getParameter("AssignmentType");
		if (assignmentType==null) assignmentType = request.getParameter("custom_AssignmentType");  // supports custom LTI variables
		long topicId = 0L;
		List<Long> topicIds = new ArrayList<Long>();

		// a resource_link_id string is required for every valid LTI launch
		String resource_link_id = request.getParameter("resource_link_id");
		//if (resource_link_id == null) resource_link_id = request.getParameter("custom_resource_link_id");
		
		// the lis_result_sourcedid is an optional LTI parameter that specifies a context grade book entry point
		String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
		if (lis_result_sourcedid == null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
		try { // encode the lis_result_sourcedid because it will be appended to the redirectUrl
			lis_result_sourcedid = URLEncoder.encode(lis_result_sourcedid,"UTF-8");
		} catch (Exception e) {
			lis_result_sourcedid = null;
		}
		
		try {  
			List<Assignment> assignments = ofy().load().type(Assignment.class).filter("groupId",user.myGroupId).list();
			
			for (Assignment a : assignments) {  // look for myAssignment having the correct resource_link_id
				if (a.resourceLinkIds != null && a.resourceLinkIds.contains(resource_link_id)) {
					myAssignment = a;
					break;
				}
			}
			
			if (myAssignment==null) { // try to find it based on the request data AssignmentType and TopicId or TopicIds (for PracticeExam)
				if (assignmentType==null) throw new Exception();
				assignments = ofy().load().type(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType",assignmentType).list();
				if (assignmentType.equals("PracticeExam")) {
					String[] tIds = request.getParameterValues("TopicIds");
					//if (tIds==null) tIds = request.getParameterValues("custom_TopicIds"); // supports custom LTI variables
					if (tIds==null || tIds.length<3) throw new Exception();
					for (int i=0;i<tIds.length;i++) topicIds.add(Long.parseLong(tIds[i]));
					for (Assignment a : assignments) {
						if (a.topicIds.equals(topicIds)) {
							myAssignment=a; 
							break;
						}
					}
					if (myAssignment==null) myAssignment = new Assignment(user.myGroupId,topicIds,assignmentType,new Date(0));

				} else {  // assignmentType is Quiz or Homework
					String tId = request.getParameter("TopicId");
					//if (tId==null) tId = request.getParameter("custom_TopicId"); // supports custom LTI variables
					topicId = Long.parseLong(tId);  // throws Exception if tId does not represent a long integer
					for (Assignment a : assignments) {
						if (a.matches(assignmentType,topicId)) {
							myAssignment=a; 
							break;
						}
					}
					if (myAssignment==null) myAssignment = new Assignment(user.myGroupId,topicId,assignmentType,new Date(0));
				}

				if (user.isInstructor()) {  // must be the instructor to modify or store myAssignment
					myAssignment.addResourceLinkId(resource_link_id);
					ofy().save().entity(myAssignment).now();
					Group g = ofy().load().type(Group.class).id(user.myGroupId).safe();
					g.setGroupTopicIds();
					ofy().save().entity(g).now();
				} else lis_result_sourcedid = null; // this prevents students from getting scores for made-up assignments
			}	
			if (myAssignment.assignmentType.equals("PracticeExam")) {
				redirectUrl = "/PracticeExam";
				for (int i=0;i<myAssignment.topicIds.size();i++) redirectUrl += (i==0?"?":"&") + "TopicId=" + myAssignment.topicIds.get(i);
				redirectUrl += "&AssignmentId=" + myAssignment.id;
			}
			else { // specify topicId for Quiz or Homework assignment
				redirectUrl ="/" + myAssignment.assignmentType + "?TopicId=" + myAssignment.topicId;
			}
			if (lis_result_sourcedid!=null) redirectUrl += "&lis_result_sourcedid=" + lis_result_sourcedid;
			
			// Send a Nonce reference in case the session is lost (no browser support for Cookies)
			redirectUrl += "&Nonce=" + Nonce.createInstance(user);
						
			return redirectUrl;  // normal finish; go directly to the assignment

		} catch (Exception e) {
			redirectUrl = "/lti?UserRequest=pick&resource_link_id="+resource_link_id;  // send the user to the pickResource page
			if (assignmentType!=null) redirectUrl += "&AssignmentType=" + assignmentType;
			if (lis_result_sourcedid!=null) redirectUrl += "&lis_result_sourcedid=" + lis_result_sourcedid;
			
			// Send a Nonce reference in case the session is lost (no browser support for Cookies)
			redirectUrl += "&Nonce=" + Nonce.createInstance(user);
			return redirectUrl;  // go to pickResourceForm to specify the assignment
		}			
	}
	
	String pickResourceForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			String resource_link_id = request.getParameter("resource_link_id"); 
			if (resource_link_id == null) resource_link_id = request.getParameter("custom_resource_link_id");
			if (resource_link_id == null) return "/Home";  // a resource_link_id value is required for every LTI launch
			
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			if (lis_result_sourcedid==null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
			
			String assignmentType = request.getParameter("AssignmentType");
			if (assignmentType==null) assignmentType = "";  // must be a valid String object
			String tId = request.getParameter("TopicId");
			if (tId==null) tId = "0";  // must be a valid String object
			
			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");
			
			buf.append("<h2>Assignment Setup Page</h2>"
					+ "The link that you just activated in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p>"
					+ "Please select the ChemVantage assignment that should be associated with this link. ");

			if (user.isInstructor()) buf.append("ChemVantage will remember this choice and send students directly to the assignment.<p>");
			else {
				buf.append("<b>Please ask your instructor to click the LMS assignment link to make this missing association.</b> "
						+ "Your scores cannot be returned to your LMS grade book until after this has been done.<p>");
			}

			// insert a script to show/hide the correct box
			buf.append("<script>"
					+ "function inspectRadios() { "
					+ "var radios = document.getElementsByName('AssignmentType');"
					+ "  if(radios[0].checked) {"
					+ "    document.getElementById('topicSelect').style.visibility='visible';"
					+ "    document.getElementById('topicSelect').style.valign='top';"
					+ "    document.getElementById('topicCheck').style.visibility='hidden';"
					+ "  }"
					+ "  else if(radios[1].checked) {"
					+ "    document.getElementById('topicSelect').style.visibility='visible';"
					+ "    document.getElementById('topicSelect').style.valign='middle';"
					+ "    document.getElementById('topicCheck').style.visibility='hidden';"
					+ "  }"
					+ "  else if(radios[2].checked) {"
					+ "    document.getElementById('topicSelect').style.visibility='hidden';"
					+ "    document.getElementById('topicCheck').style.visibility='visible';"
					+ "  }"
					+ "}"
					+ "</script>");

			buf.append("<table><form name=AssignmentForm method=GET><input type=hidden name='resource_link_id' value='" + resource_link_id + "'>");
			buf.append("<input type=hidden name=Nonce value=" + Nonce.createInstance(user) + ">");
			if (lis_result_sourcedid != null) buf.append("<input type=hidden name='lis_result_sourcedid' value='" + URLEncoder.encode(lis_result_sourcedid,"UTF-8") + "'>");
			buf.append("<tr><td>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=Quiz" + ("Quiz".equals(assignmentType)?" CHECKED":"") + ">Quiz<br>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=Homework" + ("Homework".equals(assignmentType)?" CHECKED":"") + ">Homework<br>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=PracticeExam" + ("PracticeExam".equals(assignmentType)?" CHECKED":"") + ">Practice&nbsp;Exam"
					+ "</td><td id=topicSelect style='visibility:hidden;vertical-align=top'>"
					+ "<FONT COLOR=RED>Please select one topic for this quiz or homework assignment.</FONT><br>");
			buf.append("<SELECT NAME=TopicId onChange=\"javascript: document.AssignmentForm.UserRequest.disabled=(document.AssignmentForm.TopicId.selectedIndex==0);\">"
					+ "<OPTION Value='0'" + ("0".equals(tId)?" SELECTED":"") + ">Select a topic</OPTION>");			
			List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
			for (Topic t : topics) if (!t.orderBy.equals("Hide")) {
				buf.append("<OPTION VALUE='" + t.id + "'" + (String.valueOf(t.id).equals(tId)?" SELECTED":"") + ">" + t.title + "</OPTION>");			 
			}
			buf.append("</SELECT><input type=submit name=UserRequest DISABLED=true>"
					+ "</td></tr>"
					+ "<tr><td colspan=2 id=topicCheck style='visibility:hidden'>"
					+ "<TABLE>");
			buf.append("<TR><TD COLSPAN=3 style='color:red'>Please select at least 3 topics for this practice exam:<br></TD></TR>");
			int i = 0;
			for (Topic t : topics) {
				if ("Hide".equals(t.orderBy)) continue;
				buf.append(i%3==0?"<TR><TD>":"<TD>");
				buf.append("<INPUT TYPE=CHECKBOX NAME=TopicIds VALUE='" + t.id + "' "
						+ "onClick=\"javascript: var checked=0; "
						+ "for(i=0;i<document.AssignmentForm.TopicIds.length;i++) if(document.AssignmentForm.TopicIds[i].checked) checked++;"
						+ "document.AssignmentForm.begin.disabled=(checked<3);"
						+ "if(document.AssignmentForm.begin.disabled) document.AssignmentForm.begin.value='Select at least 3 topics';"
						+ "else document.AssignmentForm.begin.value='Submit';\">" 
						+ t.title + "<br>\n");
				buf.append(i%3==2?"</TD></TR>\n":"</TD>");
				i++;
			}
			buf.append("</TABLE>"
					+"<br>The practice exam is designed to be completed in 60 minutes. "
					+"<INPUT TYPE=SUBMIT NAME=begin DISABLED=true VALUE='Select at least 3 topics'>"
					+ "</td></tr>"
					+ "</form></table>");
			buf.append("<script>inspectRadios()</script>");
		} catch (Exception e) {
			return e.getMessage();
		}
		return buf.toString();
	}

	public void doError(HttpServletRequest request, HttpServletResponse response, String s, String message, Exception e)
	throws java.io.IOException {
		String return_url = request.getParameter("launch_presentation_return_url");
		if ( return_url != null && return_url.length() > 1 ) {
			if ( return_url.indexOf('?') > 1 ) {
				return_url += "&lti_msg=" + URLEncoder.encode(s,"UTF-8");
			} else {
				return_url += "?lti_msg=" + URLEncoder.encode(s,"UTF-8");
			}
			response.sendRedirect(return_url);
			return;
		}
		PrintWriter out = response.getWriter();
		out.println(s);
	}

	@Override
	public void destroy() {

	}

}