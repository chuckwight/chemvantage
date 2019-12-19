/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2012 ChemVantage LLC
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet(urlPatterns = {"/lti/registration","/lti/registration/"})
public class LTIRegistration extends HttpServlet {

	/* This servlet class is used to apply for and grant access to LTI connections between client
	 * LMS platforms and the ChemVantage tool. The user will complete a short form with name, role,
	 * email, organization, home page and use case (testing or production). Those wanting to test
	 * will get access to dev-vantage.appspot.com, while production users will see chemvantage.org.
	 * The user will get an email with a tokenized link to a page containing the ChemVantage 
	 * end point URLs and a form for submitting the client_id and deployment_id values.
	 * */
	
	private static final long serialVersionUID = 137L;
	Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	String banner = "<div><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'>"
			+ "Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
			+ "<br>An Open Education Resource</div>";
				
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		String token = request.getParameter("token");
		try {
			if (token==null) out.println(Home.header + applicationForm() + Home.footer);
		else if ("config".equals(request.getParameter("UserRequest"))) {
			response.setContentType("text/json");
			out.println(getConfigurationJson(token));
		}
		else if ("final".equals(request.getParameter("UserRequest"))) out.println(Home.header + clientIdForm(token) + Home.footer);
		} catch (Exception e) {
			out.println("Sorry, the registration failed. " + e.getMessage());
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if ("Send Me The Registration Email".equals(request.getParameter("UserRequest"))) {
			try {
				if (!reCaptchaOK(request)) throw new Exception("ReCaptcha tool unverified. Please try again.");
				String sub = request.getParameter("sub");
				String email = request.getParameter("email");
				String aud = request.getParameter("aud");
				String url = request.getParameter("url");
				String use = request.getParameter("use");
				String lms = request.getParameter("lms");
				if (sub==null || email==null || aud==null || url==null || use==null) throw new Exception("All form fields are required.");
				String iss = use.equals("test")?"https://dev-vantage-hrd.appspot.com":"https://www.chemvantage.org";
				Date now = new Date();
				Date exp = new Date(now.getTime() + 259200000L); // three days from now
				String token = JWT.create()
						.withIssuer(iss)
						.withSubject(sub)
						.withAudience(aud)
						.withExpiresAt(exp)
						.withIssuedAt(now)
						.withClaim("email",email)
						.withClaim("url", url)
						.withClaim("lms", lms)
						.sign(algorithm);
				sendRegistrationEmail(token);
				out.println(Home.header + banner + "<h3>Thank you. A registration email has been sent to your address.</h3>" + Home.footer);
			} catch (Exception e) {
				out.println(Home.header + "Registration failure: " + e.getMessage() + "<br>" + Home.footer);
			}
			return;
		} else {  //if ("Finalize the LTI Registration".equals(request.getParameter("UserRequest"))) {
			try {
				DecodedJWT jwt = validateToken(request.getParameter("Token"));
				String client_name = jwt.getSubject();
				String email = jwt.getClaim("email").asString();
				String organization = jwt.getAudience().get(0);
				String org_url = jwt.getClaim("url").asString();
				String client_id = request.getParameter("ClientId");
				if (client_id==null) throw new Exception("Client ID value is required.");
				String deployment_id = request.getParameter("DeploymentId");
				if (deployment_id==null) deployment_id = "";
				String platform_id = request.getParameter("PlatformId");
				if (platform_id==null) throw new Exception("Platform ID value is required.");
				String oidc_auth_url = request.getParameter("OIDCAuthUrl");
				if (oidc_auth_url==null) throw new Exception("OIDC Auth URL is required.");
				String oauth_access_token_url = request.getParameter("OauthAccessTokenUrl");
				if (oauth_access_token_url==null) throw new Exception("OAuth Access Token URL is required.");
				String well_known_jwks_url = request.getParameter("JWKSUrl");
				if (well_known_jwks_url==null) throw new Exception("JSON Web Key Set URL is required.");
				new URL(platform_id);
				new URL(oidc_auth_url);
				new URL(oauth_access_token_url);
				new URL(well_known_jwks_url);
				Deployment d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,client_name,email,organization,org_url);
				ofy().save().entity(d).now();
				out.println(Home.header + banner + "<h2>Congratulations. Registration is complete.</h2>" + Home.footer);
			} catch (Exception e) {
				out.println(Home.header + "<h2>Registration Failed</h2>" + e.getMessage() + Home.footer);
			}
			return;
		}
	}	

	String applicationForm() {
		StringBuffer buf = new StringBuffer(banner);
		buf.append("<h4>ChemVantage LTI Registration</h4>"
				+ "ChemVantage uses LTI version 1.3.0 to connect with LMS platforms. Please complete "
				+ "the form below. You will receive an email containing the ChemVantage end point URLs "
				+ "to enter into your LMS. You will also receive a link to submit the corresponding end "
				+ "point URLs and client_id needed by  ChemVantage to access services provided by your LMS."
				+ "<p>"
				+ "ChemVantage is an Open Education Resource offered free for nonprofit educational purposes.<p>"
				+ "<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>"				
				+ "<form method=post>"
				+ "Your Name: <input type=text name=sub>&nbsp;"
				+ "and Email: <input type=text name=email><br>"
				+ "Your Organization: <input type=text name=aud>&nbsp;"
				+ "and Home Page: <input type=text name=url><br>"
				+ "Initial use case:<br>"
				+ "<label><input type=radio name=use value=test>Testing the LTI connection (development environment)</label><br>"
				+ "<label><input type=radio name=use value=prod>Teaching a chemistry class (production environment)</label><p>"
				+ "Type of Learning Management System:<br>"
				+ "<label><input type=radio name=lms value=canvas>Instructure Canvas</label><br>"
				+ "<label><input type=radio name=lms value=other>Other LMS supporting LTI Advantage</label><p>"
				+ "<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div><p>"
				+ "<input type=submit name=UserRequest value='Send Me The Registration Email'>"
				+ "</form>");
		return buf.toString();
	}
	
	String clientIdForm(String token) {
		StringBuffer buf = new StringBuffer(banner);
		String sub = null;
		String email = null;
		String aud = null;
		String url = null;
		try {
			DecodedJWT jwt = validateToken(token);
			sub = jwt.getSubject();
			email = jwt.getClaim("email").asString();
			aud = jwt.getAudience().get(0);
			url = jwt.getClaim("url").asString();
			
			buf.append("To the LMS Administrator:<p>"
					+ "By now you should have entered the ChemVantage end point URLs into your LMS, and you should have "
					+ "received a client_id and deployment_id from your LMS that identifies the ChemVantage tool.<p>"
					+ "To complete the registration process, please complete the form below by supplying the end point "
					+ "URLs on your LMS so that ChemVantage can access services (e.g., Assignment and Grade Services) "
					+ "provided by your LMS platform. All fields are required, and all URLs should begin with https://<p>"
					+ "<form method=post>"
					+ "<input type=hidden name=Token value='" + token + "'>"
					+ "Client ID: <input type=text name=ClientId> (required)<br>"
					+ "Deployment ID: <input type=text name=DeploymentId> (required)<br>"
					+ "Platform ID: <input type=text name=PlatformId> (must exactly match LMS base URL sent as iss in resource link launch requests)<br>"
					+ "Platform OIDC Auth URL: <input type=text name=OIDCAuthUrl> (required)<br>"
					+ "Platform OAuth Access Token URL: <input type=text name=OauthAccessTokenUrl> (required)<br>"
					+ "Platform JSON Web Key Set URL: <input type=text name=JWKSUrl> (required)<br>"
					+ "<input type=submit name=UserRequest value='Finalize the LTI Registration'>"
					+ "</form>");			
		} catch (Exception e) {
			buf.append("<h3>Registration Failed</h3>"
					+ e.getMessage() + "<p>"
					+ "Name: " + sub + "<br>"
					+ "Email: " + email + "<br>"
					+ "Organization: " + aud + "<br>"
					+ "Home Page: " + url + "<p>"
					+ "The token provided with this link could not be validated. It may have expired (after 3 days) "
					+ "or it may not have contained enough information to complete the registration request. You "
					+ "may start the registration process again <a href=/lti/registration>here</a> or contact "
					+ "Chuck Wight (admin@chemvantage.org) for assistance in completing the registration process.");
		}		
		return buf.toString();
	}
	
	DecodedJWT validateToken(String token) throws Exception {
		DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
		return jwt;
	}

	void sendRegistrationEmail(String token) throws Exception {
		DecodedJWT jwt = validateToken(token);
		String name = jwt.getSubject();
		String email = jwt.getClaim("email").asString();
		String iss = jwt.getIssuer();
		String lms = jwt.getClaim("lms").asString();
		Date exp = jwt.getExpiresAt();
		StringBuffer buf = new StringBuffer();
		buf.append("Thank you for your ChemVantage registration request.<p> "
				+ "The next step is to enter the ChemVantage configuration details into your LMS. "
				+ "This will enable your LMS to communicate securely with ChemVantage. Normally, "
				+ "you must have administrator privileges in your LMS in order to do this. "
				+ "If you are NOT the LMS administrator, please stop here and forward this message "
				+ "to an administrator with a request to complete the registration process. The "
				+ "registration link below will be active for 3 days.<p>"
				+ (iss.equals("https://dev-vantage-hrd.appspot.com")?"You indicated that your initial "
						+ "use case is testing, so we are granting access to our development server "
						+ "for this purpose. If and when you get to the point of offering ChemVantage to "
						+ "students in a class, please reregister your LMS with ChemVantage to connect "
						+ "with our production server. Do not use the development server for live "
						+ "instruction.<p>":"")
				+ "<hr>"
				+ "<br>To the LMS Administrator:<p>"
				+ "ChemVantage is a free Open Education Resource for teaching and learning college-"
				+ "level General Chemistry. Learn more about ChemVantage "
				+ "<a href=https://www.chemvantage.org/About>here</a>.<p>"
				+ "This registration request uses the LTI Advantage (version 1.3.0) specifications. "
				+ "Use the information below to register ChemVantage in your LMS:<br>"
				+ "Tool Domain URL: " + iss + "<br>"
				+ "Tool Redirect URL: " + iss + "/lti/launch<br>"
				+ "Tool Deep Linking URL: " + iss + "/lti/deeplinks<br>"
				+ "OIDC Login Initiation URL: " + iss + "/auth/token<br>"
				+ "JSON Web Key Set Endpoint: " + iss + "/jwks<p>");
		if ("canvas".equals(lms)) {
			buf.append("You are using the cloud-based Instructure Canvas LMS, so you will need to configure "
					+ "the developer key using the following configuration JSON URL:<br>" 
					+ iss + "/lti/registration?UserRequest=config&token=" + token + "<p>"
					+ "The token is valid for three days and expires at " + exp + ".<p>");
		} 
		buf.append("When you have finished the configuration, " + ("canvas".equals(lms)?"Canvas ":"your LMS ") 
				+ "should generate a client_id value to identify the ChemVantage tool. "
				+ ("canvas".equals(lms)?"Canvas also calls this the developer key. ":"")
				+ "In addition, your LMS should generate a "
				+ "deployment_id value to identify a specific account in your LMS for this tool. "
				+ "When you have these values in hand, as well as the end point URLs of your LMS that "
				+ "are needed by ChemVantage, then please click the following link to complete the "
				+ "LTI registration process:<p><a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
				+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><p>"
				+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
				+ "-Chuck Wight");

		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		Message msg = new MimeMessage(session);
		InternetAddress from = new InternetAddress("admin@chemvantage.org", "ChemVantage");
		msg.setFrom(from);
		msg.addRecipient(Message.RecipientType.TO,new InternetAddress(email,name));
		msg.addRecipient(Message.RecipientType.CC,from);
		msg.setSubject("ChemVantage LTI Registration");
		msg.setContent(buf.toString(),"text/html");
		Transport.send(msg);
	}

	boolean reCaptchaOK(HttpServletRequest request) {
		try {
			String queryString = "secret=6Ld_GAcTAAAAAD2k2iFF7Ywl8lyk9LY2v_yRh3Ci&response=" 
					+ request.getParameter("g-recaptcha-response") + "&remoteip=" + request.getRemoteAddr();
			URL u = new URL("https://www.google.com/recaptcha/api/siteverify");
	    	HttpURLConnection uc = (HttpURLConnection) u.openConnection();
	    	uc.setDoOutput(true);
	    	uc.setRequestMethod("POST");
	    	uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
	    	uc.setRequestProperty("Content-Length", String.valueOf(queryString.length()));
	    	
	    	OutputStreamWriter writer = new OutputStreamWriter(uc.getOutputStream());
			writer.write(queryString);
	    	writer.flush();
	    	writer.close();
			
			// read & interpret the JSON response from Google
	    	BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			
			return res.toString().contains("success");  // should really parse a JSON object here
			
		} catch (Exception e) {
			return false;
		}
	}
	
	String getConfigurationJson(String token) throws Exception {
		DecodedJWT jwt = validateToken(token);
		String iss = jwt.getIssuer();
		JsonObject config = new JsonObject();
		config.addProperty("title","Configuration JSON file for LTI Advantage integration of ChemVantage in Canvas accounts");
		  JsonArray scopes = new JsonArray();
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem");
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly");
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/score");
		  scopes.add("https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly");
		config.add("scopes", scopes);
		config.addProperty("description", "ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry");;
		config.addProperty("target_link_uri", iss + "/lti/launch");
		config.addProperty("oidc_initiation_url", iss + "/auth/token");
		config.addProperty("public_jwk_url", iss + "/jwks");
		  JsonArray extensions = new JsonArray();
		    JsonObject ext = new JsonObject();
		    ext.addProperty("domain", iss);
		    ext.addProperty("platform", "canvas.instructure.com");
		      JsonObject settings = new JsonObject();
		      settings.addProperty("text", "ChemVantage Content Selector");
		      settings.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		        JsonArray placements = new JsonArray();
		          JsonObject plcmnt = new JsonObject();
		          plcmnt.addProperty("text", "Embed ChemVantage Content as a Canvas Assignment");
		          plcmnt.addProperty("enabled", true);
		          plcmnt.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		          plcmnt.addProperty("placement", "assignment_selection");
		          plcmnt.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt);
		      settings.add("placements", placements);
		    ext.add("settings", settings);
		  extensions.add(ext);
		config.add("extensions", extensions);
		
		return config.toString();
	}
}
