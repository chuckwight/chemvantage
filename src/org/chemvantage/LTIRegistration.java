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
import java.util.Random;
import java.util.regex.Pattern;

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
import com.google.gson.JsonParser;

@WebServlet(urlPatterns = {"/lti/registration","/lti/registration/","/lti_config.xml"})
public class LTIRegistration extends HttpServlet {

	/* This servlet class is used to apply for and grant access to LTI connections between client
	 * LMS platforms and the ChemVantage tool. The user will complete a short form with name, role,
	 * email, organization, home page, LMS type and use case (testing or production). Those wanting to test
	 * will get access to dev-vantage.appspot.com, while production users will see chemvantage.org.
	 * There are 2 main workflow paths (for LTIv1.1 and LTIv1.3):
	 *   1) All users complete a basic form giving information about their org and the LTI request
	 *   2) ChemVantage responds by validating the fields and sending a registration email containing a link
	 *      with the tokenized information
	 * For LTIv1.1:
	 *   3) After receiving the registration email, the user clicks a tokenized link. The createBLTIConsumer
	 *      method creates a new BLTIConsumer and presents the credentials to the user with instructions
	 *   4) The user enters the credentials into their LMS and is ready to go. 
	 * For LTIv1.3:
	 *   3) The registration email contains the ChemVantage endpoints and configuration JSON to
	 *      complete the registration in the LMS. 
	 *   4) The user then clicks the tokenized link, and the clientIdForm method generates a form for 
	 *      entering the client_id and deployment_id values and LMS endpoints, if necessary.
	 *   5) The LMS admin submits the form, and the createDeployment method creates the new Deployment entity 
	 *      and completes the registration process.
	 * */
	
	private static final long serialVersionUID = 137L;
	Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
		PrintWriter out = response.getWriter();
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		String iss = "https://" + request.getServerName();
		String path = request.getServletPath();
		
