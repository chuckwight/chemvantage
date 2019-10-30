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
import java.net.URL;
import java.net.URLEncoder;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
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
		/*
			User user = null;
			HttpSession session = request.getSession();
			if (session.isNew()) user = User.getUser(request.getParameter("CvsToken"));
			else user = User.getInstance(session);
		*/
			User user = User.getUser(request.getParameter("Token"));  // may be null for LTI launch request
			
			if ("UpdateAssignment".equals(request.getParameter("UserRequest"))) {
				if (!updateAssignment(request,response)) throw new Exception("Assignment update failed.");  // POST the assignmentType and topicIds
				// construct a redirectUrl to the new assignment
				String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
				//long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				String redirectUrl = "/" + request.getParameter("AssignmentType") 
				//+ "?AssignmentId=" + assignmentId
				//+ (request.getSession().isNew()?"&CvsToken=" + user.getCvsToken():"")
				+ "?Token=" + user.token
				+ (lis_result_sourcedid==null?"":"&lis_result_sourcedid=" + lis_result_sourcedid);
				response.sendRedirect(redirectUrl);
				return;
			} else if (request.getParameter("lti_message_type")!=null) { // handle LTI launch request for LTIv1p0 and LTIv1p1
				basicLtiLaunchRequest(request,response);
			} else if (request.getParameter("id_token")!=null) {  // handle LTI v1p3 launch request
				ltiv1p3LaunchRequest(request,response);
			} else doError(request,response,"Invalid LTI launch request.",null,null); 
		} catch (Exception e) {	
		}
	}

	void ltiv1p3LaunchRequest(HttpServletRequest request,HttpServletResponse response) 
			throws IOException {
		StringBuffer debug = new StringBuffer("Start:<br>");
		Deployment d = null;
		DecodedJWT id_token = null;
		String platform_id = null;
		Map<String,Claim> id_token_claims = null;
		String platform_deployment_id = null;
		
		try {
			String iss = "https://" + request.getServerName();
			Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
			JWTVerifier verifier = JWT.require(algorithm).withIssuer(iss).build();
		    verifier.verify(request.getParameter("state"));
		    debug.append("Auth token OK. ");
		    
		    id_token = JWT.decode(request.getParameter("id_token"));
		    
		    platform_id = id_token.getIssuer();
		    if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
		    
		    id_token_claims = id_token.getClaims();
		    // validate the id_token signature:
		    // first get the correct Deployment entity for this platform to find the public key
		    Claim deployment_id_claim = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/deployment_id");
		    if (deployment_id_claim==null) throw new Exception("The deployment_id claim was not found in the id_token payload.");
		    String deployment_id = deployment_id_claim.asString();
		    platform_deployment_id = platform_id + "/" + deployment_id;
		    d = Deployment.getInstance(platform_id, deployment_id);
		    debug.append("Got deployment: " + platform_deployment_id + "<br>");
		    
			// retrieve the public Java Web Key from the platform to verify the signature
			URL jwks_url = new URL(d.well_known_jwks_url);
			JwkProvider provider = new UrlJwkProvider(jwks_url);
			if (id_token.getKeyId() == null || id_token.getKeyId().isEmpty()) throw new Exception("No JWK id found.");
		    Jwk jwk = provider.get(id_token.getKeyId()); //throws Exception when not found or can't get one
		    RSAPublicKey public_key = (RSAPublicKey)jwk.getPublicKey();
		    debug.append("Retrieved platform's public RSA key OK.<br>");
			
		    // verify LTI version 1.3.0
		    Claim lti_version = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/version");
		    if (lti_version==null) throw new Exception("LTI version claim was missing.");    
		    if (!"1.3.0".equals(lti_version.asString())) throw new Exception("Incorrect LTI version claim");
		    	
		    // Validate the LTI message_type:
		    String message_type = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type").asString();
		    if (!"LtiResourceLinkRequest".equals(message_type)) throw new Exception("Incorrect or missing LTI message_type claim");
		    
			// verify the JWT signature
		    if ("RS256".contentEquals(id_token.getAlgorithm())) algorithm = Algorithm.RSA256(public_key,null);
			JWT.require(algorithm).build().verify(id_token);  // throws Exception if not valid
			debug.append("Token validated OK.<br>");
		} catch (Exception e) {
			response.sendError(401, e.toString());
		}
		
		// At this point we have a valid LTI launch; process the claims:
		
		try {			
			// Process deployment claims:
			try {
				d.email = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/tool_platform").asMap().get("contact_email").toString();	
			} catch (Exception e) {}
			
			// Process User information:
			String sub = id_token.getSubject();  // required
			if (sub==null || sub.isEmpty()) throw new Exception("Missing or empty subject claim in the id_token.");
			String userId = platform_id + "/" + sub;
			User user = new User(userId);
/*
  			try {
				user = ofy().load().type(User.class).id(userId).safe();
			} catch (Exception e) {
				user = new User(userId);
			}
*/
			//HttpSession session = request.getSession(true);
			//session.setAttribute("UserId",userId);
			
			user.roles = 0;
			Claim roles_claim = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/roles");
			if (roles_claim==null) throw new Exception("Required roles claim is missing from the id_token");
			String[] roles = roles_claim.asArray(String.class);
			for (int i=0; i<roles.length; i++) {
				roles[i] = roles[i].toLowerCase();
				if (roles[i].contains("teachingassistant")) user.setIsTeachingAssistant(true);
				if (roles[i].contains("instructor")) user.setIsInstructor(true);
				if (roles[i].contains("administrator")) user.setIsAdministrator(true);
			}
			
			// Process context (Group) information:
			Group myGroup = null;
			Map<String,Object> lti_ags_claims = new HashMap<String,Object>();
			Map<String,Object> lti_nrps_claims = new HashMap<String,Object>();
			Map<String,Object> context = null;
			String context_id = null;
			String context_label = "default group";
			
			try {  // look for optional context claim
				context = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/context").asMap();			
				context_id = platform_id + "/" + context.get("id").toString();
				context_label = context.get("label").toString();
			} catch (Exception e) { // default context for platform
				context_id = platform_id + "/";
			}

			try {
				myGroup = ofy().load().type(Group.class).filter("context_id",context_id).first().safe();
				if (user.isInstructor()) myGroup.instructorId = user.id;
			} catch (Exception e) {
				String instructorId = user.isInstructor()?user.id:null;
				myGroup = new Group(platform_deployment_id,context_id,context_label,instructorId);
				ofy().save().entity(myGroup).now(); // produces an id value for the myGroup entity
			}
			// Make sure that this user is listed as a member of the group:
			user.myGroupId = myGroup.id;
			myGroup.addMember(user.id); // automatically checks for duplicate entries

			debug.append("Start processing LTI-AGS information:<br>");
			try {  // Process information for LTI Assignment and Grade Services (AGS)
				lti_ags_claims = id_token_claims.get("https://purl.imsglobal.org/spec/lti-ags/claim/endpoint").asMap();		

				// get the list of capabilities allowed by the platform
				String scope = lti_ags_claims.get("scope").toString();
				scope = scope.substring(1,scope.length()-1); // removes leading and trailing square brackets from the Json string
				scope = scope.replaceAll(",", " "); // replace commas to make a space separated URL-safe list of scopes
				scope = scope.replaceAll("  ", " "); // remove any leftover white space (tidying up)
				myGroup.lti_ags_scope = scope;  // store this in the Group entity for use with LTI services calls to the platform

				// if the platform allows reading/writing scores using LIS, store the URL where lineitem URLs can be found for the assignments
				if (myGroup.lti_ags_scope.contains("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem")) myGroup.lti_ags_lineitems_url = lti_ags_claims.get("lineitems").toString();
				myGroup.canReadLisScores = myGroup.lti_ags_scope.contains("https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly");
				myGroup.isUsingLisOutcomeService = myGroup.lti_ags_scope.contains("https://purl.imsglobal.org/spec/lti-ags/scope/score");
				myGroup.lis_outcome_service_format = "application/vnd.ims.lis.v1.score+json";
			} catch (Exception e) {				
			}

			try { // if the platform allows LTI Advantage Names and Roles Provisioning, store the URL
				lti_nrps_claims = id_token_claims.get("https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice").asMap();
				if (lti_nrps_claims != null) {
					myGroup.context_memberships_url = lti_nrps_claims.get("context_memberships_url").toString();
					myGroup.lti_ags_scope += " https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
				}
			} catch (Exception e) {
			}

			ofy().save().entity(myGroup);
			//ofy().save().entity(user);
			
			// Process the ResourceLinkRequest information:
			String resourceLinkId = null;
			try {
				Map<String,Object> resource_link_claims = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/resource_link").asMap();
				resourceLinkId = resource_link_claims.get("id").toString();
			} catch (Exception e) {
				throw new Exception("Resource link id was missing from payload.");
			}
			
			// Construct the URL to which the user should be redirected
			
			//cvsToken = user.getCvsToken();
			
			Assignment myAssignment = null;
			long assignmentId = 0;
			
			// If this is a Deep Linking request launch, get the assignmentId from the target_link_uri
			String target_link_uri = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/target_link_uri").asString();
			try {
				int i = target_link_uri.indexOf("AssignmentId=");
				if (i==-1) throw new Exception(); // this is not a deep link URI
				assignmentId = Long.parseLong(target_link_uri.substring(i+13));
				myAssignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
				if (myAssignment.resourceLinkId == null) {
					myAssignment.resourceLinkId = resourceLinkId;
					ofy().save().entity(myAssignment);
				}
			} catch (Exception e) {}

			if (myAssignment==null) {  // try to find the assignment based on the resourceLinkId value
				try {  // Find the existing assignment for this resourceLinkId or make a new one
					myAssignment = ofy().load().type(Assignment.class).filter("platform_deployment_id",platform_deployment_id).filter("resourceLinkId",resourceLinkId).first().safe();		
				} catch (Exception e) {  // it appears that the assignment does not exist; make a new one:
					myAssignment = new Assignment(platform_deployment_id,myGroup.id,resourceLinkId);
					ofy().save().entity(myAssignment).now();  // We will need this Assignment entity immediately
				}
			}
			
			// Update the lineitem URL for this assignment, if necessary
			if (myAssignment.lti_ags_lineitem_url==null) {
				if (lti_ags_claims.get("lineitem") != null) {  // get the lineitem from the id_token
					myAssignment.lti_ags_lineitem_url = lti_ags_claims.get("lineitem").toString(); // cache the lineitem URL 
				} else if (myAssignment.assignmentType != null && myAssignment.topicId>0) {  // create a new lineitem in the platform
					myAssignment.lti_ags_lineitem_url = LTIMessage.createLineItem(myAssignment);
				}
				ofy().save().entity(myAssignment);
			}

			// At this point we should have a valid Assignment, but it may not have an 
			// assignmentType or topicId(s) if it's new.
			user.setToken(myAssignment.id);

			if (myAssignment.assignmentType == null || myAssignment.topicId==0) {  //Show the the pickResource form:									
				response.getWriter().println(Home.header + pickResourceForm(user,myAssignment,null) + Home.footer);
				return;
			} else {  // redirect the user's browser to the assignment
				target_link_uri = "/" + myAssignment.assignmentType 
						//+ "?AssignmentId=" + myAssignment.id
						//+ "&CvsToken=" + cvsToken;
						+ "?Token=" + user.token;
				response.sendRedirect(target_link_uri);
				return;
			}
		
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
				if (roles.contains("instructor")) user.setIsInstructor(true);
				if (roles.contains("administrator")) user.setIsAdministrator(true);
				if (roles.contains("teachingassistant")) user.setIsTeachingAssistant(true);
			}
			ofy().save().entity(user).now();			
			// user information OK
			
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
			
			ofy().save().entity(myGroup).now();
			// Context info OK
			
			if (user.myGroupId != myGroup.id) {
				user.changeGroups(myGroup.id);
				ofy().save().entity(user);
			}
			
			// Use the resourceLinkId to find the assignment or create a new one:
			Assignment myAssignment = null;
			String redirectUrl;
			//String cvsToken = user.getCvsToken();
			
			try {  // load the requested Assignment entity if it exists
				myAssignment = ofy().load().type(Assignment.class).filter("domain",domain.domainName).filter("resourceLinkId", resource_link_id).first().safe();
				//ofy().save().entity(myAssignment);

				if (myAssignment.assignmentType != null) {
					user.setToken(myAssignment.id);
					redirectUrl = "/" + myAssignment.assignmentType 
							//+ "?AssignmentId=" + myAssignment.id 
							//+ "&CvsToken=" + cvsToken
							+ "?Token=" + user.token
							+ (lis_result_sourcedid==null?"":"&lis_result_sourcedid=" + lis_result_sourcedid);
					response.sendRedirect(redirectUrl);
					return;
				}
			} catch (Exception e) {  // or create a new one with the available information (but no assignmentType or topicIds)
				myAssignment = new Assignment(myGroup.id,domain.domainName,resource_link_id);
				ofy().save().entity(myAssignment).now(); // we'll need to load this in a second from the pickResource form
				user.setToken(myAssignment.id);
			}
			// At this point we should have a valid Assignment, but it does not have an 
			// assignmentType or topicId(s). Show the the pickResource form:
			response.getWriter().println(Home.header + pickResourceForm(user,myAssignment,lis_result_sourcedid) + Home.footer);
			return;

		} catch (Exception e) {
			doError(request, response,"LTI Launch failed. " + e.getMessage(), null, null);
		}		
	}
	
	boolean updateAssignment(HttpServletRequest request, HttpServletResponse response) {
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
			
			if (a.lti_ags_lineitem_url==null) { // create a new lineitem in the platform
				try {
					Group g = ofy().load().type(Group.class).id(a.groupId).safe();
					String url = null;
					if (g.lti_ags_lineitems_url != null) url = LTIMessage.createLineItem(a);
					if (url != null) a.lti_ags_lineitem_url = url;
				} catch (Exception e) {
				}
			}
			ofy().save().entity(a).now(); // going to need this is just a few milliseconds
			return true;
		} catch (Exception e) {
		}
		return false;
	}
