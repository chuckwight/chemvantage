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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
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
    	uc.setConnectTimeout(5000);
    	//if (httpMethod.equals("GET")) acceptType = "application/vnd.ims.lti.v2.ToolSettings+json";
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
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		+ "<imsx_POXEnvelopeRequest xmlns = \"http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0\">\n"
		+ "  <imsx_POXHeader>\n"
		+ "    <imsx_POXRequestHeaderInfo>\n"
		+ "      <imsx_version>V1.0</imsx_version>\n"
		+ "      <imsx_messageIdentifier>1</imsx_messageIdentifier>\n"
		+ "    </imsx_POXRequestHeaderInfo>\n"
		+ "  </imsx_POXHeader>\n"
		+ "  <imsx_POXBody>\n"
		+ "    <replaceResultRequest>\n"
		+ "      <resultRecord>\n"
		+ "        <sourcedGUID>\n"
		+ "          <sourcedId>" + lis_result_sourcedid + "</sourcedId>\n"
		+ "        </sourcedGUID>\n"
		+ "        <result>\n"
		+ "          <resultScore>\n"
		+ "            <language>en</language>\n"
		+ "            <textString>" + score + "</textString>\n"
		+ "          </resultScore>\n"
		+ "        </result>\n"
		+ "      </resultRecord>\n"
		+ "    </replaceResultRequest>\n"
		+ "  </imsx_POXBody>\n"
		+ "</imsx_POXEnvelopeRequest>\n";		
	}

	static String getAccessToken(String platformDeploymentId,String scope) {
    	// First, construct a request token to send to the platform
    	Date now = new Date();
    	try {
			Deployment d = Deployment.getInstance(platformDeploymentId);
			if (!d.scope.contains(scope)) return null;  // must be authorized
			
			String iss = System.getProperty("com.google.appengine.application.id").contains("dev-vantage")?"https://dev-vantage-hrd.appspot.com":"https://www.chemvantage.org";
			Date exp = new Date(now.getTime() + 300000L);
			String token = JWT.create()
					.withIssuer(iss)
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
					+ "&scope=" + URLEncoder.encode(d.scope, "utf-8").replace("%20", "+");
			
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
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				reader.close();
				
				// decode the Json response object. Fields include access_token, token-type, expires_in, scope
				//JsonObject json = new JsonParser().parse(res.toString()).getAsJsonObject();
				// return the access_token only
				return json.get("access_token").getAsString();
			} else throw new Exception("response code " + responseCode);
		} catch (Exception e) {
			return null;
		}
    }
    
    static String getLineItemUrl(Deployment d,Assignment a,String lti_ags_lineitems_url) throws Exception {
    	if (a.resourceLinkId == null) return "Missing resourceLinkId value.";
    	if (lti_ags_lineitems_url==null) return "Missing lti_ags_lineitems_url value.";
    	
    	String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
    	
    	String lineitems = getLineItems(d,lti_ags_lineitems_url,scope);
    	
    	try {
    		JsonElement parsed = JsonParser.parseString(lineitems);
    		if (!parsed.isJsonArray()) return null;
    		JsonArray lineitems_json_array = parsed.getAsJsonArray();
        	Iterator<JsonElement> iterator = lineitems_json_array.iterator();
    		while(iterator.hasNext()){
    			JsonObject lineitem = iterator.next().getAsJsonObject();
    			JsonElement rli = lineitem.get("resourceLinkId");
    			if (rli != null && a.resourceLinkId.equals(rli.getAsString())) {
    				return lineitem.get("id").getAsString();
    			}
    		}
    	} catch (Exception e) {
    		return lineitems;
    	}
   	
    	// If you get to here, the lineitem does not exist; create a new one:
    	return createLineItem(d,a,lti_ags_lineitems_url);
    }

    static JsonObject getLineItem(Deployment d, String lti_ags_lineitem_url) {
    	// This method returns a single lineitem from the platform
      	try {
      		String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
        	String bearerAuth = "Bearer " + getAccessToken(d.platform_deployment_id,scope);

    		URL u = new URL(lti_ags_lineitem_url);
    		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    		uc.setDoOutput(true);
    		uc.setDoInput(true);
    		uc.setRequestMethod("GET");
    		uc.setRequestProperty("Authorization", bearerAuth);
    		uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.lineitem+json");
    		uc.connect();

    		int responseCode = uc.getResponseCode();
    		if (HttpURLConnection.HTTP_OK == responseCode) { // 200
    			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    			JsonObject lineitem_json = JsonParser.parseReader(reader).getAsJsonObject();
    			reader.close();
    			
    			return lineitem_json;
    		}
    	} catch (Exception e) {
    	}
		return null;
    }
    
    static String getLineItems(Deployment d,String lti_ags_lineitems_url,String scope) {
    	// This method asks the platform to return ALL of the lineitems for the context as a JSON string
    	try {
    		String bearerAuth = "Bearer " + getAccessToken(d.platform_deployment_id,scope);

    		URL u = new URL(lti_ags_lineitems_url);
    		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    		uc.setDoInput(true);
    		uc.setRequestMethod("GET");
    		uc.setRequestProperty("Authorization", bearerAuth);
    		uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.lineitemcontainer+json");
    		uc.connect();

    		int responseCode = uc.getResponseCode();
    		if (responseCode > 199 && responseCode < 300) { // OK
    			JsonArray lineitems_json = null;
        		BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    			lineitems_json = JsonParser.parseReader(reader).getAsJsonArray();
    			reader.close();
    			return lineitems_json.toString();
    		} else {
    			return "Status " + responseCode;
    		}
    	} catch (Exception e) {
    		return e.toString() + " " + e.getMessage();
    	}
	}
	
	static String createLineItem(Deployment d,Assignment a,String lti_ags_lineitems_url) throws Exception {
		if (d==null || a==null || !a.isValid()) return null;
		//if (d==null) return "CreateLineItem: Deployment not fond.";
		//if (a==null) return "CreateLineItem: Assignment was null.";
		//if (!a.isValid()) return "CreateLineItem: Assignment was not valid.";
		StringBuffer debug = new StringBuffer("Failed to create lineitem: ");
		try {
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
			String lineItemUrl = null;
			Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
			String bearerAuth = "Bearer " + getAccessToken(d.platform_deployment_id,scope);
			int maxPossibleScore = a.assignmentType.equals("PracticeExam")?100:(a.assignmentType.equals("VideoQuiz")?5:10);

			JsonObject j = new JsonObject();
			j.addProperty("scoreMaximum", maxPossibleScore);
			j.addProperty("label", a.assignmentType + " - " + t.title);
			j.addProperty("resourceLinkId", a.resourceLinkId);
			j.addProperty("resourceId", String.valueOf(a.id));

			String json = j.toString();

			URL u = new URL(lti_ags_lineitems_url);

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
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
			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			JsonObject lineitem = JsonParser.parseReader(reader).getAsJsonObject();
			lineItemUrl = lineitem.get("id").getAsString();
			reader.close();
			toTC.close();
			
			if (success) {
				new URI(lineItemUrl);  // throws Exception if not a valid URL
			} else {
				lineitem.addProperty("status", responseCode);
				return lineitem.toString();
			}
			return lineItemUrl;			
		} catch (Exception e) {
			return e.toString() + " " + e.getMessage() + "<br>" + debug.toString();
		}
	}

	static Map<String,String> readMembershipScores(Assignment a) {
		// This method uses the LTIv1p3 message protocol to retrieve a JSON containing all of
		// the existing  scores for one assignment from the LMS. 
		// The lineitem URL corresponds to the LMS grade book column fpr the Assignment entity.
		
		// Construct the deployment_id because we may need to strip this from the userId values
		
		Map<String,String> scores = new HashMap<String,String>();		
		try {
			
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly";
			String bearerAuth = "Bearer " + getAccessToken(a.domain,scope);

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
				JsonArray json = JsonParser.parseReader(reader).getAsJsonArray();
				reader.close();

				Iterator<JsonElement> iterator = json.iterator();
				while(iterator.hasNext()) {
					JsonObject result = iterator.next().getAsJsonObject();
					String userId = result.get("userId").getAsString();
					scores.put(userId,String.valueOf(Math.round(1000.*result.get("resultScore").getAsDouble()/result.get("resultMaximum").getAsDouble())/10.));
				}
			}
		} catch (Exception e) {	
			return null;
		}
		return scores;
	}

	static String readUserScore(Assignment a, String userId) {
		// This method uses the LTIv1p3 message protocol to retrieve a user's score from the LMS.
		// The lineitem URL corresponds to the LMS grade book column fpr the Assignment entity,
		// and the specific cell is identified by the user_id value defined by the LMS platform
		try {
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly";
			String bearerAuth = "Bearer " + getAccessToken(a.domain,scope);

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
				JsonArray json = JsonParser.parseReader(reader).getAsJsonArray();
				reader.close();

				Iterator<JsonElement> iterator = json.iterator();
				while(iterator.hasNext()){
					JsonObject result = iterator.next().getAsJsonObject();
					if (result.get("userId").getAsString().equals(user_id)) {
						return String.valueOf(Math.round(1000.*result.get("resultScore").getAsDouble()/result.get("resultMaximum").getAsDouble())/10.);
					}
				}
				return "no score found";
			} else return "response code=" + responseCode; // + " for " + u.toString(); 
		} catch (Exception e) {	
			return e.toString();
		}
	}
	
	static String postUserScore(Score s) {
		// This method uses the LTIv1p3 message protocol to post a user's score to the LMS grade book.
		// The lineitem URL corresponds to the LMS grade book column for the Assignment entity,
		// and the specific cell is identified by the user_id value defined by the LMS platform
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = ofy().load().type(Assignment.class).id(s.assignmentId).safe();
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/score";
			
			String authToken = getAccessToken(a.domain,scope);
			
			if (authToken.startsWith("response")) return "Failed: could not get access token.";
			String bearerAuth = "Bearer " + authToken;
			//buf.append("Authorization: " + bearerAuth + "<br>");
			
			String raw_id = User.getRawId(s.owner.getName());
			String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
			
			JsonObject j = new JsonObject();
			j.addProperty("timestamp", timestamp);
			j.addProperty("scoreGiven", Double.valueOf(s.score));
			j.addProperty("scoreMaximum", Double.valueOf(s.maxPossibleScore));
			j.addProperty("comment", "Number of attempts="+s.numberOfAttempts);
			j.addProperty("activityProgress", "Completed");
			j.addProperty("gradingProgress", "FullyGraded");
			j.addProperty("userId", raw_id);
			//j.addProperty("user_id", raw_id);       // temporary addition
			String json = j.toString();
			
			//buf.append("JSON request body:<br>" + json + "<p>");
		
			URL u = new URL(a.lti_ags_lineitem_url + "/scores");
			//buf.append("URL: " + u.toString() + "<br>");
			
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Content-Type", "application/vnd.ims.lis.v1.score+json");
			uc.setDoOutput(true);
			
			// send the message
			//OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
		    OutputStream os = uc.getOutputStream();
		    byte[] json_bytes = json.getBytes("utf-8");
			os.write(json_bytes, 0, json_bytes.length);           

			//toTC.write(json);
			//toTC.flush();
			int responseCode = uc.getResponseCode(); // success if 200-202
			boolean success = responseCode>199 && responseCode<203;
			if (success) {  
				s.lisReportComplete = true;
				ofy().save().entity(s);
				buf.append("Success");
			} else {
			/*
				buf.append(uc.getRequestMethod() + " " + u.toString() + "<br>"
						+ "Content-Type: application/vnd.ims.lis.v1.score+json<br>"
						+ "Authorization: " + bearerAuth + "<p>"
						+ json + "<p>");
				Map<String,List<String>> headers = uc.getHeaderFields();
				for (Entry<String,List<String>> e : headers.entrySet()) {
					buf.append(e.getKey() + ": ");
					for (String es : e.getValue()) buf.append(es + " ");
					buf.append("<br>");
				}
				*/
				buf.append(String.valueOf(responseCode));
			}		
		} catch (Exception e) {
			return "Score submission failed: " + e.toString() + e.getMessage() + "<br>" + buf.toString();
		}
		return buf.toString();
	}
	
	static Map<String,String[]> getMembership(Assignment a) {
		// This method uses the LTIv1p3 message protocol to retrieve the group membership from the LMS.
		// If this service is offered by providing the endpoint, the Json array MUST contain the user_id and roles
		// values, but may also include other fields such as name, given_name, middle_name, family_name, email, ...
		Map<String,String[]> membership = new HashMap<String,String[]>();
		String bearerAuth = null;
		
		try {
			String scope = "https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
			if ((bearerAuth=getAccessToken(a.domain,scope)).startsWith("response")) throw new Exception("the LMS failed to issue an auth token: " + bearerAuth);
			else bearerAuth = "Bearer " + bearerAuth;
			
			if (a.lti_nrps_context_memberships_url==null) throw new Exception("the service endpoint URL for this group is unknown");
			URL u = new URL(a.lti_nrps_context_memberships_url);

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
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				reader.close();
				
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