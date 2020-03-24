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
			if ("UpdateAssignment".equals(request.getParameter("UserRequest"))) {
				User user = User.getUser(request.getParameter("Token"));  // may be null for LTI launch request
				if (user==null) throw new Exception("Invalid user. Token may have expired.");
				
				Assignment myAssignment = updateAssignment(request,response);
				ofy().save().entity(myAssignment).now();  // we will need this in a few milliseconds
				
				// construct a redirectUrl to the new assignment
				String redirectUrl = "/" + request.getParameter("AssignmentType") + "?Token=" + user.token;
				response.sendRedirect(redirectUrl);
			} else if (request.getParameter("lti_message_type")!=null) { // handle LTI launch request for LTIv1p0 and LTIv1p1
				basicLtiLaunchRequest(request,response);
			} else if (request.getParameter("id_token")!=null) throw new Exception("Launch URL was incorrect.");
				else doError(request,response,"Invalid LTI launch request. Missing lti_message_type.",null,null); 
		} catch (Exception e) {
			doError(request,response,"","",e);
		}
	}

	void basicLtiLaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws IOException {
		StringBuffer debug = new StringBuffer();
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
			debug.append("Basic LTI launch message validated...");
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
			//boolean securityAlert = false;
			
			if (tool_state != null && platform_state != null) { // This is a LTIv1.1.2 relaunch response. Validate the tool_state value
				try {
				    Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
				    JWT.require(algorithm)
				        .withIssuer("https://www.chemvantage.org")
				        .withClaim("platform_state", platform_state)
				        .build().verify(tool_state);
					if (tc.lti_version==null || !tc.lti_version.equals("LTI-1p1p2")) {
						tc.lti_version="LTI-1p1p2";
						ofy().save().entity(tc);
				}
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
				    lti_version = "LTI-1p1p2_proposed";
				} catch (Exception e){
					throw new Exception("Tool state JWT could not be created.");
				}
			    return;  // wait for relaunch from platform
			} else { // this is an basic LTIv1.1.1 launch not supported after Dec 31, 2020
				Date jan2021 = new Date(1609477200000L); // January 1, 2021
				Date now = new Date();
				if (now.after(jan2021)) throw new Exception("Due to potential internet security flaws, the version of LTI "
						+ "supported by your LMS was <a href=https://www.imsglobal.org/lti-security-announcement-and-deprecation-schedule-july-2019> "
						+ "deprecated by IMS Global Learning Solutions</a> effective 1 January 2021. We are therefore unable "
						+ "to support this connection until you upgrade to an LMS that supports, at a minimum, the LTI version "
						+ "1.1.2 security update. We apologize for this inconvenience.");
			}
			// End of LTIv1p1p2 section. Continue with normal LTI launch sequence
			
			// Gather some information about the user
			String userId = request.getParameter("user_id");
			userId = oauth_consumer_key + ":" + (userId==null?"":userId);

			// Process user information, provision a new user account if necessary, and store the userId in the user's session
			User user = new User(userId);

			// check if user has Instructor or Administrator role
			String roles = request.getParameter("roles");
			if (roles != null) {
				roles = roles.toLowerCase();
				user.setIsInstructor(roles.contains("instructor"));
				user.setIsAdministrator(roles.contains("administrator"));
				user.setIsTeachingAssistant(roles.contains("teachingassistant"));
			}
			// user information OK;
			debug.append("userId=" + userId + " and role=" + (user.isInstructor()?"Instructor":"Learner") + "...");

			// Gather information that may be needed to return a score to the LMS:
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			debug.append("lis_result_sourcedid=" + lis_result_sourcedid + "...");
			String lisOutcomeServiceUrl = request.getParameter("lis_outcome_service_url");
			debug.append("lis_outcome_service_url=" + lisOutcomeServiceUrl + "...");
			
			// Use the resourceLinkId to find the assignment or create a new one:
			Assignment myAssignment = null;
			String redirectUrl = null;
			boolean saveAssignment = false;
			try {  // load the requested Assignment entity if it exists
				myAssignment = ofy().load().type(Assignment.class).filter("domain",oauth_consumer_key).filter("resourceLinkId", resource_link_id).first().safe();
				debug.append("Found assignment: ");
				user.setToken(myAssignment.id,lis_result_sourcedid);
				debug.append("User token set...");
				if (lisOutcomeServiceUrl != null && !lisOutcomeServiceUrl.equals(myAssignment.lis_outcome_service_url)) {
					myAssignment.lis_outcome_service_url = lisOutcomeServiceUrl;
					saveAssignment = true;
				}
				if (saveAssignment) ofy().save().entity(myAssignment); 
			} catch (Exception e) {  // or create a new one with the available information (but no assignmentType or topicIds)
				myAssignment = new Assignment(oauth_consumer_key,resource_link_id,lisOutcomeServiceUrl,true);
				ofy().save().entity(myAssignment).now(); // we'll need the new id value immediately
				user.setToken(myAssignment.id,lis_result_sourcedid);
				debug.append("User token set...");
			}
			debug.append("assignmentId=" + myAssignment.id + "...");
			
			// At this point we should have a valid Assignment, but it may not have an 
			// assignmentType or topicId(s). If so, show the the pickResource form:
			if (myAssignment.isValid()) {
				redirectUrl = "/" + myAssignment.assignmentType + "?Token=" + user.token;
				
				// Warn instructor of LTI-1p1p2 security patch requirement
				boolean after1July2020 = new Date().after(new Date(1593576000000L)); // July 1, 2020
				if (after1July2020 && tc.lti_version.contentEquals("LTI-1p0") && user.isInstructor()) redirectUrl += "&SecurityAlert=true";
				
				debug.append("Redirecting to: " + redirectUrl);
				response.sendRedirect(redirectUrl);
			} else response.getWriter().println(Home.header + pickResourceForm(user,myAssignment) + Home.footer);
			return;

		} catch (Exception e) {
			doError(request, response,"LTI Launch failed. " + e.getMessage() + "<br>" + debug.toString(),null, e);
		}		
	}
	
	Assignment updateAssignment(HttpServletRequest request, HttpServletResponse response) throws Exception {			
		User user = User.getUser(request.getParameter("Token"));
		if (user==null) throw new Exception("Unable to identify user because the token was expired or invalid.");
		if (!user.isInstructor()) throw new Exception("User must be instructor to update thisd assignment.");
		long assignmentId = user.getAssignmentId();
		if (assignmentId == 0L) throw new Exception("Assignment ID was 0L.");
		Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		a.assignmentType = request.getParameter("AssignmentType");
		
		if (a.assignmentType.contentEquals("Quiz") || a.assignmentType.contentEquals("Homework")) {
			try {
				a.topicId = Long.parseLong(request.getParameter("TopicId"));
				if (a.topicId>0) a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			} catch (Exception e) {}
		} else if (a.assignmentType.contentEquals("PracticeExam")) {
			try {
				String[] topicIds = request.getParameterValues("TopicIds");
				if (topicIds==null || topicIds.length<3) throw new Exception("You must choose at least three topics for thisd practice exam.");
				a.topicIds = new ArrayList<Long>();
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<topicIds.length;i++) {
					long tId = Long.parseLong(topicIds[i]);
					a.topicIds.add(tId);
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tId).keys().list());
				}
			} catch (Exception e) {}
		}
		//if (a.assignmentType != null) ofy().save().entity(a).now(); // going to need this is just a few milliseconds
		return a;		
	}

	String pickResourceForm(User user,Assignment myAssignment) throws Exception {
		return pickResourceForm(user,myAssignment,0);
	}
	
	String pickResourceForm(User user,Assignment myAssignment,int topicKey) throws Exception {
		StringBuffer buf = new StringBuffer();

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
				+ "function refreshTopics() {"
				+ "  var asstyp = document.getElementByName('AssignmentType').value;"
				+ "  var inpts = document.getElementsByName('TopicId');"
				+ "  var cls = document.getElementByName('Cls').value;"
				+ "  var labls=document.querySelectorAll('0');"
				+ "  for (i=0;i<inpts.length;i++) {"
				+ "    if (asstyp=='Quiz' || asstyp=='Homework') inpts[i].type=radio;"
				+ "    else if (asstyp=='Practice Exam') inpts[i].type=checkbox;"
				+ "  }"
				+ "  for (i=0;i<labls.length;i++) {"
				+ "    if (labls[i].hasClass(cls)) labls[i].style.display='';"
				+ "    else labls[i].style.display='none';"
				+ "  }"
				+ "}"
				+ "</script>");

		List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
		// Split the topics List into two separate lists corresponding to first-semester and second-semester topics (traditional)
		// The orderBy attribute starts with a 1 or 2, except pre-semester assessments and hidden topics
		List<Topic> sem1 = new ArrayList<Topic>();
		List<Topic> sem2 = new ArrayList<Topic>();
		for (Topic t : topics) {
			if (t.orderBy.startsWith("1")) sem1.add(t);
			else if (t.orderBy.startsWith("2")) sem2.add(t);
		}
		
		// Print a table containing the following main sections:
		// Top left (always visible) radio buttons to select the AssignmentType
		// Top right (visible after selecting AssignmentType) radio buttons to select topicGroup (topics that align with a particular textbook)
		// Below (visible after selecting topicGroup), a 2-column table of topics (semesters 1 & 2) with radio buttons or check boxes 
		// appropriate to the AssignmentType. When changing topicGroup or AssignmentType, the topic inputs must be refreshed for the correct
		// input type and visibility.
		
		buf.append("<table><form name=AssignmentForm method=POST>");
		buf.append("<input type=hidden name=UserRequest value=UpdateAssignment>");
		buf.append("<input type=hidden name=Token value='" + user.token + "'>");
		
		// Radio buttons to select the AssignmentType:
		buf.append("<tr><td>"
				+ "<label><input type=radio name=AssignmentType onClick=document.getElementById('topicKeySelect').style.visibility='visible'; value=Quiz>Quiz</label><br>"
				+ "<label><input type=radio name=AssignmentType onClick=document.getElementById('topicKeySelect').style.visibility='visible'; value=Homework>Homework</label><br>"
				+ "<label><input type=radio name=AssignmentType onClick=document.getElementById('topicKeySelect').style.visibility='visible'; value=PracticeExam>Practice&nbsp;Exam</label><br>"
				+ "</td>");
		//========================== MODIFIED CODE ===========================
		// Radio buttons to select TopicKey
		int maxClasses = 1;  // number of textbooks from which aligned topics can be selected. "class" refers to the CSS attribute of the input labels below
		// Each textbook is associated with a class integer (text1 has class=1, text2 has class=2, etc.
		// The topicKey for each class is equal to 2^(class-1), so the topicKey for text1 is 1, for text2 is 2, for text3 is 4, text4 is 8, etc.
		// Each topic may be aligned with one or more textbooks and has a topicGroup attribute, which is an integer from 0 to 2^(maxClasses)-1;
		// The topicGroup value is equal to the sum of the topicKey values for the texts with which it is aligned
		// Examples: topicGroup=0 means no alignment, topicGroup=1 means alignment with text1 topicGroup=2 means alignment with text2
		// topicGroup=3 means alignment with both text1 and text2, topicGroup=4 means alignment with text3
		// In general, the topicGroup value includes a class iff topicGroup % (2^class) / 2^(class-1) == 1 where % and / are the integer modulus and div operators
		// Alternatively, topicGroup includes a class if topicGroup % (2*topicKey) / topicKey == 1
		// A topicKey value of 0 is special, and means "include all topics"
		
		// Create a set of radio buttons to select a topic group (by class value):
		buf.append("<td id=topicKeySelect style='visibility:hidden'>"
				+ "Select a topic group:<br>"
				+ "<label><input type=radio name=Cls value=0 onClick=refreshTopics();>Show all topics</label><br>"
				+ "<label><input type=radio name=Cls value=1 onClick=refreshTopics();>Show only topics for the OpenStax Chemistry 2e text</label><br>"
				+ "</td></tr>");

		// Create a second row of the table with two columns, one for each traditional semester topics
		buf.append("<tr><td>");   // left column Chem1 topics		
		for (Topic t : sem1) {
			String cls = "0";
			for (int c=1; c<maxClasses; c++) cls += t.topicGroup % (int)Math.pow(2,c) / (int)Math.pow(2,c-1) == 1?" " + c:"";
			buf.append("<label class='" + cls + "' style='display:none'><input type=radio name=TopicId value=" + t.id + "></label><br>");
		}
		buf.append("</td><td>");  // right column Chem2 topics
		for (Topic t : sem2) {
			String cls = "0";
			for (int c=1; c<maxClasses; c++) cls += t.topicGroup % (int)Math.pow(2,c) / (int)Math.pow(2,c-1) == 1?" " + c:"";
			buf.append("<label class='" + cls + "' style='display:none'><input type=radio name=TopicId value=" + t.id + "></label><br>");
		}
		buf.append("</td></tr></table>");
		
		
