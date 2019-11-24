/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2019 ChemVantage LLC
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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.googlecode.objectify.Key;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

@WebServlet(urlPatterns = {"/lti","/lti/"})
public class LTILaunch extends HttpServlet {

	// This servlet handles LTI launch requests for tool consumers connecting using the LTI v1.1
	// only. For LTI v1p3 launches, use the URL pattern /lti/launch handled by LTIv1p3Launch.java
	
	private static final long serialVersionUID = 137L;
	private static String jwtSecret = Subject.getSubject().HMAC256Secret;
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.sendRedirect("/lti/registration");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("Token"));  // may be null for LTI launch request
			
			if ("UpdateAssignment".equals(request.getParameter("UserRequest"))) {
				updateAssignment(request,response); // POST the assignmentType and topicIds
				// construct a redirectUrl to the new assignment
				String redirectUrl = "/" + request.getParameter("AssignmentType") + "?Token=" + user.token;
				response.sendRedirect(redirectUrl);
				return;
			} else if (request.getParameter("lti_message_type")!=null) { // handle LTI launch request for LTIv1p0 and LTIv1p1
				basicLtiLaunchRequest(request,response);
			} else if (request.getParameter("id_token")!=null) {  // handle LTI v1p3 launch request
				response.getWriter().println("The correct launch URL for LTI v1p3 is https://" + request.getServerName() + "/lti/launch");
			} else doError(request,response,"Invalid LTI launch request. Missing lti_message_type.",null,null); 
		} catch (Exception e) {
			doError(request,response,"","",e);
		}
	}

	void basicLtiLaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws IOException {
		// check for required LTI launch parameters:
		try {
			String lti_message_type = request.getParameter("lti_message_type");
			if (lti_message_type == null || !"basic-lti-launch-request".contentEquals(lti_message_type)) {
				doError(request,response,"Invalid lti_message_type parameter.",null,null);
				return;
			}
			
			String lti_version = request.getParameter("lti_version");
			if (lti_version==null) {
				doError(request,response,"Missing lti_version parameter.",null,null);
				return;
			} else if (!lti_version.equals("LTI-1p0")) {
				doError(request,response,"Invalid lti_version parameter.",null,null);
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
				if (tc.secret==null) throw new Exception("Shared secret was not found in the ChemVantage database.");
				if (tc.lti_version==null || tc.lti_version.isEmpty() || !tc.lti_version.equals(lti_version)) {
						tc.lti_version=lti_version;
						ofy().save().entity(tc);
				}
			} catch (Exception e) {
				throw new Exception("Invalid oauth_consumer_key. Please verify that the oauth_consumer_key is entered into your LMS exactly as you are registered with ChemVantage.");
			}

			OAuthMessage oam = OAuthServlet.getMessage(request, null);
			OAuthValidator oav = new SimpleOAuthValidator();
			OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed",oauth_consumer_key,tc.secret,null);
			OAuthAccessor acc = new OAuthAccessor(cons);
			OAuthSignatureMethod.getBaseString(oam);
			
			if (!Nonce.isUnique(request.getParameter("oauth_nonce"), request.getParameter("oauth_timestamp"))) 
				throw new Exception("Invalid nonce or timestamp.");
			
			try {
				oav.validateMessage(oam,acc);
			} catch(Exception e) {
				throw new Exception("OAuth validation failed, most likely due to an invalid shared_secret value in your LMS. Check carefully to eliminate leading or trailing blank spaces.");
			}
			// BLTI Launch message was validated successfully at this point
			
			// Detect whether this is an anonymous LTI launch request per LTIv1p1p2. This is a security patch that
			// prevents a cross-site request forgery threat applicable to versions of LTI released prior to v1.3.
			// The launch procedure is for the TC to issue an anonymous BLTI launch request with no user information.
			// The TP wraps the TC-defined platform_state into an encrypted JSON Web Token (JWT) and redircects the browser
			// to the TC-specified relaunch_url with the original platform_state and the new tool_state parameters, where
			// tool_state is the encrypted JWT. The TC then relaunches to the TP with the user information and the
			// two state parameters, which must be verified by the TP to proceed with the launch. This security patch makes
			// ChemVantage compliant with LTIv1p1p2. If the parameters are not included, the TP may proceed with a 
			// normal v1p0 BLTI launch; however this is subject to the following deprecation schedule:
			// LTIv1p0		last certification 12/31/2019 and last market availability 12/31/2020
			// LTIv1p1p2 	last certification 06/30/2021 and last market availability 06/30/2022
			
			String relaunch_url = request.getParameter("relaunch_url");
			String platform_state = request.getParameter("platform_state");
			String tool_state = request.getParameter("tool_state");
			
			if (tool_state != null && platform_state != null) { // This is a LTIv1.1.2 relaunch response. Validate the tool_state value
				try {
				    Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
				    JWT.require(algorithm)
				        .withIssuer("https://www.chemvantage.org")
				        .withClaim("platform_state", platform_state)
				        .build().verify(tool_state);
				} catch (Exception e) {
					throw new Exception("Tool state could not be validated.");
				}			
			} else if (relaunch_url != null && platform_state != null) {  // Anonymous LRTIv1p1p2 launch request. Execute relaunch sequence:
				try {
					Date expires = new Date(new Date().getTime() + 600000); // 10 minutes from now
				    Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
				    tool_state = JWT.create()
				        .withIssuer("https://www.chemvantage.org")
				        .withClaim("platform_state", platform_state)
				        .withExpiresAt(expires)
				        .sign(algorithm);
				    response.sendRedirect(relaunch_url + "?platform_state=" + platform_state + "&tool_state=" + tool_state);
				} catch (Exception e){
					throw new Exception("Tool state JWT could not be created.");
				}
			    return;  // wait for relaunch from platform
			}
			// End of LTIv1p1p2 section. Continue with normal LTI launch sequence
			
			// Gather some information about the user
			String userId = request.getParameter("user_id");
			userId = oauth_consumer_key + ":" + (userId==null?"":userId);

			// Process user information, provision a new user account if necessary, and store the userId in the user's session
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId",userId);
			User user = User.getInstance(session); // returns null if user is not anonymous and not in the database
			if (user==null) user = User.createBLTIUser(request); // first-ever login for this user
			
			// ensure the proper authDomain and domain values
			if (user.authDomain == null || !user.authDomain.equals("BLTI")) user.authDomain = "BLTI";
			if (user.domain == null || !user.domain.equals(oauth_consumer_key)) user.domain = oauth_consumer_key;

			// check if user has Instructor or Administrator role
			String roles = request.getParameter("roles");
			if (roles != null) {
				roles = roles.toLowerCase();
				user.setIsInstructor(roles.contains("instructor"));
				user.setIsAdministrator(roles.contains("administrator"));
				user.setIsTeachingAssistant(roles.contains("teachingassistant"));
			}
			// user information OK; save user after processing context info
			
			// Create the domain if it doesn't already exist
			Domain domain = ofy().load().type(Domain.class).filter("domainName",oauth_consumer_key).first().now();
			if (domain==null) domain = new Domain(oauth_consumer_key);
			domain.setLastLogin(new Date());
			
			String lisOutcomeServiceURL = request.getParameter("lis_outcome_service_url");
			if (lisOutcomeServiceURL!=null) {
				domain.resultServiceEndpoint = lisOutcomeServiceURL;
				domain.resultServiceFormat = "application/xml";
				domain.supportsResultService = true;
			}
			
			if (user.isAdministrator()) domain.addAdmin(user.id);
			
			ofy().save().entity(domain).now();
			//Domain info processed successfully
			
			// Process context (group) information
			String context_id = request.getParameter("context_id");
			String context_title = request.getParameter("context_title");
			
			if (context_id==null) {
				context_id = oauth_consumer_key + ":defaultGroup";
				context_title = "default group";
			}
			if (context_title==null) context_title = context_id; // missing course title

			// Find the user's group, and if necessary create a new one and put the user in it
			Group myGroup = ofy().load().type(Group.class).filter("domain",oauth_consumer_key).filter("context_id",context_id).first().now();
			if (myGroup == null) { // create a new group
				String instructorId = user.isInstructor()?user.id:"unknown";
				myGroup = new Group(domain.domainName,context_id,context_title,instructorId);
			}
			
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			// update the LIS result outcome service URL, if necessary
			if (domain.supportsResultService && domain.resultServiceEndpoint!=null && !domain.resultServiceEndpoint.equals(myGroup.lis_outcome_service_url)) {  // update the URL and format as Group properties
				myGroup.lis_outcome_service_url=domain.resultServiceEndpoint;
				myGroup.lis_outcome_service_format = domain.resultServiceFormat;
				myGroup.isUsingLisOutcomeService = true;
			}							
			
			if (user.isInstructor()) myGroup.instructorId = user.id;
			myGroup.addMember(user.id);
			ofy().save().entity(myGroup).now();
			user.myGroupId = myGroup.id;
			ofy().save().entity(user);			
			
			// Context info OK
			
			// Use the resourceLinkId to find the assignment or create a new one:
			Assignment myAssignment = null;
			String redirectUrl;
			
			try {  // load the requested Assignment entity if it exists
				myAssignment = ofy().load().type(Assignment.class).filter("domain",domain.domainName).filter("resourceLinkId", resource_link_id).first().safe();
				user.setToken(myAssignment.id,lis_result_sourcedid);
				
				if (myAssignment.assignmentType != null) {
					redirectUrl = "/" + myAssignment.assignmentType + "?Token=" + user.token;
					response.sendRedirect(redirectUrl);
					return;
				}
			} catch (Exception e) {  // or create a new one with the available information (but no assignmentType or topicIds)
				myAssignment = new Assignment(myGroup.id,domain.domainName,resource_link_id);
				ofy().save().entity(myAssignment).now(); // we'll need to load this in a second from the pickResource form
				user.setToken(myAssignment.id,lis_result_sourcedid);
			}
			// At this point we should have a valid Assignment, but it does not have an 
			// assignmentType or topicId(s). Show the the pickResource form:
			response.getWriter().println(Home.header + pickResourceForm(user,myAssignment) + Home.footer);
			return;

		} catch (Exception e) {
			doError(request, response,"LTI Launch failed. " + e.getMessage(), null, null);
		}		
	}
	
	void updateAssignment(HttpServletRequest request, HttpServletResponse response) throws Exception {
			
		User user = User.getUser(request.getParameter("Token"));
			if (user==null) throw new Exception("Unable to identify user because the token was expired or invalid.");
			if (!user.isInstructor()) throw new Exception("User must be instructor to update thisd assignment.");
			long assignmentId = user.getAssignmentId();
			if (assignmentId == 0L) throw new Exception("Assignment ID was 0L.");
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			a.assignmentType = request.getParameter("AssignmentType");
			if (a.assignmentType.contentEquals("Quiz") || a.assignmentType.contentEquals("Homework")) {
				a.topicId = Long.parseLong(request.getParameter("TopicId"));
			 	a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			} else if (a.assignmentType.contentEquals("PracticeExam")) {
				String[] topicIds = request.getParameterValues("TopicIds");
				if (topicIds==null || topicIds.length<3) throw new Exception("You must choose at least three topics for thisd practice exam.");
				a.topicIds = new ArrayList<Long>();
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<topicIds.length;i++) {
					long tId = Long.parseLong(topicIds[i]);
					a.topicIds.add(tId);
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tId).keys().list());
				}
			} else throw new Exception("Assignment type is undefined.");	
			ofy().save().entity(a).now(); // going to need this is just a few milliseconds
	}
	
	String pickResourceForm(User user,Assignment myAssignment) {
		StringBuffer buf = new StringBuffer();

		try {			
			String assignmentType = myAssignment.assignmentType;

			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");
			
			buf.append("<h2>Assignment Setup Page</h2>"
					+ "The link that you just activated in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p>");
					
			if (user.isInstructor()) buf.append("Please select the ChemVantage assignment that should be associated with this link. "
					+ "ChemVantage will remember this choice and send students directly to the assignment.<p>");
			else {
				buf.append("<b>Please ask your instructor to click the LMS assignment link to make this missing association.</b> "
						+ "You will not be able to complete this assignment until after this has been done.");
				return buf.toString();
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

			buf.append("<table><form name=AssignmentForm method=POST>");
			buf.append("<input type=hidden name=UserRequest value=UpdateAssignment>");
			buf.append("<input type=hidden name=Token value='" + user.token + "'>");
			buf.append("<tr><td>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=Quiz" + ("Quiz".equals(assignmentType)?" CHECKED":"") + ">Quiz</label><br>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=Homework" + ("Homework".equals(assignmentType)?" CHECKED":"") + ">Homework</label><br>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=PracticeExam" + ("PracticeExam".equals(assignmentType)?" CHECKED":"") + ">Practice&nbsp;Exam</label>"
					+ "</td><td id=topicSelect style='visibility:hidden;vertical-align=top'>"
					+ "<FONT COLOR=RED>Please select one topic for this quiz or homework assignment.</FONT><br>");
			
			buf.append("<SELECT NAME=TopicId onChange=document.AssignmentForm.start.disabled=(document.AssignmentForm.TopicId.selectedIndex==0);>"
					+ "<OPTION Value='0'" + (myAssignment.topicId==0L?" SELECTED":"") + ">Select a topic</OPTION>");			
			
			List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
			for (Topic t : topics) if (!t.orderBy.equals("Hide")) {
				buf.append("<OPTION VALUE='" + t.id + "'" + (t.id==myAssignment.topicId?" SELECTED":"") + ">" + t.title + "</OPTION>");			 
			}
			buf.append("</SELECT><input type=submit name=start disabled=true>"
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
		if ( return_url != null && !return_url.isEmpty() ) {
			if ( return_url.indexOf('?') > 1 ) {
				return_url += "&lti_msg=" + URLEncoder.encode(s,"UTF-8");
			} else {
				return_url += "?lti_msg=" + URLEncoder.encode(s,"UTF-8");
			}
			response.sendRedirect(return_url);
			return;
		}
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(s + (e==null?"":"<br>" + e.toString()));
	}

	@Override
	public void destroy() {

	}
}