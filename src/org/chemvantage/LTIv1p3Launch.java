/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2019 ChemVantage LLC
*   
*   This program is free software: you can redistribute it and/or modify
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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

/* This servlet executes a valid LTI ResourceLink launch request using LTI v1.3 specifications
 * The basic requesting entity is a Deployment. Although a single LMS platform may host several
 * Deployments (e.g., as separate accounts), each Deployment will designate a client_id that identifies
 * the tool provider (ChemVantage) and thereby defines the security contract for the connection.
 *  
 * The following values are determined by the Deployment, and therefore cannot be considered
 * to be universally unique for the tool provider:
 * platform_id - this must be the fully qualified secure base URL for the platform (LMS)
 * deployment_id - non-uniqueness is mitigated by prepending the platform_id to form a platformDeploymentId
 * client_id - this value should be confirmed only after the platformDeploymentId is known
 * user_id - this should be prepended with the platformId to form a unique platformUserId value
 * resource_link_id - the assignmentId should be identified by the platformDeploymentId and the resource_link_id
 * 
 * The User.token is a JWT containing useful information about the transaction, including
 * platformDeploymentId
 * platformUserId
 * user.roles - an integer defining Learner (0), Instructor (8) and Administrator (16) roles
 * assignmentId - the datastore id value for the Assignment entity for this launch
 * 
 * ChemVantage requires that each assignment must be accessed through a separate LTIv1p3Launch request
 */

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
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
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

