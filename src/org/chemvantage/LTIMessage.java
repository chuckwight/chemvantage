/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2019 ChemVantage LLC
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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;

public class LTIMessage {  // utility for sending LTI-compliant "POX" or "REST+JSON" messages to a Tool Consumer (LMS)
	String contentType="text/html";
	String acceptType = "application/xml";
	String messageText="";
	String httpMethod="POST";
	String oauth_consumer_key;
	String oauth_shared_secret;
	String destinationURL;
	
	LTIMessage() {}

    LTIMessage(String msgType,String msgText,String destURL,String key) {
    	this.contentType = msgType;
    	this.messageText = msgText;
    	this.destinationURL = destURL;
    	this.oauth_consumer_key = key;
    	this.oauth_shared_secret = BLTIConsumer.getSecret(oauth_consumer_key);
    }
    
    LTIMessage(String contentType,String acceptType,String msgText,String destURL,String key,String secret) {
    	this.contentType = contentType;
    	this.acceptType = acceptType;
    	this.messageText = msgText;
    	this.destinationURL = destURL;
    	this.oauth_consumer_key = key;
    	this.oauth_shared_secret = secret;
    }
    
    LTIMessage(String httpMethod,String acceptType,String destURL,BLTIConsumer c) {
    	this.httpMethod = httpMethod;
    	this.destinationURL = destURL;
    	this.acceptType = acceptType;
    	this.oauth_consumer_key = c.oauth_consumer_key;
    	this.oauth_shared_secret = c.secret;
    }
    
    protected String send() throws Exception {	
    	// construct an oauth_body_hash of the message text to include as a custom parameter in the Authorization header
    	String body_hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(messageText.getBytes()));
    	
    	final String nonce = UUID.randomUUID().toString();
    	final String timestamp = Long.toString(new Date().getTime()/1000);  // current time in seconds

    	final OAuthMessage message = new OAuthMessage(OAuthMessage.POST, destinationURL, null);
    	message.addParameter(OAuth.OAUTH_CONSUMER_KEY, oauth_consumer_key);
    	message.addParameter(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.HMAC_SHA1);
    	message.addParameter(OAuth.OAUTH_NONCE, nonce);
    	message.addParameter(OAuth.OAUTH_TIMESTAMP, timestamp);
    	message.addParameter(OAuth.OAUTH_CALLBACK, "about:blank");
    	message.addParameter(OAuth.OAUTH_VERSION, "1.0");
    	message.addParameter("oauth_body_hash", body_hash);

    	final OAuthConsumer consumer = new OAuthConsumer(null, oauth_consumer_key, oauth_shared_secret, null);
    	final OAuthAccessor accessor = new OAuthAccessor(consumer);
    	try
    	{
    		message.sign(accessor);
    	}
    	catch( Exception e )
    	{
    		throw new RuntimeException(e);
    	}

    	// construct the signed message in the required format
    	URL u = new URL(destinationURL);
    	HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    	uc.setDoOutput(true);
    	uc.setDoInput(true);
    	uc.setRequestMethod(httpMethod);
    	if (httpMethod.equals("GET")) acceptType = "application/vnd.ims.lti.v2.ToolSettings+json";
    	uc.setRequestProperty("Content-Type",contentType);
    	if (!acceptType.isEmpty()) uc.setRequestProperty("Accept", acceptType);
    	uc.setRequestProperty("Content-Length",Integer.toString(messageText.length()));
    	uc.setRequestProperty("Authorization",message.getAuthorizationHeader(""));
    	
    	if (!messageAppearsValid()) return "Error: Message parameters were invalid.";
    	else uc.connect();

    	// send the message
    	OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
    	toTC.write(messageText);
    	toTC.flush();
    	
