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
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		// For now, we will support LTI version 1.1 registration by default.
		// To register by version 1.3.0 select this option in the applicationForm
		
		if ("config".contentEquals(userRequest)) {
			response.setContentType("text/json");
			String iss = "https://" + request.getServerName();
			out.println(getConfigurationJson(iss,request.getParameter("lms")));
		} else if ("final".contentEquals(userRequest)) {
			String token = request.getParameter("token");
			out.println(Home.header + clientIdForm(token) + Home.footer);
		} else out.println(Home.header + applicationForm() + Home.footer);		
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";

		if ("Send Me The Registration Email".contentEquals(userRequest)) {
			try {
				if (!reCaptchaOK(request)) throw new Exception("ReCaptcha tool unverified. Please try again.");
				String sub = request.getParameter("sub");
				String email = request.getParameter("email");
				String aud = request.getParameter("aud");
				String url = request.getParameter("url");
				String use = request.getParameter("use");
				String ver = request.getParameter("ver");
				String lms = request.getParameter("lms");
				
				if (sub.isEmpty() || email.isEmpty() || aud.isEmpty() || url.isEmpty() || use.isEmpty() || ver.isEmpty()) throw new Exception("All form fields are required.");
				
				String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w]+\\.)+[\\w]+[\\w]$";
				if (!email.matches(regex)) throw new Exception("Your email address was not formatted correctly.");
				
				try {
					new URL(url);   // throws Exception if URL is not formatted correctly
				} catch (Exception e) {
					throw new Exception("Badly formatted home page URL (" + e.getMessage() + ")");
				}
				
				if (lms==null) throw new Exception("Please select the type of LMS that you are connecting to ChemVantage.");
				if ("other".contentEquals(lms)) lms = request.getParameter("lms_other");
				if (lms==null || lms.isEmpty()) throw new Exception("Please describe the type of LMS that you are connecting to ChemVantage.");
				
				String iss = use.equals("test")?"https://dev-vantage-hrd.appspot.com":"https://www.chemvantage.org";
				Date now = new Date();
				Date exp = new Date(now.getTime() + 259200000L); // three days from now
				String con = BLTIConsumer.getNewConsumerKey();
				String token = JWT.create()
						.withIssuer(iss)
						.withSubject(sub)
						.withAudience(aud)
						.withExpiresAt(exp)
						.withIssuedAt(now)
						.withClaim("email",email)
						.withClaim("url", url)
						.withClaim("lms", lms)
						.withClaim("ver", ver)
						.withClaim("con", con)  // this is specific to LTI1.1 registrations
						.sign(algorithm);
				sendRegistrationEmail(token);
				out.println(Home.header + banner + "<h3>Registration Success</h3>Thank you. A registration email has been sent to your address.<p>" + Home.footer);
			} catch (Exception e) {
				out.println(Home.header + banner + "<h3>Registration Failure</h3>" + e.getMessage() + "<p>" + Home.footer);
			}
		} else if ("finalize".contentEquals(userRequest)) {
			try {
				DecodedJWT jwt = validateToken(request.getParameter("Token"));
				String client_name = jwt.getSubject();
				String email = jwt.getClaim("email").asString();
				String organization = jwt.getAudience().get(0);
				String org_url = jwt.getClaim("url").asString();
				String lms = jwt.getClaim("lms").asString();
				String client_id = request.getParameter("ClientId");
				if (client_id==null) throw new Exception("Client ID value is required.");
				String deployment_id = request.getParameter("DeploymentId");
				if (deployment_id==null) throw new Exception("Deployment ID value is required.");
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
				Deployment d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,client_name,email,organization,org_url,lms);
				ofy().save().entity(d).now();
				out.println(Home.header + banner + "<h2>Congratulations. Registration is complete.</h2>" + Home.footer);
			} catch (Exception e) {
				out.println(Home.header + "<h2>Registration Failed</h2>" + e.getMessage() + Home.footer);
			}
		} else out.println(Home.header + "<h2>Registration Failed</h2>POST was missing a required parameter." + Home.footer);
	}	

	String applicationForm() {
		StringBuffer buf = new StringBuffer(banner + "<p>");
		buf.append("ChemVantage is an Open Education Resource for teaching and learning college-level General "
				+ "Chemistry. We offer this service at no charge for nonprofit educational purposes.");
		buf.append("<h4>ChemVantage LTI Registration</h4>");
		buf.append("Please complete the form below to obtain a free set of LTI credentials. The information you "
				+ "provide will help us to create a connection between your learning management system (LMS) and "
				+ "ChemVantage that is convenient, secure and "
				+ "<a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>. "
				+ "When you submit the form, you will receive an email containing the information you need to "
				+ "complete the configuration of your LMS as well as a link to finalize the registration.<p>");
		buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");				
		buf.append("<form method=post action=/lti/registration>"
				+ "Your Name: <input type=text name=sub>&nbsp;"
				+ "and Email: <input type=text name=email><br>"
				+ "Your Organization: <input type=text name=aud>&nbsp;"
				+ "and Home Page: <input type=text name=url><br>"
				+ "Select your initial use case:<br>"
				+ "<label><input type=radio name=use value=test checked>Testing the LTI connection (development environment)</label><br>"
				+ "<label><input type=radio name=use value=prod>Teaching a chemistry class (production environment)</label><p>"
				+ "Type of LTI registration:<br>"
				+ "<label><input type=radio name=ver value=1p1 checked>LTI version 1.1.2 (preferred)</label><br>"
				+ "<label><input type=radio name=ver value=1p3>LTI Advantage (certified but still clunky)</label><p>"
				+ "Type of Learning Management System:<br>"
				+ "<label><input type=radio name=lms value=blackboard>Blackboard</label><br>"
				+ "<label><input type=radio name=lms value=brightspace>Brightspace</label><br>"
				+ "<label><input type=radio name=lms value=canvas>Canvas</label><br>"
				+ "<label><input type=radio name=lms value=moodle>Moodle</label><br>"
				+ "<label><input type=radio name=lms value=sakai>Sakai</label><br>"
				+ "<label><input type=radio name=lms value=schoology>Schoology</label><br>"
				+ "<label><input type=radio name=lms value=other>Other: </label><input type=text name=lms_other><p>"
				+ "<div class='g-recaptcha' data-sitekey='6LcB3skUAAAAAFUnRPxnlYsQGkJyiJXDnROLoz0o'></div><p>"
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
		String lms = null;
		String ver = null;
		try {
			DecodedJWT jwt = validateToken(token);
			
			sub = jwt.getSubject();
			email = jwt.getClaim("email").asString();
			aud = jwt.getAudience().get(0);
			url = jwt.getClaim("url").asString();
			lms = jwt.getClaim("lms").asString();
			ver = jwt.getClaim("ver").asString();
			
			if ("1p1".contentEquals(ver)) { // older LTIv1.1.2 registration request
				BLTIConsumer con = null;
				try {
					con = ofy().load().type(BLTIConsumer.class).id(jwt.getClaim("con").asString()).safe();
				} catch (Exception e) {
					con = new BLTIConsumer(jwt.getClaim("con").asString());
					con.email = email;
					con.contact_name = sub;
					con.lms = lms;
					con.organization = aud;
					con.org_url = url;
					ofy().save().entity(con).now();
				}
				
				buf.append("Here are your LTI registration credentials:<p>"
						+ "Launch URL: " + jwt.getIssuer() + "/lti<br>"
						+ "Consumer Key: " + con.oauth_consumer_key + "<br>"
						+ "Shared Secret: " + con.secret + "<p>");
				buf.append("Important notes:<ol>"
						+ "<li>When entering this information into your LMS, be sure to use the "
						+ "Launch URL method, not the domain method (leave the domain field blank)."
						+ "<li>Enter the key and secret values carefully, being sure not to include "
						+ "any leading or trailing blank spaces. This is the most common error."
						+ "<li>IMS Global Learning Solutions has published a "
						+ "<a href=https://www.imsglobal.org/lti-security-announcement-and-deprecation-schedule-july-2019>deprecation schedule</a> "
						+ "for this version of LTI registrations. At a minimum, your LMS must support "
						+ "LTI v1.1.2 for this connection to work after December 31, 2020."
						+ "</ol>");
				buf.append("If you need assistance for this registration, please contact me at admin@chemvantage.org<p>- Chuck Wight");
			
			} else {  // LTIAdvantage (version 1.3.0) registration request
				buf.append("<h4>To the LMS Administrator:</h4>"
						+ "By now you should have configured your LMS to connect with ChemVantage, and you should have "
						+ "received a client_id from your LMS that identifies the ChemVantage tool and a deployment_id "
						+ "that identifies your account in your LMS. Please enter these values here:<p>"
						+ "<form method=post action=/lti/registration>"
						+ "<input type=hidden name=UserRequest value='finalize'>"
						+ "<input type=hidden name=Token value='" + token + "'>"
						+ "Client ID: <input type=text size=40 name=ClientId><br>"
						+ "Deployment ID: <input type=text size=40 name=DeploymentId><p>");
				if ("canvas".contentEquals(lms)) {
					buf.append("Canvas uses the developer key as the client_id, so enter that value from the list of "
							+ "developer keys. It is a numeric value that looks something like 32570000000000041.<p>"
							+ "The deployment_id can be found in Settings | Apps | App Configurations by opening the "
							+ "settings menu for ChemVantage.<br>");
				} else {
					buf.append("In addition, ChemVantage needs URLs for the end points on your LMS in order to access services "
							+ "(e.g., Assignment and Grade Services) provided by your LMS platform. All fields are required, "
							+ "and all URLs should be secure (i.e., begin with https):<br>"
							+ "Platform ID: <input type=text size=40 name=PlatformId> (base URL for your LMS)<br>"
							+ "Platform OIDC Auth URL: <input type=text name=OIDCAuthUrl><br>"
							+ "Platform OAuth Access Token URL: <input type=text name=OauthAccessTokenUrl><br>"
							+ "Platform JSON Web Key Set URL: <input type=text name=JWKSUrl><br>");
				}
				buf.append("<input type=submit value='Complete the LTI Registration'></form>");	
			}
		} catch (Exception e) {
			buf.append("<h3>Registration Failed</h3>"
					+ e.getMessage() + "<p>"
					+ "Name: " + sub + "<br>"
					+ "Email: " + email + "<br>"
					+ "Organization: " + aud + "<br>"
					+ "Home Page: " + url + "<p>"
					+ "The token provided with this link could not be validated. It may have expired (after 3 days) "
					+ "or it may not have contained enough information to complete the registration request. You "
					+ "may <a href=/lti/registration>start the registration process again</a> or contact "
					+ "Chuck Wight (admin@chemvantage.org) for assistance.");
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
		String org = jwt.getAudience().get(0);
		String url = jwt.getClaim("url").asString();
		String iss = jwt.getIssuer();
		String lms = jwt.getClaim("lms").asString();
		String ver = jwt.getClaim("ver").asString();
		
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>ChemVantage Registration</h2>");
		buf.append("Name: " + name + " (" + email + ")<br>"
				+ "Org: " + org + " (" + url + ")<br>"
				+ "LMS: " + lms + "<p>");
		
		if (ver.contentEquals("1p1")) { // older LTIv1p1 registration process; deprecated 12/31/2020
			buf.append("Thank you for your ChemVantage registration request. Click the link below "
					+ "to view your free LTI credentials for connecting to ChemVantage. The link "
					+ "is valid for three days and expires at " + jwt.getExpiresAt() + "<p>");
			
			buf.append("<a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
					+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><p>");
			
			if ("moodle".contentEquals(lms)) {
				buf.append("Please note: Several Moodle users have experienced difficulty getting "
						+ "scores returned to the Moodle grade book using LTI. We believe that this is due to the Moodle server being "
						+ "configured in a way that refuses this type of LTI connection. You can rectify the situation by adding the "
						+ "following rewrite rule into the .htaccess file on the Moodle server:<br>"
						+ "RewriteCond %{HTTP:Authorization} ^(.+)" 
						+ "RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]<p>");
			}
			
			buf.append("If you  need assistance, please contact me at admin@chemvantage.org. <p>"
					+ "-Chuck Wight");		
		
		} else { // LTIAdvantage registration
			buf.append("Thank you for your ChemVantage registration request.<p> "
					+ "The next step is to enter the ChemVantage configuration details into your LMS. "
					+ "This will enable your LMS to communicate securely with ChemVantage. Normally, "
					+ "you must have administrator privileges in your LMS in order to do this. "
					+ "If you are NOT the LMS administrator, please stop here and forward this message "
					+ "to an administrator with a request to complete the registration process. The "
					+ "registration link below will be active for 3 days and expires at " + jwt.getExpiresAt() + ".<p>"
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
					+ "<a href=https://www.chemvantage.org/About>here</a>.<p>");
			if ("canvas".contentEquals(lms)) {
				buf.append("This request indicates that you are using the cloud-based Instructure Canvas LMS. "
						+ "To configure ChemVantage in Canvas please perform the following steps:<ol>"
						+ "<li>Configure a new LTI Developer Key for your Canvas Account "
						+ "(<a href=https://community.canvaslms.com/docs/DOC-16729-42141110178>see detailed instuctions here</a>)"
						+ "<br>Use the following Key Settings:<ul>"
						+   "<li>Key Name: ChemVantage" + (iss.contains("dev")?" Development":"")
						+   "<li>Owner Email: admin@chemvantage.org"
						+   "<li>Redirect URIs:<br>" + iss + "/lti/launch<br>" + iss + "/lti/deeplinks"
						+   "<li>Configure Method: Enter URL"
						+   "<li>JSON URL: " + iss + "/lti/registration?UserRequest=config&lms=canvas"
						+   "</ul>"
						+ "<li>Copy or write down the client_id and deployment_id created in step 1. "
						+ "Canvas uses the developer key as the client_id, so it can be viewed from the list of "
						+ "developer keys. It is a numeric value that looks something like 32570000000000041. "
						+ "The deployment_id can be found in Settings | Apps | App Configurations by opening the "
						+ "settings menu for ChemVantage."
						+ "<li>Add ChemVantage as an External App to your account using the client_id created in step 1 "
						+ "(<a href=https://community.canvaslms.com/docs/DOC-16730-42141110273>see detailed instructions here</a>)"
						+ "<li>Click <a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">this link</a> " 
						+ "to register the new client_id and deployment_id created in step 1 with ChemVantage"
						+ "<li>To use ChemVantage, create an assignment in Canvas. Under Submission Type, choose External Tool, "
						+ "then click Find and select ChemVantage" + (iss.contains("dev")?" Development":"") + ". You will be "
						+ "redirected to ChemVantage to select a Quiz, Homework, or Practice Exam for students in the course. "
						+ "Student scores are returned directly to the Canvas grade book. You should test this functionality "
						+ "using Settings | Test Student."
						+ "</ol>"
						+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
						+ "-Chuck Wight");
			} else {
				buf.append("This registration request uses the LTI Advantage (version 1.3) specifications. "
						+ "Use the information below to register ChemVantage in your LMS:<br>"
						+ "Tool Domain URL: " + iss + "<br>"
						+ "Tool Redirect URL: " + iss + "/lti/launch<br>"
						+ "Tool Deep Linking URL: " + iss + "/lti/deeplinks<br>"
						+ "OIDC Login Initiation URL: " + iss + "/auth/token<br>"
						+ "JSON Web Key Set Endpoint: " + iss + "/jwks<p>");

				if ("moodle".contentEquals(lms)) {
					buf.append("Please note: Several Moodle users have experienced difficulty getting "
							+ "scores returned to the Moodle grade book using LTI. We believe that this is due to the Moodle server being "
							+ "configured in a way that refuses this type of LTI connection. You can rectify the situation by adding the "
							+ "following rewrite rule into the .htaccess file on the Moodle server:<br>"
							+ "RewriteCond %{HTTP:Authorization} ^(.+)" 
							+ "RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]<p>");
				}
				
				buf.append("When you have finished the configuration, " + ("canvas".equals(lms)?"Canvas ":"your LMS ") 
						+ "should generate a client_id value to identify the ChemVantage tool. "
						+ ("canvas".equals(lms)?"Canvas also calls this the developer key. ":"")
						+ "In addition, your LMS should generate a "
						+ "deployment_id value to identify a specific account in your LMS for this tool. "
						+ "When you have these values in hand, please click the following link to complete the "
						+ "LTI registration.<p>");
				buf.append("<a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
						+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><p>"
						+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
						+ "-Chuck Wight");
			}
		}
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
	
	String getConfigurationJson(String iss,String lms) {
		String domain = null;
		try {
			domain = new URL(iss).getHost();
		} catch (Exception e) { 
			return "Domain was not valid."; 
		}
		
		JsonObject config = new JsonObject();
		config.addProperty("title","ChemVantage" + (iss.contains("dev")?" Development":""));
		config.addProperty("description", "ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry");;
		config.addProperty("public_jwk_url", iss + "/jwks");
		config.addProperty("target_link_uri", iss + "/lti/launch");
		config.addProperty("oidc_initiation_url", iss + "/auth/token");
		  JsonArray scopes = new JsonArray();
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem");
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem.readonly");
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly");
		  scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/score");
		  scopes.add("https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly");
		config.add("scopes", scopes);
		  JsonArray extensions = new JsonArray();
		    JsonObject ext = new JsonObject();
		    ext.addProperty("domain", domain);
		    ext.addProperty("platform", "canvas.instructure.com");
		    ext.addProperty("privacy_level", "public");
			  JsonObject settings = new JsonObject();
		      settings.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		      settings.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		        JsonArray placements = new JsonArray();
		          JsonObject plcmnt = new JsonObject();
		          plcmnt.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
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