/*	
	void resourceUrlFinder(User user, HttpServletRequest request, HttpServletResponse response) {
		/* 
		 * This method searches for an assignment containing the resource_link_id for this LTI launch
		 * and redirects the user to the correct assignment. If no assignment can be found, the method 
		 * tries to validate the assignmentType and topicId or topicIds. If the user is the instructor
		 * then a new assignment is created and the resource link added. Then the user is redirected 
		 * to the correct assignment.  If the assignment cannot be validated, the user is sent to the 
		 * resourcePicker page to choose a valid assignmentId and topicId or topicIds.
		 */
/*
		try {
			// a resource_link_id string is required for every valid LTI launch
			String resource_link_id = request.getParameter("resource_link_id");
			Assignment myAssignment = null;
			try {  // the LTIv1p3 way:
				long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				myAssignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			} catch (Exception e) {  // legacy LTIv1p1 method
				myAssignment = ofy().load().type(Assignment.class).filter("domain",user.domain).filter("resourceLinkId", resource_link_id).first().now();			
			}
			// try to resurrect an older LTI1p1 assignment
			if (myAssignment==null) {	// didn't find it. Look for resource_link_id in the (deprecated) resourceLinkIds list
				List<Assignment> allAssignments = ofy().load().type(Assignment.class).list();			
				for (Assignment a : allAssignments) {
					if (resource_link_id.contentEquals(a.resourceLinkId) || (a.resourceLinkIds != null && a.resourceLinkIds.contains(resource_link_id))) {  // found it
						myAssignment = a;
						a.resourceLinkId = resource_link_id;
						ofy().save().entity(a);
						break;
					}
				}
			}			
			String assignmentType = request.getParameter("AssignmentType");
			if (myAssignment==null) myAssignment = new Assignment(user.myGroupId,user.domain,resource_link_id,assignmentType);

			// At this point we have an Assignment, either
			// Given the id from LTI1p3 launch, or
			// 1) found an older 1p1 type from the resourceLinkId, or
			// 2) found an older one from a set of resourceLinkIds, or
			// 3) made up a new 1p1 assignment
			// Note that assignmentType may be null if using resourcePicker
			
			if (assignmentType != null) {  // instructor is creating the assignment now
				long topicId = 0L;
				List<Long> topicIds = new ArrayList<Long>();
				try {
					if ("PracticeExam".equals(assignmentType)) { // make a new PracticeExam assignment
						String[] tIds = request.getParameterValues("TopicIds");
						if (tIds==null || tIds.length<3) throw new Exception();
						for (int i=0;i<tIds.length;i++) topicIds.add(Long.parseLong(tIds[i]));
						myAssignment.topicIds = topicIds;
					} else if (("Quiz".equals(assignmentType) || "Homework".equals(assignmentType))) { // make a new Quiz or Homework assignment
						topicId = Long.parseLong(request.getParameter("TopicId"));
						if (topicId == 0) throw new Exception();
						myAssignment.topicId = topicId;
					}
					ofy().save().entity(myAssignment).now();  // need it now because the user is about to be redirected to it
					
				} catch (Exception e) {}
			}

			String redirectUrl;
			String cvsToken = user.getCvsToken();
			if (myAssignment.assignmentType != null) {
				redirectUrl = "/" + myAssignment.assignmentType + "?AssignmentId=" + myAssignment.id + "&CvsToken=" + cvsToken;;
				response.sendRedirect(redirectUrl);
				return;
			} else response.getWriter().println(Home.header + pickResourceForm(user,myAssignment) + Home.footer);
			
		} catch (Exception e) {
		}
	}
*/	
	String pickResourceForm(User user,Assignment myAssignment,String lis_result_sourcedid) {
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
			//buf.append("<input type=hidden name=AssignmentId value=" + myAssignment.id + ">");
			//buf.append("<input type=hidden name=CvsToken value=" + user.cvsToken + ">");
			buf.append("<input type=hidden name=Token value=" + user.token + ">");
			if (lis_result_sourcedid != null) buf.append("<input type=hidden name=lis_result_sourcedid value=" + lis_result_sourcedid + ">");
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
		out.println(s + "<br>" + e.toString());
	}

	@Override
	public void destroy() {

	}
}