@WebServlet(urlPatterns = {"/lti/launch","/lti/launch/"})
public class LTIv1p3Launch extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.sendRedirect("/lti/registration");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		try {
			if (request.getParameter("id_token") != null) ltiv1p3LaunchRequest(request,response);  // handle LTI v1p3 launch request			
			else if ("UpdateAssignment".equals(request.getParameter("UserRequest"))) { // POST the assignmentType and topicIds			
				User user = User.getUser(request.getParameter("sig"));
				if (user==null) throw new Exception("User token was missing or invalid.");
				
				if (!user.isInstructor()) throw new Exception("User must be instructor to update this assignment.");

				Assignment myAssignment = updateAssignment(request,user);
				boolean refresh = Boolean.parseBoolean(request.getParameter("Refresh"));

				if (!refresh && myAssignment.isValid()) {
					ofy().save().entity(myAssignment).now();  // we will need this in a few milliseconds					
					String redirectUrl = "/" + request.getParameter("AssignmentType") + "?sig=" + user.getTokenSignature();
					response.sendRedirect(redirectUrl);	
				} else {  // send the user back to the resourcePickerForm
					int topicKey = 1;
					try {topicKey = Integer.parseInt(request.getParameter("TopicKey"));} catch (Exception e) {}
					response.getWriter().println(Home.header("Select A ChemVantage Assignment") + pickResourceForm(user,myAssignment,topicKey) + Home.footer);
				}
			} else {
				throw new Exception("Wrong URL or Bad Request. This URL only receives LTI Advantage (v1.3) Resource Link and Submission Review launch requests for ChemVantage. "
						+ "Please check to ensure that your LMS is registered properly. Contact admin@chemvantage.org for assistance.");
			}
		} catch (Exception e) {	
			response.sendError(401, "Status 401: " + e.toString());
		}
	}

	void ltiv1p3LaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws Exception {
		//StringBuffer debug = new StringBuffer();
		JsonObject state = validateStateToken(request); // ensures proper OIDC authorization flow completed			

		Deployment d = validateIdToken(request);  // returns the validated Deployment
		
		// Decode the JWT id_token payload as a JsonObject:
		JsonObject claims = null;
		try {
			DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
			String json = new String(Base64.getUrlDecoder().decode(id_token.getPayload()));
			claims = JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			throw new Exception("id_token was not a valid JWT.");
		}
		
		// verify that the redirect_uri are consistent with the state token:
		if (!state.get("redirect_uri").getAsString().contains("https://" + request.getServerName() + "/lti/launch")) throw new Exception("Invalid redirect_uri.");
		
		verifyLtiMessageClaims(claims); // required
		User user = getUserClaims(claims);

		// At this point we have all of the REQUIRED info for a valid LTI launch
		// Process all remaining optional claims in try/catch structures to avoid
		// throwing unnecessary Exceptions

		switch (claims.get("https://purl.imsglobal.org/spec/lti/claim/message_type").getAsString()) {
		case "LtiResourceLinkRequest":
			launchResourceLink(request,response,d,user,claims);
			break;
		case "LtiSubmissionReviewRequest":
			launchSubmissionReview(response, claims, d, user);
			break;
		}
	}
	
	void launchResourceLink(HttpServletRequest request, HttpServletResponse response, Deployment d, User user, JsonObject claims) throws Exception {
		// Process deployment claims:
		Deployment original_d = d.clone();  // make a copy to compare for updating later

		try {
			Date now = new Date();
			Date yesterday = new Date(now.getTime()-86400000L); // 24 hrs ago
			if (d.lastLogin==null || d.lastLogin.before(yesterday)) {
				d.lastLogin = now;
				d.claims = claims.toString();
			}
			JsonObject platform = claims.get("https://purl.imsglobal.org/spec/lti/claim/tool_platform").getAsJsonObject();
			d.email = platform.get("email_contact").getAsString();
			d.lms_type = platform.get("product_family_code").getAsString();
		} catch (Exception e) {}	
		
		// Process information for LTI Assignment and Grade Services (AGS)
		String scope = "";
		String lti_ags_lineitem_url = null;
		String lti_ags_lineitems_url = null;
		try {  
			JsonObject lti_ags_claims = claims.get("https://purl.imsglobal.org/spec/lti-ags/claim/endpoint").getAsJsonObject();

			// get the list of AGS capabilities allowed by the platform
			JsonArray scope_claims = lti_ags_claims.get("scope")==null?new JsonArray():lti_ags_claims.get("scope").getAsJsonArray();
			Iterator<JsonElement> scopes_iterator = scope_claims.iterator();
			while (scopes_iterator.hasNext()) scope += scopes_iterator.next().getAsString() + (scopes_iterator.hasNext()?" ":"");
			lti_ags_lineitems_url = (lti_ags_claims.get("lineitems")==null?null:lti_ags_claims.get("lineitems").getAsString());
			lti_ags_lineitem_url = (lti_ags_claims.get("lineitem")==null?null:lti_ags_claims.get("lineitem").getAsString());
		} catch (Exception e) {				
		}
		
		// Process information for LTI Advantage Names and Roles Provisioning (NRPS)
		String lti_nrps_context_memberships_url = null;
		try { 
			JsonObject lti_nrps_claims = claims.get("https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice").getAsJsonObject();
			if (lti_nrps_claims != null) scope += (scope.length()>0?" ":"") + "https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
			lti_nrps_context_memberships_url = lti_nrps_claims.get("context_memberships_url").getAsString();
		} catch (Exception e) {
		}
		
		if (!scope.isEmpty()) d.scope = scope;

		// Save the updated Deployment entity, if necessary
		if (!d.equivalentTo(original_d)) ofy().save().entity(d).now();
		
		/* Find assignment (try the following, in order, until an assignment is found):
		 *   1. Find an assignment in the datastore with a matching lti_ags_lineitem_url (should work for all established graded assignments)
		 *   2. Find an assignment with matching resourceId value (tool-defined assignmentId set during Deep Linking)
		 *   3. Find an assignment with matching domain and resourceLinkId (should work for all established ungraded assignments)
		 *   4. Create a new assignment and send the user to the resourcePicker page
		 */
		
		Assignment myAssignment = null;
		String resourceLinkId = claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link").getAsJsonObject().get("id").getAsString();
		
		if (lti_ags_lineitem_url != null) myAssignment = ofy().load().type(Assignment.class).filter("lti_ags_lineitem_url",lti_ags_lineitem_url).first().now();	

		if (myAssignment == null) {  // This section searches for the resourceId parameter, which is a String version of the ChemVantage assigmentId
			String resourceId = null;
			switch (d.lms_type) {
			case "canvas":           // Canvas does not support setting the resourceId in the lineitem, but allows this to be sent as a request parameter
				resourceId = request.getParameter("resourceId");
				break;
			default:     // all other LMS platforms should put the resourceId from DeepLinking flow into the lineitem
				JsonObject lineitem = null;
				try {
					if (lti_ags_lineitem_url == null) {  // the launch id_token may or may not specify the lineitem URL
						lineitem = LTIMessage.getLineItem(d, resourceLinkId, lti_ags_lineitems_url);
						lti_ags_lineitem_url = lineitem.get("id").getAsString();
					} else lineitem = LTIMessage.getLineItem(d, lti_ags_lineitem_url);
					JsonElement rId = lineitem.get("resourceId");
					if (rId != null) resourceId = rId.getAsString();
					else resourceId = lineitem.get("resourceid").getAsString();  // special case for LTI Reference Implementation
				} catch (Exception e) {}
			}
			try {
				myAssignment = ofy().load().type(Assignment.class).id(Long.parseLong(resourceId)).safe();
			} catch (Exception e) {}
		}

		if (myAssignment == null) myAssignment = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).filter("resourceLinkId",resourceLinkId).first().now();
		
		if (myAssignment == null) myAssignment = new Assignment(d.platform_deployment_id,resourceLinkId,lti_nrps_context_memberships_url);
		// At this point we should have a valid (but possibly incomplete) Assignment entity
		
		// Update the Assignment parameters:
		Assignment original_a = myAssignment.clone(); // make a copy to compare with for updating later
		myAssignment.resourceLinkId = resourceLinkId;		
		if (lti_ags_lineitem_url != null) myAssignment.lti_ags_lineitem_url = lti_ags_lineitem_url;
		if (lti_nrps_context_memberships_url != null) myAssignment.lti_nrps_context_memberships_url = lti_nrps_context_memberships_url;

		// If required, save the updated Assignment entity now so its id will be accessible
		if (myAssignment.id==null || !myAssignment.equivalentTo(original_a)) ofy().save().entity(myAssignment).now();
		
		// Create a cross-site request forgery (CSRF) token containing the Assignment.id
		user.setAssignment(myAssignment.id);
		
		// If this is the first time this Assignment has been used, it may be missing the assignmentType and topicId(s)
		if (!myAssignment.isValid()) {  //Show the the pickResource form:
			response.getWriter().println(Home.header("Select A ChemVantage Assignment") + pickResourceForm(user,myAssignment,1) + Home.footer);
			return;
		} else {  // redirect the user's browser to the assignment
			response.sendRedirect("/" + myAssignment.assignmentType + "?sig=" + user.getTokenSignature());
			Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
			queue.add(withUrl("/HashUserIds").param("UserId",URLEncoder.encode(user.id, "UTF-8")));			
			return;
		}
	}
	
	void launchSubmissionReview(HttpServletResponse response, JsonObject claims, Deployment d, User u) throws Exception {
				
		String for_user_id = claims.get("https://purl.imsglobal.org/spec/lti/claim/for_user").getAsJsonObject().get("user_id").getAsString();  // required
		User forUser = new User(claims.get("iss").getAsString(), for_user_id);
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if (u.id.equals(forUser.id) || u.isInstructor()) { // viewing the submission record is approved
			String resourceLinkId = claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link").getAsJsonObject().get("id").getAsString();
			Assignment a = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).filter("resourceLinkId",resourceLinkId).first().now();
			switch (a.assignmentType) {
			case "Quiz":
				out.println(Home.header() + Home.banner + Quiz.showScores(forUser) + Home.footer);
				break;
			case "Homework":
				out.println(Home.header() + Home.banner + Homework.showScores(forUser) + Home.footer);
				break;
			case "PracticeExam":
				out.println(Home.header() + Home.banner + PracticeExam.submissionReview(u, forUser) + Home.footer);
				break;
			default:
				out.println(Home.header() + Home.banner + "<h2>Sorry, submission review is not currently available for this type of ChemVantage assignment.</h2>" + Home.footer);
			}
		} else throw new Exception("You must be logged into your LMS ");
	}
	
	protected JsonObject validateStateToken(HttpServletRequest request) throws Exception {
		/* This method ensures that the state token required by LTI v1.3 standards is a
		 * valid token issued by the tool provider (ChemVantage) as part of the LTI
		 * launch request sequence. Otherwise throws a JWTVerificationException.
		 */
		
		String iss = "https://" + request.getServerName();
		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
		JWTVerifier verifier = JWT.require(algorithm).withIssuer(iss).build();
		String state = request.getParameter("state");
	    verifier.verify(state);
	    String nonce = JWT.decode(state).getClaim("nonce").asString();
	    if (!Nonce.isUnique(nonce)) throw new Exception("Nonce was used previously.");
	    
	    // return the state token payload as a JSON
	    return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(JWT.decode(state).getPayload()))).getAsJsonObject();
	}

	protected Deployment validateIdToken(HttpServletRequest request) throws Exception {
		DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
		
		// get the platform_id and deployment_id to load the correct Deployment d
		String platform_id = id_token.getIssuer();
		String deployment_id = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
		if (deployment_id == null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
		String platformDeploymentId = platform_id + "/" + deployment_id;
		Deployment d = Deployment.getInstance(platformDeploymentId);
		
		if (d==null) throw new Exception("The deployment was not found in the ChemVantage database. You "
				+ "can register your LMS with ChemVantage at https://www.chemvantage.org/lti/registration");
		if ("blocked".equals(d.status)) throw new Exception("Sorry, we were unable to launch ChemVantage from this "
				+ "account, most likely because the subscription has lapsed or is unpaid. Please contact "
				+ "admin@chemvantage.org for assistance to reactivate the account. Thank you.");
		if (d.expires != null && d.expires.before(new Date())) d.status = "pending";
		if (d.status == null) d.status = "pending";
		
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
		switch (message_type.getAsString()) {
		case "LtiResourceLinkRequest": break;
		case "LtiSubmissionReviewRequest": break;
		default: throw new Exception("The LTI message_type claim " + message_type.getAsString() + " is not suppported.");
		}
		//if (!"LtiResourceLinkRequest".equals(message_type.getAsString())) throw new Exception("LTI message_type claim must be LtiResourceLinkRequest");

		// Process the ResourceLink claim information:
		JsonElement resource_link_claims = claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link");
		if (resource_link_claims == null) throw new Exception("Resource link claims were missing from the id_token.");
		if (resource_link_claims.getAsJsonObject().get("id").getAsString()==null) throw new Exception("Resource link ID value missing from id_token claims. ");

		return;
	}
	
	User getUserClaims(JsonObject claims) throws Exception {
		// Process User information:
		
		User user = null;
		JsonElement sub = claims.get("sub");
		if (sub==null || sub.getAsString().isEmpty()) user = new User();  // special provision to allow anonymous user via LTI launch
		else user = new User(claims.get("iss").getAsString(), sub.getAsString());
		
		JsonElement roles_claim = claims.get("https://purl.imsglobal.org/spec/lti/claim/roles");
		if (roles_claim == null || !roles_claim.isJsonArray()) throw new Exception("Required roles claim is missing from the id_token");
		JsonArray roles = roles_claim.getAsJsonArray();
		Iterator<JsonElement> roles_iterator = roles.iterator();
		while(roles_iterator.hasNext()){
			String role = roles_iterator.next().getAsString().toLowerCase();
			if (!user.isTeachingAssistant()) user.setIsTeachingAssistant(role.contains("teachingassistant"));
			if (!user.isInstructor()) user.setIsInstructor(role.contains("instructor"));
			if (!user.isAdministrator()) user.setIsAdministrator(role.contains("administrator"));
		}
		return user;
	}
	
	Assignment updateAssignment(HttpServletRequest request, User user) throws Exception {			
		long assignmentId = user.getAssignmentId();
		if (assignmentId == 0L) throw new Exception("Assignment ID was 0L.");
		
		Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		String topicId = null;
		a.assignmentType = request.getParameter("AssignmentType");
		
		switch (a.assignmentType) {
		case "Quiz":
			topicId = request.getParameter("TopicId");
			if (topicId != null) {
				a.topicId = Long.parseLong(topicId);
				a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			}
			break;
		case "Homework":
			topicId = request.getParameter("TopicId");
			if (topicId != null) {
				a.topicId = Long.parseLong(topicId);
				a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			}
			break;
		case "PracticeExam":
			String[] topicIds = request.getParameterValues("TopicIds");
			if (topicIds!=null) {
				a.topicIds = new ArrayList<Long>();
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<topicIds.length;i++) {
					long tId = Long.parseLong(topicIds[i]);
					a.topicIds.add(tId);
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tId).keys().list());
				}
			}
			break;
		case "VideoQuiz":
			String videoId = request.getParameter("VideoId");
			if (videoId != null) a.videoId = Long.parseLong(videoId);
			break;
		case "Poll":
			break;
		}
		return a;		
	}
	
	String pickResourceForm(User user,Assignment myAssignment,int topicKey) throws Exception {
		StringBuffer buf = new StringBuffer();

		// Print a nice banner
		buf.append(Home.banner);

		buf.append("<h2>Assignment Setup Page</h2>"
				+ "The link that you just activated in your learning management system (LMS) is not yet associated with a ChemVantage assignment.<p></p>");

		if (user.isInstructor()) buf.append("Please select the ChemVantage assignment that should be associated with this link. "
				+ "ChemVantage will remember this choice and send students directly to the assignment.<p></p>");
		else {
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
		buf.append("<input type=hidden name=UserRequest value=UpdateAssignment />");
		buf.append("<input type=hidden name=sig value='" + user.getTokenSignature() + "' />");
		buf.append("<input type=hidden name=Refresh value=false />");
		
		String assignmentType = myAssignment.assignmentType; // convenience variable; may be null for new Assignment
		
		// Build a table for Parts 1 and 2 (side by side in 1 row)
		buf.append("<div style='display:table'><div style='display:table-row'><div style='display:table-cell'>");
		buf.append("Select the type of assignment to create...<br />");
		buf.append("<label><input type=radio name=AssignmentType " + ("Quiz".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Quiz' />Quiz</label><br />"
				+ "<label><input type=radio name=AssignmentType " + ("Homework".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Homework' />Homework</label><br />"
				+ "<label><input type=radio name=AssignmentType " + ("VideoQuiz".equals(assignmentType)?"checked ":" ") + "onClick=showVideos(); value='VideoQuiz' '>Video</label><br />"
				+ "<label><input type=radio name=AssignmentType " + ("Poll".equals(assignmentType)?"checked ":" ") + "onClick=hideTopics(); value='Poll' />In-class&nbsp;Poll</label><br />"
				+ "<label><input type=radio name=AssignmentType " + ("PracticeExam".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='PracticeExam' />Practice&nbsp;Exam</label><p></p>");
		buf.append("</div>");
		
		// Put Part 2 in a cell on the right side of the first row
		buf.append("<div id=topicKeySelect style='display:table-cell;visibility:" + (assignmentType==null?"hidden":"visible") + "'>");
		buf.append("and a group of topics to choose from:<br />");
		buf.append("<label><input type=radio name=TopicKey value=0 " + (topicKey==0?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit(); />Show all topics</label><br />"
				+ "<label><input type=radio name=TopicKey value=1 "+ (topicKey==1?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit(); />Show topics for the OpenStax Chemistry 2e</label><br />");
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
				+ "    document.getElementById('pollNotice').style.display='none';"
				+ "    clearChecks();"
				+ "  } else if (type = 'check') {"
				+ "    document.getElementById('radioSelect').style.display='none';"
				+ "    document.getElementById('checkSelect').style.display='block';"
				+ "    document.getElementById('videoSelect').style.display='none';"
				+ "    document.getElementById('pollNotice').style.display='none';"
				+ "    clearRadios();"
				+ "  }"
				+ "}"
				+ "function showVideos() {"
				+ "  document.getElementById('topicKeySelect').style.visibility='hidden';"
				+ "  document.getElementById('radioSelect').style.display='none';"
				+ "  document.getElementById('checkSelect').style.display='none';"
				+ "  document.getElementById('videoSelect').style.display='block';"
				+ "  document.getElementById('pollNotice').style.display='none';"
				+ "  clearChecks();"
				+ "  clearRadios();"
				+ "}"
				+ "function hideTopics() {"
				+ "  document.getElementById('pollNotice').style.display='block';"
				+ "  document.getElementById('topicKeySelect').style.visibility='hidden';"
				+ "  document.getElementById('radioSelect').style.display='none';"
				+ "  document.getElementById('checkSelect').style.display='none';"
				+ "  document.getElementById('videoSelect').style.display='none';"
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

		// Create instructions for the Poll assignmentType:
		buf.append("<div id=pollNotice style='display:none'><input type=submit value='Create an in-class poll' /><br />"
				+ "Poll questions will be selected or created when the assignment is launched by the instructor.</div>");

		// Create a radio-type selector for video quiz assignments
		buf.append("<div id=videoSelect style='display:" + (selectorType.equals("video")?"block":"none") + "'>");
		buf.append("<font color=red>Please assign one video to watch:</font><br />");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Video v : sem1Videos) buf.append("<label><input type=radio name=VideoId value=" + v.id + " onClick=this.form.vidsub.disabled=false; />" + v.title + (v.breaks==null?"":" *") + "</label><br />");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Video v : sem2Videos) buf.append("<label><input type=radio name=VideoId value=" + v.id + " onClick=this.form.vidsub.disabled=false; />" + v.title + (v.breaks==null?"":" *") + "</label><br />");
		buf.append("</div></div></div><br />");  // end of cell, row, table
		buf.append("Video marked with an asterisk (*) have embedded quizzes; others will give full credit for watching to the end.<br />");
		buf.append("<input type=submit name=vidsub disabled=true value='Select this video' />"); // submit button for radios
		buf.append("</div>"); // end of big box with radio buttons for video selection

		// Create a table with radio buttons for Quiz or Homework assignments
		buf.append("<div id=radioSelect style='display:" + (selectorType.equals("radio")?"block":"none") + "'>");  // big box containing radio buttons
		buf.append("<font color=red>Please select one topic for this assignment:</font><br />");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false; />" + t.title + "</label><br />");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false; />" + t.title + "</label><br />");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit name=radsub disabled=true value='Select this topic' />"); // submit button for radios
		buf.append("</div>"); // end of big box with radio buttons

		// Create a table with check boxes for Practice Exam assignments
		buf.append("<div id=checkSelect style='display:" + (selectorType.equals("check")?"block":"none") + "'>"); // big box containing check boxes
		buf.append("<font color=red>Please select 3 or more topics for this exam:</font><br />");
		buf.append("<div style='display:table'>"); // start table of check boxes
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks(); />" + t.title + "</label><br />");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2Topics) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks(); />" + t.title + "</label><br />");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit id=checksub disabled=true value='Select at least 3 topics for this assignment' /><br />");
		buf.append("</div>"); // end of big box with check boxes
		
		buf.append("</form>");
		return buf.toString();
	}
	
}	