/*		
		//=========================== ORIGINAL CODE ==========================
		// Radio buttons to select TopicGroup
		buf.append("<td id=topicKeySelect style='visibility:hidden'>"
				+ "<label><input type=radio name=TopicKey value=0" + (topicKey==0?" checked":"") + " onClick=this.form.RefreshTopics.value='true';this.form.submit();>Show all topics</label><br>"
				+ "<label><input type=radio name=TopicKey value=1" + (topicKey==1?" checked":"") + " onClick=this.form.RefreshTopics.value='true';this.form.submit();>Show topics aligned with OpenStax Chemistry 2e text</label><br>"
				+ "<input type=hidden name=RefreshTopics value=false></td></tr>");
		
		// Select box to select the Quiz or Homework topic
		buf.append("<tr id=topicSelect style='display:none'><td colspan=2>"
				+ "<p><FONT COLOR=RED>Please select one topic for this quiz or homework assignment.</FONT><br>"
				+"<SELECT NAME=TopicId onChange=document.AssignmentForm.start.disabled=(document.AssignmentForm.TopicId.selectedIndex==0);>"
				+ "<OPTION Value='0'" + (myAssignment.topicId==0L?" SELECTED":"") + ">Select a topic</OPTION>");			

		for (Topic t : topics) { //if (!t.orderBy.equals("Hide") && (topicGroup==0 || t.topicGroup%(topicGroup+1)/topicGroup==1)) {
			if ("Hide".equals(t.orderBy) || (topicKey>0 && t.topicGroup%(topicKey*2)/topicKey==0)) continue;
			buf.append("<OPTION VALUE='" + t.id + "'" + (t.id==myAssignment.topicId?" SELECTED":"") + ">" + t.title + "</OPTION>");			 
		}
		buf.append("</SELECT><input type=submit name=start disabled=true></td></tr>");
				
		// Table of PracticExam topics
		buf.append("<tr id=topicCheck style='display:none'><td colspan=2>"
				+ "<TABLE>");
		buf.append("<TR><TD COLSPAN=3 style='color:red'><p>Please select at least 3 topics for this practice exam:<br></TD></TR>");
		int i = 0;
		for (Topic t : topics) {
			if ("Hide".equals(t.orderBy) || (topicKey>0 && t.topicGroup%(topicKey*2)/topicKey==0 )) continue;
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
		//===================================================================
*/
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
		out.println(s + (e==null?"":"<br>" + e.toString()) + message);
	}

	@Override
	public void destroy() {

	}
}