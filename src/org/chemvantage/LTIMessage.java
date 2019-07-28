/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2014 ChemVantage LLC
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

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
    	// construct a hash of the message text to include as a custom parameter
    	String body_hash = Base64.getEncoder().encodeToString(messageText.getBytes());
    	
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


/*    	
    	OAuth$Parameter params = new OAuthParameters();
    	params.setOAuthConsumerKey(oauth_consumer_key);
    	params.setOAuthConsumerSecret(oauth_shared_secret);
    	params.setOAuthNonce(OAuthUtil.getNonce());
    	params.setOAuthTimestamp(OAuthUtil.getTimestamp());
    	params.setOAuthCallback("about:blank");
    	params.setOAuthSignatureMethod("HMAC-SHA1");
    	params.addCustomBaseParameter("oauth_version", "1.0");
    	params.addCustomBaseParameter("oauth_body_hash",hash);

    	String baseString = OAuthUtil.getSignatureBaseString(destinationURL,"POST",params.getBaseParameters());
    	String signature = new OAuthHmacSha1Signer().getSignature(baseString,params);
    	params.setOAuthSignature(signature);
    	params.addCustomBaseParameter("oauth_signature",signature);
*/   	

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

}