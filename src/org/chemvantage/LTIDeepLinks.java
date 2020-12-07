package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.googlecode.objectify.Key;

@WebServlet(urlPatterns={"/lti/deeplinks","/lti/deeplinks/"})
public class LTIDeepLinks extends HttpServlet {
	private static final long serialVersionUID = 1L;

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try { // All requests to this servlet must contain a valid ChemVantage "state" parameter
			String state = request.getParameter("state");			
			Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
			String iss = "https://" + request.getServerName();
			JWTVerifier verifier = JWT.require(algorithm).withIssuer(iss).build();
			verifier.verify(state);  // throws Exception if invalid or expired
			// At this point, the state parameter is valid
			
			JsonObject claims = validateDeepLinkRequest(request);
			User user = getUserClaims(claims);
			
			if ("Select assignment".equals(request.getParameter("UserRequest"))) {  // submitting desired links
				if (Boolean.parseBoolean(request.getParameter("Refresh"))) {
					int topicKey = 0;
					try {topicKey = Integer.parseInt(request.getParameter("TopicKey"));} catch (Exception e) {}
					out.println(contentPickerForm(user,request,claims,topicKey));
				} else out.println(deepLinkResponseMsg(request));
			} else if (request.getParameter("id_token") != null) { // This is a fresh Deep Links request.
				out.println(contentPickerForm(user,request,claims,1));
			}
		} catch (Exception e) {	 
			response.sendError(401,e.toString() + " " + e.getMessage());
		}
	}

	JsonObject validateDeepLinkRequest(HttpServletRequest request) throws Exception {
			validateStateToken(request); // ensures proper OIDC authorization flow completed			

			validateIdToken(request);  // returns the validated Deployment
			
			// Decode the JWT id_token payload as a JsonObject:
			JsonObject claims = null;
			try {
				DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
				String json = new String(Base64.getUrlDecoder().decode(id_token.getPayload()));
				claims = JsonParser.parseString(json).getAsJsonObject();
			} catch (Exception e) {
				throw new Exception("id_token was not a valid JWT.");
			}
			
			verifyLtiMessageClaims(claims);
			verifyIsInstructor(claims);
			
			return claims;
	}
	
	protected void validateStateToken(HttpServletRequest request) throws Exception {
		/* This method ensures that the state token required by LTI v1.3 standards is a
		 * valid token issued by the tool provider (ChemVantage) as part of the LTI
		 * launch request sequence. Otherwise throws a JWTVerificationException.
		 */
		String iss = "https://" + request.getServerName();
		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
		JWTVerifier verifier = JWT.require(algorithm).withIssuer(iss).build();
	    verifier.verify(request.getParameter("state"));
	}

	protected Deployment validateIdToken(HttpServletRequest request) throws Exception {
		DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
		
		// get the platform_id and deployment_id to load the correct Deployment d
		String platform_id = id_token.getIssuer();
		String deployment_id = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
		if (deployment_id == null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
		String platformDeploymentId = platform_id + "/" + deployment_id;
		
		Deployment d = Deployment.getInstance(platformDeploymentId);
		if (d==null) throw new Exception("Deployment not found: " + platformDeploymentId);
		
		// validate the id_token audience:
		List<String> aud = id_token.getAudience();
		if (aud.size()==1 && aud.get(0).contentEquals(d.client_id)); // OK, continue
		else if (aud.size()>1 && aud.contains(d.client_id) && id_token.getClaim("azp").asString().contentEquals(d.client_id)); // OK, continue
		else throw new Exception("The id_token client_id claim is not authorized in ChemVantage.");

		// validate the id_token signature:
		// retrieve the public Java Web Key from the platform to verify the signature
		if (d.well_known_jwks_url==null) throw new Exception("The deployment does not have a valid JWKS URL.");
		URL jwks_url = new URL(d.well_known_jwks_url);
		JwkProvider provider = new UrlJwkProvider(jwks_url);
		if (id_token.getKeyId() == null || id_token.getKeyId().isEmpty()) throw new Exception("No JWK id found.");
		Jwk jwk = provider.get(id_token.getKeyId()); //throws Exception when not found or can't get one
		RSAPublicKey public_key = (RSAPublicKey)jwk.getPublicKey();
		// verify the JWT signature
		Algorithm algorithm = Algorithm.RSA256(public_key,null);
		if (!"RS256".contentEquals(id_token.getAlgorithm())) throw new Exception("JWT algorithm must be RS256");
		JWT.require(algorithm).build().verify(id_token);  // throws JWTVerificationException if not valid
		return d;
	}

	void verifyLtiMessageClaims(JsonObject claims) throws Exception {
		// verify LTI version 1.3.0
		JsonElement lti_version = claims.get("https://purl.imsglobal.org/spec/lti/claim/version");
		if (lti_version == null) throw new Exception("LTI version claim was missing.");    
		if (!"1.3.0".equals(lti_version.getAsString())) throw new Exception("Incorrect LTI version claim");

		// Validate the LTI message_type:
		JsonElement message_type = claims.get("https://purl.imsglobal.org/spec/lti/claim/message_type");
		if (message_type == null) throw new Exception("Missing LTI message_type.");
		if (!"LtiDeepLinkingRequest".equals(message_type.getAsString())) throw new Exception("LTI message_type claim must be LtiDeepLinkingRequest");
	
		JsonElement deep_linking_settings = claims.get("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings");
		if (deep_linking_settings == null) throw new Exception("Required settings claim was not found.");
		
		JsonElement userId_claim = claims.get("sub");
		if (userId_claim == null) throw new Exception("The id_token was missing required subject claim");
	}
	
	void verifyIsInstructor(JsonObject claims) throws Exception {
		// use the roles claim to verify that the requester is an instructor or administrator
		boolean authorized = false;
		JsonElement roles_claim = claims.get("https://purl.imsglobal.org/spec/lti/claim/roles");
		if (roles_claim == null || !roles_claim.isJsonArray()) throw new Exception("Required roles claim is missing from the id_token");
		JsonArray roles = roles_claim.getAsJsonArray();
		Iterator<JsonElement> roles_iterator = roles.iterator();
		while(roles_iterator.hasNext()){
			String role = roles_iterator.next().getAsString().toLowerCase();
			if (role.contains("instructor") || role.contains("administrator")) authorized = true;
		}
		if (!authorized) throw new Exception("You must be logged into your LMS in an instructor "
				+ "or administrator role in order to select assignment resources for this class.");
	}
	
	String contentPickerForm(User user, HttpServletRequest request,JsonObject claims,int topicKey) throws Exception {
		StringBuffer buf = new StringBuffer(Home.header("Select ChemVantage Assignment"));
		
		JsonObject settings = claims.get("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").getAsJsonObject();
		boolean acceptsLtiResourceLink = settings.get("accept_types").getAsJsonArray().contains(new JsonPrimitive("ltiResourceLink"));
		if (!acceptsLtiResourceLink) throw new Exception("Deep Link request failed because platform does not accept new LtiResourceLinks.");
		boolean acceptsMultiple = settings.get("accept_multiple").getAsBoolean();
		
		// Print a nice banner
		buf.append(Home.banner);

		buf.append("<h2>Assignment Setup Page</h2>");

		if (acceptsMultiple) buf.append("Use this page to create multiple ChemVantage assignments in your learning management system (LMS). "
				+ "Select the assignment type (e.g., Quiz) and then select the topics of all the different quizzes to create "
				+ "(one quiz per topic). Return to this page to create multiple Video or Homework assignments, if needed. For Practice Exam "
				+ "assignents, each exam covers multiple topics, so you can create only one Practice Exam for each visit to this page.<p>");
		
		// Start building a form to select the assignment attributes:
		// The form has 4 sections:
		// 1. A group of radio buttons to specify the AssignmentType (always visible)
		// 2. A group of radio buttons (or drop-down selector) to specify the TopicKey (visible when AssignmentType is selected)
		// 3. A group of radio buttons to select a single topic for Quiz or Homework assignment (visible when AssignmentType is Quiz or Homework)
		// 4. A group of checkboxes to select 3 or more topics for a Practice Exam (visible when AssignmentType is PracticeExam)
		// Clicking any AssignmentType button or loading the page with a valid AssignmentType makes the TopicKey set (2) visible
		// and makes the relevant table of radio buttons (3) or checkboxes (4) visible (and clears and hides the opposite one).
		// Clicking any TopicKey button reloads the page from the server with a modified set of topics

		buf.append("<form name=AssignmentForm action=/lti/deeplinks method=POST>");
		buf.append("<input type=hidden name=id_token value='" + request.getParameter("id_token") + "'>");
		buf.append("<input type=hidden name=state value='" + request.getParameter("state") + "'>");
		buf.append("<input type=hidden name=UserRequest value='Select assignment'>");
		buf.append("<input type=hidden name=Refresh value=false>");

		// Build a table for Parts 1 and 2 (side by side in 1 row)
		String assignmentType = request.getParameter("AssignmentType");
		if (assignmentType==null) assignmentType = "";
		buf.append("<div style='display:table'><div style='display:table-row'><div style='display:table-cell'>");
		buf.append("Select the type of assignment to create...<br>");
		buf.append("<label><input type=radio name=AssignmentType onClick=showAssignmentTopics('Quiz'); value='Quiz'" + (assignmentType.equals("Quiz")?" CHECKED>":">") + "Quiz</label><br>"
				+ "<label><input type=radio name=AssignmentType onClick=showAssignmentTopics('Homework'); value='Homework'" + (assignmentType.equals("Homework")?" CHECKED>":">") + "Homework</label><br>"
				+ "<label><input type=radio name=AssignmentType onClick=showAssignmentTopics('VideoQuiz'); value='VideoQuiz'" + (assignmentType.equals("VideoQuiz")?" CHECKED>":">") + "Video</label><br>"
				+ "<label><input type=radio name=AssignmentType onClick=showAssignmentTopics('PracticeExam'); value='PracticeExam'" + (assignmentType.equals("PracticeExam")?" CHECKED>":">") + "Practice&nbsp;Exam</label><p>");
		buf.append("</div>");

		// Each textbook is associated with an integer topicKey in sequence of powers of 2 so that for each text i, topicKey[i] = 2^(i-1) where i=1,2,3,...,N
		// Each topic has a topicGroup attribute which is the sum of the sum of topicKeys for aligned texts, so topicGroup ranges from 0 to 2^(N)-1
		// topicGroup=3 means alignment with both text1 and text2, topicGroup=4 means alignment only with text3, and so on.
		// In general, the topicGroup value includes a topicKey iff topicGroup % 2*topicKey / topicKey == 1 where % and / are the integer modulus and div operators
		// A topic having topicGroup = 0 means that the topic does not align with any particular text, but can be viewed if topicKey = 0 (meaning view all topics).

		// Put Part 2 in a cell on the right side of the first row
		buf.append("<div id=topicKeySelect style='display:table-cell;visibility:" + (assignmentType.equals("")?"hidden":"visible") + "'>");
		buf.append("and a group of topics to choose from:<br>");
		buf.append("<label><input type=radio name=TopicKey value=0 " + (topicKey==0?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Show all topics</label><br>"
				+ "<label><input type=radio name=TopicKey value=1 "+ (topicKey==1?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Show topics for the OpenStax Chemistry 2e</label><br>");
		buf.append("</div></div></div>");
		// End of top table

		buf.append("<script>"
				+ "function showAssignmentTopics(type){"
				+ "  document.getElementById('topicKeySelect').style.visibility='visible';"
				+ "  switch (type) {"
				+ "    case 'PracticeExam': document.getElementById('examSelect').style.display='block';document.getElementById('videoSelect').style.display='none';document.getElementById('quizSelect').style.display='none';break;"
				+ "    case 'VideoQuiz': document.getElementById('examSelect').style.display='none';document.getElementById('videoSelect').style.display='block';document.getElementById('quizSelect').style.display='none';break;"
				+ "    default: document.getElementById('examSelect').style.display='none';document.getElementById('videoSelect').style.display='none';document.getElementById('quizSelect').style.display='block';"
				+ "  }"
				+ "}"
				+ "function countChecks(type) {"
				+ "  var examArray=document.getElementsByName('TopicIds');"
				+ "  var videoArray=document.getElementsByName('VideoId');"
				+ "  var quizArray=document.getElementsByName('TopicId');"
				+ "  var checkSubmit = document.getElementById('checksub');"
				+ "  var videoSubmit = document.getElementById('vidsub');"
				+ "  var radioSubmit = document.getElementById('radsub');"
				+ "  var count=0;"
				+ "  switch (type) {"
				+ "    case 'PracticeExam':"
				+ "      for (var i=0;i<examArray.length;i++) if (examArray[i].checked) count++;"
				+ "      if (count==1) {"
				+ "        for (var i=0;i<videoArray.length;i++) videoArray[i].checked=false;"
				+ "        videoSubmit.disabled = true;"
				+ "        videoSubmit.value = 'Select" + (acceptsMultiple?" at least":"") + " one video topic';"
				+ "        for (var i=0;i<quizArray.length;i++) quizArray[i].checked=false;"
				+ "        radioSubmit.disabled = true;"
				+ "        radioSubmit.value = 'Select" + (acceptsMultiple?" at least":"") + " one topic';"
				+ "      }"
				+ "      checkSubmit.disabled=(count<3);"
				+ "      if (count<3) checkSubmit.value='Select at least 3 topics';"
				+ "      else checkSubmit.value='Create this exam';"
				+ "      break;"
				+ "    case 'VideoQuiz':"
				+ "      for (var i=0;i<examArray.length;i++) examArray[i].checked=false;"
				+ "      checkSubmit.disabled = true;"
				+ "      checkSubmit.value = 'Select at least 3 topics for this exam';"
				+ "      for (var i=0;i<quizArray.length;i++) quizArray[i].checked=false;"
				+ "      radioSubmit.disabled = true;"
				+ "      radioSubmit.value = 'Select" + (acceptsMultiple?" at least":"") + " one topic';"
				+ "      for (var i=0;i<videoArray.length;i++) if (videoArray[i].checked) count++;"
				+ "      videoSubmit.disabled = (count<1);"
				+ "      if (count<1) videoSubmit.value='Select" + (acceptsMultiple?" at least":"") + " one video topic';"
				+ "      else videoSubmit.value='Create " + (acceptsMultiple?"these assignments":"this assignment") + "';"
				+ "      break;"
				+ "    default:"
				+ "      for (var i=0;i<examArray.length;i++) examArray[i].checked=false;"
				+ "      checkSubmit.disabled = true;"
				+ "      checkSubmit.value = 'Select at least 3 topics for this exam';"
				+ "      for (var i=0;i<videoArray.length;i++) videoArray[i].checked=false;"
				+ "      videoSubmit.disabled = true;"
				+ "      videoSubmit.value = 'Select" + (acceptsMultiple?" at least":"") + " one video topic';"
				+ "      for (var i=0;i<quizArray.length;i++) if (quizArray[i].checked) count++;"
				+ "      radioSubmit.disabled = (count<1);"
				+ "      if (count<1) radioSubmit.value='Select" + (acceptsMultiple?" at least":"") + " one topic';"
				+ "      else radioSubmit.value='Create " + (acceptsMultiple?"these assignments":"this assignment") + "';"
				+ "  }"
				+ "}"
				+ "</script>");
		
		String selectorType = (acceptsMultiple?"checkbox":"radio");
		
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

		// Create a selector table for VideoQuiz assignments
		buf.append("<div id=videoSelect style='display:" + (assignmentType.equals("VideoQuiz")?"block":"none") + "'>");
		buf.append("<font color=red>Please select " + (acceptsMultiple?"at least":"") + " one video:</font><br>");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Video v : sem1Videos) buf.append("<label><input type=" + selectorType + " name=VideoId value=" + v.id + " onClick=countChecks('VideoQuiz');>" + v.title + (v.breaks==null?"":" *") + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Video v : sem2Videos) buf.append("<label><input type=" + selectorType + " name=VideoId value=" + v.id + " onClick=countChecks('VideoQuiz');>" + v.title + (v.breaks==null?"":" *") + "</label><br>");
		buf.append("</div></div></div><br>");  // end of cell, row, table
		buf.append("Video marked with an asterisk (*) have embedded quizzes; others will give full credit for watching to the end.<br>");
		buf.append("<input type=submit id=vidsub disabled=true value='Select" + (selectorType.equals("checkbox")?" at least":"") + " one video topic'>"); // submit button for videos
		buf.append("</div>"); // end of big box for VideoQuiz selection

		// Create a selector table for Quiz or Homework assignments
		buf.append("<div id=quizSelect style='display:" + (assignmentType.equals("Quiz")||assignmentType.equals("Homework")?"block":"none") + "'>");  // big box containing radio buttons
		buf.append("<font color=red>Please select " + (acceptsMultiple?"at least":"") + " one topic:</font><br>");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=" + selectorType + " name=TopicId value=" + t.id + " onClick=countChecks('Quiz');>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=" + selectorType + " name=TopicId value=" + t.id + " onClick=countChecks('Quiz');>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit id=radsub disabled=true value='Select" + (selectorType.equals("checkbox")?" at least":"") + " one topic'>"); // submit button for radios
		buf.append("</div>"); // end of big box for Quiz/Homework selection

		// Create a table with check boxes for Practice Exam assignments
		buf.append("<div id=examSelect style='display:" + (assignmentType.equals("PracticeExam")?"block":"none") + "'>"); // big box containing check boxes
		buf.append("<font color=red>Please select 3 or more topics for this exam:</font><br>");
		buf.append("<div style='display:table'>"); // start table of check boxes
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks('PracticeExam');>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks('PracticeExam');>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit id=checksub disabled=true value='Select at least 3 topics for this exam'><br>");
		buf.append("</div>"); // end of big box for PracticeExam selection
	
		buf.append("</form>");
		
		buf.append(Home.footer);
		return buf.toString();
	}
	
	User getUserClaims(JsonObject claims) throws Exception {
		// Process User information:
		String sub = claims.get("sub").getAsString();  // required
		if (sub==null || sub.isEmpty()) throw new Exception("Missing or empty subject claim in the id_token.");
		String platformUserId = claims.get("iss").getAsString() + "/" + sub;
		User user = new User(platformUserId);

		JsonElement roles_claim = claims.get("https://purl.imsglobal.org/spec/lti/claim/roles");
		if (roles_claim == null || !roles_claim.isJsonArray()) throw new Exception("Required roles claim is missing from the id_token");
		JsonArray roles = roles_claim.getAsJsonArray();
		Iterator<JsonElement> roles_iterator = roles.iterator();
		while(roles_iterator.hasNext()){
			String role = roles_iterator.next().getAsString().toLowerCase();
			if (!user.isInstructor()) user.setIsInstructor(role.contains("instructor"));
			if (!user.isAdministrator()) user.setIsAdministrator(role.contains("administrator"));
		}
		return user;
	}

	String deepLinkResponseMsg(HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer();
		String deep_link_return_url = null;
		try {
			JsonObject claims = null;
			DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
			String json = new String(Base64.getUrlDecoder().decode(id_token.getPayload()));
			claims = JsonParser.parseString(json).getAsJsonObject();
			
			String platform_id = claims.get("iss").getAsString();
			String deployment_id = claims.get("https://purl.imsglobal.org/spec/lti/claim/deployment_id").getAsString();
			JsonObject settings = claims.get("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").getAsJsonObject();		
			deep_link_return_url = settings.get("deep_link_return_url").getAsString();
			String data = settings.get("data")==null?"":settings.get("data").getAsString();
			boolean acceptsMultiple = settings.get("accept_multiple")==null?false:settings.get("accept_multiple").getAsBoolean();
			
			Date now = new Date();
			Date exp = new Date(now.getTime() + 5400000L); // 90 minutes from now
			Deployment d = Deployment.getInstance(platform_id + "/" + deployment_id);
			
			// Determine the assignmentType for this selection
			String assignmentType = request.getParameter("AssignmentType");
			if (assignmentType == null || assignmentType.isEmpty()) throw new Exception("Assignment type is missing.");
			
			// Make a List of topicIds for this selection (may be VideoIds instead)
			// PracticeExam assignments always transmit as TopicIds in checkboxes
			// VideoQuiz assignments transmit as VideoId (checkboxes if acceptsMultiple; otherwise radio)
			// Quiz or Homework assignments transmit as TopicId (checkboxes if acceptsMultiple; otherwise radio)
			
			String[] topicIdArray;
			List<Long> topicIds = new ArrayList<Long>();
			switch (assignmentType) {
			case "PracticeExam":
				topicIdArray = request.getParameterValues("TopicIds");
				for (int i=0;i<topicIdArray.length;i++) topicIds.add(Long.parseLong(topicIdArray[i]));			
				break;
			case "VideoQuiz":
				if (acceptsMultiple) {
					topicIdArray = request.getParameterValues("VideoId");
					for (int i=0;i<topicIdArray.length;i++) topicIds.add(Long.parseLong(topicIdArray[i]));			
				} else topicIds.add(Long.parseLong(request.getParameter("VideoId")));
				break;
			default:  // Quiz or Homework Assignment
				if (acceptsMultiple) {
					topicIdArray = request.getParameterValues("TopicId");
					for (int i=0;i<topicIdArray.length;i++) topicIds.add(Long.parseLong(topicIdArray[i]));			
				} else topicIds.add(Long.parseLong(request.getParameter("TopicId")));
			}
	
			// At this point all of the topicIds or VideoIds are in the List topicIds
			// If the assignmentType is PracticeExam, make a single Assignment, otherwise one per topic
			
			Assignment a = null;
			List<Assignment> assignments = new ArrayList<Assignment>();
			if (assignmentType.equals("PracticeExam")) {
				a = new Assignment("PracticeExam",0L,topicIds,d.platform_deployment_id);
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<a.topicIds.size();i++) {
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",a.topicIds.get(i)).keys().list());
				}
				assignments.add(a);
			} else {  // Quiz, Homework or VideoQuiz
				for (Long tid : topicIds) {
					a = new Assignment(assignmentType,0L,null,d.platform_deployment_id);
					if (assignmentType.equals("VideoQuiz")) a.videoId = tid;
					else {  // Quiz or Homework
						a.topicId = tid;
						a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
					}
					assignments.add(a);
				}
			}
						
			ofy().save().entities(assignments).now();
				
			String serverUrl = "https://" + request.getServerName();
			String launchUrl = serverUrl + "/lti/launch";
			String iconUrl = serverUrl + "/images/CVLogo_thumb.png";
			String client_id = d.client_id;
			String subject = request.getParameter("Subject");
			String nonce = Nonce.generateNonce();
			
			Encoder enc = Base64.getUrlEncoder().withoutPadding();
			
			// Create a JSON header for the JWT to send as DeepLinkingResponse
			JsonObject header = new JsonObject();
			header.addProperty("typ", "JWT");
			header.addProperty("alg", "RS256");
			header.addProperty("kid", d.rsa_key_id);
			byte[] hdr = enc.encode(header.toString().getBytes("UTF-8"));
			
			// Create a JSON payload for the JWT to send as DeepLinkingResponse:
			JsonObject payload = new JsonObject();
			payload.addProperty("iss",client_id);
			payload.addProperty("sub",subject);
			payload.addProperty("aud",platform_id);
			payload.addProperty("nonce", nonce);
			payload.addProperty("exp", exp.getTime()/1000);
			payload.addProperty("iat", now.getTime()/1000);
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiDeepLinkingResponse");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/deployment_id", deployment_id);
			if (!data.isEmpty()) payload.addProperty("https://purl.imsglobal.org/spec/lti-dl/claim/data", data);
		
			//Add the user-selected assignments to the payload as an array of content_items:
			JsonArray content_items = new JsonArray();
			
			for (Assignment a1 : assignments) {
				JsonObject item = new JsonObject();
				item.addProperty("type", "ltiResourceLink");
					
				String title = null;
				switch (assignmentType) {
					case "PracticeExam":
						title = "Practice Exam - ";
						for (long tId : topicIds) title += ofy().load().type(Topic.class).id(tId).now().title + ", ";
						title.substring(0, title.length()-2);  // strip off the last comma and space
						break;
					case "VideoQuiz":
						title = "Video - " + ofy().load().type(Video.class).id(a1.videoId).now().title;
						break;
					default:
						title = a1.assignmentType + " - " + ofy().load().type(Topic.class).id(a1.topicId).now().title;	
				}
				item.addProperty("title", title);

				JsonObject lineitem = new JsonObject();
				lineitem.addProperty("scoreMaximum", (assignmentType.contentEquals("PracticeExam")?100:10));
				lineitem.addProperty("label", title);
				
				switch (d.lms_type) { // this section binds a resourceId (String version of assignmentId) to the lineitem
				case "canvas":        // Unfortunately, canvas does not support this, so we have to bind it as a request parameter instead
					launchUrl = serverUrl + "/lti/launch?resourceId=" + a1.id;
					break;
				default:
					launchUrl = serverUrl + "/lti/launch";
					lineitem.addProperty("resourceId", String.valueOf(a1.id));
				}				
				item.add("lineItem", lineitem);
				item.addProperty("url", launchUrl);
				
				JsonObject icon = new JsonObject();
				icon.addProperty("url", iconUrl);
				icon.addProperty("width", 75);
				icon.addProperty("height", 70);
				item.add("icon", icon);
				
				content_items.add(item);
			}
			payload.add("https://purl.imsglobal.org/spec/lti-dl/claim/content_items", content_items);
			
			byte[] pld = enc.encode(payload.toString().getBytes("UTF-8"));
			
			// Join the header and payload together with a period separator:
			String jwt = String.format("%s.%s",new String(hdr),new String(pld));
			
			// Add a signature item to complete the JWT:
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(KeyStore.getRSAPrivateKey(d.rsa_key_id),new SecureRandom());
			signature.update(jwt.getBytes("UTF-8"));
			String sig = new String(enc.encode(signature.sign()));
			jwt = String.format("%s.%s", jwt, sig);
			
			// Create a form to be auto-submitted to the platform by the user_agent browser
			buf.append("Submitting your selection back to your LMS...");
			buf.append("<div style='visibility: hidden'>"
					+ "<form id=selections method=POST action='" + deep_link_return_url + "'>"
					+ "<input type=hidden name=JWT value='" + jwt + "'>"
					+ "Assignment selection OK. Please click the Submit button &rarr; <input type=submit>"
					+ "</form></div>");
			buf.append("<script>document.getElementById('selections').submit();</script>");
			//buf.append("The new content items are: " + content_items.toString());
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage());
		}
		return buf.toString();
	}
	
}