    	int responseCode = uc.getResponseCode();
    	if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) { // 200 or 201
    		BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    		StringBuffer res = new StringBuffer();
    		String line;
    		while ((line = reader.readLine()) != null) {
    			res.append(line);
    		}
    		reader.close();
    		toTC.close();
    		return res.toString();
    	} else {
    	 	toTC.close();
    	 	throw new Exception("Server returned status code: " + responseCode);  
    	}
    }

    private boolean messageAppearsValid() {
    	if (contentType == null) return false;
    	if (messageText==null || (messageText.isEmpty() && !httpMethod.equals("GET"))) return false;
    	if (oauth_consumer_key==null || oauth_consumer_key.isEmpty()) return false;
    	if (oauth_shared_secret==null || oauth_shared_secret.isEmpty()) return false;
    	if (destinationURL==null || destinationURL.isEmpty()) return false;
    	return true;
    }
    
	static String xmlReadResult(String lis_result_sourcedid) {
		return "<?xml version = \"1.0\" encoding = \"UTF-8\"?>"
		+ "<imsx_POXEnvelopeRequest xmlns = \"http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0\">"
		+ "  <imsx_POXHeader>"
		+ "    <imsx_POXRequestHeaderInfo>"
		+ "      <imsx_version>V1.0</imsx_version>"
		+ "      <imsx_messageIdentifier>1</imsx_messageIdentifier>"
		+ "    </imsx_POXRequestHeaderInfo>"
		+ "  </imsx_POXHeader>"
		+ "  <imsx_POXBody>"
		+ "    <readResultRequest>"
		+ "      <resultRecord>"
		+ "        <sourcedGUID>"
		+ "          <sourcedId>" + lis_result_sourcedid + "</sourcedId>"
		+ "        </sourcedGUID>"
		+ "      </resultRecord>"
		+ "    </readResultRequest>"
		+ "  </imsx_POXBody>"
		+ "</imsx_POXEnvelopeRequest>";
	}

	static String xmlReplaceResult(String lis_result_sourcedid, String score) {	
		//lis_result_sourcedid = lis_result_sourcedid.replaceAll("\"", "\\\"");
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
		+ "<imsx_POXEnvelopeRequest xmlns = \"http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0\">"
		+ "<imsx_POXHeader>"
		+ "<imsx_POXRequestHeaderInfo>"
		+ "<imsx_version>V1.0</imsx_version>"
		+ "<imsx_messageIdentifier>1</imsx_messageIdentifier>"
		+ "</imsx_POXRequestHeaderInfo>"
		+ "</imsx_POXHeader>"
		+ "<imsx_POXBody>"
		+ "<replaceResultRequest>"
		+ "<resultRecord>"
		+ "<sourcedGUID>"
		+ "<sourcedId>" + lis_result_sourcedid + "</sourcedId>"
		+ "</sourcedGUID>"
		+ "<result>"
		+ "<resultScore>"
		+ "<language>en</language>"
		+ "<textString>" + score + "</textString>"
		+ "</resultScore>"
		+ "</result>"
		+ "</resultRecord>"
		+ "</replaceResultRequest>"
		+ "</imsx_POXBody>"
		+ "</imsx_POXEnvelopeRequest>";		
	}

    static String getAccessToken(Group g) {
    	// First, construct a request token to send to the platform
    	Date now = new Date();
    	try {
			Deployment d = Deployment.getInstance(g.domain);
			Date exp = new Date(now.getTime() + 300000L);
			String token = JWT.create()
					.withIssuer(d.client_id)
					.withSubject(d.client_id)
					.withAudience(d.oauth_access_token_url)
					.withKeyId(d.rsa_key_id)
					.withExpiresAt(exp)
					.withIssuedAt(now)
					.withJWTId(Nonce.generateNonce())
					.sign(Algorithm.RSA256(null,KeyStore.getRSAPrivateKey(d.rsa_key_id)));
			
			String body = "grant_type=client_credentials"
					+ "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
					+ "&client_assertion=" + token
					+ "&scope=" + g.lti_ags_scope;
			
			URL u = new URL(d.oauth_access_token_url);
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Accept", "application/json;charset=UTF-8");
			uc.setRequestProperty("charset", "utf-8");
			uc.setUseCaches(false);
			
			// send the message
			DataOutputStream wr = new DataOutputStream(uc.getOutputStream());
			wr.writeBytes(body);

			int responseCode = uc.getResponseCode();
			
			if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) { // 200m or 201
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));				
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) res.append(line);
				reader.close();
				
				// decode the Json response object. Fields include access_token, token-type, expires_in, scope
				JsonObject json = new JsonParser().parse(res.toString()).getAsJsonObject();
				// return the access_token only
				return json.get("access_token").getAsString();
			} else throw new Exception("response code " + responseCode);
		} catch (Exception e) {
			return null;
		}
    }
    
	static String getLineItemUrl(Group g, Assignment a) {
		try {
			JsonArray json = new JsonParser().parse(getLineItems(g)).getAsJsonArray();
			Iterator<JsonElement> iterator = json.iterator();
			while(iterator.hasNext()){
		        JsonObject lineitem = iterator.next().getAsJsonObject();
		        if (a.resourceLinkId.equals(lineitem.get("resourceLinkId").getAsString())) {
		        	return lineitem.get("id").getAsString();
		        }
		    }
			return "Not found.";
			} catch (Exception e) {
				return "An unexpected error occurred: " + e.toString();
			}
	}
	
	static long getAssignmentId(Group g, String resourceLinkId) {
		long assignmentId = 0;
		try {
			JsonArray json = new JsonParser().parse(getLineItems(g)).getAsJsonArray();
			Iterator<JsonElement> iterator = json.iterator();
			while(iterator.hasNext()){
		        JsonObject lineitem = iterator.next().getAsJsonObject();
		        if (resourceLinkId.equals(lineitem.get("resourceLinkId").getAsString())) {
		        	return lineitem.get("resourceId").getAsLong(); // this should be the assignmentId
		        }  												// that was created during a DeepLinking work flow
		    }
		} catch (Exception e) {  // returns value of 0L if unable to find a 
		}						 // lineitem with the correct resourceLinkId and valid resourceId
		return assignmentId;
	}
	
	static String getLineItems(Group g) {
		try {
			String bearerAuth = "Bearer " + getAccessToken(g);
			
			URL u = new URL(g.lti_ags_lineitems_url);
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.lineitemcontainer+json");
			uc.connect();

			int responseCode = uc.getResponseCode();
			if (HttpURLConnection.HTTP_OK == responseCode) { // 200
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();
				return res.toString();
			} else return "Response Code was " + responseCode;
		} catch (Exception e) {
			return e.toString();
		}
	}
	
	static String createLineItem(Assignment a) {
		if (a==null) return null;
		String lineItemUrl = null;
		//StringBuffer debug = new StringBuffer();
		try {
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
			String bearerAuth = "Bearer " + getAccessToken(g);
			int maxPossibleScore = a.assignmentType.equals("PracticeExam")?100:10;

			String json = "{"
					+ "\"scoreMaximum\":" + maxPossibleScore + ","
					+ "\"label\":\"" + a.assignmentType + " - " + t.title + "\","
					+ "\"resourceLinkId\":\"" + a.resourceLinkId + "\""
					+ "}";
			//debug.append("POSTed: " + json + "<br>");
			URL u = new URL(g.lti_ags_lineitems_url);
			//debug.append("To the lineitems URL: " + g.lti_ags_lineitems_url + "<br>");
			
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Content-Type", "application/vnd.ims.lis.v2.lineitem+json");
			uc.setRequestProperty("Accept-Type", "application/vnd.ims.lis.v2.lineitem+json");
			uc.setRequestProperty("Content-Length", String.valueOf(json.length()));
			uc.connect();

			// send the message
			OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
			toTC.write(json);
			toTC.flush();
			int responseCode = uc.getResponseCode(); // success if 200-202
			//debug.append("Response code: " + responseCode + "<br>");
			
			boolean success = responseCode>199 && responseCode<203;
			if (success) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();
				//debug.append("Received: " + res + "<br>");
				JsonObject lineItem = new JsonParser().parse(res.toString()).getAsJsonObject();
				if (lineItem.isJsonObject()) {
					lineItemUrl = lineItem.get("id").getAsString();
					new URI(lineItemUrl);  // throws Exception if not a valid URL
					return lineItemUrl;
				}
			}
		} catch (Exception e) {
			// return e.toString() + "<p>" + debug.toString();
		}	
		return null;
	}

	static Map<String,String> readMembershipScores(Assignment a) {
		// This method uses the LTIv1p3 message protocol to retrieve a JSON containing all of
		// the existing  scores for one assignment from the LMS. 
		// The lineitem URL corresponds to the LMS grade book column fpr the Assignment entity.
		
		// Construct the deployment_id because we may need to strip this from the userId values
		String deploymentId = "";
		try {
			deploymentId = new URI(a.domain).getPath().substring(1);
		} catch (Exception e) {}
	
		Map<String,String> scores = new HashMap<String,String>();		
		String bearerAuth = null;
		try {
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			if ((bearerAuth=getAccessToken(g)).startsWith("response")) throw new Exception("the LMS failed to issue an auth token: " + bearerAuth);
			else bearerAuth = "Bearer " + bearerAuth;

			if (a.lti_ags_lineitem_url==null) throw new Exception("the lineitem URL for this assignment is unknown");
			URL u = new URL(a.lti_ags_lineitem_url + "/results");

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.resultcontainer+json");
			uc.connect();

			int responseCode = uc.getResponseCode();
			if (responseCode > 199 && responseCode < 203) {  // OK
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();

				JsonArray json = new JsonParser().parse(res.toString()).getAsJsonArray();
				Iterator<JsonElement> iterator = json.iterator();
				while(iterator.hasNext()) {
					JsonObject result = iterator.next().getAsJsonObject();
					String userId = result.get("userId").getAsString();
					if (userId.startsWith(deploymentId)) userId = userId.substring(deploymentId.length()+1);
					scores.put(userId,String.valueOf(Math.round(1000.*result.get("resultScore").getAsDouble()/result.get("resultMaximum").getAsDouble())/10.));
				}
			}
		} catch (Exception e) {	
			return null;
		}
		return scores;
	}
