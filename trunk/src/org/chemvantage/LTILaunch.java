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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	Map<String,String> sharedSecrets = new HashMap<String,String>();
	private static final long serialVersionUID = 137L;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			if (user != null && "pick".equals(request.getParameter("UserRequest")))
				out.println(pickResourceForm(user,request));
			else if (user != null && "Go".equals(request.getParameter("UserRequest")))
				response.sendRedirect(resourceUrlFinder(user,request));
			else doPost(request, response);
		} catch (Exception e) {}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		if (Login.lockedDown) doError(request,response,"ChemVantage is temporarily unavailable, sorry.",null,null);

		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		String userId = oauth_consumer_key + ":" + request.getParameter("user_id");
		String context_id = request.getParameter("context_id");
		String resource_link_id = request.getParameter("resource_link_id");
		if ( ! "basic-lti-launch-request".equals(request.getParameter("lti_message_type")) || oauth_consumer_key == null || resource_link_id == null ) {
			doError(request, response, "LTI launch request was missing a required parameter.", null, null);
			return;
		}
		
		// Lookup the secret that corresponds to the oauth_consumer_key in the AppEngine datastore
		if (!sharedSecrets.containsKey(oauth_consumer_key)) {
			String secret = BLTIConsumer.getSecret(oauth_consumer_key);
			if (secret != null) sharedSecrets.put(oauth_consumer_key,secret);
		}
		String oauth_secret = sharedSecrets.get(oauth_consumer_key);

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
		boolean isInstructor = false;
		boolean isAdministrator = false;
		String userRole = request.getParameter("roles");
		if (userRole != null) {
			userRole = userRole.toLowerCase();
			isInstructor = userRole.contains("instructor");
			isAdministrator = userRole.contains("administrator");
		}
		if (isInstructor != !user.isInstructor()) {  // new instructor status
			user.setIsInstructor(isInstructor);
			user.setPremium(true);
			ofy.put(user);
		}		
		if (isAdministrator != !user.isAdministrator()) {  // new Administrator status
			user.setIsAdministrator(isAdministrator);
			user.setPremium(true);
			ofy.put(user);
		}		
		
		// Check to see if the LMS is providing an LIS Outcome Service URL (LTI v1.1)
		String lisOutcomeServiceUrl = request.getParameter("lis_outcome_service_url");
		// the lis_result_sourcedid is an optional LTI parameter that specifies a context gradebook entry point
		String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
		boolean supportsLIS = lisOutcomeServiceUrl != null && lisOutcomeServiceUrl != null;
		
		// Provision a new context (group), if necessary and put the user into it
		Group g = null;
		if (context_id != null && !context_id.isEmpty()) {
			g = ofy.query(Group.class).filter("context_id",context_id).get();
			if (g == null) { // create this new group
				String contextTitle = request.getParameter("context_title");
				if (contextTitle==null) contextTitle = "";
				g = new Group("BLTI",context_id,contextTitle);
				g.domain = domain.domainName;
				ofy.put(g);
			}
			
			if (supportsLIS && !lisOutcomeServiceUrl.equals(g.lis_outcome_service_url)) {  // update the URL as a Group property
				g.lis_outcome_service_url=lisOutcomeServiceUrl;
				ofy.put(g);
			}							
			
			if (user.isInstructor()) {
				if (g.instructorId.equals("unknown")) {  // assign the instructor to this group
					g.instructorId = user.id;
					ofy.put(g);
				}
			}
		}

		// assign the user to a new group, if necessary and eligible
		if (g != null && user.myGroupId != g.id && user.processPremiumUpgrade(g)) user.changeGroups(g.id);
		if (!user.hasPremiumAccount() && user.myGroupId > 0) user.changeGroups(0L);  // boots basic users out of groups
				
		
		if (g==null) {  // no context data was contained in the launch parameters; send the user to the Home page
			user.changeGroups(0L);
			response.sendRedirect("/Home");
			return;
		}

		// Use the resourcePicker method to discover the URL for the assignment associated with this link
		String redirectUrl = resourceUrlFinder(user,request);
		
		if (redirectUrl.equals("/Verification")) {
			session.setAttribute("ResourceLinkId", resource_link_id);
			session.setAttribute("GroupId", g.id);
			if (supportsLIS) session.setAttribute("LisResultSourcedid", lis_result_sourcedid);
		} 

		response.sendRedirect(redirectUrl);
	}		
	
	String resourceUrlFinder(User user, HttpServletRequest request) {
		String redirectUrl = "";
		Date now = new Date();
		Date sixMonthsFromNow = new Date(now.getTime() + 15768000000L);  // exact time far into the future
		try {  
			// a resource_link_id string should be provided with every valid LTI launch
			String resource_link_id = request.getParameter("resource_link_id");
			
			// the lis_result_sourcedid is an optional LTI parameter that specifies a context gradebook entry point
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			
			// if the user was not provisioned with a premium account automatically (purchase required):
			if (user.myGroupId <= 0 || user.requiresUpdatesNow()) return "/Verification";
			
			Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",user.myGroupId);
			Assignment myAssignment = null;
			for (Assignment a : assignments) 
				if (a.resourceLinkIds != null && a.resourceLinkIds.contains(resource_link_id)) {
					myAssignment = a;
					break;
				}
			if (myAssignment == null) { // try to find it based on the request data
				String assignmentType = request.getParameter("AssignmentType");
				long topicId = 0;
				if (assignmentType != null) {
					redirectUrl = "/" + assignmentType;
					try {
						topicId = Long.parseLong(request.getParameter("TopicId"));
						redirectUrl += "?TopicId=" + topicId;
						myAssignment = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType",assignmentType).filter("topicId",topicId).get();
					} catch (Exception e) {
						myAssignment = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType",assignmentType).get();	
					}
					if (myAssignment == null) {
						myAssignment = new Assignment(user.myGroupId,topicId,assignmentType,sixMonthsFromNow);
						Group g = ofy.find(Group.class,user.myGroupId);
						if (g != null) {
							g.setGroupTopicIds();
							ofy.put(g);
						}
					}
					if (user.isInstructor()) {  // if this is the instructor, remember this association for future launches
						myAssignment.resourceLinkIds.add(resource_link_id);
						ofy.put(myAssignment);
					}
				}
			}

			if (myAssignment == null) {  // assignment is unknown at this point; go pick the right one
				redirectUrl = "/lti?UserRequest=pick&resource_link_id="+resource_link_id;  // send the user to the pickResource page
			} else {  // found the assignment; configure the correct URL for redirection
				redirectUrl = "/" + myAssignment.assignmentType;
				if (myAssignment.topicId>0) redirectUrl += "?TopicId=" + myAssignment.topicId;
			}
			if (lis_result_sourcedid != null && !redirectUrl.equals("/Home")) redirectUrl += "&lis_result_sourcedid=" + URLEncoder.encode(lis_result_sourcedid,"UTF-8");
		} catch (Exception e) {
			redirectUrl="/Home";
		}
		return redirectUrl;
	}
	
	String pickResourceForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			String resource_link_id = request.getParameter("resource_link_id"); if (resource_link_id == null) return "/Home";
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			if (user.isInstructor()) {
				buf.append("<h3>Choose A ChemVantage Resource For This Link</h3>"
						+ "Please select the ChemVantage page that should be associated with the link "
						+ "that you just activated in your learning management system. "
						+ "ChemVantage will remember this choice and send students directly to the page.<p>");

				buf.append("<form method=GET><input type=hidden name=resource_link_id value='" + resource_link_id + "'>"
						+ "<input type=hidden name=AssignmentType value=Home><input type=submit name=UserRequest value=Go>"
						+ " to the <b>ChemVantage Home Page</b> (select this if the link is not associated with an assignment)</form><p>");
				buf.append("or, select the appropriate assignment type and topic below:<p>");
				buf.append("<table><form method=GET><input type=hidden name='resource_link_id' value='" + resource_link_id + "'>");
				if (lis_result_sourcedid != null) buf.append("<input type=hidden name='lis_result_sourcedid' value='" + URLEncoder.encode(lis_result_sourcedid,"UTF-8") + "'>");
				buf.append("<tr><td><input type=radio name=AssignmentType value=Quiz>Quiz</a></td><td rowspan=2>");
				buf.append("<SELECT NAME=TopicId><OPTION Value='0' SELECTED>Select a topic</OPTION>");			
				List<Topic> topics = ofy.query(Topic.class).order("orderBy").list();
				for (Topic t : topics) if (!t.orderBy.equals("Hide")) buf.append("<OPTION VALUE='" + t.id + "'>" + t.title + "</OPTION>");			 
				buf.append("</SELECT><input type=submit name=UserRequest value=Go></td></tr>"
						+ "<tr><td><input type=radio name=AssignmentType value=Homework>Homework</a></td></tr>"
						+ "</form></table>");
			} else {
				buf.append("<h3>Missing ChemVantage Assignment</h3>"
						+ "The link that you just clicked in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p>"
						+ "<b>Please ask your instructor to click the LMS assignment link to make this missing association.</b><p>"
						+ "You may go to the Home page now to work on any quizzes or homework problems.  However, no scores can be "
						+ "reported back to the LMS grade book until after your instructor has completed the link to an assignment.<p>"
						+ "<a href=/Home>Go to the ChemVantage Home Page now</a>");
			}
		} catch (Exception e) {
			buf.append(e.getMessage());
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