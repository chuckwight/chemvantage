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
import com.google.gson.JsonObject;

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
			
			if ("Select assignments".equals(request.getParameter("UserRequest"))) {  // submitting desired links
				out.println(deepLinkResponseMsg(request));
				return;
			} else { // This is probably a fresh Deep Links request. Check the required token:
				DecodedJWT id_token = JWT.decode(request.getParameter("id_token"));
				if (validDeepLinkRequest(id_token)) out.println(contentPickerForm(id_token,state));
			}
		} catch (Exception e) {	 
			out.println(e.toString());
		}
	}

	boolean validDeepLinkRequest(DecodedJWT id_token) {
		try {
			String platform_id = id_token.getIssuer();
			if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
			Map<String,Claim> id_token_claims = id_token.getClaims();
			String deployment_id = id_token_claims.get("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
			Deployment d = Deployment.getInstance(platform_id, deployment_id);

			// retrieve the public Java Web Key from the platform to verify the signature
			URL jwks_url = new URL(d.well_known_jwks_url);
			JwkProvider provider = new UrlJwkProvider(jwks_url);
			Jwk jwk = provider.get(id_token.getKeyId()); //throws Exception when not found or can't get one
			RSAPublicKey public_key = (RSAPublicKey)jwk.getPublicKey();
			Algorithm algorithm = null;
			if ("RS256".contentEquals(id_token.getAlgorithm())) algorithm = Algorithm.RSA256(public_key,null);
			JWT.require(algorithm).build().verify(id_token);  // throws Exception if not valid
			if (!id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/message_type").asString().equals("LtiDeepLinkingRequest"))
				throw new Exception("Wrong LTI message type");
			if (!id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/version").asString().equals("1.3.0"))
				throw new Exception("Wrong LTI version");

			// At this point the request token is valid.
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	String contentPickerForm(DecodedJWT id_token,String state) {
		StringBuffer buf = new StringBuffer(Home.header);
		try {
			buf.append("<h3>ChemVantage Resource Picker</h3>");
			
			String platform_id = id_token.getIssuer();
			if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
			Map<String,Claim> claims = id_token.getClaims();			
			String deployment_id = claims.get("https://purl.imsglobal.org/spec/lti/claim/deployment_id").asString();
			Map<String,Object> settings = claims.get("https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings").asMap();
			String data = settings.get("data").toString();
			String subject = id_token.getSubject();
			String deep_link_return_url = settings.get("deep_link_return_url").toString();
			
			// Use the id_token to verify that this user is an instructor or administrator:
			String[] roles = claims.get("https://purl.imsglobal.org/spec/lti/claim/roles").asArray(String.class);
			boolean authorized = false;
			for (int i=0;i<roles.length;i++) {
				roles[i] = roles[i].toLowerCase();
				if (roles[i].contains("instructor") || roles[i].contains("administrator")) authorized = true;
			}			
			if (!authorized) {
				buf.append("You must be logged into your LMS in an instructor or administrator role in "
						+ "order to select assignment links for this class.");
				throw new Exception();
			}

			Map<String,Object> context = id_token.getClaim("https://purl.imsglobal.org/spec/lti/claim/context").asMap();			
			String context_id = platform_id + "/" + context.get("id").toString();
			long groupId = ofy().load().type(Group.class).filter("context_id",context_id).first().safe().id;
	
			List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
			buf.append("You may select any of the following Quiz or Homework assignments to make it easy for your LMS "
					+ "to create assignment links for your students. The subjects are arranged roughly in the order "
					+ "frequently encountered in many year-long General Chemistry courses, but you may present them to "
					+ "your students in any order without difficulty.<p>");
					
			buf.append("<form action=/lti/deeplinks method=POST>"
					+ "<input type=hidden name=PlatformId value='" + id_token.getIssuer() + "'>"
					+ "<input type=hidden name=DeploymentId value='" + deployment_id + "'>"
					+ "<input type=hidden name=Subject value='" + subject + "'>"
					+ "<input type=hidden name=state value=" + state + ">"
					+ "<input type=hidden name=GroupId value=" + groupId + ">"
					+ "<input type=hidden name=deep_link_return_url value=" + deep_link_return_url + ">"
					+ (data==null?"":"<input type=hidden name=data value='" + data + "'>"));
			buf.append("<table><tr><th>Title</th><th>Quiz</th><th>Homework</th></tr>");
			for (Topic t : topics) {
				// The checkbox values are the topicIds with a Q or H prepended to them to indicate the assignmentType
				buf.append("<tr><td>" + t.title + "</td><td align=center><input type=checkbox name=Selections value=Q" + t.id + "></td><td align=center><input type=checkbox name=Selections value=H" + t.id + "></td></tr>");
			}
			buf.append("</table><input type=submit name=UserRequest value='Select assignments'></form>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString() + Home.footer;
	}
	
	String deepLinkResponseMsg(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String platform_id = request.getParameter("PlatformId");
		String deployment_id = request.getParameter("DeploymentId");
		String deep_link_return_url = request.getParameter("deep_link_return_url");
		try {
			Date now = new Date();
			Date exp = new Date(now.getTime() + 5400000L); // 90 minutes from now
			String data = request.getParameter("data");
			Deployment d = Deployment.getInstance(platform_id, deployment_id);
			
			Group g = ofy().load().type(Group.class).id(Long.parseLong(request.getParameter("GroupId"))).safe();
			
			// The contentPickerForm selections are string values of Topic ids with Q or H prepended to indicate the assignmentType
			// We need to use these to create the relevant assignments with Topic titles
			// Start by making a list of the topicIds corresponding to the selections, because we'll need the titles later
			String[] selections = request.getParameterValues("Selections");
			List<Long> topicIds = new ArrayList<Long>();
			for (int i=0;i<selections.length;i++) {
				long tId = Long.parseLong(selections[i].substring(1));
				if (!topicIds.contains(tId)) topicIds.add(tId);
			}
			
			// Create the assignments corresponding to the selections (Quiz and/or Homework)
			// and save them to a List<Assignment> so they can be saved in a single operation
			List<Assignment> assignments = new ArrayList<Assignment>();
			String assignmentType = null;
			for (int i=0;i<selections.length;i++) {
				switch (selections[i].substring(0,1)) {
					case ("Q"): assignmentType = "Quiz"; break;
					case ("H"): assignmentType = "Homework"; break;
				}
				long tId = Long.parseLong(selections[i].substring(1));
				assignments.add(new Assignment(assignmentType,tId,d.platform_deployment_id,g.id));				
			}
			ofy().save().entities(assignments).now(); // using now() ensures creation of the id values
	
			Map<Long,Topic> topicsMap = ofy().load().type(Topic.class).ids(topicIds);
			
			String serverUrl = "https://" + request.getServerName();
			String client_id = d.client_id;
			String subject = request.getParameter("Subject");
			String nonce = Nonce.generateNonce();
			
			Encoder enc = Base64.getUrlEncoder().withoutPadding();
			
			JsonObject header = new JsonObject();
			header.addProperty("typ", "JWT");
			header.addProperty("alg", "RS256");
			byte[] hdr = enc.encode(header.toString().getBytes("UTF-8"));
		
			JsonObject payload = new JsonObject();
			payload.addProperty("iss",client_id);
			payload.addProperty("sub",subject);
			payload.addProperty("aud",platform_id);
			payload.addProperty("nonce", nonce);
			payload.addProperty("exp", exp.getTime()/1000);
			payload.addProperty("iat", now.getTime()/1000);
			payload.addProperty("kid", d.rsa_key_id);
			payload.addProperty("jwks_uri", serverUrl + "/jwks");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiDeepLinkingResponse");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/deployment_id", deployment_id);
			payload.addProperty("https://purl.imsglobal.org/spec/lti-dl/claim/data", data);
		
			JsonArray content_items = new JsonArray();
			for (Assignment a : assignments) {
				JsonObject item = new JsonObject();
				item.addProperty("type", "ltiResourceLink");
				item.addProperty("url", serverUrl + "/" + a.assignmentType + "?AssignmentId=" + a.id);
				item.addProperty("title", a.assignmentType + " - " + topicsMap.get(a.topicId).title);
				content_items.add(item);
			}
			payload.add("https://purl.imsglobal.org/spec/lti-dl/claim/content_items", content_items);
		
			byte[] pld = enc.encode(payload.toString().getBytes("UTF-8"));
			
			String jwt = String.format("%s.%s",new String(hdr),new String(pld));
			
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(KeyStore.getRSAPrivateKey(d.rsa_key_id),new SecureRandom());
			signature.update(jwt.getBytes("UTF-8"));
			String sig = new String(enc.encode(signature.sign()));
			jwt = String.format("%s.%s", jwt, sig);
					
			//	Create a form to be auto-submitted to the platform by the user_agent browser
			buf.append("<html><head></head>"
					+ "<body>"// onLoad=document.forms['selections'].submit()>"
					+ "Click the Submit button to POST the following JSON Web Token:<p>" + jwt + "<p>"
					+ "<form name=selections method=POST action='" + deep_link_return_url + "'>"
					+ "<input type=hidden name=JWT value='" + jwt + "'>"
					+ "<input type=submit>"
					+ "</form></body></html>");
		
		} catch (Exception e) {
			buf.append("<html><head><meta http-equiv='Refresh' content='5; url=" + deep_link_return_url + "' />"
					+ "</head><body>"
					+ "Sorry, an unexpected error occurred: " + e.toString() + "<p>"
					+ "Please try again later. If this page does not close automatically "
					+ "in 5 seconds, please follow this link to return to your LMS:<br>"
					+ "<a href=" + deep_link_return_url + ">" + deep_link_return_url + "</a>" + Home.footer
					+ "</body></html>");
		}
		return buf.toString();
	}
	
}
