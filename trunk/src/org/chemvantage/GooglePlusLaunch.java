/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.chemvantage;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

import com.google.gdata.util.common.base.Charsets;
import com.google.gdata.util.common.io.CharStreams;
import com.google.gdata.util.common.util.Base64;
import com.googlecode.objectify.Objectify;


/**
 * Servlet for handling GooglePlus logins.  
 */
public class GooglePlusLaunch extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private static final GoogleClient CLIENT = GoogleClient.getInstance();
	//private static final String client_id = "890312835091-rtjtii84uafa0v1bsmoe03nc0uutivb7.apps.googleusercontent.com";
	//private static final String client_secret = "wSvwjpiomYbePKl5Z62apDFr";
	
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	JSONObject openid_config = null;
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		try {  
			// Load openId configuration endpoint URLS, if necessary
			if (openid_config==null) openid_config = getOpenIdConfig();
			
			// Validate the session/parameter state variable
			HttpSession session = request.getSession();
			String sessionState = (String)session.getAttribute("state");
			String requestState = request.getParameter("state");
			if (!sessionState.equals(requestState)) throw new Exception();  // protects against request forgery attack
			session.removeAttribute("state");  // state is for one-time login only

			//Exchange the one-time code for a Google+ access token JSON object
			String code = request.getParameter("code");
			JSONObject accessToken = getToken(code);
			
			// Handle possible authentication failure
			if (accessToken.containsKey("error")) throw new Exception();
			
			// Retrieve the id_token inside the accessToken and decode the JSON Web Token (JWT) payload:
			String id_token = accessToken.getString("id_token");
			String[] pieces = id_token.split("\\.");
			if (pieces.length!=3) throw new Exception();  // JWT token structure is invalid			
			JSONObject payload = JSONObject.fromObject(new String(Base64.decode(pieces[1])));
			
			// Verify that this JWT is targeted to the correct site for Google+ login
			if (!CLIENT.client_id.equals(payload.getString("aud"))) throw new Exception();  // JWT token has wrong audience
			
			// Check to ensure that JWT has not expired
			Date now = new Date();
			Date expires = new Date(1000L*new Long(payload.getInt("exp")));
			if (expires.before(now)) throw new Exception();
			
			// Retrieve the id from the JWT 
			String userId = payload.getString("id");
			if (userId==null || userId.isEmpty()) throw new Exception();
				
			// Everything looks OK; sign-in to ChemVantage
			session.setAttribute("UserId", userId);			

			// Check to see if this is a first-time Google+ sign-in
			User user = User.getInstance(session);		
			if (user==null) {  
				user = User.createGooglePlusUser(payload);
				user.setFirstName(getUserName(userId,accessToken));
				ofy.put(user);
			}
			Cookie c = new Cookie("IDProvider","Google");
			c.setMaxAge(2592000); // expires after 30 days (in seconds)
			response.addCookie(c);

		} catch (Exception e) {
			System.out.println(e.toString());
			response.setStatus(500);
		}
	}	
				
	String getOneTimeCode(HttpServletRequest request) throws IOException {
		ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
		InputStream inputStream = request.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		int readChar;
		while ((readChar = reader.read()) != -1) {
			resultStream.write(readChar);
		}
		reader.close();
		return new String(resultStream.toByteArray(), "UTF-8");
	}
	
	JSONObject getOpenIdConfig() {
		try {
			URL u = new URL("https://accounts.google.com/.well-known/openid-configuration");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();			
			return JSONObject.fromObject(res.toString());	
		} catch (Exception e) {
			return null;
		}
	}

	JSONObject getToken(String code) {
		JSONObject accessToken = null;
		try {
			// Exchange the one-time authorization code for an access token:		
			URL u = new URL(openid_config.getString("token_endpoint"));
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Accept-Charset", "UTF-8");
			String queryString = "code=" + code;
			queryString += "&client_id=890312835091-rtjtii84uafa0v1bsmoe03nc0uutivb7.apps.googleusercontent.com"; //+ CLIENT.client_id;
			queryString += "&client_secret=wSvwjpiomYbePKl5Z62apDFr"; //+ CLIENT.client_secret;
			queryString += "&redirect_uri=postmessage";
			queryString += "&grant_type=authorization_code";
			
			OutputStream output = uc.getOutputStream();
			output.write(queryString.getBytes());
			output.flush();

			//read the response from Google+ and convert it to a JSON object
			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			
			accessToken = JSONObject.fromObject(res.toString());
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		return accessToken;
	}
	
	String getUserName(String userId,JSONObject accessToken) {
		String givenName = "";
		try {
			// Open a new URL connection to the Google People API endpoint:
			URL u = new URL("https://www.googleapis.com/plus/v1/people/" + userId);
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setRequestProperty("Authorization", "Bearer " + accessToken.getString("access_token"));
			
			//read the response from Google+ and convert it to a JSON object
			String content = CharStreams.toString(new InputStreamReader(uc.getInputStream(),Charsets.UTF_8));
			if (content==null || content.isEmpty()) throw new Exception();
			
			JSONObject userInfo = JSONObject.fromObject(content);
			givenName = userInfo.getJSONObject("name").getString("givenName");
		} catch (Exception e) {
		}
		return givenName;
	}
}

