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
 * Deployments (e.g., as separate accounts), each Deployment will have a designated client_id
 * value (identifying the tool provider), which defines the security contract for the connection. 
 * The following values are determined by the Deployment, and therefore cannot be considered
 * to be universally unique for the tool provider:
 * deployment_id - this is mitigated by prepending a platform_id to form a unique platformDeploymentId value
 * context_id - the groupId should be identified by using both the platformDeploymentId and the context_id
 * user_id - this should be prepended with the platformDeploymentId to form a unique userId value
 * resource_link_id - the assignmentId should be identified by the platformDeploymentId and the resource_link_id
 * 
 * The token is a JWT containing useful information about the transaction, including
 * platformDeploymentId
 * userId
 * user.roles
 * groupId
 * assignmentId
 * 
 * 
 */

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import com.auth0.jwt.interfaces.Claim;
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
			else { // POST the assignmentType and topicIds			
				String token = updateAssignment(request,response);   
				if (token == null) throw new Exception("Assignment update failed.");
				else {
					String redirectUrl = "/" + request.getParameter("AssignmentType") + "?Token=" + token;
					response.sendRedirect(redirectUrl);
				}
			}
		} catch (Exception e) {	
			response.sendError(401, e.getMessage());
		}
	}

	void ltiv1p3LaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws Exception {
		
		validateStateToken(request); // ensures proper OIDC authorization flow completed			
		
		StringBuffer debug = new StringBuffer("Starting LTIv1.3 launch sequence...");
		
		// Decode the JWT id_token as a JsonObject:
		DecodedJWT id_token = null;
		JsonObject claims = null;
		try {
			id_token = JWT.decode(request.getParameter("id_token"));
			String json = new String(Base64.getUrlDecoder().decode(id_token.getPayload()));
			claims = new JsonParser().parse(json).getAsJsonObject();
		} catch (Exception e) {
			throw new Exception("id_token was not a valid JWT.");
		}
		
		// get the platform_id and deployment_id to load the correct Deployment d
		String platform_id = id_token.getIssuer();
		String deployment_id = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
		if (deployment_id == null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
		String platformDeploymentId = platform_id + "/" + deployment_id;
		Deployment d = Deployment.getInstance(platformDeploymentId);
		Deployment original_d = d.clone();  // make a copy to compare for updating later
		
		// validate the id_token audience:
		List<String> aud = id_token.getAudience();
		if (aud.size()==1 && aud.get(0).contentEquals(d.client_id)); // OK, continue
		else if (aud.size()>1 && aud.contains(d.client_id) && id_token.getClaim("azp").asString().contentEquals(d.client_id)); // OK, continue
		else throw new Exception("The id_token audience is not authorized in ChemVantage.");

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

		// verify LTI version 1.3.0
		JsonElement lti_version = claims.get("https://purl.imsglobal.org/spec/lti/claim/version");
		if (lti_version == null) throw new Exception("LTI version claim was missing.");    
		if (!"1.3.0".equals(lti_version.getAsString())) throw new Exception("Incorrect LTI version claim");

		// Validate the LTI message_type:
		JsonElement message_type = claims.get("https://purl.imsglobal.org/spec/lti/claim/message_type");
		if (message_type == null) throw new Exception("Missing LTI message_type.");
		if (!"LtiResourceLinkRequest".equals(message_type.getAsString())) throw new Exception("LTI message_type claim must be LtiResourceLink");

		// At this point we have a valid LTI launch; process the claims:
		debug.append("state token and id_token validated...");

		// Process deployment claims:
		try {
			JsonObject platform = claims.get("https://purl.imsglobal.org/spec/lti/claim/tool_platform").getAsJsonObject();
			d.email = platform.get("email_contact").getAsString();
			d.lms_type = platform.get("product_family_code").getAsString() + " version " + platform.get("version").getAsString();
		} catch (Exception e) {}

		
		// Process User information:
		String sub = id_token.getSubject();  // required
		if (sub==null || sub.isEmpty()) throw new Exception("Missing or empty subject claim in the id_token.");
		String userId = platform_id + "/" + sub;
		User user = new User(userId);

		JsonElement roles_claim = claims.get("https://purl.imsglobal.org/spec/lti/claim/roles");
		if (roles_claim == null || !roles_claim.isJsonArray()) throw new Exception("Required roles claim is missing from the id_token");
		JsonArray roles = roles_claim.getAsJsonArray();
		Iterator<JsonElement> roles_iterator = roles.iterator();
		while(roles_iterator.hasNext()){
			String role = roles_iterator.next().getAsString().toLowerCase();
			user.setIsInstructor(role.contains("instructor"));
			user.setIsAdministrator(role.contains("administrator"));
		}

		debug.append("user info OK...");
			
		// Process information for LTI Assignment and Grade Services (AGS)
		String scope = "";
		String lti_ags_lineitem_url = "";
		String lti_ags_lineitems_url = "";
		try {  
			JsonObject lti_ags_claims = claims.get("https://purl.imsglobal.org/spec/lti-ags/claim/endpoint").getAsJsonObject();
			
			// get the list of AGS capabilities allowed by the platform
			JsonArray scope_claims = lti_ags_claims.get("scope").getAsJsonArray();
			Iterator<JsonElement> scopes_iterator = scope_claims.iterator();
			while (scopes_iterator.hasNext()) scope += scopes_iterator.next().getAsString() + " ";
		
			lti_ags_lineitem_url = lti_ags_claims.get("lineitem").getAsString();
			lti_ags_lineitems_url = lti_ags_claims.get("lineitems").getAsString();
			} catch (Exception e) {				
		}

		// Process information for LTI Advantage Names and Roles Provisioning (NRPS)
		String nrps_context_memberships_url = null;
		try { 
			JsonObject lti_nrps_claims = claims.get("https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice").getAsJsonObject();
			if (lti_nrps_claims != null) scope += " https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
			nrps_context_memberships_url = lti_nrps_claims.get("context_memberships_url").getAsString();
			debug.append("supports NRPS...");
		} catch (Exception e) {
		}

		// Update scope and lti_ags_lineitems_url, if necessary
		if ((scope != null && !scope.equals(d.scope)) || (lti_ags_lineitems_url != null && !lti_ags_lineitems_url.equals(d.lti_ags_lineitems_url))) {
			d.scope = scope;
			d.lti_ags_lineitems_url = lti_ags_lineitems_url;
			debug.append("scope updated OK...");
		}

		// Save the updated Deployment entity, if necessary
		if (!d.equivalentTo(original_d)) ofy().save().entity(d);
		
		// Process the ResourceLinkRequest information:
		String resourceLinkId = null;
		try {
			JsonObject resource_link_claims = claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link").getAsJsonObject();
			resourceLinkId = resource_link_claims.get("id").toString();
		} catch (Exception e) {
			throw new Exception("Resource link id was missing from payload.");
		}

		/* Find or create the correct Assignment entity for this LtiResourceLinkRequest launch
		*  Note: every launch MUST be accompanied by the resourceLinkId value. This is a platform-generated 
		*  value that must be unique to a link in the LMS platform. On every launch, ChemVantage will  
		*  1) look for an Assignment entity associated with this LMS and this resourceLinkId value
		*  2) look for a lineitem created through the DeepLinking flow with a resourceId equal to the Assignment.id
		*  3) look for an Assignment entity associated with a resourceLinkId in the comma-separated list 
		*     of URL-encoded resource link ID values in the ResourceLink.id.history variable (not yet implemented)
		*  4) create a new Assignment and lineitem, if required   
		*/
		 
		Assignment myAssignment = null;
			
		// 1) Try to find the assignment using the platformDeploymentId and the resourceLinkId value
		myAssignment = ofy().load().type(Assignment.class).filter("domain",platformDeploymentId).filter("resourceLinkId",resourceLinkId).first().now();

		// 2) If the lineitem was created by DeepLinking, try to get the resourceId from the lineitem service; that will be the assignmentId
		if (myAssignment == null) {
			myAssignment = ofy().load().type(Assignment.class).id(LTIMessage.getAssignmentId(d,resourceLinkId)).now();
		}

		// 3) Look for an Assignment with a resourceLinkId value listed in the ResourceLink.id.history list for this launch
		// Note: not yet implemented as of January 2020

		// 4) If none of that worked, then the assignment probably doesn't exist, so make a new one:
		if (myAssignment == null) {
			myAssignment = new Assignment(platformDeploymentId,resourceLinkId,null,null);
		}

		// Update the Assignment parameters:
		Assignment original_a = myAssignment.clone(); // make a copy to compare with for updating later

		myAssignment.resourceLinkId = resourceLinkId;			
		if (lti_ags_lineitem_url != null) myAssignment.lti_ags_lineitem_url = lti_ags_lineitem_url;
		else if (d.lti_ags_lineitems_url != null) {
			lti_ags_lineitem_url = LTIMessage.getLineItemUrl(d,resourceLinkId);
			if (lti_ags_lineitem_url.contentEquals("Not found.")) myAssignment.lti_ags_lineitem_url = LTIMessage.createLineItem(myAssignment);
		}						
		myAssignment.lti_nrps_context_memberships_url = nrps_context_memberships_url;

		// Save the updated Assignment entity, if necessary
		if (!myAssignment.equivalentTo(original_a)) ofy().save().entity(myAssignment);
		debug.append("assignment saved OK...");

		// Create a cross-site request forgery (CSRF) token containing the Assignment.id
		user.setToken(myAssignment.id);
		debug.append("user token set OK...");

		// If this is the first time this Assignment has been used, it may be missing the assignmentType and topicId(s)
		if (myAssignment.assignmentType == null) {  //Show the the pickResource form:									
			response.getWriter().println(Home.header + pickResourceForm(user,myAssignment) + Home.footer);
			return;
		} else {  // redirect the user's browser to the assignment
			response.sendRedirect("/" + myAssignment.assignmentType + "?Token=" + user.token);
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
	    verifier.verify(request.getParameter("state"));
	}
	
	protected Map<String,Claim> validateIdToken(DecodedJWT id_token) throws Exception {
		/* This method validates the id_token sent from the LMS platform, verifies that
		 * the elements required by the LTI v1.3 standard are present for a valid 
		 * ResourceLinkId launch request, and returns a set of Claims embedded in the JWT.
		 */
		// get the platform_id and deployment_id to load the correct Deployment d
		String platform_id = id_token.getIssuer();
	    if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
	    Map<String,Claim> id_token_claims = id_token.getClaims();
	    // validate the id_token signature:
	    // first get the correct Deployment entity for this platform to find the public key
	    Claim deployment_id_claim = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/deployment_id");
	    if (deployment_id_claim==null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
	    String deployment_id = deployment_id_claim.asString();
	    Deployment d = Deployment.getInstance(platform_id + "/" + deployment_id);
	    if (d==null) throw new Exception("Deployment not found in the datastore.");

	    List<String> aud = id_token.getAudience();
	    if (aud.size()==1 && aud.get(0).contentEquals(d.client_id)); // OK, continue
	    else if (aud.size()>1 && aud.contains(d.client_id) && id_token.getClaim("azp").asString().contentEquals(d.client_id)); // OK, continue
	    else throw new Exception("The id_token audience is not authorized in ChemVantage.");
	    
	    try {
			String email = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/tool_platform").asMap().get("contact_email").toString();	
			if (email != null && !email.contentEquals(d.email)) {
				d.email = email;
				ofy().save().entity(d);
			}
	    } catch (Exception e) {}
		
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
	    // verify LTI version 1.3.0
	    Claim lti_version = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/version");
	    if (lti_version.isNull()) throw new Exception("LTI version claim was missing.");    
	    if (!"1.3.0".equals(lti_version.asString())) throw new Exception("Incorrect LTI version claim");
	    	
	    // Validate the LTI message_type:
	    Claim message_type = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type");
	    if (message_type.isNull()) throw new Exception("Missing LTI message_type.");
	    if (!"LtiResourceLinkRequest".equals(message_type.asString())) throw new Exception("LTI message_type claim must be LtiResourceLink");

	    return id_token_claims;
	}
	
	String updateAssignment(HttpServletRequest request, HttpServletResponse response) {
		try {
			User user = User.getUser(request.getParameter("Token"));
			if (!user.isInstructor()) throw new Exception();
			long assignmentId = user.getAssignmentId();
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			a.assignmentType = request.getParameter("AssignmentType");
			if (a.assignmentType.contentEquals("Quiz") || a.assignmentType.contentEquals("Homework")) {
				a.topicId = Long.parseLong(request.getParameter("TopicId"));
			 	a.questionKeys = ofy().load().type(Question.class).filter("assignmentType",a.assignmentType).filter("topicId",a.topicId).keys().list();
			} else if (a.assignmentType.contentEquals("PracticeExam")) {
				String[] topicIds = request.getParameterValues("TopicIds");
				if (topicIds==null || topicIds.length<3) throw new Exception();
				a.topicIds = new ArrayList<Long>();
				a.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<topicIds.length;i++) {
					long tId = Long.parseLong(topicIds[i]);
					a.topicIds.add(tId);
					a.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tId).keys().list());
				}
			}

			ofy().save().entity(a).now(); // going to need this is just a few milliseconds
			return user.token;
		} catch (Exception e) {
		}
		return null;
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
			buf.append("<input type=hidden name=Token value=" + user.token + ">");
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
		PrintWriter out = response.getWriter();
		out.println(s + "<br>" + (e!=null?e.toString():"unknown error") + "<br>" + message);
	}

	@Override
	public void destroy() {

	}
}