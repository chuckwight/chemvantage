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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
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

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class LTILaunch extends HttpServlet {

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
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
			if (user==null) throw new Exception();
			
			if ("pick".equals(request.getParameter("UserRequest")))
				out.println(pickResourceForm(user,request));
			else if ("Go".equals(request.getParameter("UserRequest"))) {
				String assignmentType = request.getParameter("AssignmentType");
				long topicId = Long.parseLong(request.getParameter("TopicId"));				
				if (assignmentType!=null && topicId>0L) {
					response.sendRedirect(resourceUrlFinder(user,request));
				}
				else out.println(pickResourceForm(user,request));
			}
			else doPost(request, response);
		} catch (Exception e) {
			response.sendRedirect("/lti/registration");
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		if (Login.lockedDown) doError(request,response,"ChemVantage is temporarily unavailable, sorry.",null,null);

		// check for minimum required elements for a basic-lti-launch-request
		String lti_message_type=request.getParameter("lti_message_type");
		if ("ToolProxyRegistrationRequest".equals(lti_message_type)) {  // redirect this LTI registration request
			String msg = "Please POST LTI registration requests to https://chem-vantage.appspot.com/lti/registration/";
			doError(request,response,msg,null,null);
			return;
		} else if (!"basic-lti-launch-request".equals(lti_message_type)) {
			doError(request,response,"Missing or invalid lti_message_type parameter.",null,null);
			return;
		}
		
		String lti_version = request.getParameter("lti_version");
		if (lti_version==null) {
			doError(request,response,"Missing lti_version parameter.",null,null);
			return;
		}
		switch (lti_version) {
			case "LTI-1p0": break;
			case "LTI-2p0": break;
			default: doError(request,response,"ChemVantage supports only LTI versions 1.0, 1.1, and 2.0",null,null); return;
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
		
		String userId = request.getParameter("user_id");
		userId = oauth_consumer_key + (userId==null?"":":"+userId);
		
		String context_id = request.getParameter("context_id");
		
		String oauth_secret = BLTIConsumer.getSecret(oauth_consumer_key);
		
		OAuthMessage oam = OAuthServlet.getMessage(request, null);
		OAuthValidator oav = new SimpleOAuthValidator();
		OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", 
				oauth_consumer_key, oauth_secret, null);

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
			doError(request, response,"Launch data does not validate.", context_id, null);
			return;
		}
		// BLTI Launch message was validated successfully. 

		// Provision a new user account if necessary, and store the userId in the user's session
		HttpSession session = request.getSession(true);
		session.setAttribute("UserId",userId);
		User user = User.getInstance(session);
		if (user==null) user = User.createBLTIUser(request); // first-ever login for this user
		// try to set a cookie in the user's browser with the identity provider
		Cookie c = new Cookie("IDProvider","BLTI");
		c.setMaxAge(2592000); // expires after 30 days (in seconds)
		response.addCookie(c);
	
		// Create the domain if it doesn't already exist
		Domain domain = ofy.query(Domain.class).filter("domainName",oauth_consumer_key).get();
		if (domain == null) {
			domain = new Domain(oauth_consumer_key);
			if (user.isAdministrator()) domain.addAdmin(user.id);
			ofy.put(domain);
		}
		
		// check if user has Instructor or Administrator role
		String roles = request.getParameter("roles");
		if (roles != null) {
			int oldRoles = user.roles;
			roles = roles.toLowerCase();
			if (roles.contains("instructor")) user.setIsInstructor(true);
			if (roles.contains("administrator")) user.setIsAdministrator(true);
			if (user.roles!=oldRoles) ofy.put(user);
		}
		
		if (!user.hasPremiumAccount()) {
			user.setPremium(true);  // new! All LTI users have free premium accounts
			ofy.put(user);	
		}
		
		// Check to see if the LMS is providing an LIS Outcome Service URL (LTI v1.1)
		String lisOutcomeServiceUrl = request.getParameter("lis_outcome_service_url");
		if (lisOutcomeServiceUrl==null) lisOutcomeServiceUrl = request.getParameter("custom_lis_outcome_service_url");
		
		// the lis_result_sourcedid is an optional LTI parameter that specifies a context gradebook entry point
		String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
		if (lis_result_sourcedid==null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
		
		// Provision a new context (group), if necessary and put the user into it
		Group g = null;
		if (context_id==null) context_id = oauth_consumer_key + ":defaultGroup";
		g = ofy.query(Group.class).filter("domain",oauth_consumer_key).filter("context_id",context_id).get();
		if (g == null) { // create this new group
			String contextTitle = request.getParameter("context_title");
			if (contextTitle==null) contextTitle = "";
			g = new Group("BLTI",context_id,contextTitle);
			g.domain = domain.domainName;
			ofy.put(g);
		}
		user.changeGroups(g.id);
		
		boolean supportsLIS = lisOutcomeServiceUrl != null && lis_result_sourcedid != null;
		
		// update the LIS result outcome service URL, if necessary
		if (supportsLIS && !lisOutcomeServiceUrl.equals(g.lis_outcome_service_url)) {  // update the URL as a Group property
			g.lis_outcome_service_url=lisOutcomeServiceUrl;
			g.isUsingLisOutcomeService = true;
			g.lis_outcome_service_format = BLTIConsumer.getResultServiceFormat(oauth_consumer_key);
			ofy.put(g);
		}							

		if (user.isInstructor()) {
			if (g.instructorId.equals("unknown")) {  // assign the instructor to this group
				g.instructorId = user.id;
				ofy.put(g);
			}
		}

		// Use the resourceUrlFinder method to discover the URL for the assignment associated with this link
		String redirectUrl = resourceUrlFinder(user,request);		
		response.sendRedirect(redirectUrl);
	}		
	
	String resourceUrlFinder(User user, HttpServletRequest request) {
		String redirectUrl = "";
		Date now = new Date();
		Date sixMonthsFromNow = new Date(now.getTime() + 15768000000L);  // exact time far into the future
		try {  
			// a resource_link_id string should be provided with every valid LTI launch
			String resource_link_id = request.getParameter("resource_link_id");
			if (resource_link_id==null) resource_link_id = request.getParameter("custom_resource_link_id");
			
			// the lis_result_sourcedid is an optional LTI parameter that specifies a context grade book entry point
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			if (lis_result_sourcedid==null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
			
			Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",user.myGroupId);
			Assignment myAssignment = null;
			for (Assignment a : assignments) 
				if (a.resourceLinkIds != null && a.resourceLinkIds.contains(resource_link_id)) {
					myAssignment = a;
					break;
				}
			if (!(myAssignment == null)) {  // found the correct assignment
				redirectUrl ="/" + myAssignment.assignmentType + "?TopicId=" + myAssignment.topicId;
				if (lis_result_sourcedid!=null) redirectUrl += "&lis_result_sourcedid=" + URLEncoder.encode(lis_result_sourcedid,"UTF-8");
				return redirectUrl;  // normal finish for assignment
			} else { // try to find it based on the request data AssignmentType and TopicId
				String assignmentType = request.getParameter("AssignmentType");
				if (assignmentType==null) assignmentType = request.getParameter("custom_AssignmentType");
				String tId = request.getParameter("TopicId");
				if (tId==null) tId = request.getParameter("custom_TopicId");
				long topicId = 0L;
				
				try {
					if (assignmentType==null) throw new Exception();
					topicId = Long.parseLong(tId);  // throws Exception if tId does not represent a long integer
					myAssignment = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType",assignmentType).filter("topicId",topicId).get();
					if (user.isInstructor()) {  // must be the instructor to create this assignment
						if (myAssignment==null) {  // create this new assignment
							myAssignment = new Assignment(user.myGroupId,topicId,assignmentType,sixMonthsFromNow);
							myAssignment.addResourceLinkId(resource_link_id);
							ofy.put(myAssignment);
							Group g = ofy.get(Group.class,user.myGroupId);
							g.setGroupTopicIds();
							ofy.put(g);

						} else {  // assignment already exists; add this resource_link_id
							myAssignment.addResourceLinkId(resource_link_id);
							ofy.put(myAssignment);
						}
					}
				} catch (Exception e) {  // could not identify the assignment; send user to pickResource page
					redirectUrl = "/lti?UserRequest=pick&resource_link_id="+resource_link_id;  // send the user to the pickResource page
					if (assignmentType!=null) redirectUrl += "&AssignmentType=" + assignmentType;
					if (topicId>0) redirectUrl += "&TopicId=" + topicId;
					if (lis_result_sourcedid!=null) redirectUrl += "&lis_result_sourcedid=" + URLEncoder.encode(lis_result_sourcedid,"UTF-8");
					return redirectUrl;
				}
				redirectUrl ="/" + assignmentType + "?TopicId=" + topicId;
				if (lis_result_sourcedid!=null) redirectUrl += "&lis_result_sourcedid=" + URLEncoder.encode(lis_result_sourcedid,"UTF-8");
				return redirectUrl;
			}
		} catch (Exception e) {
			return e.toString();  // an unexpected fatal error occurred; 
		}			
	}
	
	String pickResourceForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			String resource_link_id = request.getParameter("resource_link_id"); if (resource_link_id == null) return "/Home";
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			if (lis_result_sourcedid==null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
			
			String assignmentType = request.getParameter("AssignmentType");
			if (assignmentType==null) assignmentType = "";  // must be a valid String object
			String tId = request.getParameter("TopicId");
			if (tId==null) tId = "0";  // must be a valis String object
			
			boolean flag = Boolean.parseBoolean(request.getParameter("flag"));
			
			buf.append("<h3>Choose the ChemVantage Assignment For This Link</h3>"
					+ "The link that you just clicked in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p>"
					+ "Please select the ChemVantage assignment that should be associated with this link. ");

			if (user.isInstructor()) buf.append("ChemVantage will remember this choice and send students directly to the assignment.<p>");
			else {
				buf.append("<b>Please ask your instructor to click the LMS assignment link to make this missing association.</b> "
						+ "Your scores cannot be returned to your LMS grade book until after this has been done.<p>");
			}

			if (flag) buf.append("<span style='color:red'><b>You must choose the assignment type (quiz or homework) AND the topic covered.</b></span>");

			// insert a script to show/hide the correct box
			buf.append("<script>"
					+ "function inspectRadios() { "
					+ "var radios = document.getElementsByName('AssignmentType');"
					+ "  if(radios[0].checked || radios[1].checked) "
					+ "    {document.getElementById('topicSelect').style.visibility='visible';document.getElementById('topicCheck').style.visibility='hidden';}"
					+ "  else if(radios[2].checked)"
					+ "    {document.getElementById('topicSelect').style.visibility='hidden';document.getElementById('topicCheck').style.visibility='visible';}"
					+ "}"
					+ "</script>");

			buf.append("<table><form method=GET><input type=hidden name='resource_link_id' value='" + resource_link_id + "'>");
			buf.append("<input type=hidden name=flag value=true>");  // used to highlight instructions 2nd time
			if (lis_result_sourcedid != null) buf.append("<input type=hidden name='lis_result_sourcedid' value='" + URLEncoder.encode(lis_result_sourcedid,"UTF-8") + "'>");
			buf.append("<tr><td>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=Quiz" + ("Quiz".equals(assignmentType)?" CHECKED":"") + ">Quiz<br>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=Homework" + ("Homework".equals(assignmentType)?" CHECKED":"") + ">Homework<br>"
					+ "<input type=radio name=AssignmentType onClick='inspectRadios();' value=PracticeExam" + ("PracticeExam".equals(assignmentType)?" CHECKED":"") + ">Practice&nbsp;Exam"
					+ "</td><td id=topicSelect style='visibility:hidden'>");
			buf.append("<SELECT NAME=TopicId><OPTION Value='0'" + ("0".equals(tId)?" SELECTED":"") + ">Select a topic</OPTION>");			
			List<Topic> topics = ofy.query(Topic.class).order("orderBy").list();
			for (Topic t : topics) if (!t.orderBy.equals("Hide")) buf.append("<OPTION VALUE='" + t.id + "'" + (String.valueOf(t.id).equals(tId)?" SELECTED":"") + ">" + t.title + "</OPTION>");			 
			buf.append("</SELECT><input type=submit name=UserRequest value=Go>"
					+ "</td></tr>"
					+ "<tr><td colspan=2 id=topicCheck style='visibility:hidden'>"
					+ "Checkboxes go here"
					+ "/td></tr>"
					+ "</form></table>");
			buf.append("<script>inspectRadios()</script>");
		} catch (Exception e) {
			return e.getMessage();
		}
		return buf.toString();
	}

	public void doError(HttpServletRequest request, HttpServletResponse response, 
			String s, String message, Exception e)
	throws java.io.IOException
	{
		//System.out.println(s);
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