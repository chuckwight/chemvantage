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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LTIMessage {  // utility for sending LTI-compliant "POX" or "REST+JSON" messages to a Tool Consumer (LMS)
	
	static Map<String,String> authTokens = new HashMap<String,String>();
	
	static String getAccessToken(String platformDeploymentId,String scope) {
		
		// First, try to retrieve an appropriate authToken from the class variable HashMap authTokens
		// If the token expires more than 5 minutes from now, use it. Otherwise, request a new one.
		Deployment d = null;
		Date in15Minutes = new Date(new Date().getTime() + 300000L);  // 5 minutes from now
		StringBuffer debug = new StringBuffer("Failed LTIMessage.getAccessToken()<br/>");
		
		DataOutputStream wr = null;
		BufferedReader reader = null;
		try {
			d = Deployment.getInstance(platformDeploymentId);
			if (d==null) debug.append("Deployment unknown<br/>");
			else debug.append("Deployment: " + d.platform_deployment_id + " (" + d.org_url + ")<br/>");
			
			if (!d.scope.contains(scope)) return null;  // must be authorized

			String authToken = authTokens.get(platformDeploymentId);
			if (authToken != null) {  //found a cached authToken; check the expiration and use it
				JsonObject jAuthToken = JsonParser.parseString(authToken).getAsJsonObject();
				if (in15Minutes.before(new Date(jAuthToken.get("exp").getAsLong()))) return jAuthToken.get("access_token").getAsString();			
			}

			// At this point no valid cached authToken was found, so we request a new authToken from the LMS platform:
			// First, construct a request token to send to the platform
			Date now = new Date();
			String iss = System.getProperty("com.google.appengine.application.id").contains("dev-vantage")?"https://dev-vantage-hrd.appspot.com":"https://www.chemvantage.org";
			debug.append("Requested by: " + iss + "<br/>Denied by: " + d.oauth_access_token_url + "<br/>");
			
			String aud = d.oauth_access_token_url;
			String sub = d.client_id;
			
			if ("brightspace".equals(d.lms_type) || "desire2learn".equals(d.lms_type)) {
				iss = sub;
				aud = "https://api.brightspace.com/auth/token";
			}
			
			String token = JWT.create()
					.withIssuer(iss)
					.withSubject(sub)
					.withAudience(aud)
					.withKeyId(d.rsa_key_id)
					.withExpiresAt(in15Minutes)
					.withIssuedAt(now)
					.withJWTId(Nonce.generateNonce())
					.sign(Algorithm.RSA256(null,KeyStore.getRSAPrivateKey(d.rsa_key_id)));
			
			String body = "grant_type=client_credentials"
					+ "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
					+ "&client_assertion=" + token
					+ "&scope=" + URLEncoder.encode(d.scope, "utf-8").replaceAll("%20", "+");
			debug.append("Body: " + body + "<br/>");
			
			URL u = new URL(d.oauth_access_token_url);
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Accept", "application/json;charset=UTF-8");
			uc.setRequestProperty("charset", "utf-8");
			uc.setUseCaches(false);
			uc.setReadTimeout(5000);
			// send the message
			wr = new DataOutputStream(uc.getOutputStream());
			wr.writeBytes(body);
			wr.close();
			
			int responseCode = uc.getResponseCode();
			debug.append("ResponseCode: " + responseCode + "<br/>Content: ");
			debug.append(uc.getContent().toString() + "<br/>");
			
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));				
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();
			debug.append("Response: " + json.toString() + "<br/>");

			if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_ACCEPTED) { // 200m or 201 or 202
				// decode the Json response object. Fields include access_token, token-type, expires_in, scope
				String access_token = json.get("access_token").getAsString();
				long expires_in = json.get("expires_in").getAsLong();  // number of seconds from now, typically 3600
				long exp = new Date().getTime() + expires_in*1000L;

				// cache the token in the authTokens Map:
				JsonObject cached_token = new JsonObject();
				cached_token.addProperty("access_token", access_token);
				cached_token.addProperty("exp", exp);
				authTokens.put(d.platform_deployment_id, cached_token.toString());

				// return the access_token only
				return access_token;
			} else throw new Exception("response code " + responseCode);
		} catch (Exception e) {
			sendEmailToAdmin("Failed AuthToken Request",debug.toString() + "<br/>" + e.toString() + e.getMessage());
			return "Failed AuthToken Request <br/>" + debug.toString();
		}    
	}
   
    static JsonObject getLineItem(Deployment d,String resourceLinkId,String lti_ags_lineitems_url) throws Exception {   	
    	String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";    	
    	try {
    		String lineitems = getLineItemContainer(d,lti_ags_lineitems_url,resourceLinkId,scope);
    		JsonArray lineitems_json_array = JsonParser.parseString(lineitems).getAsJsonArray();

    		// We submitted the request including the resourceLinkId query, so there are only two possible valid responses:
    		// 0 - if the array is empty, we need to return null
    		// 1 - if there is exactly one lineitem, we need to return it

    		return lineitems_json_array.get(0).getAsJsonObject();
    	} catch (Exception e) {
    		return null;
    	}
    }

    static JsonObject getLineItem(Deployment d, String lti_ags_lineitem_url) {
    	// This method returns a single lineitem from the platform
    	int responseCode = 0;
    	try {
    		String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
    		String bearerAuth = "Bearer " + getAccessToken(d.platform_deployment_id,scope);

    		URL u = new URL(lti_ags_lineitem_url);
    		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    		uc.setDoInput(true);
    		uc.setRequestMethod("GET");
    		uc.setRequestProperty("Authorization", bearerAuth);
    		uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.lineitem+json");
    		uc.connect();

    		responseCode = uc.getResponseCode();
    		if (responseCode == 200) {
    			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    			JsonObject lineitem_json = JsonParser.parseReader(reader).getAsJsonObject();
    			reader.close();
    			return lineitem_json;
    		} 
    	} catch (Exception e) {
    		JsonObject response = new JsonObject();
    		response.addProperty("responseCode", responseCode);
    		response.addProperty("errorMessage", e.getMessage());
    		return response;
    	}
    	return null;
    }
    
    static JsonArray getLineItemContainer(Deployment d,String lti_ags_lineitems_url) {
    	// This method asks the platform to return all lineitems for the context as a JsonArray container
    	// Note: this may require multiple calls to the platform to get batches of lineitems. If the container 
    	// is incomplete, the platform MUST provide one or more Link objects in the response header. These have the form of a comma-sepated list
    	// Link: <https://lms.example.com/sections/2923/lineitems/69?p=3>; rel="next",<https://lms.example.com/sections/2923/lineitems/69?p=1>; rel="prev"
    	
    	JsonArray container = new JsonArray();
    	try {
    		String accessToken = getAccessToken(d.platform_deployment_id,d.scope);
    		if (accessToken.indexOf("Failed AuthToken Request")>=0) throw new Exception("Failed AuthToken request.");
    		String bearerAuth = "Bearer " + accessToken;
    		String next_url = lti_ags_lineitems_url;
    		while (next_url != null) {
    			URL u = new URL(next_url);
    			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    			uc.setDoInput(true);
    			uc.setRequestMethod("GET");
    			uc.setRequestProperty("Authorization", bearerAuth);
    			uc.setRequestProperty("Accept", "application/vnd.ims.lis.v2.lineitemcontainer+json");
    			uc.connect();
    			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    			container.addAll(JsonParser.parseReader(reader).getAsJsonArray());
    			reader.close();
    			
    			next_url = null;
    			String[] links = uc.getHeaderField("Link").split(",");
    			for (String l : links) if (l.contains("next")) {
    				next_url = l.trim().substring(1); // removes any leading/trailing white space and the first <
    				next_url = next_url.substring(0,next_url.indexOf(">"));
    			}
    		}
    	} catch (Exception e) {
    		JsonArray error =  new JsonArray();
    		error.add(e.getMessage()==null?e.toString():e.getMessage());
    	}
    	return container;
    }

    static String getLineItemContainer(Deployment d,String lti_ags_lineitems_url,String resourceLinkId,String scope) {
    	// This method asks the platform to return one lineitems for the context having the specified resourceLinkId
    	try {
    		String accessToken = getAccessToken(d.platform_deployment_id,scope);
    		if (accessToken == null) return "Access token not granted: " + accessToken;
    		String bearerAuth = "Bearer " + accessToken;

    		URL u = new URL(lti_ags_lineitems_url + "?resource_link_id=" + resourceLinkId);
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
    			return "Response code " + responseCode;
    		}
    	} catch (Exception e) {
    		return null;
    		//return "getLineItemContainer: " + e.toString() + " " + e.getMessage();
    	}
	}
	
	static String createLineItem(Deployment d,Assignment a,String lti_ags_lineitems_url) throws Exception {
		if (d==null || a==null || !a.isValid()) return null;
	
		StringBuffer debug = new StringBuffer("Failed to create lineitem: ");
		try {
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
			String lineItemUrl = null;
			
			String accessToken = getAccessToken(d.platform_deployment_id,scope);
    		if (accessToken == null) return "Access token not granted.";
    		String bearerAuth = "Bearer " + accessToken;
    		debug.append("Authorization: " + bearerAuth + "<br>");
			
			JsonObject j = new JsonObject();
			
			String label = "ChemVantage Assignment";
			try {
				Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
				label = a.assignmentType + " - " + t.title;
			} catch (Exception e) {}
			
			int maxPossibleScore = 100;
			try {
				maxPossibleScore = a.assignmentType.equals("PracticeExam")?100:(a.assignmentType.equals("VideoQuiz")?5:10);
			} catch (Exception e) {}
			
			j.addProperty("scoreMaximum", maxPossibleScore);
			j.addProperty("label", label);
			j.addProperty("resourceLinkId", a.resourceLinkId);
			j.addProperty("resourceId", String.valueOf(a.id));

			String json = j.toString();
			debug.append("Lineitem JSON: " + json);
			
			URL u = new URL(lti_ags_lineitems_url);

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Content-Type", "application/vnd.ims.lis.v2.lineitem+json");
			uc.setRequestProperty("Accept-Type", "application/vnd.ims.lis.v2.lineitem+json");
			//uc.setRequestProperty("Content-Length", String.valueOf(json.length()));
			uc.connect();

			// send the message
			OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
			toTC.write(json);
			toTC.flush();
			int responseCode = uc.getResponseCode(); // success if 200-202
			debug.append("Response code: " + responseCode + "<br>");

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
			return null;  //e.toString() + " " + e.getMessage() + "<br>" + debug.toString();
		}
	}

	static void updateLineItem(Deployment d, String lti_ags_lineitem_url, long assignmentId) throws Exception {
		if (d==null || lti_ags_lineitem_url == null || assignmentId == 0L) return;
		
		JsonObject lineitem = null;
		try {
			lineitem = LTIMessage.getLineItem(d, lti_ags_lineitem_url);
		} catch (Exception e) {
			throw new Exception("Failed to get Lineitem. " + e.toString() + " " + e.getMessage());
		}
		
		// check for required id element:
		if (lineitem.get("id") == null) return;  // not a valid lineitem
		
		String resourceId = null;
		if (lineitem.get("resourceId") != null)	{
			resourceId = lineitem.remove("resourceId").getAsString();
			if (resourceId.equals(String.valueOf(assignmentId))) return; // everything is OK
		}
		
		// add the proper value of the resourceId:
		lineitem.addProperty("resourceId", String.valueOf(assignmentId));
		
		// PUT the revised lineitem to the platform:
		try {
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem";
			String bearerAuth = "Bearer " + getAccessToken(d.platform_deployment_id,scope);
			
			URL u = new URL(lti_ags_lineitem_url);

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("PUT");
			uc.setRequestProperty("Authorization", bearerAuth);
			uc.setRequestProperty("Content-Type", "application/vnd.ims.lis.v2.lineitem+json");
			uc.setRequestProperty("Accept-Type", "application/vnd.ims.lis.v2.lineitem+json");
			uc.setRequestProperty("Content-Length", String.valueOf(lineitem.toString().length()));
			uc.connect();

			// send the message
			OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
			toTC.write(lineitem.toString());
			toTC.flush();
			toTC.close();
		} catch (Exception e) {
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
			//URL u = new URL(a.lti_ags_lineitem_url + "/results");
			// append "/scores" to the lineitem URL, taking into account that the URL may have a query part (thank you, Moodle)
			URL u = null;
			int i = a.lti_ags_lineitem_url.indexOf("?")==-1?a.lti_ags_lineitem_url.length():a.lti_ags_lineitem_url.indexOf("?");
			u = new URL(a.lti_ags_lineitem_url.substring(0,i) + "/results" + a.lti_ags_lineitem_url.substring(i));

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
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
			scores = null;
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

			String user_id = userId.substring(userId.lastIndexOf("/")+1); // stripped of the platform_id and "/"
			
			if (a.lti_ags_lineitem_url==null) throw new Exception("the lineitem URL for this assignment is unknown");
			
			URL u = null;
			if (a.lti_ags_lineitem_url.contains("?")) { // the lineitem URL already has a query part
				String base_url = a.lti_ags_lineitem_url.substring(0,a.lti_ags_lineitem_url.indexOf("?")) + "/results";
				String query = a.lti_ags_lineitem_url.substring(a.lti_ags_lineitem_url.indexOf("?"));
				u = new URL(base_url + query + "&user_id=" + user_id + "&userId=" + user_id);
			} else {
				u = new URL(a.lti_ags_lineitem_url + "/results?user_id=" + user_id + "&userId=" + user_id);
			}
			
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			//uc.setDoOutput(true);
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
				return "no score found: " + json.toString();
			} else return "response code=" + responseCode + " for " + u.toString(); 
		} catch (Exception e) {	
			return e.toString();
		}
	}
	
	static String postUserScore(Score s, String userId) {
		// This method uses the LTIv1p3 message protocol to post a user's score to the LMS grade book.
		// The lineitem URL corresponds to the LMS grade book column for the Assignment entity,
		// and the specific cell is identified by the user_id value defined by the LMS platform
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = ofy().load().type(Assignment.class).id(s.assignmentId).safe();
			String scope = "https://purl.imsglobal.org/spec/lti-ags/scope/score";
			
			String authToken = getAccessToken(a.domain,scope);
			
			if (authToken.startsWith("response")) throw new Exception("Failed: could not get access token. " + authToken);
			String bearerAuth = "Bearer " + authToken;
			//buf.append("Authorization: " + bearerAuth + "<br>");
			
			String raw_id = userId.substring(userId.lastIndexOf("/")+1);
			String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
			
			JsonObject j = new JsonObject();
			j.addProperty("timestamp", timestamp);
			j.addProperty("scoreGiven", Double.valueOf(s.score));
			j.addProperty("scoreMaximum", Double.valueOf(s.maxPossibleScore));
			j.addProperty("comment", "Number of attempts="+s.numberOfAttempts);
			j.addProperty("activityProgress", "Completed");
			j.addProperty("gradingProgress", "FullyGraded");
			j.addProperty("userId", raw_id);
			String json = j.toString();
			
			// append "/scores" to the lineitem URL, taking into account that the URL may have a query part (thank you, Moodle)
			URL u = null;
			int i = a.lti_ags_lineitem_url.indexOf("?")==-1?a.lti_ags_lineitem_url.length():a.lti_ags_lineitem_url.indexOf("?");
			u = new URL(a.lti_ags_lineitem_url.substring(0,i) + "/scores" + a.lti_ags_lineitem_url.substring(i));
			
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
			os.close();
			
			int responseCode = uc.getResponseCode(); // success if 200-202
			boolean success = responseCode>199 && responseCode<203;
			if (success) {  
				s.lisReportComplete = true;
				ofy().save().entity(s);
				buf.append("Success " + responseCode + "<br/>AuthToken: " + authToken + "<br/>JSON: " + json);
				//if (a.domain.equals("https://canvas.instructure.com/10812:5d76708f19931de80763b1b539e8bb0233b99bbd")) sendEmailToAdmin("Score submission success",buf.toString());
			} else if (responseCode==422) {
				buf.append("Response code 422: This LMS does not allow LTI score submissions for instructors or test students.<br/>");
			} else {			
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
				
				buf.append(String.valueOf(responseCode) + "<br />");
				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				String line;
	    		while ((line = reader.readLine()) != null) {
	    			buf.append(line);
	    		}
	    		reader.close();
	    		//sendEmailToAdmin("Score submission failed",buf.toString());
			}
		} catch (Exception e) {
			//sendEmailToAdmin("Score submission failed",buf.toString());
			return "Score submission error: " + e.toString() + e.getMessage() + "<br>" + buf.toString();
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
			//uc.setDoOutput(true);
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
				
				JsonArray members = json.get("members").getAsJsonArray();
				Iterator<JsonElement> iterator = members.iterator();
				while(iterator.hasNext()){
					JsonObject member = iterator.next().getAsJsonObject();
					String user_id = member.get("user_id").getAsString();
					//String roles = member.get("roles").getAsString().toLowerCase();
					String roles = member.get("roles").getAsJsonArray().toString().toLowerCase();
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

	static void sendEmailToAdmin(String subject,String message) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,
					new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject(subject);
			msg.setContent(message,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
		}
	}

}