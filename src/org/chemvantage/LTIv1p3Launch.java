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
import java.net.URL;
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
import javax.servlet.http.HttpSession;

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
				User user = User.getUser((String)request.getSession().getAttribute("Token"));
				if (!user.signatureIsValid(request.getParameter("sig"))) throw new Exception();
				
				if (!user.isInstructor()) throw new Exception("User must be instructor to update thisd assignment.");

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
			}		
		} catch (Exception e) {	
			response.sendError(401, e.getMessage());
		}
	}

	void ltiv1p3LaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws Exception {
		StringBuffer debug = new StringBuffer();

		validateStateToken(request); // ensures proper OIDC authorization flow completed			

		Deployment d = validateIdToken(request);  // returns the validated Deployment
		Deployment original_d = d.clone();  // make a copy to compare for updating later

		// Decode the JWT id_token payload as a JsonObject:
		JsonObject claims = null;
		try {
			DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
			String json = new String(Base64.getUrlDecoder().decode(id_token.getPayload()));
			claims = JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			throw new Exception("id_token was not a valid JWT.");
		}
		debug.append("id_token OK...");

		String resourceLinkId = verifyLtiMessageClaims(claims);

		User user = getUserClaims(claims);

		// At this point we have all of the REQUIRED info for a valid LTI launch
		// Process all remaining optional claims in try/catch structures to avoid
		// throwing unnecessary Exceptions

		// Process deployment claims:
		try {
			Date now = new Date();
			Date yesterday = new Date(now.getTime()-86400000L); // 24 hrs ago
			if (d.lastLogin==null || d.lastLogin.before(yesterday)) {
				d.lastLogin = now;
				d.claims = claims.toString();
			}
			JsonObject platform = claims.get("https://purl.imsglobal.org/spec/lti/claim/tool_platform").getAsJsonObject();
			d.email = platform.get("email_contact").getAsString();
			d.lms_type = platform.get("product_family_code").getAsString() + " version " + platform.get("version").getAsString();
		} catch (Exception e) {}	
		debug.append("deployment claims OK...");

		// Process information for LTI Assignment and Grade Services (AGS)
		String scope = "";
		String lti_ags_lineitems_url = null;
		String lti_ags_lineitem_url = null;
		try {  
			JsonObject lti_ags_claims = claims.get("https://purl.imsglobal.org/spec/lti-ags/claim/endpoint").getAsJsonObject();

			// get the list of AGS capabilities allowed by the platform
			JsonArray scope_claims = lti_ags_claims.get("scope")==null?new JsonArray():lti_ags_claims.get("scope").getAsJsonArray();
			Iterator<JsonElement> scopes_iterator = scope_claims.iterator();
			while (scopes_iterator.hasNext()) scope += scopes_iterator.next().getAsString() + (scopes_iterator.hasNext()?" ":"");
			lti_ags_lineitems_url = lti_ags_claims.get("lineitems")==null?null:lti_ags_claims.get("lineitems").getAsString();
			lti_ags_lineitem_url = lti_ags_claims.get("lineitem")==null?null:lti_ags_claims.get("lineitem").getAsString();
		} catch (Exception e) {				
		}
		debug.append("Assignment & Grade Services claims OK...");

		// Process information for LTI Advantage Names and Roles Provisioning (NRPS)
		String lti_nrps_context_memberships_url = null;
		try { 
			JsonObject lti_nrps_claims = claims.get("https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice").getAsJsonObject();
			if (lti_nrps_claims != null) scope += (scope.length()>0?" ":"") + "https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
			lti_nrps_context_memberships_url = lti_nrps_claims.get("context_memberships_url").getAsString();
			debug.append("supports NRPS...");
		} catch (Exception e) {
		}
		debug.append("Roles and Membership Services claims OK...");		

		if (!scope.isEmpty()) d.scope = scope;

		// Save the updated Deployment entity, if necessary
		try {
			if (!d.equivalentTo(original_d)) {
				ofy().save().entity(d).now();
				debug.append("Deployment updated OK...");
			} else debug.append("Deployment unchanged...");		
		} catch (Exception e) {
			throw new Exception("Update of the Deployment entity failed. " + e.getMessage());
		}

		/* Find or create the correct Assignment entity for this LtiResourceLinkRequest launch
		 *  Note: every launch MUST be accompanied by the resourceLinkId value. This is a platform-generated 
		 *  value that must be unique to a link in the LMS platform. On every launch, ChemVantage will  
		 *  1) look for an Assignment entity associated with this LMS and this resourceLinkId value
		 *  2) look for a lineitem created through the DeepLinking flow:
		 *     a) or it may have a lineitem with a resourceId equal to the Assignment.id
		 *     b) it may have an AssignmentId parameter in the URL (Canvas works this way)
		 *  3) look for an Assignment entity associated with a resourceLinkId in the comma-separated list 
		 *     of URL-encoded resource link ID values in the ResourceLink.id.history variable. (not yet implemented)  
		 *     This would be the case of a course being copied to another course. We should clone the Assignment 
		 *     entity to allow for independent customization    
		 *  4) create a new Assignment and lineitem, if required   
		 */

		Assignment myAssignment = null;
		debug.append("Starting to find assignment...");
		HttpSession session = request.getSession();
		
		// 1) Try to find the assignment using the platformDeploymentId and the resourceLinkId value
		myAssignment = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).filter("resourceLinkId",resourceLinkId).first().now();
		debug.append((myAssignment==null?"not ":"") + "found in the datastore...");

		// 2a) If the lineitem URL is available, try to get the assignmentId from the resourceId parameter (set in Deep Linking)
		if (myAssignment == null && lti_ags_lineitem_url != null) {
			try {
				debug.append("looking for lineitem...");
				JsonObject lineitem = LTIMessage.getLineItem(d, lti_ags_lineitem_url);
				long assignmentId = Long.parseLong(lineitem.get("resourceId").getAsString());
				myAssignment = ofy().load().type(Assignment.class).id(assignmentId).now();						
				debug.append((myAssignment==null?"not ":"") + "found in the lineitem...");
			} catch (Exception e) {
			}
		}

		// 2b) See if the assignmerntId is included in the user's Session from DeepLinking flow. This is the Canvas way...
		if (myAssignment == null) {
			try {
				long assignmentId = Long.parseLong((String)session.getAttribute("AssignmentId"));
				myAssignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
				session.removeAttribute("AssignmentId");
				debug.append("found assignmentId in the user's session");
			} catch(Exception e) {}
		}

		// 3) Look for an Assignment with a resourceLinkId value listed in the ResourceLink.id.history list for this launch
		// Note: not yet implemented as of June 2020

		// 4) If none of that worked, then the assignment and lineitem probably don't exist, so make a new Assignment:
		if (myAssignment == null) {
			myAssignment = new Assignment(d.platform_deployment_id,resourceLinkId,lti_nrps_context_memberships_url);
			debug.append("Created new assignment with id=" + myAssignment.id + "...");
		}
		debug.append("assignment " + (myAssignment == null?"still missing.":"OK..."));

		// Update the Assignment parameters:
		debug.append("Cloning myAssignment...");
		Assignment original_a = myAssignment.clone(); // make a copy to compare with for updating later
		debug.append("Created assignment clone with id=" + original_a.id + "...");

		myAssignment.resourceLinkId = resourceLinkId;			
		if (lti_ags_lineitem_url != null) myAssignment.lti_ags_lineitem_url = lti_ags_lineitem_url;
		else  myAssignment.lti_ags_lineitem_url = LTIMessage.getLineItemUrl(d, myAssignment,lti_ags_lineitems_url);
		
		myAssignment.lti_nrps_context_memberships_url = lti_nrps_context_memberships_url;

		// If required, save the updated Assignment entity now so its id will be accessible
		if (myAssignment.id==null || !myAssignment.equivalentTo(original_a)) ofy().save().entity(myAssignment).now();
		debug.append("assignment saved OK...");

		// Create a cross-site request forgery (CSRF) token containing the Assignment.id
		user.setAssignment(myAssignment.id);
		session.setAttribute("Token",user.token);
		debug.append("user token set OK...");

		// If this is the first time this Assignment has been used, it may be missing the assignmentType and topicId(s)
		if (!myAssignment.isValid()) {  //Show the the pickResource form:									
			response.getWriter().println(Home.header("Select A ChemVantage Assignment") + pickResourceForm(user,myAssignment,1) + Home.footer);
			return;
		} else {  // redirect the user's browser to the assignment
			response.sendRedirect("/" + myAssignment.assignmentType + "?sig=" + user.getTokenSignature());
			return;
		}
	}
	
	protected void validateStateToken(HttpServletRequest request) throws Exception {
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
	}

	protected Deployment validateIdToken(HttpServletRequest request) throws Exception {
		DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
		
		// get the platform_id and deployment_id to load the correct Deployment d
		String platform_id = id_token.getIssuer();
		String deployment_id = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
		if (deployment_id == null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
		String platformDeploymentId = platform_id + "/" + deployment_id;
		Deployment d = Deployment.getInstance(platformDeploymentId);
		
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
	
	String verifyLtiMessageClaims(JsonObject claims) throws Exception {
		// verify LTI version 1.3.0
		JsonElement lti_version = claims.get("https://purl.imsglobal.org/spec/lti/claim/version");
		if (lti_version == null) throw new Exception("LTI version claim was missing.");    
		if (!"1.3.0".equals(lti_version.getAsString())) throw new Exception("Incorrect LTI version claim");

		// Validate the LTI message_type:
		JsonElement message_type = claims.get("https://purl.imsglobal.org/spec/lti/claim/message_type");
		if (message_type == null) throw new Exception("Missing LTI message_type.");
		if (!"LtiResourceLinkRequest".equals(message_type.getAsString())) throw new Exception("LTI message_type claim must be LtiResourceLinkRequest");

		// Process the ResourceLinkRequest information:
		JsonElement resource_link_claims = claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link");
		if (resource_link_claims == null) throw new Exception("Resource link claims were missing from the id_token.");
		String resourceLinkId = resource_link_claims.getAsJsonObject().get("id").getAsString();

		return resourceLinkId;
	}
	
	User getUserClaims(JsonObject claims) throws Exception {
		// Process User information:
		String sub = claims.get("sub").getAsString();  // required
		if (sub==null || sub.isEmpty()) throw new Exception("Missing or empty subject claim in the id_token.");
		String platformUserId = claims.get("iss").getAsString() + "/" + sub;
		User user = new User(platformUserId);
		
		if (claims.has("email")) user.email = claims.get("email").getAsString();
		
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
		a.assignmentType = request.getParameter("AssignmentType");
		
		if (a.assignmentType.contentEquals("Quiz") || a.assignmentType.contentEquals("Homework")) {
			try {
				a.topicId = Long.parseLong(request.getParameter("TopicId"));
				if (a.topicId>0) a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
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

		if (user.isInstructor()) buf.append("Please select the ChemVantage assignment that should be associated with this link. "
				+ "ChemVantage will remember this choice and send students directly to the assignment.<p>");
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
		buf.append("<input type=hidden name=UserRequest value=UpdateAssignment>");
		buf.append("<input type=hidden name=sig value='" + user.getTokenSignature() + "'>");
		buf.append("<input type=hidden name=Refresh value=false>");
		
		String assignmentType = myAssignment.assignmentType; // convenience variable; may be null for new Assignment
		
		// Build a table for Parts 1 and 2 (side by side in 1 row)
		buf.append("<div style='display:table'><div style='display:table-row'><div style='display:table-cell'>");
		buf.append("Select the type of assignment to create...<br>");
		buf.append("<label><input type=radio name=AssignmentType " + ("Quiz".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Quiz'>Quiz</label><br>"
				+ "<label><input type=radio name=AssignmentType " + ("Homework".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='Homework'>Homework</label><br>"
				+ "<label><input type=radio name=AssignmentType " + ("PracticeExam".equals(assignmentType)?"checked ":" ") + "onClick=showTopics(); value='PracticeExam'>Practice&nbsp;Exam</label><p>");
		buf.append("</div>");
		
		// Put Part 2 in a cell on the right side of the first row
		buf.append("<div id=topicKeySelect style='display:table-cell;visibility:" + (assignmentType==null?"hidden":"visible") + "'>");
		buf.append("and a group of topics to choose from:<br>");
		buf.append("<label><input type=radio name=TopicKey value=0 " + (topicKey==0?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Show all topics</label><br>"
				+ "<label><input type=radio name=TopicKey value=1 "+ (topicKey==1?"checked ":"") + "onClick=this.form.Refresh.value=true;this.form.submit();>Show topics for the OpenStax Chemistry 2e</label><br>");
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
				+ "    clearChecks();"
				+ "  } else if (type = 'check') {"
				+ "    document.getElementById('radioSelect').style.display='none';"
				+ "    document.getElementById('checkSelect').style.display='block';"
				+ "    clearRadios();"
				+ "  }"
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
		List<Topic> sem1 = new ArrayList<Topic>();
		List<Topic> sem2 = new ArrayList<Topic>();
		for (Topic t : topics) {
			if (t.orderBy.startsWith("1") && (topicKey==0 || t.topicGroup%(2*topicKey)/topicKey==1)) sem1.add(t);
			else if (t.orderBy.startsWith("2") && (topicKey==0 || t.topicGroup%(2*topicKey)/topicKey==1)) sem2.add(t);
		}

		String selectorType = "";
		if ("Quiz".equals(assignmentType) || "Homework".equals(assignmentType)) selectorType = "radio";
		else if ("PracticeExam".equals(assignmentType)) selectorType = "check";
		
		// Create a table with radio buttons for Quiz or Homework assignments
		buf.append("<div id=radioSelect style='display:" + (selectorType.equals("radio")?"block":"none") + "'>");  // big box containing radio buttons
		buf.append("<font color=red>Please select one topic for this assignment:</font><br>");
		buf.append("<div style='display:table'>"); // start table of radio buttons
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false;>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2) buf.append("<label><input type=radio name=TopicId value=" + t.id + " onClick=this.form.radsub.disabled=false;>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit name=radsub disabled=true value='Select this topic'>"); // submit button for radios
		buf.append("</div>"); // end of big box with radio buttons

		// Create a table with check boxes for Practice Exam assignments
		buf.append("<div id=checkSelect style='display:" + (selectorType.equals("check")?"block":"none") + "'>"); // big box containing check boxes
		buf.append("<font color=red>Please select 3 or more topics for this exam:</font><br>");
		buf.append("<div style='display:table'>"); // start table of check boxes
		buf.append("<div style='display:table-row'><div style='display:table-cell'>");   // left column Chem1 topics		
		for (Topic t : sem1) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks();>" + t.title + "</label><br>");
		buf.append("</div><div style='display:table-cell'>");  // right column Chem2 topics
		for (Topic t : sem2) buf.append("<label><input type=checkbox name=TopicIds value=" + t.id + " onClick=countChecks();>" + t.title + "</label><br>");
		buf.append("</div></div></div>");  // end of cell, row, table
		buf.append("<input type=submit id=checksub disabled=true value='Select at least 3 topics for this assignment'><br>");
		buf.append("</div>"); // end of big box with check boxes
		
		buf.append("</form>");
		return buf.toString();
	}
}	