		if (path.contentEquals("/lti_config.xml")) {
			response.setContentType("application/xml");
			out.println(getConfigurationXml(iss));
		}
		else if ("config".contentEquals(userRequest)) {
			response.setContentType("application/json");
			out.println(getConfigurationJson(iss,request.getParameter("lms")));
		} else if (request.getParameter("token")!=null) {
			response.setContentType("text/html");
			String token = request.getParameter("token");
			DecodedJWT decoded = JWT.require(algorithm).withIssuer(iss).build().verify(token);
			String ltiVersion = decoded.getClaim("ver").asString();
			if (ltiVersion.equals("1p1")) out.println(Home.header("LTI Registration") + createBLTIConsumer(token) + Home.footer);
			else if (ltiVersion.equals("1p3")) out.println(Home.header("LTI Registration") + clientIdForm(token) + Home.footer);
			else throw new Exception("LTI version was missing or invalid.");
		} else {
			response.sendRedirect("/Registration.jsp");
		}
		} catch (Exception e) {
			response.sendError(401, e.getMessage());
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";

		String iss = "https://" + request.getServerName();
		
		try {
			if ("Send Me The Registration Email".contentEquals(userRequest)) {			
				String token = validateApplicationFormContents(request);
				sendRegistrationEmail(token);
				out.println(Home.header("ChemVantage LTI Registration") + Home.banner + "<h3>Registration Success</h3>Thank you. A registration email has been sent to your address.<p>" + Home.footer);			
			} else if ("finalize".contentEquals(userRequest)) {				
				String token = request.getParameter("Token");
				JWT.require(algorithm).withIssuer(iss).build().verify(token);
				out.println(Home.header("ChemVantage LTI Registration") + Home.banner + createDeployment(request) + Home.footer);			
			}
		} catch (Exception e) {
			response.sendError(401,e.getMessage() + "Please go BACK and try again or contact admin@chemvantage.org for assistance.");
		}
	}
		
	String validateApplicationFormContents(HttpServletRequest request) throws Exception {
		String sub = request.getParameter("sub");
		String email = request.getParameter("email");
		String aud = request.getParameter("aud");
		String url = request.getParameter("url");
		String typ = request.getParameter("typ");
		String use = request.getParameter("use");
		String ver = request.getParameter("ver");
		String lms = request.getParameter("lms");
		
		if (sub.isEmpty() || email.isEmpty() || use==null ||use.isEmpty() || ver==null || ver.isEmpty()) throw new Exception("All form fields are required. ");
		if (aud.isEmpty() || url.isEmpty()) throw new Exception("You must enter your organization name and home page. ");
		else {
			aud = (aud==null?"":aud);
			url = (url==null?"":url);
		}
		
		String regex = "^[A-Za-z0-9+_.-]+@(.+)$";		 
		Pattern pattern = Pattern.compile(regex);
		if (!pattern.matcher(email).matches()) throw new Exception("Your email address was not formatted correctly. ");
		
		try {
			if (!"personal".contentEquals(typ)) new URL(url);   // throws Exception if URL is not formatted correctly
		} catch (Exception e) {
			throw new Exception("Invalid domain name (" + e.getMessage() + ").");
		}
		
		if (typ==null) throw new Exception("Please specify the type of organization connecting to ChemVantage. ");
		
		if (lms==null) throw new Exception("Please select the type of LMS that you are connecting to ChemVantage. ");
		if ("other".contentEquals(lms)) lms = request.getParameter("lms_other");
		if (lms==null || lms.isEmpty()) throw new Exception("Please describe the type of LMS that you are connecting to ChemVantage. ");
		
		if (!"true".equals(request.getParameter("AcceptChemVantageTOS"))) throw new Exception("You must accept the ChemVantage Terms of Service. ");
		
		if (!reCaptchaOK(request)) throw new Exception("ReCaptcha tool unverified. ");
	
		// Construct a new registration token
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
				.withClaim("typ", typ)
				.withClaim("lms", lms)
				.withClaim("ver", ver)
				.sign(algorithm);
		
		return token;
	}
		
	boolean reCaptchaOK(HttpServletRequest request) throws Exception {
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
		JsonObject captchaResponse = JsonParser.parseReader(reader).getAsJsonObject();
    	reader.close();
		
		//JsonObject captchaResp = JsonParser.parseString(res.toString()).getAsJsonObject();
		return captchaResponse.get("success").getAsBoolean();
	}

	void sendRegistrationEmail(String token) throws Exception {
		DecodedJWT jwt = JWT.decode(token);
		String name = jwt.getSubject();
		String email = jwt.getClaim("email").asString();
		String org = jwt.getAudience().get(0);
		String url = jwt.getClaim("url").asString();
		String iss = jwt.getIssuer();
		String lms = jwt.getClaim("lms").asString();
		String ver = jwt.getClaim("ver").asString();
		String typ = jwt.getClaim("typ").asString();
		
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>ChemVantage Registration</h2>");
		buf.append("Name: " + name + " (" + email + ")<br>"
				+ (org.isEmpty()?"":"Org: " + org + " (" + url + ")<br>")
				+ "LMS: " + lms + "<p>");
		
		buf.append("Thank you for your ChemVantage registration request.<p>");
		
		if (iss.contains("dev")) {
			buf.append("You indicated on the registration form that your use case is testing LTI connections. ChemVantage is pleased to support "
					+ "the LTI community by offering access to our code development server for this purpose. When you complete the registration "
					+ "steps below, your account will be activated for a free 10 day trial period for up to 5 users in your LMS. If you requre "
					+ "access to this server for more than 10 days and/or 5 users, please contact us at admin@chemvantage.org.<p>"
					+ "Please do not use the development server for serious instructional purposes.<p>");
		} else {
			switch (typ) {
			case "nonprofit":
				buf.append("You indicated on the registration form that " + org + " is a public or non-profit educational institution. As such, ChemVantage "
						+ "services are provided free for up to 1000 users in your LMS. Please contact us for pricing beyond this limit.<p>"
						+ "<b>By clicking the link below, you certify that your organization is a public or non-profit institution.</b><p>");
				break;
			case "personal":
				buf.append("You indicated the registration form that you intend to use ChemVantage for a small business or personal use. You may use "
						+ "this account for offering instuction in General Chemistry for up to 5 users from your LMS. " 
						+ "To exceed this limit, please contact us for pricing at admin@chemvantage.org.<p>"
						+ "Your account has been fully activated for 10 days pending payment of the $20 monthly subscription fee. You should receive an "
						+ "invoice from PayPal via email within the next 2 days.<p>");
				break;			
			case "forprofit":
				buf.append("You indicated on the registration form that " + org + " desires to establish a corporate parnership account. ChemVantage will "
						+ "send you an invoice in the next few days for payment of the $5000 annual subscription charge. This subscription allows you to "
						+ "provide ChemVantage services for up to 10,000 users from your LMS. To exceed this limit, please contact us for pricing.<p>"
						+ "Your account has been fully activated for 10 days pending payment of the $5000 annual subscription fee. You should receive an " 
						+ "invoice from PayPal via email within the next 2 days.<p>");
				break;
			default: 
			}
		}
		
		if (ver.contentEquals("1p1")) { // older LTIv1p1 registration process; deprecated 12/31/2020
			
			buf.append("Click the link below to obtain your LTI credentials for connecting to ChemVantage. "
					+ "<b>Print or save the credentials in a safe place.</b> "
					+ "For your security, the link is valid for only three days and expires at " + jwt.getExpiresAt() + "<p>");
			
			buf.append("<a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
					+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><p>");
			
			switch (lms) {
			case "blackboard":
				String providerDomain = iss.replaceFirst("https://", "");
				buf.append("<b>Detailed instructions for connecting ChemVantage to a Blackboard course:</b><p>");
				buf.append("<u>For the Blackboard Account System Administrator:</u>");
				buf.append("<ol><li>Go to System Admin | Integrations: LTI Tool Providers | Register Provider Domain"
						+ "<ul><li>Provider Domain: " + providerDomain + "</li>"
						+ " <li>Provider Domain Status: Approved</li>"
						+ " <li>Secondary Hostnames (leave blank)</li>"
						+ " <li>Default configuration: Set globally</li>" 
						+ " <li>Tool Provider Key and Tool Provider Secret: (cut/paste values from LTI credentials)</li>" 
						+ " <li>Tool Provider Custom Parameters: (leave blank)</li>" 
						+ " <li>Send user data only over SSL</li>"
						+ " <li>User fields: check Role, Name, Email</li>"
						+ " <li>Allow Membership Service Access: Yes</li>"
						+ " <li>Submit</li>"
						+ "</ul></li>"
						+ "<li>Go back to the LTI Tool Providers Page, and select the ChemVantage provider domain | Manage Placements | Create Placement" 
						+ "<ul><li>Label: ChemVantage</li>"
						+ " <li>Description: ChemVantage is a free Open Education Resource for teaching and learning college-level General Chemistry.</li>"
						+ " <li>Handle: (any unique string like Handle: chemvantage_lti_v1p1)</li>"
						+ " <li>Availability: Yes</li>"
						+ " <li>Select Course content tool, allows grading (no deep linking)</li>"
						+ " <li>Tool Provider URL: " + iss + "/lti</li>"
						+ " <li>Tool Provider Key: (oauth_consumer_key)</li>"
						+ " <li>Tool Provider Secret: (shared_secret)</li>"
						+ " <li>Custom Parameters (leave blank)</li>"
						+ " <li>Submit</li>"
						+ "</ul></li></ol>");
				buf.append("<u>For the Blackboard Instructor:</u>");
				buf.append("<ol><li>Go to the course | Content | Build Content | ChemVantage</li>"
						+ "<li>Name: as appropriate (e.g., Quiz - Heat & Enthalpy)</li>"
						+ "<li>Grading:"
						+ "<ul><li>Enable Evaluation - Yes</li>"
						+ " <li>Points - 10 for quiz or homework; 5 for video; 100 for practice exam</li>"
						+ " <li>Visible to Students - Yes</li>"
						+ "</ul></li>"
						+ "<li>Submit</li>"
						+ "<li>Click the new assignment link to launch ChemVantage</li>"
						+ "<li>Choose the relevant assignment (e.g., Quiz on Heat & Enthalpy)</li>"
						+ "<li>Customize the assignment, if desired, using the highlighted link</li>"
						+ "</ol>");
				break;		
			case "canvas":
				buf.append("<b>Detailed instructions for connecting ChemVantage to a Canvas course:</b>");
				buf.append("<ol><li>Print or Save your <a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">LTI credentials</a></li>"
						+ "<li>Login to Canvas as a course Instructor, navigate to Settings, select the Apps tab, and click View App Configurations. "
						+ "Then click the red +App button and complete the following fields to create a new External Tool:" 
						+ "<ul><li>Configuration Type: By URL</li>"
						+ " <li>Name: ChemVantage</li>"
						+ " <li>Consumer Key and Shared Secret: (cut/paste values from step 1; do not include any blank spaces)</li>" 
						+ " <li>Config URL: " + iss + "/lti_config.xml</li>" 
						+ " <li>Submit</li>"
						+ "</ul></li>"
						+ "<li>Create a new Canvas assignment with the following recommended parameters:" 
						+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)</li>"
						+ " <li>Points: 10 for quiz or homework; 5 for video; 100 for practice exam</li>"
						+ " <li>Submission Type: External Tool</li>"
						+ " <li>External Tool URL: Find ChemVantage or enter " + iss + "/lti</li>"
						+ " <li>Save or Save and Publish</li>"
						+ "</ul></li>"
						+ "<li>After you Save the assignment, you should see the ChemVantage Assignment Setup Page. "
						+ "Select the appropriate ChemVantage assignment (e.g., Quiz on Heat & Enthalpy). ChemVantage will remember this choice.</li>"
						+ "<li>When the assignment appears, you may use the highlighted link to customize it for your class.</li>"
						+ "<li>After you Publish the assignment, you should navigate to Settings, Student View and submit the assignment "
						+ "to ensure that the score is posted correctly in the Canvas grade book. ChemVantage does not collect any personally "
						+ "identifiable information (PII) from students, so returning scores to the Canvas grade book is the ONLY way to ensure "
						+ "that students get credit for their work.</li>"
						+ "</ol>");
				break;
			case "moodle":
				buf.append("Please note: Several Moodle users have experienced difficulty getting "
						+ "scores returned to the Moodle grade book using LTI. We believe that this is due to the Moodle server being "
						+ "configured in a way that refuses this type of LTI connection. You can rectify the situation by adding the "
						+ "following rewrite rule into the .htaccess file on the Moodle server:<br>"
						+ "RewriteCond %{HTTP:Authorization} ^(.+)" 
						+ "RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]<p>");
				break;
			default:
			}
			
			buf.append("If you  need assistance, please contact me at admin@chemvantage.org. <p>"
					+ "-Chuck Wight");		
		
		} else { // LTIAdvantage registration
			buf.append("The next step is to enter the ChemVantage configuration details into your LMS. "
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
			switch (lms) {
			case "blackboard":
				buf.append("This request indicates that you are using the cloud-based Blackboard Learn LMS. "
						+ "To configure ChemVantage in Blackboard please perform the following steps:<ol>"
						+ "<li>Go to System Admin | Integrations: LTI Tool Providers | Register LTI 1.3 Tool"
						+ "<li>Enter the Client ID: " + (iss.equals("https://dev-vantage-hrd.appspot.com")?"ec076e8c-b90f-4ecf-9b5d-a9eff03976be":"be1004de-6f8e-45b9-aae4-2c1370c24e1e")
						+ "<li>Make a copy of the deployment_id and set Tool status: Approved"
						+ "<li>Institution Policies: Send Role, Name, Email; Allow Grade Service and Membership Service"
						+ "<li>Submit"
						+ "<li>Click <a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">this link</a> " 
						+ "to register the deployment_id with ChemVantage"
						+ "<li>Go back to the LTI Tool Providers page, and from the dropdown menu on the ChemVantage app select Manage Placements"
						+ "<li>Click Create Placement"
						+ "<ul><li>Label: ChemVantage</li>"
						+ " <li>Description: ChemVantage is a free Open Educational Resource for teaching and learning college-level General Chemistry"
						+ " <li>Handle: (any unique string)"
						+ " <li>Availability: Yes"
						+ " <li>Course Content Tool (supports deep linking)"
						+ " <li>Tool Provider URL: " + iss + "/lti/deeplinks"
						+ " <li>Custom Parameters (leave blank)"
						+ " <li>Submit</li></ul></ol>");
				buf.append("<hr><br>To the Course Instructor:");
				buf.append("<ol><li>Go to the course | Content | Build Content | ChemVantage</li>"
						+ "<li>Name: as appropriate (e.g., Quiz - Heat & Enthalpy)</li>"
						+ "<li>Grading:"
						+ "<ul><li>Enable Evaluation - Yes</li>"
						+ " <li>Points - 10 for quiz or homework; 5 for video; 100 for practice exam</li>"
						+ " <li>Visible to Students - Yes</li>"
						+ "</ul></li>"
						+ "<li>Submit</li>"
						+ "<li>Click the new assignment link to launch ChemVantage</li>"
						+ "<li>Choose the relevant assignment (e.g., Quiz on Heat & Enthalpy)</li>"
						+ "<li>Customize the assignment, if desired, using the highlighted link</li>"
						+ "</ol>");
				break;
			case "canvas":
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
						+ "<li>Click Save."
						+ "<li>Copy or write down the client_id and deployment_id created in step 1. "
						+ "Canvas uses the developer key as the client_id, so it can be viewed from the list of "
						+ "developer keys. It is a numeric value that looks something like 32570000000000041. "
						+ "The deployment_id can be found in Settings | Apps | App Configurations by opening the "
						+ "settings menu for ChemVantage."
						+ "<li>Add ChemVantage as an External App to your account using the client_id created in step 1 "
						+ "(<a href=https://community.canvaslms.com/docs/DOC-16730-42141110273>see detailed instructions here</a>)"
						+ "<li>Click <a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">this link</a> " 
						+ "to register the new client_id and deployment_id created in step 1 with ChemVantage</ol>");
				buf.append("<hr><br>To the Course Instructor:<ol>"
						+ "<li>Create a new Canvas assignment with the following recommended parameters:" 
						+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)</li>"
						+ " <li>Points: 10 for quiz or homework; 5 for video; 100 for practice exam</li>"
						+ " <li>Submission Type: External Tool</li>"
						+ " <li>External Tool URL: Find ChemVantage or enter " + iss + "/lti</li>"
						+ " <li>Save or Save and Publish</li>"
						+ "</ul></li>"
						+ "<li>When you launch the assignment, you may use the highlighted link to customize it for your class.</li>"
						+ "</ol>");
				break;
			default:
				buf.append("This registration request uses the LTI Advantage (version 1.3) specifications. "
						+ "Use the information below to register ChemVantage in your LMS:<br>"
						+ "Tool Domain URL: " + iss + "<br>"
						+ "Tool Redirect URL: " + iss + "/lti/launch<br>"
						+ "Tool Deep Linking URL: " + iss + "/lti/deeplinks<br>"
						+ "OIDC Login Initiation URL: " + iss + "/auth/token<br>"
						+ "JSON Web Key Set URL: " + iss + "/jwks<p>");
				
				buf.append("If your LMS requires you to enter a specific public RSA key instead of the JSON Web Key Set URL, you can "
						+ " get it here:<br>"
						+ "<a href=" + iss + "/jwks?kid=public&fmt=x509>PEM key in X509 format</a> or <a href=" + iss + "/jwks?kid=public>JSON Web Key</a><p>");
				
				buf.append("When you have finished the configuration, your LMS "
						+ "should generate a client_id value to identify the ChemVantage tool. "
						+ "In addition, your LMS should generate a "
						+ "deployment_id value to identify a specific account in your LMS for this tool. "
						+ "When you have these values in hand, please click the following link to complete the "
						+ "LTI registration.<p>");
				buf.append("<a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
						+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><p>");
			}
			
			buf.append("If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
					+ "-Chuck Wight");

		}
		sendEmail(name,email,"ChemVantage LTI Registration",buf.toString());
	}

	protected void sendEmail(String recipientName, String recipientEmail, String subject, String messageBody) throws Exception {
		Message msg = new MimeMessage(Session.getDefaultInstance(new Properties()));
		InternetAddress from = new InternetAddress("admin@chemvantage.org", "ChemVantage");
		msg.setFrom(from);
		msg.addRecipient(Message.RecipientType.TO,new InternetAddress(recipientEmail,recipientName));
		msg.addRecipient(Message.RecipientType.CC,from);
		msg.setSubject(subject);
		msg.setContent(messageBody,"text/html");
		Transport.send(msg);
	}
	
	String createBLTIConsumer(String token) throws Exception {
		DecodedJWT jwt = JWT.decode(token); 
		String iss = jwt.getIssuer();          // already verified to be this ChemVantage server (dev or production)
		String sub = jwt.getSubject();         // name of registering person
		String email = jwt.getClaim("email").asString();  // email of registering person
		String aud = jwt.getAudience().get(0);            // name of applicant organization
		String url = jwt.getClaim("url").asString();      // home page of applicant organization
		String typ = jwt.getClaim("typ").asString();      // type of org (nonprofit, forprofit, personal)
		String lms = jwt.getClaim("lms").asString();      // type of LMS platform
		
		String oauth_consumer_key = null;
		if (lms.equals("blackboard")) {  // issue the global blackboard key
			oauth_consumer_key = iss.contains("dev-vantage")?"12fbeed9-eabf-414b-9aea-80f2cba3772e":"1001bea7-e99e-49a7-bd0e-5d596b1455e0";
		} else { // issue a key: "CK" plus a random 6-digit number based on the token value
			oauth_consumer_key = "CK" + (new Random(token.hashCode()).nextInt(899999) + 100000);
		}
		
		BLTIConsumer con = null;
		try {  // retrieve the BLTIConsumer that was saved when the token was used
			con = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).safe();
		} catch (Exception e) {  // this is the first-time use of this token; save the BLTIConsumer
			con = new BLTIConsumer(oauth_consumer_key);
			con.email = email;
			con.contact_name = sub;
			con.lms = lms;
			con.organization = aud;
			con.org_url = url;
			con.org_type = typ;
			con.created = new Date();
			
			if (iss.contains("dev")) con.expires = new Date(new Date().getTime() + 864000000L);  // dev server free trial period of 10 days
			else {  // request id for access to the production server
				switch (typ) {
				case "nonprofit": con.expires = new Date(new Date().getTime() + 864000000L); break;  // 10 days
				case "forprofit": con.expires = new Date(new Date().getTime() + 864000000L); break;  // 10 days
				case "personal": con.expires = new Date(new Date().getTime() + 864000000L); break;  // 10 days
				default: con.expires = new Date();
				}
			}
			
			ofy().save().entity(con).now();
		}
		
		// The next line checks to see if the email for BLTIConsumr retrieved matches the email in the token
		// This catches the (unlikely) case where the same CK is issued to 2 different people, but also catches the
		// (more likely) case where a valid login changed the email contact. Either way protects the CK from being revealed.
		if (!email.equals(con.email)) throw new Exception("This consumer key was successfully registered and is already in use. Please register again, if necessary.");
		
		StringBuffer buf = new StringBuffer();

		buf.append("<h3>Thank you for registering your LMS with ChemVantage</h3>");

		buf.append("Here are your LTI registration credentials:<p>"
				+ "Tool Name: ChemVantage<br>"
				+ "Description: ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry.<br>"
				+ "Launch URL: " + iss + "/lti<br>"
				+ "Consumer Key: " + con.oauth_consumer_key + "<br>"
				+ "Shared Secret: " + con.secret + "<p>");
		buf.append("Important notes:<ol>"
				+ "<li>When entering this information into your LMS, be sure to use the "
				+ "Launch URL method, not the domain method (leave the domain field blank)."
				+ "<li>Enter the key and secret values carefully, being sure not to include "
				+ "any leading or trailing blank spaces. This is the most common error."
				+ "<li>IMS Global Learning Solutions has published a "
				+ "<a href=https://www.imsglobal.org/lti-security-announcement-and-deprecation-schedule-july-2019>deprecation schedule</a> "
				+ "for this version of LTI registrations. Most LMS platforms have agreed to support this version of LTI through at least "
				+ "the 2021 calendar year. After that you should upgrade to LTI Advantage (version 1.3).");
		
		if (iss.contains("dev")) {
			buf.append("<li>You indicated on the registration form that your initial use case is checking the LTI connection. So we granted access "
					+ "to the ChemVantage Development server. When you are ready to use ChemVantage for instruction, please "
					+ "<a href=https://www.chemvantage.org/lti/registration?use=prod>register again here</a> to access the production server. Do not use "
					+ "the Development server for live instruction.<p>");
		} else {
			buf.append("<li>After registering ChemVantage in your LMS, simply create an assignment in the LMS and configure the submission to be "
					+ "from a third-party app (ChemVantage). Suggested point values are 10 for each quiz or homework set, 100 for practice exams.");
		}
		buf.append("</ol>");
		buf.append("If you need assistance for this registration, please contact me at admin@chemvantage.org<p>- Chuck Wight");
		
		return buf.toString();
	}
	
	String clientIdForm(String token) {
		StringBuffer buf = new StringBuffer(Home.banner);
		String iss = null;
		String sub = null;
		String email = null;
		String aud = null;
		String url = null;
		String lms = null;
		
		try {
			DecodedJWT jwt = JWT.decode(token);  // registration token is valid for only 3 days
			iss = jwt.getIssuer();
			sub = jwt.getSubject();
			email = jwt.getClaim("email").asString();
			aud = jwt.getAudience().get(0);
			url = jwt.getClaim("url").asString();
			lms = jwt.getClaim("lms").asString();
			buf.append("<h4>To the LMS Administrator:</h4>"
					+ "By now you should have configured your LMS to connect with ChemVantage, and you should have "
					+ "received a client_id from your LMS that identifies the ChemVantage tool and a deployment_id "
					+ "that identifies your account in your LMS. Please enter these values here:<p>"
					+ "<form method=post action=/lti/registration>"
					+ "<input type=hidden name=UserRequest value='finalize'>"
					+ "<input type=hidden name=Token value='" + token + "'>");
			
			switch (lms) {
			case "blackboard":
				String clientId = (iss.equals("https://dev-vantage-hrd.appspot.com")?"ec076e8c-b90f-4ecf-9b5d-a9eff03976be":"be1004de-6f8e-45b9-aae4-2c1370c24e1e");
				buf.append("<input type=hidden name=ClientId value=" + clientId + ">");
				buf.append("Client ID: " + clientId + "<br>"
						+ "Deployment ID: <input type=text size=40 name=DeploymentId><p>");
				break;
			case "canvas":
				buf.append("Canvas account URL: <input type=text size=40 name=AccountUrl placeholder=https://myschool.instructure.com><br>");
				buf.append("Canvas uses the developer key as the client_id, so enter that value from the list of "
						+ "developer keys. It is a numeric value that looks something like 32570000000000041.<p>"
						+ "The deployment_id can be found in Settings | Apps | App Configurations by opening the "
						+ "settings menu for ChemVantage.<br>");
				buf.append("Client ID: <input type=text size=40 name=ClientId><br>"
						+ "Deployment ID: <input type=text size=40 name=DeploymentId><p>");
				break;
			default:
				buf.append("Client ID: <input type=text size=40 name=ClientId><br>"
						+ "Deployment ID: <input type=text size=40 name=DeploymentId><p>");
				buf.append("In addition, ChemVantage needs URLs for the end points on your LMS in order to access services "
						+ "(e.g., Assignment and Grade Services) provided by your LMS platform. All fields are required, "
						+ "and all URLs should begin with https://<br>"
						+ "Platform ID: <input type=text size=40 name=PlatformId> (base URL for your LMS)<br>"
						+ "Platform OIDC Auth URL: <input type=text name=OIDCAuthUrl><br>"
						+ "Platform OAuth Access Token URL: <input type=text name=OauthAccessTokenUrl><br>"
						+ "Platform JSON Web Key Set URL: <input type=text name=JWKSUrl><br>");
			}
			buf.append("<input type=submit value='Complete the LTI Registration'></form>");	
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
	
	String createDeployment(HttpServletRequest request) throws Exception {
		DecodedJWT jwt = JWT.decode(request.getParameter("Token"));
		String client_name = jwt.getSubject();
		String email = jwt.getClaim("email").asString();
		String organization = jwt.getAudience().get(0);
		String org_url = jwt.getClaim("url").asString();
		String lms = jwt.getClaim("lms").asString();
		String client_id = request.getParameter("ClientId");
		if (client_id==null) throw new Exception("Client ID value is required.");
		String deployment_id = request.getParameter("DeploymentId");
		if (deployment_id==null) throw new Exception("Deployment ID value is required.");
		String platform_id;
		String oidc_auth_url;
		String oauth_access_token_url;
		String well_known_jwks_url;
		switch (lms) {
		case "blackboard":
			platform_id = "https://blackboard.com";
			oidc_auth_url = "https://developer.blackboard.com/api/v1/gateway/oidcauth";
			well_known_jwks_url = "https://developer.blackboard.com/api/v1/management/applications/" + client_id + "/jwks.json";
			oauth_access_token_url = "https://developer.blackboard.com/api/v1/gateway/oauth2/jwttoken";
			break;
		case "canvas":
			platform_id = "https://canvas.instructure.com";
			oidc_auth_url = "https://canvas.instructure.com/api/lti/authorize_redirect";
			well_known_jwks_url = "https://canvas.instructure.com/api/lti/security/jwks";
			oauth_access_token_url = request.getParameter("AccountUrl");
			if (oauth_access_token_url==null || oauth_access_token_url.isEmpty()) throw new Exception("Canvas account URL is required.");
			oauth_access_token_url += "/login/oauth2/token";
			break;
		default:
			platform_id = request.getParameter("PlatformId");
			if (platform_id==null || platform_id.isEmpty()) throw new Exception("Platform ID value is required.");
			oidc_auth_url = request.getParameter("OIDCAuthUrl");
			if (oidc_auth_url==null || oidc_auth_url.isEmpty()) throw new Exception("OIDC Auth URL is required.");
			oauth_access_token_url = request.getParameter("OauthAccessTokenUrl");
			if (oauth_access_token_url==null || oauth_access_token_url.isEmpty()) throw new Exception("OAuth Access Token URL is required.");
			well_known_jwks_url = request.getParameter("JWKSUrl");
			if (well_known_jwks_url==null || well_known_jwks_url.isEmpty()) throw new Exception("JSON Web Key Set URL is required.");
		}
			
		Deployment d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,client_name,email,organization,org_url,lms);
		
		Deployment prior = Deployment.getInstance(d.platform_deployment_id);
		
		ofy().save().entity(d).now();  // registration is now complete
		
		String msg = "<h2>Congratulations. Registration is complete.</h2>";
		
		if (prior==null) return msg;
		else if (prior.client_id.equals(d.client_id)) return msg + "Note: this platform deployment was registered previously. The registration data have now been updated.<p>";
		else return msg + "Note: This platform deployment was registered previously. The client_id and registration data have now been updated. If this is not correct, you should contact admin@chemvantage.org immediately.<p>";
		/*
		// check to ensure that this is not a duplicate registration:
		if (Deployment.getInstance(d.platform_deployment_id) == null) {
			
			return "<h2>Congratulations. Registration is complete.</h2>";
		} else return "Registration Failed: This deployment is already registered with ChemVantage.";
		*/
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
		config.addProperty("privacy_level", "public");
		config.addProperty("target_link_uri", iss + "/lti/launch");
		config.addProperty("oidc_initiation_url", iss + "/auth/token");
		//config.addProperty("public_jwk_url", iss + "/jwks");
		config.add("public_jwk", KeyStore.getJwk(KeyStore.getAKeyId(lms)));
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
		      JsonObject settings = new JsonObject();
		      settings.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		      settings.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		        JsonArray placements = new JsonArray();
		         JsonObject plcmnt1 = new JsonObject();
		          plcmnt1.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt1.addProperty("enabled", true);
		          plcmnt1.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		          plcmnt1.addProperty("placement", "assignment_selection");
		          plcmnt1.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt1.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt1);
		         JsonObject plcmnt2 = new JsonObject();
		          plcmnt2.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt2.addProperty("enabled", true);
		          plcmnt2.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		          plcmnt2.addProperty("placement", "editor_button");
		          plcmnt2.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt2.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt2);
		         JsonObject plcmnt3 = new JsonObject();
		          plcmnt3.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt3.addProperty("enabled", true);
		          plcmnt3.addProperty("icon_url", iss + "/images/CVLogo_thumb.jpg");
		          plcmnt3.addProperty("placement", "link_selection");
		          plcmnt3.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt3.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt3);
		      settings.add("placements", placements);
		    ext.add("settings", settings);
		  extensions.add(ext);
		config.add("extensions", extensions);
		
		return config.toString();
	}
	
	String getConfigurationXml(String iss) {
		boolean dev = iss.contains("dev");
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
				"<cartridge_basiclti_link xmlns=\"http://www.imsglobal.org/xsd/imslticc_v1p0\"\n" + 
				"    xmlns:blti = \"http://www.imsglobal.org/xsd/imsbasiclti_v1p0\"\n" + 
				"    xmlns:lticm =\"http://www.imsglobal.org/xsd/imslticm_v1p0\"\n" + 
				"    xmlns:lticp =\"http://www.imsglobal.org/xsd/imslticp_v1p0\"\n" + 
				"    xmlns:xsi = \"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
				"    xsi:schemaLocation = \"http://www.imsglobal.org/xsd/imslticc_v1p0 http://www.imsglobal.org/xsd/lti/ltiv1p0/imslticc_v1p0.xsd\n" + 
				"    http://www.imsglobal.org/xsd/imsbasiclti_v1p0 http://www.imsglobal.org/xsd/lti/ltiv1p0/imsbasiclti_v1p0.xsd\n" + 
				"    http://www.imsglobal.org/xsd/imslticm_v1p0 http://www.imsglobal.org/xsd/lti/ltiv1p0/imslticm_v1p0.xsd\n" + 
				"    http://www.imsglobal.org/xsd/imslticp_v1p0 http://www.imsglobal.org/xsd/lti/ltiv1p0/imslticp_v1p0.xsd\">\n" + 
				"    <blti:title>ChemVantage" + (dev?" Development":"") + "</blti:title>\n" + 
				"    <blti:description>ChemVantage is an Open Education Resource for teaching and learning college level General Chemistry.</blti:description>\n" + 
				"    <blti:extensions platform=\"canvas.instructure.com\">\n" + 
				"      <lticm:property name=\"tool_id\">chemvantage.org</lticm:property>\n" + 
				"      <lticm:property name=\"privacy_level\">anonymous</lticm:property>\n" + 
				"    </blti:extensions>\n" + 
				"    <blti:secure_launch_url>" + iss + "/lti</blti:secure_launch_url>\n" + 
				"    <blti:secure_icon>" + iss + "/favicon.png</blti:secure_icon>\n" + 
				"    <blti:vendor>\n" + 
				"        <lticp:code>www.chemvantage.org</lticp:code>\n" + 
				"        <lticp:name>ChemVantage LLC</lticp:name>\n" + 
				"        <lticp:description>ChemVantage provides Open Education learning tools for chemistry.</lticp:description>\n" + 
				"        <lticp:url>http://www.chemvantage.org/</lticp:url>\n" + 
				"        <lticp:contact>\n" + 
				"            <lticp:email>admin@chemvantage.org</lticp:email>\n" + 
				"        </lticp:contact>\n" + 
				"    </blti:vendor>\n" + 
				"    <cartridge_bundle identifierref=\"BLTI001_Bundle\"/>\n" + 
				"    <cartridge_icon identifierref=\"BLTI001_Icon\"/>\n" + 
				"</cartridge_basiclti_link>";
	}
}
