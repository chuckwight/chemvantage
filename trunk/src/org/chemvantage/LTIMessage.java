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
import java.net.URLEncoder;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.gdata.client.authn.oauth.OAuthHmacSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthParameters;
import com.google.gdata.client.authn.oauth.OAuthUtil;
import com.google.gdata.util.common.util.Base64;

public class LTIMessage {  // utility for sending LTI-compliant "POX" or "REST+JSON" messages to a Tool Consumer (LMS)
	String messageType;
	String messageText;
	String oauth_consumer_key;
	String oauth_shared_secret;
	String destinationURL;
	
	LTIMessage() {}

    LTIMessage(String msgType,String msgText,String destURL,String key) {
    	this.messageType = msgType;
    	this.messageText = msgText;
    	this.destinationURL = destURL;
    	this.oauth_consumer_key = key;
    	this.oauth_shared_secret = BLTIConsumer.getSecret(oauth_consumer_key);
    }
    
    LTIMessage(String msgType,String msgText,String destURL,String key,String secret) {
    	this.messageType = msgType;
    	this.messageText = msgText;
    	this.destinationURL = destURL;
    	this.oauth_consumer_key = key;
    	this.oauth_shared_secret = secret;
    }
    
    protected String send() throws Exception {
    	if (!messageAppearsValid()) return "Error: Message parameters were invalid.";

    	// construct a hash of the message text to include as a custom parameter
    	String hash = new String(Base64.encode(DigestUtils.sha(messageText)));

    	OAuthParameters params = new OAuthParameters();
    	params.setOAuthConsumerKey(oauth_consumer_key);
    	params.setOAuthConsumerSecret(oauth_shared_secret);
    	params.setOAuthNonce(OAuthUtil.getNonce());
    	params.setOAuthTimestamp(OAuthUtil.getTimestamp());
    	params.setOAuthSignatureMethod("HMAC-SHA1");
    	params.addCustomBaseParameter("oauth_version", "1.0");
    	params.addCustomBaseParameter("oauth_body_hash",hash);

    	String baseString = OAuthUtil.getSignatureBaseString(destinationURL,"POST",params.getBaseParameters());
    	String signature = new OAuthHmacSha1Signer().getSignature(baseString,params);
    	params.setOAuthSignature(signature);
    	params.addCustomBaseParameter("oauth_signature",signature);

    	// construct the signed message in the required format
    	URL u = new URL(destinationURL);
    	HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    	uc.setDoOutput(true);
    	uc.setDoInput(true);
    	uc.setRequestMethod("POST");
    	uc.setRequestProperty("Content-Type",messageType);
    	uc.setRequestProperty("Content-Length",Integer.toString(messageText.length()));
    	uc.setRequestProperty("Authorization",buildAuthHeaderString(params));
    	
    	// send the message
    	OutputStreamWriter toTC = new OutputStreamWriter(uc.getOutputStream());
    	toTC.write(messageText);
    	toTC.close();

    	int responseCode=uc.getResponseCode();
    	
    	if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
    		BufferedReader reader = new BufferedReader(new InputStreamReader(u.openStream()));
    		StringBuffer res = new StringBuffer();
    		String line;
    		while ((line = reader.readLine()) != null) {
    			res.append(line);
    		}
    		reader.close();
    		return (res.length()>0?res.toString():"(no response received from server)");
    	} else throw new Exception("Server returned status code: " + responseCode);
    }

    private boolean messageAppearsValid() {
    	if (messageType == null) return false;
    	if (messageText==null || messageText.isEmpty()) return false;
    	if (oauth_consumer_key==null || oauth_consumer_key.isEmpty()) return false;
    	if (oauth_shared_secret==null || oauth_shared_secret.isEmpty()) return false;
    	if (destinationURL==null || destinationURL.isEmpty()) return false;
    	return true;
    }
    
	private String buildAuthHeaderString(OAuthParameters params) {
		StringBuffer buffer = new StringBuffer();
		try {
			int cnt = 0;
			buffer.append("OAuth ");
			Map<String, String> paramMap = params.getBaseParameters();
			Object[] paramNames = paramMap.keySet().toArray();
			for (Object paramName : paramNames) {
				String value = paramMap.get((String) paramName);
				buffer.append(paramName + "=\"" + URLEncoder.encode(value,"UTF-8") + "\"");
				cnt++;
				if (paramNames.length > cnt) {
					buffer.append(",");
				}

			}
		} catch (Exception e) {
		}
		return buffer.toString();
	}

}