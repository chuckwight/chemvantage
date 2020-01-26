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
			
			if ("Select assignment".equals(request.getParameter("UserRequest"))) {  // submitting desired links
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
			
			Deployment d = Deployment.getInstance(platform_id + "/" + deployment_id);

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
			if (!authorized) throw new Exception("You must be logged into your LMS in an instructor "
					+ "or administrator role in order to select assignment resources for this class.");

			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");

			buf.append("<h2>Assignment Setup Page</h2>"
					+ "Please select the ChemVantage resource that should be associated with this assignment. "
					+ "ChemVantage will remember this choice and send students directly to the assignment.<p>");

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

			buf.append("<form name=AssignmentForm action=/lti/deeplinks method=POST>"
					+ "<input type=hidden name=PlatformId value='" + id_token.getIssuer() + "'>"
					+ "<input type=hidden name=DeploymentId value='" + deployment_id + "'>"
					+ "<input type=hidden name=Subject value='" + subject + "'>"
					+ "<input type=hidden name=state value=" + state + ">"
					+ "<input type=hidden name=deep_link_return_url value=" + deep_link_return_url + ">"
					+ "<input type=hidden name=UserRequest value='Select assignment'>"
					+ (data==null?"":"<input type=hidden name=data value='" + data + "'>"));

			// Display radio buttons for the choice of assignment type:
			buf.append("<table><tr><td>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=Quiz>Quiz</label><br>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=Homework>Homework</label><br>"
					+ "<label><input type=radio name=AssignmentType onClick='inspectRadios();' value=PracticeExam>Practice&nbsp;Exam</label>"
					+ "</td>");
			
			// Display a select box for the choice of topics (initially hidden; visible if Quiz or Homework is the AssignmentType
			buf.append("<td id=topicSelect style='visibility:hidden;vertical-align=top'>"
					+ "<FONT COLOR=RED>Please select one topic for this quiz or homework assignment.</FONT><br>");

			List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
			buf.append("<SELECT NAME=TopicId onChange=document.AssignmentForm.start.disabled=(document.AssignmentForm.TopicId.selectedIndex==0);>"
					+ "<OPTION Value='0'>Select a topic</OPTION>");			
			for (Topic t : topics) if (!t.orderBy.equals("Hide")) buf.append("<OPTION VALUE='" + t.id + "'>" + t.title + "</OPTION>");			 			
			buf.append("</SELECT><input type=submit name=start disabled=true></td></tr>");
			
			// Display a checkbox list for choice of multiple topics (initially hidden) if AssignmentType is PracticeExam
			buf.append("<tr><td colspan=2 id=topicCheck style='visibility:hidden'>");
			buf.append("<TABLE><TR><TD COLSPAN=3 style='color:red'>Please select at least 3 topics for this practice exam:<br></TD></TR>");
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
			buf.append("</TABLE><br>");
			buf.append("The practice exam is designed to be completed in 60 minutes. "
					+"<INPUT TYPE=SUBMIT NAME=begin DISABLED=true VALUE='Select at least 3 topics'>"
					+ "</td></tr></table>");
			buf.append("</form>");
			buf.append("<script>inspectRadios()</script>");
			return buf.toString();

		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString() + Home.footer;
	}
	
	String deepLinkResponseMsg(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<html><head></head><body onLoad=document.forms['selections'].submit()>");
		String platform_id = request.getParameter("PlatformId");
		String deployment_id = request.getParameter("DeploymentId");
		String deep_link_return_url = request.getParameter("deep_link_return_url");
		try {
			Date now = new Date();
			Date exp = new Date(now.getTime() + 5400000L); // 90 minutes from now
			String data = request.getParameter("data");
			Deployment d = Deployment.getInstance(platform_id + "/" + deployment_id);
			
			// Determine the assignmentType for this selection
			String assignmentType = request.getParameter("AssignmentType");
			if (assignmentType == null || assignmentType.isEmpty()) throw new Exception("Assignment type is missing.");
			
			// Determine the topicId or topicIds for this selection
			// Store a list of topic titles in a List<String>
			long topicId = 0L;
			List<Long> topicIds = new ArrayList<Long>();
			List<String> topicTitles = new ArrayList<String>();
			if (assignmentType.contentEquals("Quiz") || assignmentType.contentEquals("Homework")) {
				topicId = Long.parseLong(request.getParameter("TopicId"));
				topicTitles.add(ofy().load().type(Topic.class).id(topicId).now().title);
			} else if (assignmentType.contentEquals("PracticeExam")) {
				String[] topicIdArray = request.getParameterValues("TopicIds");
				if (topicIdArray==null || topicIdArray.length<3) throw new Exception("At least 3 topics must be selected for a practice exam.");
				for (int i=0;i<topicIdArray.length;i++) {
					long tId = Long.parseLong(topicIdArray[i]);
					topicIds.add(tId);
					topicTitles.add(ofy().load().type(Topic.class).id(tId).now().title);
				}
			} else throw new Exception("TopicId values were missing.");
			
			String title = assignmentType + " - ";
			for (String t : topicTitles) title += t + ", ";
			title = title.substring(0, title.length()-2);  // strip off the last comma and space
			
			// Create the new Assignment entity and load the questionKeys
			Assignment assignment = new Assignment(assignmentType,topicId,topicIds,d.platform_deployment_id);
			if (assignment.topicId>0) { // Load the quiz or homework questionKeys
				assignment.questionKeys = ofy().load().type(Question.class).filter("assignmentType",assignment.assignmentType).filter("topicId",assignment.topicId).keys().list();
			} else if (assignment.topicIds.size()>2) { // Load the practice exam questionKeys for each topic
				assignment.questionKeys = new ArrayList<Key<Question>>();
				for (int i=0;i<assignment.topicIds.size();i++) {
					assignment.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",assignment.topicIds.get(i)).keys().list());
				}
			}
			ofy().save().entity(assignment).now(); // we need the assignmentId to send to the platform as the lineitem resourceId
			
			String serverUrl = "https://" + request.getServerName();
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
			payload.addProperty("https://purl.imsglobal.org/spec/lti-dl/claim/data", data);
		
			//Add the user-selected content item to the payload as an array of 1 content_item:
			JsonArray content_items = new JsonArray();
			  JsonObject item = new JsonObject();
			  item.addProperty("type", "ltiResourceLink");
			  item.addProperty("url", serverUrl + "/lti/launch");
			  item.addProperty("title", assignmentType + " - " + title);
			    JsonObject lineitem = new JsonObject();
			    lineitem.addProperty("scoreMaximum", (assignmentType.contentEquals("PracticeExam")?100:10));
			    lineitem.addProperty("label", title);
			    lineitem.addProperty("resourceId", String.valueOf(assignment.id));
			  item.add("lineItem", lineitem);
			content_items.add(item);
			
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
					
			//buf.append("Click the Submit button to POST the following JSON Web Token:<p>" + jwt + "<p>"
			//		+ "The public signing key is<br>" + KeyStore.getRSAPublicKeyX509(d.rsa_key_id) + "<p>");
			
			// Create a form to be auto-submitted to the platform by the user_agent browser
			buf.append("<form name=selections method=POST action='" + deep_link_return_url + "'>"
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
