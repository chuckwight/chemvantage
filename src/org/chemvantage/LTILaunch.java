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
				User user = User.getUser(request.getParameter("sig"));
				if (user==null) throw new Exception();
				
				if (!user.isInstructor()) throw new Exception("User must be instructor to update this assignment.");
				
				Assignment myAssignment = updateAssignment(request,user);
				boolean refresh = Boolean.parseBoolean(request.getParameter("Refresh"));
				
				if (!refresh && myAssignment.isValid()) {
					ofy().save().entity(myAssignment).now();  // we will need this in a few milliseconds					
					String redirectUrl = "/" + request.getParameter("AssignmentType") + "?sig=" + user.getTokenSignature();
					response.sendRedirect(redirectUrl);	
				} else {  // send the user back to the resourcePickerForm
					int topicKey = -1;
					try {topicKey = Integer.parseInt(request.getParameter("TopicKey"));} catch (Exception e) {}
					response.getWriter().println(Home.header("Select A ChemVantage Assignment") + pickResourceForm(user,myAssignment,topicKey) + Home.footer);
				}
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
				Date now = new Date();
				Date yesterday = new Date(now.getTime()-86400000L);  // 24 hrs ago
				if (tc.lastLogin==null || tc.lastLogin.before(yesterday)) {
					tc.lastLogin = now;
					tc.launchParameters = request.getParameterMap();
					ofy().save().entity(tc);  // update the lastLogin value
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
			
			if (tool_state != null && platform_state != null) { // This is a LTIv1.1.2 relaunch response. Validate the tool_state value
				try {
				    Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
				    JWT.require(algorithm)
				        .withIssuer("https://www.chemvantage.org")
				        .withClaim("platform_state", platform_state)
				        .build().verify(tool_state);
					if (tc.lti_version==null || !tc.lti_version.equals("LTI-1p1p2")) {
						tc.lti_version="LTI-1p1p2";
						ofy().save().entity(tc);  // should have to do this only once
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
			} 
			// End of LTIv1p1p2 section. Continue with normal LTI launch sequence
						
			// Gather some information about the user
			String userId = request.getParameter("user_id");
			userId = oauth_consumer_key + ":" + (userId==null?"":userId);

			// Process user information, provision a new user account if necessary, and store the userId in the user's session
			User user = new User(userId);
			//user.email = request.getParameter("lis_person_contact_email_primary");
			
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
				user.setAssignment(myAssignment.id,lis_result_sourcedid);
				debug.append("User token set...");
				if (lisOutcomeServiceUrl != null && !lisOutcomeServiceUrl.equals(myAssignment.lis_outcome_service_url)) {
					myAssignment.lis_outcome_service_url = lisOutcomeServiceUrl;
					saveAssignment = true;
				}
				if (saveAssignment) ofy().save().entity(myAssignment); 
			} catch (Exception e) {  // or create a new one with the available information (but no assignmentType or topicIds)
				myAssignment = new Assignment(oauth_consumer_key,resource_link_id,lisOutcomeServiceUrl,true);
				ofy().save().entity(myAssignment).now(); // we'll need the new id value immediately
				user.setAssignment(myAssignment.id,lis_result_sourcedid);
				debug.append("User token set...");
			}
			debug.append("assignmentId=" + myAssignment.id + "...");			
			
			// At this point we should have a valid Assignment, but it may not have an 
			// assignmentType or topicId(s). If so, show the the pickResource form:
			if (myAssignment.isValid()) {
				switch (myAssignment.assignmentType) {
				case "Quiz":
					redirectUrl = "/Quiz.jsp" + "?sig=" + user.getTokenSignature();
				default:
					redirectUrl = "/" + myAssignment.assignmentType + "?sig=" + user.getTokenSignature();
				}
				response.sendRedirect(redirectUrl);
			} else response.getWriter().println(Home.header("Select A ChemVantage Assignment") + pickResourceForm(user,myAssignment,1) + Home.footer);
			return;

		} catch (Exception e) {
			doError(request, response,"LTI Launch failed. " + e.getMessage() + "<br>" + debug.toString(),null, e);
		}		
	}
	
	Assignment updateAssignment(HttpServletRequest request, User user) throws Exception {			
		long assignmentId = user.getAssignmentId();
		if (assignmentId == 0L) throw new Exception("Assignment ID was 0L.");
		
		Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		a.assignmentType = request.getParameter("AssignmentType");
		
		if (a.assignmentType.contentEquals("Quiz") || a.assignmentType.contentEquals("Homework")) {
			try {
				a.topicId = Long.parseLong(request.getParameter("TopicId"));
				if (a.topicId>0) a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			} catch (Exception e) {}
		} else if (a.assignmentType.contentEquals("VideoQuiz")) {
			try {
				a.videoId = Long.parseLong(request.getParameter("VideoId"));
			} catch (Exception e) {}
		} else if (a.assignmentType.contentEquals("PracticeExam")) {
			try {
				String[] topicIds = request.getParameterValues("TopicIds");
				if (topicIds==null || topicIds.length<3) throw new Exception("You must choose at least three topics for this practice exam.");
				a.topicIds = new ArrayList<Long>();
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<topicIds.length;i++) {
					long tId = Long.parseLong(topicIds[i]);
					a.topicIds.add(tId);
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tId).keys().list());
				}
			} catch (Exception e) {}
		}
		return a;		
	}

	String pickResourceForm(User user,Assignment myAssignment,int topicKey) throws Exception {
		StringBuffer buf = new StringBuffer();

		// Print a nice banner
		buf.append("<img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo' align=left>"
				+ "<span>Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
				+ "<br>An Open Education Resource</span>");

		buf.append("<h2>Assignment Setup Page</h2>"
				+ "The link that you just activated in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p>");
		
		if (user.isInstructor()) {
			buf.append("Please select the ChemVantage assignment that should be associated with this link. "
					+ "ChemVantage will remember this choice and send students directly to the assignment.<p>");
			/*
			 *   THIS SECTION IS TO WARN INSTUCTORS OF IMPENDING CHANGES TO THE CHEMVANTAGE SERVICE:
			 *   01JAN2021: LTI VERSION 1.1 IS DEPRECATED
			 */
			Date now = new Date();
			Date Jan2021 = new Date(1609477200000L);
			if (now.after(Jan2021)) {
				String message = "You are currently using LTI version 1.1 to connect to ChemVantage. This version of LTI was deprecated by the IMS Global "
						+ "Learning Corsortium on December 31, 2020. We highly recommend that you register ChemVantage in your LMS using the current "
						+ "LTI version 1.3. Your LMS and/or ChemVantage may discontinue support for version 1.1 at any time.";
				buf.append("<SCRIPT>alert('" + message + "');</SCRIPT>");
			}
			// ============ END OF WARNING SECTION ==================================
			// ======================================================================

		} else {
			buf.append("<b>Please ask your instructor to click the LMS assignment link to make this missing association.</b> "
					+ "You will not be able to complete this assignment until after this has been done.");
			return buf.toString();
		}
		
		// Start building a form to select the assignment attributes:
		// The form has 4 sections:
		// 1. A group of radio buttons to specify the AssignmentType (always visible)
		// 2. A group of radio buttons (or drop-down selector) to specify the TopicKey (visible when AssignmentType is selected)
		// 3. A group of radio buttons to select a single topic for Quiz or Homework assignment (visible when AssignmentType is Quiz or Homework)
		// 4. A group of checkboxes to select 3 or more topics for a Practice Exam (visible when AssignmentType is PracticeExam)
		// Clicking any AssignmentType button or loading the page with a valid AssignmentType makes the TopicKey set (2) visible
		// and makes the relevant table of radio buttons (3) or checkboxes (4) visible (and clears and hides the opposite one).
		// Clicking any TopicKey button reloads the page from the server with a modified set of topics
		
		buf.append("<form name=AssignmentForm method=POST>");
		buf.append("<input type=hidden name=UserRequest value=UpdateAssignment>");
		buf.append("<input type=hidden name=sig value='" + user.getTokenSignature() + "'>");
		buf.append("<input type=hidden name=Refresh value=false>");
		
		String assignmentType = myAssignment.assignmentType; // convenience variable; may be null for new Assignment
		
		// Build a table for Parts 1 and 2 (side by side in 1 row)
		buf.append("<div style='display:table'><div style='display:table-row'><div style='display:table-cell'>");
		buf.append("Select the type of assignment to create...<br>");
		buf.append("<label><input type=radio name=AssignmentType " + ("Quiz".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Quiz'>Quiz</label><br>"
				+ "<label><input type=radio name=AssignmentType " + ("Homework".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Homework'>Homework</label><br>"
				+ "<label><input type=radio name=AssignmentType " + ("VideoQuiz".equals(assignmentType)?"checked ":" ") + "onClick=showVideos(); value='VideoQuiz'>Video</label><br>"
				+ "<label><input type=radio name=AssignmentType " + ("PracticeExam".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='PracticeExam'>Practice&nbsp;Exam</label><p>");
		buf.append("</div>");
		
		// Put Part 2 in a cell on the right side of the first row
		buf.append("<div id=topicKeySelect style='display:table-cell;visibility:" + (assignmentType==null || assignmentType.equals("VideoQuiz")?"hidden":"visible") + "'>");
		buf.append("and a group of topics to choose from:<br>");
		buf.append("<label><input type=radio name=TopicKey value=0 " + (topicKey==0?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Show all topics</label><br>"
				+ "<label><input type=radio name=TopicKey value=1 "+ (topicKey==1?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Topics for OpenStax Chemistry 2e (recommended)</label><br>");
		buf.append("</div></div></div>");
		// End of top table
		
		// Load a javascript function to count or clear the selected check boxes and radio buttons
		buf.append("<script>"
				+ "function countChecks() {"
				+ "  var boxes = document.getElementsByName('TopicIds');"
				+ "  var count=0;"
				+ "  for (i=0;i<boxes.length;i++) if (boxes[i].checked==true) count++;"
				+ "  if (count<3) {"
				+ "    document.getElementById('checksub').disabled=true;"
				+ "    document.getElementById('checksub').value='Select at least 3 topics for this assignment';"
				+ "  } else {"
				+ "    document.getElementById('checksub').disabled=false;"
				+ "    document.getElementById('checksub').value='Create the exam from the selected topics';"
				+ "  }"
				+ "}"
				+ "function showTopics() {"
				+ "  document.getElementById('topicKeySelect').style.visibility='visible';"
				+ "  var aTypes = document.getElementsByName('AssignmentType');"
				+ "  var type;"
				+ "  for (i=0;i<aTypes.length;i++) if (aTypes[i].checked) type = (aTypes[i].value=='PracticeExam'?'check':'radio');"
				+ "  if (type == 'radio') {"
				+ "    document.getElementById('radioSelect').style.display='block';"
				+ "    document.getElementById('checkSelect').style.display='none';"
				+ "    document.getElementById('videoSelect').style.display='none';"
				+ "    clearChecks();"
				+ "  } else if (type = 'check') {"
				+ "    document.getElementById('radioSelect').style.display='none';"
				+ "    document.getElementById('checkSelect').style.display='block';"
				+ "    document.getElementById('videoSelect').style.display='none';"
				+ "    clearRadios();"
				+ "  }"
				+ "}"
				+ "function showVideos() {"
				+ "  document.getElementById('topicKeySelect').style.visibility='hidden';"
				+ "  document.getElementById('radioSelect').style.display='none';"
				+ "  document.getElementById('checkSelect').style.display='none';"
				+ "  document.getElementById('videoSelect').style.display='block';"
				+ "  clearChecks();"
				+ "  clearRadios();"
				+ "}"
				+ "function clearChecks() {"
				+ "  var boxes = document.getElementsByName('TopicIds');"
				+ "  for (i=0;i<boxes.length;i++) boxes[i].checked=false;"
				+ "}"
				+ "function clearRadios() {"
				+ "  var boxes = document.getElementsByName('TopicId');"
				+ "  for (i=0;i<boxes.length;i++) boxes[i].checked=false;"
				+ "}"
				+ "</script>");	

		// Each textbook is associated with an integer topicKey in sequence of powers of 2 so that for each text i, topicKey[i] = 2^(i-1) where i=1,2,3,...,N
		// Each topic has a topicGroup attribute which is the sum of the sum of topicKeys for aligned texts, so topicGroup ranges from 0 to 2^(N)-1
		// topicGroup=3 means alignment with both text1 and text2, topicGroup=4 means alignment only with text3, and so on.
		// In general, the topicGroup value includes a topicKey iff topicGroup % 2*topicKey / topicKey == 1 where % and / are the integer modulus and div operators
		// A topic having topicGroup = 0 means that the topic does not align with any particular text, but can be viewed if topicKey = 0 (meaning view all topics).

		// Retrieve the entire list of topics from the datastore
		List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
		// Split the topics List into two separate lists corresponding to first-semester and second-semester topics (traditional)
		// The orderBy attribute starts with a 1 or 2, except pre-semester assessments and hidden topics
		List<Topic> sem1Topics = new ArrayList<Topic>();
		List<Topic> sem2Topics = new ArrayList<Topic>();
		for (Topic t : topics) {
			if (t.orderBy.startsWith("1") && (topicKey==0 || t.topicGroup%(2*topicKey)/topicKey==1)) sem1Topics.add(t);
			else if (t.orderBy.startsWith("2") && (topicKey==0 || t.topicGroup%(2*topicKey)/topicKey==1)) sem2Topics.add(t);
		}
		
		// Make a separate list of videos with embedded quizzes to display in a radio-type video selector
		List<Video> videos = ofy().load().type(Video.class).order("orderBy").list();
		// Split the topics List into two separate lists corresponding to first-semester and second-semester topics (traditional)
		// The orderBy attribute starts with a 1 or 2, except pre-semester assessments and hidden topics
		List<Video> sem1Videos = new ArrayList<Video>();
		List<Video> sem2Videos = new ArrayList<Video>();
		for (Video v : videos) {
			if (v.orderBy.startsWith("1")) sem1Videos.add(v);
			else if (v.orderBy.startsWith("2") ) sem2Videos.add(v);
		}

		String selectorType = "";
		if ("Quiz".equals(assignmentType) || "Homework".equals(assignmentType)) selectorType = "radio";
		else if ("VideoQuiz".equals(assignmentType)) selectorType = "video";
		else if ("PracticeExam".equals(assignmentType)) selectorType = "check";
		
		// Create a radio-type selector for video quiz assignments
		buf.append("<div id=videoSelect style='display:" + (selectorType.equals("video")?"block":"none") + "'>");
		buf.append("<font color=red>Please assign one video to watch:</font><br>");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Video v : sem1Videos) buf.append("<label><input type=radio name=VideoId value=" + v.id + " onClick=this.form.vidsub.disabled=false;>" + v.title + (v.breaks==null?"":" *") + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Video v : sem2Videos) buf.append("<label><input type=radio name=VideoId value=" + v.id + " onClick=this.form.vidsub.disabled=false;>" + v.title + (v.breaks==null?"":" *") + "</label><br>");
		buf.append("</div></div></div><br>");  // end of cell, row, table
		buf.append("Video marked with an asterisk (*) have embedded quizzes; others will give full credit for watching to the end.<br>");
		buf.append("<input type=submit name=vidsub disabled=true value='Select this video'>"); // submit button for radios
		buf.append("</div>"); // end of big box with radio buttons for video selection

		// Create a table with radio buttons for Quiz or Homework assignments
		buf.append("<div id=radioSelect style='display:" + (selectorType.equals("radio")?"block":"none") + "'>");  // big box containing radio buttons
		buf.append("<font color=red>Please select one topic for this assignment:</font><br>");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false;>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false;>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit name=radsub disabled=true value='Select this topic'>"); // submit button for radios
		buf.append("</div>"); // end of big box with radio buttons

		// Create a table with check boxes for Practice Exam assignments
		buf.append("<div id=checkSelect style='display:" + (selectorType.equals("check")?"block":"none") + "'>"); // big box containing check boxes
		buf.append("<font color=red>Please select 3 or more topics for this exam:</font><br>");
		buf.append("<div style='display:table'>"); // start table of check boxes
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks();>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks();>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit id=checksub disabled=true value='Select at least 3 topics for this assignment'><br>");
		buf.append("</div>"); // end of big box with check boxes
		
		buf.append("</form>");
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