/*
	static JsonArray readMembershipScores(Assignment a,String dummy) {
		// This method uses the LTIv1p3 message protocol to retrieve a JSON containing all of
		// the existing  scores for one assignment from the LMS. 
		// The lineitem URL corresponds to the LMS grade book column fpr the Assignment entity.
		
		//Map<String,String> scores = new HashMap<String,String>();		
		String bearerAuth = null;
		try {
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			if ((bearerAuth=getAccessToken(g)).startsWith("response")) throw new Exception("the LMS failed to issue an auth token: " + bearerAuth);
			else bearerAuth = "Bearer " + bearerAuth;

			if (a.lti_ags_lineitem_url==null) throw new Exception("the lineitem URL for this assignment is unknown");
			URL u = new URL(a.lti_ags_lineitem_url + "/results");

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.resultcontainer+json");
			uc.connect();
			
			StringBuffer res = new StringBuffer();
			
			int responseCode = uc.getResponseCode();
			if (responseCode > 199 && responseCode < 203) {  // OK
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();
			}
			return new JsonParser().parse(res.toString()).getAsJsonArray();
		} catch (Exception e) {	
			return null;
		}
	}
*/	
	static String readUserScore(Assignment a, String userId) {
		// This method uses the LTIv1p3 message protocol to retrieve a user's score from the LMS.
		// The lineitem URL corresponds to the LMS grade book column fpr the Assignment entity,
		// and the specific cell is identified by the user_id value defined by the LMS platform
		String bearerAuth = null;
		try {
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			if ((bearerAuth=getAccessToken(g)).startsWith("response")) throw new Exception("the LMS failed to issue an auth token: " + bearerAuth);
			else bearerAuth = "Bearer " + bearerAuth;
			String user_id = User.getRawId(userId); // stripped of the platform_id and "/"

			if (a.lti_ags_lineitem_url==null) throw new Exception("the lineitem URL for this assignment is unknown");
			URL u = new URL(a.lti_ags_lineitem_url + "/results?user_id=" + user_id);
			
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.resultcontainer+json");
			uc.connect();

			int responseCode = uc.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) { // 200
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();

				JsonArray json = new JsonParser().parse(res.toString()).getAsJsonArray();
				Iterator<JsonElement> iterator = json.iterator();
				while(iterator.hasNext()){
					JsonObject result = iterator.next().getAsJsonObject();
					if (result.get("userId").getAsString().equals(user_id)) {
						return String.valueOf(Math.round(1000.*result.get("resultScore").getAsDouble()/result.get("resultMaximum").getAsDouble())/10.);
					}
				}
				return "no score for this user was found";
			} else return "the LMS issued response code: " + responseCode; 
		} catch (Exception e) {	
			return e.toString();
		}
	}
	
	static boolean postUserScore(Score s) {
		// This method uses the LTIv1p3 message protocol to post a user's score to the LMS grade book.
		// The lineitem URL corresponds to the LMS grade book column for the Assignment entity,
		// and the specific cell is identified by the user_id value defined by the LMS platform
		try {
			Assignment a = ofy().load().type(Assignment.class).id(s.assignmentId).safe();
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			String bearerAuth = getAccessToken(g);
			if (bearerAuth.startsWith("response")) return false;
			bearerAuth = "Bearer " + bearerAuth;
			
			String raw_id = User.getRawId(s.owner.getName());
			
			JsonObject j = new JsonObject();
			j.addProperty("timestamp", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
			j.addProperty("scoreGiven", String.valueOf(s.score));
			j.addProperty("scoreMaximum", String.valueOf(s.maxPossibleScore));
			j.addProperty("comment",  "");
			j.addProperty("activityProgress", "Completed");
			j.addProperty("gradingProgress", "FullyGraded");
			j.addProperty("userId", raw_id);
			String json = j.toString();
/*			
			String json = "{"
					+ "\"timestamp\":\"" + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) + "\","
					+ "\"scoreGiven\":\"" + s.score + "\","
					+ "\"scoreMaximum\":\"" + s.maxPossibleScore + "\","
					+ "\"comment\":\"\","
					+ "\"activityProgress\":\"Completed\","
					+ "\"gradingProgress\":\"FullyGraded\","
					+ "\"userId\":\"" + raw_id + "\""
					+ "}";
*/			
			URL u = new URL(a.lti_ags_lineitem_url + "/scores");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Content-Type", "application/vnd.ims.lis.v1.score+json");
			uc.setRequestProperty("Content-Length", String.valueOf(json.length()));
			uc.connect();

			// send the message
			OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
			toTC.write(json);
			toTC.flush();
			int responseCode = uc.getResponseCode(); // success if 200-202
			boolean success = responseCode>199 && responseCode<203;
			if (success) {  
				s.lisReportComplete = true;
				ofy().save().entity(s);
			}
		} catch (Exception e) {
		}
		return false;
	}
	
	static Map<String,String[]> getMembership(Group g) {
		// This method uses the LTIv1p3 message protocol to retrieve the group membership from the LMS.
		// If this service is offered by providing the endpoint, the Json array MUST contain the user_id and roles
		// values, but may also include other fields such as name, given_name, middle_name, family_name, email, ...
		Map<String,String[]> membership = new HashMap<String,String[]>();
		String bearerAuth = null;
		
		try {
			if ((bearerAuth=getAccessToken(g)).startsWith("response")) throw new Exception("the LMS failed to issue an auth token: " + bearerAuth);
			else bearerAuth = "Bearer " + bearerAuth;
			
			if (g.context_memberships_url==null) throw new Exception("the service endpoint URL for this group is unknown");
			URL u = new URL(g.context_memberships_url);

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Accept", "application/vnd.ims.lti-nrps.v2.membershipcontainer+json");
			uc.connect();

			int responseCode = uc.getResponseCode();
			if (responseCode > 199 && responseCode < 203) { // OK
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				StringBuffer res = new StringBuffer();
				String line;
				while ((line = reader.readLine()) != null) {
					res.append(line);
				}
				reader.close();

				JsonObject json = new JsonParser().parse(res.toString()).getAsJsonObject();
				
				// check to ensure that the context_id matches the group
				//JsonObject context = json.get("context").getAsJsonObject();
				//if (!g.context_id.contains(context.get("id").getAsString())) throw new Exception("incorrect context_id value");
				
				JsonArray members = json.get("members").getAsJsonArray();
				Iterator<JsonElement> iterator = members.iterator();
				while(iterator.hasNext()){
					JsonObject member = iterator.next().getAsJsonObject();
					String user_id = member.get("user_id").getAsString();
					String roles = member.get("roles").getAsString().toLowerCase();
					String role = roles.contains("administrator")?"Administrator":roles.contains("instructor")?"Instructor":"Learner";
					String name = "";
					try {
						name = member.get("name").getAsString();
					} catch (Exception e) {
						try {
							name = member.get("family_name").getAsString() + ", " + member.get("given_name").getAsString();
						} catch (Exception e2) {
						}
					}
					String email = "";
					try {
						email = member.get("email").getAsString();
					} catch (Exception e) {
					}
					String[] properties = {role, name, email};
					membership.put(user_id,properties);
				}
			} else return null; 
		} catch (Exception e) {	
		}
		return membership;
	}

}