/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2012 ChemVantage LLC
*   
*	This servlet file is adapted from an open-source Java servlet 
*	LTIProviderServlet written by Charles Severance at 1edtech.org
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.regex.Pattern;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet(urlPatterns = {"/lti/registration","/lti/registration/"})
public class LTIRegistration extends HttpServlet {

	/* This servlet class is used to apply for and grant access to LTI connections between client
	 * LMS platforms and the ChemVantage tool. The user will complete a short form with name, role,
	 * email, organization, home page, and LMS type. and use case (testing or production).
	 * Requests for LTIv1.1 credentials were discontinued in 2021.
	 * The workflow path for LTIv1.3 requests is:
	 *   1) All users complete a basic form giving information about their org and the LTI request. If the
	 *      launch uses Dynamic Registration, this information is used to eliminate some of the fields. If
	 *      present, the OpenID Configuration URL and Registration Token are included in the POST to ChemVantage.
	 *   2) ChemVantage validates the registration parameters, and if necessary, displays the form again
	 *      to correct any errors.
	 *   3) The registration email contains an activation token and, if necessary, the ChemVantage endpoints 
	 *      and configuration JSON to complete the registration in the LMS. 
	 *   4) The user then clicks the tokenized link, which contains the platformDeploymentId. If necessary, a form
	 *      is presented to supply the client_id and deployment_id values and LMS endpoints. Otherwise, the 
	 *      registration is complete.
	 *      
	 * For LTI Dynamic Registration, the ChemVantage endpoint is the same, and the form still applies, but
	 * some information is automatically received (e.g., LMS product name, LTIAdvantage) so does not appear 
	 * as an option on the form. When submitted, the registration success email is sent immediately.
	 * 
	 * */
	
	private static final long serialVersionUID = 137L;
	static String price = "2";
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		
		try {
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";

			String iss = "https://" + request.getServerName();
			
			if ("config".contentEquals(userRequest)) {
				response.setContentType("application/json");
				out.println(getConfigurationJson(iss,request.getParameter("lms")));
			} else if (request.getParameter("token")!=null) {
				response.setContentType("text/html");
				String token = request.getParameter("token");
				Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
				JWT.require(algorithm).withIssuer(iss).build().verify(token);
				out.println(Subject.header("LTI Registration") + clientIdForm(token) + Subject.footer);
			} else {
				out.println(Subject.header() + registrationForm(request,null) + Subject.footer);
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
		StringBuffer debug = new StringBuffer();
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";

		String iss = "https://" + request.getServerName();
		boolean dynamicRegistration = request.getParameter("openid_configuration")!=null;
		
		try {
			if ("finalize".contentEquals(userRequest)) {				
				String token = request.getParameter("Token");
				Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
				JWT.require(algorithm).withIssuer(iss).build().verify(token);
				out.println(Subject.header("ChemVantage LTI Registration") + "<h1>ChemVantage Registration</h1>" + createDeployment(request) + Subject.footer);			
			} else {
				if (request.getParameter("email")==null) throw new Exception("Email was not given.");
				String token = validateApplicationFormContents(request);
				debug.append("Debug:0<br/>");
				if (dynamicRegistration) {
					debug.append("Token:<br/>"+token+"<br/>");
					JsonObject openIdConfiguration = getOpenIdConfiguration(request);  // LTIDRSv1p0 section 3.4
					debug.append("OpenIdConfiguration:<br/>"+openIdConfiguration.toString()+"<br/>");
					validateOpenIdConfigurationURL(request.getParameter("openid_configuration"),openIdConfiguration);  // LTIDRSv1p0 section 3.5.1
					debug.append("Valid<br/>");
					JsonObject registrationResponse = postRegistrationRequest(openIdConfiguration,request);  // LTIDRSv1p0 section 3.5.2 & 3.6
					debug.append("Registration Response:<br/>"+registrationResponse.toString()+"<br/>");
					
					Enumeration<String> enumeration = request.getParameterNames();
					while(enumeration.hasMoreElements()){
			            String parameterName = enumeration.nextElement();
			            debug.append(parameterName + ": " + request.getParameter(parameterName) + "<br/>");
			        }
					
			        Deployment d = createNewDeployment(openIdConfiguration,registrationResponse,request);
					debug.append("Deployment created: " + d.platform_deployment_id);
					if ("canvas".equals(d.lms_type) && d.getDeploymentId().isEmpty()) throw new Exception("Missing Deployment ID");
					sendApprovalEmail(d,request);
					response.setContentType("text/html");
					out.println(successfulRegistrationRequestPage(openIdConfiguration,request));
				} else {
					sendRegistrationEmail(token,request);
					out.println(Subject.header("ChemVantage LTI Registration") + "<h1>ChemVantage</h1>" + "<h2>Registration Success</h2>Thank you. A registration email has been sent to your address.<p>" + Subject.footer);			
				}
			}
		} catch (Exception e) {
			String message = (e.getMessage()==null?e.toString():e.getMessage());
			if (dynamicRegistration) {
				String emailmessage = message + "<br/>"
						+ "Name: " + request.getParameter("sub") + "<br/>"
						+ "Email: " + request.getParameter("email") + "<br/>"
						+ "Org: " + request.getParameter("aud") + "<br/>"
						+ "URL: " + request.getParameter("url") + "<br/>"
						+ "LMS: " + request.getParameter("lms") + "<br/>"
						+ debug.toString();
				Utilities.sendEmail("ChemVantage Administrator","admin@chemvantage.org","Dynamic Registration Error",emailmessage);
			}
			out.println(Subject.header() + registrationForm(request,message) + Subject.footer);
		}
	}
		
	String registrationForm(HttpServletRequest request, String message) {
		String sub = request.getParameter("sub");
		String email = request.getParameter("email");
		String aud = request.getParameter("aud");
		String url = request.getParameter("url");
		String lms = request.getParameter("lms");
		String lms_other = request.getParameter("lms_other");
		String reg_code = request.getParameter("reg_code");
		String AcceptChemVantageTOS = request.getParameter("AcceptChemVantageTOS");
		String openid_configuration = request.getParameter("openid_configuration");
		String registration_token = request.getParameter("registration_token");
		boolean dynamic = openid_configuration != null;
		
		StringBuffer buf = new StringBuffer(Subject.banner);
		
		if (message != null) {
			buf.append("<span style='color: #EE0000; border: 2px solid red'>&nbsp;" + message + " &nbsp;</span>");
		}
		
		buf.append("<h1>LTI Advantage " + (dynamic?"Dynamic ":"") + "Registration</h1>");
		
		buf.append("<form id=regform method=post action=/lti/registration>"
				+ "Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage "
				+ "that is convenient, secure and <a href=https://site.imsglobal.org/certifications/chemvantage/chemvantage>certified by 1EdTech</a>. "
				+ "When you submit the form, ChemVantage will send "
				+ (dynamic?"a back-end registration request to your LMS. If successful, you must activate the deployment in your LMS.":"a confirmation email with a tokenized link to complete the registration. ")
				+ "<br/><br/>\n");
		
		buf.append("Please tell us how to contact you if there is ever a problem with your account (see our <a href=/privacy_policy.html>Privacy Policy</a>):<br/>"
				+ "<label>Your Name: <input type=text required name=sub size=40 value='" + (sub==null?"":sub) + "' /> </label><br/>"
				+ "<label>Your Email: <input type=text required name=email size=40 value='" + (email==null?"":email) + "' /> </label><br/><br/>\n");
		
		buf.append("Please tell us about your school, business or organization:<br/>"
				+ "<label>Org Name: <input type=text required name=aud  value='" + (aud==null?"":aud) + "' /> </label><br/>\n"
				+ "<label>Home Page: <input type=text required name=url placeholder='https://myschool.edu' value='" + (url==null?"":url) + "' /></label><br/><br/>\n");
		
		if (dynamic) {
			if (registration_token!=null) buf.append("<input type=hidden name=registration_token value='" + registration_token + "' />");
			buf.append("<input type=hidden name=openid_configuration value='" + openid_configuration + "' />");
		} else {
			buf.append("<fieldset style='width:400px'><legend>Type of Learning Management System:<br/></legend>\n"
					+ "<label><input type=radio name=lms required value=blackboard " + ((lms!=null && lms.equals("blackboard"))?"checked":"") + "  />Blackboard</label><br/>\n"
					+ "<label><input type=radio name=lms required value=brightspace " + ((lms!=null && lms.equals("brightspace"))?"checked":"") + "  />Brightspace</label><br/>\n"
					+ "<label><input type=radio name=lms required value=canvas " + ((lms!=null && lms.equals("canvas"))?"checked":"") + "  />Canvas</label><br/>\n"
					+ "<label><input type=radio name=lms required value=moodle " + ((lms!=null && lms.equals("moodle"))?"checked":"") + "  />Moodle</label><br/>\n"
					+ "<label><input type=radio name=lms required value=sakai " + ((lms!=null && lms.equals("sakai"))?"checked":"") + "  />Sakai</label><br/>\n"
					+ "<label><input type=radio name=lms required value=schoology " + ((lms!=null && lms.equals("schoology"))?"checked":"") + "  />Schoology</label><br/>\n"
					+ "<label><input type=radio name=lms required id=other value=other " + ((lms!=null && lms.equals("other"))?"checked":"") + "  />Other:</label>\n"
					+ "<label><input type=text name=lms_other value='" + (lms_other==null?"":lms_other) + "' placeholder='(specify)' onFocus=document.getElementById('other').checked=true; /></label>\n"
					+ "</fieldset>\n"
					+ "<br/><br/>");
		}

		if (Subject.getProjectId().equals("dev-vantage-hrd")) {
			buf.append("Enter your registration code here: <input type=text required name=reg_code value='" + (reg_code==null?"":reg_code) + "' /><br/>"
					+ "To purchase a registration code, please contact us at admin@chemvantage.org<br/><br/>");
		} else {
			buf.append("Pricing:"
					+ "<ul>"
					+ "	<li>LTI registration and instructor accounts are free.</li>"
					+ "	<li>Each student account costs only $2 USD per month or $8 USD per semester (5 months).</li>"
					+ "	<li>Institutions can purchase 1-year student licenses for $5 USD/each (10 license minimum). Contact admin@chemvantage.org for an invoice.</li>"
					+ "</ul>\n");
		}
		buf.append("<label><input type=checkbox required name=AcceptChemVantageTOS value=true " + ((AcceptChemVantageTOS!=null && AcceptChemVantageTOS.equals("true"))?"checked":"") + " />Accept the <a href=/terms_and_conditions.html target=_blank aria-label='opens new tab'>ChemVantage Terms of Service</a></label><br/><br/>\n");

		if (!dynamic) {  // show recaptcha tool
			buf.append("<div class='g-recaptcha' data-sitekey='" + Subject.getReCaptchaSiteKey() + "' aria-label='Google Recaptcha'></div><br/><br/>"
					+ "<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>\n");
			}
		
		buf.append("<input type=submit value='Submit Registration'/>"
				+ "</form><br/><br/>");
		
		return buf.toString();
	}
	
	String validateApplicationFormContents(HttpServletRequest request) throws Exception {
		String sub = request.getParameter("sub");
		String email = request.getParameter("email");
		String aud = request.getParameter("aud");
		String url = request.getParameter("url");
		String lms = request.getParameter("lms");
		String lms_other = request.getParameter("lms_other");
		String openid_configuration = request.getParameter("openid_configuration");
		
		if (sub.isEmpty() || email.isEmpty()) throw new Exception("All form fields are required. ");
		String regex = "^[A-Za-z0-9+_.-]+@(.+)$";		 
		Pattern pattern = Pattern.compile(regex);
		if (!pattern.matcher(email).matches()) throw new Exception("Your email address was not formatted correctly. ");
		if (aud.isEmpty()) throw new Exception("Please enter your organization name.");
		if (url.isEmpty()) throw new Exception("Please enter the URL for your organization's home page.");
		
		if (!url.isEmpty() && !url.startsWith("http")) url = "https://" + url;
		try {
			new URI(url).toURL();   // throws Exception if URL is not formatted correctly
		} catch (Exception e) {
			throw new Exception("Invalid domain name (" + url + "). " + e.toString());
		}

		if (openid_configuration==null) {
			if (lms==null) throw new Exception("Please select the type of LMS that you are connecting to ChemVantage. ");
			if ("other".equals(lms) && (lms_other==null || lms_other.isEmpty())) throw new Exception("Please describe the type of LMS that you are connecting to ChemVantage. ");
			if ("other".equals(lms)) lms = lms_other;
			if (!request.getServerName().equals("localhost") && !reCaptchaOK(request)) throw new Exception("ReCaptcha tool was unverified. Please try again. ");
		}
		
		if (!"true".equals(request.getParameter("AcceptChemVantageTOS"))) throw new Exception("Please read and accept the ChemVantage Terms of Service. ");

		String iss = Subject.getProjectId().equals("dev-vantage-hrd")?"https://dev-vantage-hrd.appspot.com":"https://www.chemvantage.org";
		
		if (Subject.getProjectId().equals("dev-vantage-hrd") ) {
			String reg_code = request.getParameter("reg_code");
			Date now = new Date();
			Date exp = new Date(User.encode(Long.parseLong(reg_code,16)));
    		Date oneYearFromNow = new Date(now.getTime() + 31536000000L);
    		if (exp.before(now) || exp.after(oneYearFromNow)) throw new Exception("Registration code was invalid or expired.");
		}
		
		// Construct a new registration token
		Date now = new Date();
		Date exp = new Date(now.getTime() + 604800000L); // seven days from now
		Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
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
		
		return token;
	}
		
	boolean reCaptchaOK(HttpServletRequest request) throws Exception {
		String queryString = "secret=" + Subject.getReCaptchaSecret() + "&response=" 
				+ request.getParameter("g-recaptcha-response") + "&remoteip=" + request.getRemoteAddr();
		URL u = new URI("https://www.google.com/recaptcha/api/siteverify").toURL();
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

	void sendRegistrationEmail(String token, HttpServletRequest request) throws Exception {
		DecodedJWT jwt = JWT.decode(token);
		String name = jwt.getSubject();
		String email = jwt.getClaim("email").asString();
		String org = jwt.getAudience().get(0);
		String url = jwt.getClaim("url").asString();
		String iss = jwt.getIssuer();
		String lms = jwt.getClaim("lms").asString();
		
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>ChemVantage Registration</h2>");
		buf.append("Name: " + name + " (" + email + ")<br/>");
		buf.append("Organization: " + org + (url.isEmpty()?"":" (" + url + ")") + "<br/>");
		buf.append("LMS: " + lms + "<br/><br/>");
		
		buf.append("Thank you for your ChemVantage registration request.<p>");
		
		if (iss.contains("dev-vantage-hrd.appspot.com")) {
			buf.append("ChemVantage is pleased to provide access to our software development server for testing LTI connections. "
					+ "Please note that the server is sometimes in an unstable state, and accounts may be reset or even deleted at any time. ");
		} else {
			buf.append("<h3>Getting Started</h3>"
					+ "When you complete the registration steps below, your account will be activated immediately. You may create ChemVantage "
					+ "assignments using Deep Linking to explore and customize placement exams, SmartText assignment, quizzes, homework, practice exams, "
					+ "video lectures, and in-class polls. In order to access the assignments, students must subscribe to the ChemVantage service "
					+ "for $" + price + ".00 USD per month (1 month minimum). As a reminder, access to ChemVantage by instructors and LMS account "
					+ "administrators is always free. ");
		}
		
		buf.append("If you have questions or require assistance, please contact us at admin@chemvantage.org.");

		buf.append("<h3>Complete the LTI Advantage Registration Process</h3>");
		buf.append("The next step is to enter the ChemVantage configuration details into your LMS. "
				+ "This will enable your LMS to communicate securely with ChemVantage. Normally, "
				+ "you must have administrator privileges in your LMS in order to do this. "
				+ "If you are NOT the LMS administrator, please stop here and forward this message "
				+ "to an administrator with a request to complete the registration process. The "
				+ "registration link below will be active for 7 days and expires at " + jwt.getExpiresAt() + ".<p>"
				+ "<hr>"
				+ "<br>To the LMS Administrator:<p>"
				+ "ChemVantage is an Open Education Resource for General Chemistry. Learn more about ChemVantage "
				+ "<a href=https://www.chemvantage.org>here</a>.<p>"
				+ "Follow the detailed instructions for installing ChemVantage in your LMS at "
				+ "<a href=https://www.chemvantage.org/install.html>https://www.chemvantage.org/install.html</a>.");
		
		buf.append("<h3>After you finish installing ChemVantage in your LMS</h3>"
				+ "The installation instructions MAY ask you to click the tokenized link below "
				+ "to complete the registration by providing the Client ID, Deployment ID and/or "
				+ "configuration URLs to ChemVantage. If the link expires, click <a href=" + iss + "/lti/registration>here</a> to get anther one.<p>");
		
		buf.append("<a href=" + iss + "/lti/registration?UserRequest=final&token=" + token + ">"
				+ iss + "/lti/registration?UserRequest=final&token=" + token + "</a><br/><br/>");

		buf.append("If you need additional assistance, please contact me at admin@chemvantage.org. <p>"
				+ "-Chuck Wight");

		Utilities.sendEmail(name,email,"ChemVantage LTI Registration",buf.toString());
	}

	String clientIdForm(String token) {
		StringBuffer buf = new StringBuffer("<h1>ChemVantage Registration</h1>");
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
			String client_id = "";
			String deployment_id = "";
			String platform_id = "";
			String oidc_auth_url = "";
			String oauth_access_token_url = "";
			String well_known_jwks_url = "";
			
			buf.append("<h2>To the LMS Administrator:</h2>"
					+ "By now you should have configured your LMS to connect with ChemVantage, and you should have "
					+ "received a Client ID and Deployment ID from your LMS. Please enter these below, along with the "
					+ "secure URLs (https://) that identify the service endpoints for your LMS. In some cases, these are "
					+ "provided below, but you may need to edit them for your specific situation.<p>"
					+ "<form method=post action=/lti/registration>"
					+ "<input type=hidden name=UserRequest value='finalize'>"
					+ "<input type=hidden name=Token value='" + token + "'>");
			
			switch (lms) {
			case "blackboard":
				client_id = (iss.equals("https://dev-vantage-hrd.appspot.com")?"ec076e8c-b90f-4ecf-9b5d-a9eff03976be":"be1004de-6f8e-45b9-aae4-2c1370c24e1e");
				platform_id = "https://blackboard.com";
				oidc_auth_url = "https://developer.blackboard.com/api/v1/gateway/oidcauth";
				well_known_jwks_url = "https://developer.blackboard.com/api/v1/management/applications/" + client_id + "/jwks.json";
				oauth_access_token_url = "https://developer.blackboard.com/api/v1/gateway/oauth2/jwttoken";
				break;
			case "schoology":
				client_id = (iss.equals("https://www.chemvantage.org")?"6558245496":"");
				platform_id = "https://schoology.schoology.com";
				oidc_auth_url = "https://lti-service.svc.schoology.com/lti-service/authorize-redirect";
				well_known_jwks_url = "https://lti-service.svc.schoology.com/lti-service/.well-known/jwks";
				oauth_access_token_url = "https://lti-service.svc.schoology.com/lti-service/access-token";
				
				buf.append("The Schoology admin can get the Deployment ID value for ChemVantage by clicking the "
						+ "Apps icon > App Center > " + (iss.equals("https://www.chemvantage.org")?"Organization Apps. ":"My Developer Apps. ") 
						+ "Find ChemVantage and select Configure. The Deployment ID should be two large (~10-digit) numbers separated by a hyphen. "
						+ (iss.equals("https://dev-vantage-hrd.appspot.com")?"The Client ID value is the first 10-digit number in the Deployment ID.":"") 
						+ "<p>");
				break;
			case "canvas":
				platform_id = "https://canvas.instructure.com";
				oidc_auth_url = "https://sso.canvaslms.com/api/lti/authorize_redirect";
				well_known_jwks_url = "https://sso.canvaslms.com/api/lti/security/jwks";
				oauth_access_token_url = "https://sso.canvaslms.com/login/oauth2/token";
				
				buf.append("Canvas uses the developer key as the Client ID, so enter that value from the list of "
						+ "developer keys. It is a numeric value that looks something like 32570000000000041.<br/>"
						+ "The Deployment ID can be found in Settings | Apps | App Configurations by opening the "
						+ "settings menu for ChemVantage. It is a compound value that consists of a number and a hex string "
						+ "separated by a colon and looks something like 10408:7db438070728c02373713c12c73869b3af470b68.<p>");
				break;
			case "LTI Certification":
			case "1EdTech Certification":
				platform_id = "https://ltiadvantagevalidator.imsglobal.org";
				oidc_auth_url = "https://ltiadvantagevalidator.imsglobal.org/ltitool/oidcauthurl.html";
				well_known_jwks_url = "https://oauth2server.imsglobal.org/jwks";
				oauth_access_token_url = "https://ltiadvantagevalidator.imsglobal.org/ltitool/authcodejwt.html";
				deployment_id = "testdeploy";
				break;
			default:
			}
			
			buf.append("Client ID: <input type=text size=40 required name=ClientId value='" + client_id + "' /><br>"
					+ "Deployment ID: <input type=text size=40 required name=DeploymentId value='" + deployment_id + "' /><br>"
					+ "Platform ID: <input type=text size=40 required name=PlatformId value='" + platform_id + "' /> (base URL for your LMS)<br>"
					+ "Platform OIDC Auth URL: <input type=text size=40 required name=OIDCAuthUrl value='" + oidc_auth_url + "' /><br>"
					+ "Platform OAuth Access Token URL: <input type=text size=40 required name=OauthAccessTokenUrl value='" + oauth_access_token_url + "' /><br>"
					+ "Platform JSON Web Key Set URL: <input type=text size=40 required name=JWKSUrl value='" + well_known_jwks_url + "' /><br>");

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
					+ "may <a href=/lti/registration>get a new token</a> by restarting the registration, or contact "
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
		String deployment_id = request.getParameter("DeploymentId");
		String platform_id = request.getParameter("PlatformId");
		String oidc_auth_url = request.getParameter("OIDCAuthUrl");
		String oauth_access_token_url = request.getParameter("OauthAccessTokenUrl");
		String well_known_jwks_url = request.getParameter("JWKSUrl");
		
		if (client_id==null) throw new Exception("Client ID value is required.");
		//if (deployment_id==null) throw new Exception("Deployment ID value is required.");
		if (platform_id==null || platform_id.isEmpty()) throw new Exception("Platform ID value is required.");
		if (oidc_auth_url==null || oidc_auth_url.isEmpty()) throw new Exception("OIDC Auth URL is required.");
		if (oauth_access_token_url==null || oauth_access_token_url.isEmpty()) throw new Exception("OAuth Access Token URL is required.");
		if (well_known_jwks_url==null || well_known_jwks_url.isEmpty()) throw new Exception("JSON Web Key Set URL is required.");
		
		if (deployment_id != null) {
			Deployment d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,client_name,email,organization,org_url,lms);
			d.status = "pending";
			d.price = Subject.getProjectId().equals("dev-vantage-hrd")?0:Integer.parseInt(price);

			Deployment prior = Deployment.getInstance(d.platform_deployment_id);

			String msg = "<h2>Congratulations. Registration is complete.</h2>"
					+ "<br/><br/>Contact Chuck Wight at admin@chemvantage.org for support with any questions or issues.<br/><br/>Thank you.";

			if (prior!=null) {  // this is a repeat registration
				d.status = prior.status==null?"pending":prior.status;
				if (prior.client_id.equals(d.client_id)) msg += "Note: this platform deployment was registered previously. The registration data have now been updated.<p>";
				else msg += "<p>Note: This platform deployment was registered previously. The client_id and registration data have now been updated. If this is not correct, you should contact admin@chemvantage.org immediately.<p>";
			}

			ofy().save().entity(d).now();  // registration is now complete
			return msg;
		} else {  // this path is used by Canvas, which doesn't send the deployment_id until the first launch
			ProvisionalDeployment pd = new ProvisionalDeployment(platform_id,client_id,client_name,email,organization,org_url);
			String msg = "<h2>Congratulations. Registration was successful.</h2>"
					+ "You should now create a deployment (developer key) using the client_id " + client_id + ". The deployment_id will be sent to ChemVantage automatically "
					+ "with the first launch. If you have a sandbox course, please create a test assignment to be sure that everything is working.<p>"
					+ "Contact Chuck Wight at admin@chemvantage.org for support with any questions or issues.<br/><br/>Thank you.";
			ofy().save().entity(pd).now();
			return msg;
		}
	}

	String getConfigurationJson(String iss,String lms) {
		String domain = null;
		try {
			domain = new URI(iss).toURL().getHost();
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
		      settings.addProperty("icon_url", iss + "/images/CVLogo_thumb.png");
		        JsonArray placements = new JsonArray();
		         JsonObject plcmnt1 = new JsonObject();
		          plcmnt1.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt1.addProperty("enabled", true);
		          plcmnt1.addProperty("icon_url", iss + "/images/CVLogo_thumb.png");
		          plcmnt1.addProperty("placement", "assignment_selection");
		          plcmnt1.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt1.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt1);
		         JsonObject plcmnt2 = new JsonObject();
		          plcmnt2.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt2.addProperty("enabled", true);
		          plcmnt2.addProperty("icon_url", iss + "/images/CVLogo_thumb.png");
		          plcmnt2.addProperty("placement", "editor_button");
		          plcmnt2.addProperty("message_type", "LtiDeepLinkingRequest");
		          plcmnt2.addProperty("target_link_uri", iss + "/lti/deeplinks");
		        placements.add(plcmnt2);
		         JsonObject plcmnt3 = new JsonObject();
		          plcmnt3.addProperty("text", "ChemVantage" + (iss.contains("dev")?" Development":""));
		          plcmnt3.addProperty("enabled", true);
		          plcmnt3.addProperty("icon_url", iss + "/images/CVLogo_thumb.png");
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
	
	JsonObject getOpenIdConfiguration(HttpServletRequest request) throws Exception {
	 	// This method retrieves the OpenID Configuration from the platform for Dynamic Registration
    	try {
    		URL u = new URI(request.getParameter("openid_configuration")).toURL();
    		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
    		uc.setDoInput(true);
    		uc.setRequestMethod("GET");
    		uc.connect();
    		int responseCode = uc.getResponseCode();
    		if (responseCode == 200) {
    			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
    			JsonObject openIdConfiguration = JsonParser.parseReader(reader).getAsJsonObject();
    			reader.close();
    			return openIdConfiguration;
    		} else throw new Exception("Platform returned response code " + responseCode);
    	} catch (Exception e) {
    		throw new Exception("Failed to retrieve OpenID Configuration from platform: " + e.getMessage());
    	}
    }
	
	void validateOpenIdConfigurationURL(String openIdConfigurationURL, JsonObject openIdConfiguration) throws Exception {
		try {
			URL issuer = new URI(openIdConfiguration.get("issuer").getAsString()).toURL();
			URL config = new URI(openIdConfigurationURL).toURL();
			if (!issuer.getProtocol().contains("https")) throw new Exception("Issuer protocol must be https:// ");
			if (!config.getProtocol().contains("https")) throw new Exception("OpenID configuration URL protocol must be https:// ");
			if (!issuer.getHost().equals(config.getHost())) throw new Exception("Host names of issuer and openid_configuration URL must match. ");
			if (config.getRef() != null) throw new Exception("OpenID configuration URL must not contain any fragmant parameter. ");
		} catch (Exception e) {
			throw new Exception("Invalid openid_configuration from " + openIdConfigurationURL + ": " + e.getMessage());
		}		
	}
	
	JsonObject postRegistrationRequest(JsonObject openIdConfiguration,HttpServletRequest request) throws Exception {
		
		String registrationToken = request.getParameter("registration_token");
		StringBuffer registrationResponseBuffer = new StringBuffer();
		JsonObject registrationResponse = new JsonObject();;
		JsonObject regJson = new JsonObject();
		StringBuffer debug = new StringBuffer("a");
		try {
		regJson.addProperty("application_type","web");
		JsonArray grantTypes = new JsonArray();
		grantTypes.add("implicit");
		grantTypes.add("client_credentials");
		regJson.add("grant_types", grantTypes);
		JsonArray responseTypes = new JsonArray();
		responseTypes.add("id_token");
		regJson.add("response_types", responseTypes);
		String projectId = Subject.getProjectId();
		String iss = null;
		String domain = null;
		switch (projectId) {
		case "dev-vantage-hrd":
			iss = "https://dev-vantage-hrd.appspot.com";
			domain = "dev-vantage-hrd.appspot.com";
			break;
		case "chem-vantage-hrd":
			iss = "https://www.chemvantage.org";
			domain = "chemvantage.org";
		}
		JsonArray redirectUris = new JsonArray();
		redirectUris.add(iss + "/lti/launch");
		redirectUris.add(iss + "/lti/deeplinks");
		regJson.add("redirect_uris", redirectUris);
		regJson.addProperty("initiate_login_uri", iss + "/auth/token");
		regJson.addProperty("client_name", "ChemVantage" + (iss.contains("dev-vantage")?" Development":""));
		regJson.addProperty("jwks_uri", iss + "/jwks");
		regJson.addProperty("logo_uri", iss + "/images/CVLogo_thumb.png");
		regJson.addProperty("token_endpoint_auth_method", "private_key_jwt");
		JsonArray contactEmails = new JsonArray();
		contactEmails.add("admin@chemvantage.org");
		regJson.add("contacts", contactEmails);		
		regJson.addProperty("client_uri", iss);
		regJson.addProperty("tos_uri", iss + "/terms_and_conditions.html");
		regJson.addProperty("policy_uri", iss + "/privacy_policy.html");
		regJson.addProperty("scope", "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem https://purl.imsglobal.org/spec/lti-ags/scope/lineitem.readonly https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly https://purl.imsglobal.org/spec/lti-ags/scope/score https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly");
		JsonObject ltiToolConfig = new JsonObject();
		ltiToolConfig.addProperty("domain", domain);
		ltiToolConfig.addProperty("description",  "ChemVantage is an Open Education Resource for General Chemistry.");
		ltiToolConfig.addProperty("target_link_uri", iss + "/lti/launch");
		JsonArray idTokenClaims = new JsonArray();
		idTokenClaims.add("iss");
		idTokenClaims.add("sub");
		idTokenClaims.add("email");
		idTokenClaims.add("name");
		idTokenClaims.add("given_name");
		idTokenClaims.add("family_name");
		ltiToolConfig.add("claims", idTokenClaims);
		debug.append("b");
		JsonArray ltiMessages = new JsonArray();
		JsonObject deepLinking = new JsonObject();
		deepLinking.addProperty("type",  "LtiDeepLinkingRequest");
		deepLinking.addProperty("target_link_uri", iss + "/lti/deeplinks");
		deepLinking.addProperty("label", "ChemVantage" + (iss.contains("dev-vantage")?" Development":""));
		debug.append("c");
		try {
			JsonArray messagesSupported = openIdConfiguration.get("https://purl.imsglobal.org/spec/lti-platform-configuration").getAsJsonObject().get("messages_supported").getAsJsonArray();
			Iterator<JsonElement> iterator = messagesSupported.iterator();
			JsonObject message;
			while (iterator.hasNext()) {
				message = iterator.next().getAsJsonObject();
				if ("LtiDeepLinkingRequest".equals(message.get("type").getAsString()) && message.get("placements")!=null) {
					deepLinking.add("placements", message.get("placements").getAsJsonArray());
					break;
				};
			}
		} catch (Exception e) {
		}	
		ltiMessages.add(deepLinking);
		debug.append("d");
		JsonObject resourceLaunch = new JsonObject();
		resourceLaunch.addProperty("type",  "LtiResourceLinkRequest");
		resourceLaunch.addProperty("target_link_uri", iss + "/lti/launch");
		resourceLaunch.addProperty("label", "ChemVantage" + (iss.contains("dev-vantage")?" Development":""));
		debug.append("e");
		try {
			JsonArray messagesSupported = openIdConfiguration.get("https://purl.imsglobal.org/spec/lti-platform-configuration").getAsJsonObject().get("messages_supported").getAsJsonArray();
			Iterator<JsonElement> iterator = messagesSupported.iterator();
			JsonObject message;
			while (iterator.hasNext()) {
				message = iterator.next().getAsJsonObject();
				if ("LtiResourceLinkRequest".equals(message.get("type").getAsString()) && message.get("placements")!=null) {
					resourceLaunch.add("placements", message.get("placements").getAsJsonArray());
					break;
				};
			}
		} catch (Exception e) {
		}
		ltiMessages.add(resourceLaunch);
		debug.append("f");
		ltiToolConfig.add("messages", ltiMessages);
		regJson.add("https://purl.imsglobal.org/spec/lti-tool-configuration", ltiToolConfig);
		byte[] json_bytes = regJson.toString().getBytes("utf-8");

		String reg_endpoint = openIdConfiguration.get("registration_endpoint").getAsString();
		debug.append("b");

		URL u = new URI(reg_endpoint).toURL();
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setRequestMethod("POST");
		if (registrationToken != null) uc.setRequestProperty("Authorization", "Bearer " + registrationToken);
		uc.setRequestProperty("Content-Type", "application/json");
		uc.setRequestProperty("Content-Length", String.valueOf(json_bytes.length));
		uc.setRequestProperty("Accept", "application/json");

		try {
			switch (openIdConfiguration.get("https://purl.imsglobal.org/spec/lti-platform-configuration").getAsJsonObject().get("product_family_code").getAsString()) {
			case "moodle": 
				if (iss.equals("https://www.chemvantage.org")) uc.setRequestProperty("Host", "www.chemvantage.org"); // prevents code 400 failure in Moodle due to getRemoteHost()->chem-vantage-hrd.appspot.com
				break;
			default:
			}
		} catch (Exception e) {}

		uc.setDoOutput(true);
		uc.setDoInput(true);
		debug.append("c");

		// send the message
		OutputStream os = uc.getOutputStream();
		os.write(json_bytes, 0, json_bytes.length);           
		os.close();
		debug.append("d");

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			debug.append("e");
			registrationResponse = JsonParser.parseReader(reader).getAsJsonObject();
			debug.append("f");
		} catch (Exception e) {
			debug.append("g ");
			reader = new BufferedReader(new InputStreamReader(uc.getErrorStream()));
			String line = "";
			while ((line = reader.readLine()) != null) registrationResponseBuffer.append(line);
			debug.append(line);
		}
		if (reader != null) reader.close();
		debug.append("i");
		if (uc.getResponseCode() >= 400) throw new Exception("Platform refused registration request with code " + uc.getResponseCode() + ":<br/>" + registrationResponseBuffer.toString());
		} catch (Exception e) {
			throw new Exception(e.getMessage() + "<br/>Debug: " + debug.toString());
		}
		return registrationResponse;
	}
	
	Deployment createNewDeployment(JsonObject openIdConfiguration, JsonObject registrationResponse, HttpServletRequest request) throws Exception {
		try {
			String platformId = openIdConfiguration.get("issuer").getAsString();
			String clientId = registrationResponse.get("client_id").getAsString();
			String oidc_auth_url = openIdConfiguration.get("authorization_endpoint").getAsString();
			String oauth_access_token_url = openIdConfiguration.get("token_endpoint").getAsString();
			String well_known_jwks_url = openIdConfiguration.get("jwks_uri").getAsString();
			
			String lms = "unknown";
			try {
				lms = openIdConfiguration.get("https://purl.imsglobal.org/spec/lti-platform-configuration").getAsJsonObject().get("product_family_code").getAsString();
			} catch (Exception e) {	
				Utilities.sendEmail("ChemVantage Administrator","admin@chemvantage.org","Dynamic Registration Error: LMS Type Unknown",openIdConfiguration.toString());
			}

			String contact_name = request.getParameter("sub");
			String contact_email = request.getParameter("email");
			String organization = request.getParameter("aud");
			String org_url = request.getParameter("url");
			
			String deploymentId = "";  // Most LMS platforms send the deployment_id in the registration response, but it's not required. Thanks, Brightspace.
			try {
				deploymentId = registrationResponse.get("https://purl.imsglobal.org/spec/lti-tool-configuration").getAsJsonObject().get("deployment_id").getAsString();
			} catch (Exception e) {}
			
			Deployment d = new Deployment(platformId,deploymentId,clientId,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,contact_name,contact_email,organization,org_url,lms);
			d.status = "pending";
			d.price = Integer.parseInt(price);

			ofy().save().entity(d);
			return d;
		} catch (Exception e) {
			throw new Exception("Failed to create new deployment in ChemVantage: " + e.toString() + "<br/>OpenId Configuration: " + openIdConfiguration.toString() + "<br/>Registration Response: " + registrationResponse.toString());
		}
	}
	
	String successfulRegistrationRequestPage(JsonObject openid_configuration, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		buf.append(Subject.header() + "<h1>ChemVantage</h1>");
		buf.append("<h2>Your Registration Request Was Successful</h2>"
				+ "The LTI Advantage deployment was created in ChemVantage and in your LMS.<br/>"
				+ "Please be sure to activate the deployment in your LMS.<br/><br/>");

		if (request.getServerName().contains("dev-vantage-hrd.appspot.com")) {
			buf.append("ChemVantage is pleased to provide access to our software development server for testing LTI connections. "
					+ "Please note that the server is sometimes in an unstable state, and accounts may be reset or even deleted at any time.<br/><br/>");
		} else {
			buf.append("Your ChemVantage has been fully activated and provisioned with 1 free student license for testing. Each unique student "
					+ "login will use one license. You may purchase additional licenses in bulk directly from ChemVantage at a discount. "
					+ "Otherwise, ChemVantage will charge each student a subsciption price of $" + price + ".00 USD per month to access the assignments. "
					+ "As a reminder, access to ChemVantage by instructors and LMS account administrators is always free.<br/><br/>");
		}
		
		buf.append("<a href=# onclick=\"(window.opener || window.parent).postMessage({subject:'org.imsglobal.lti.close'},'*');\">Click here to close this window.</a>");

		String lms = null;
		try {
			lms = openid_configuration.get("https://purl.imsglobal.org/spec/lti-platform-configuration").getAsJsonObject().get("product_family_code").getAsString();
		} catch (Exception e) {}
		
		switch (lms) {
		case "moodle":
			buf.append("<h2>For the Instructor</h2>"
					+ "To add ChemVantage assignments to your course, turn editing ON and:<ol>"
					+ "<li>Click 'Add an activity or resource'</li>"
					+ "<li>Click 'External Tool'</li>"
					+ "<li>Select ChemVantage from preconfigured tools and click 'Select content'</li>"
					+ "<li>Choose one or more ChemVantage assignments, click 'Submit' and then 'Continue'</li>"
					+ "</ol>");
			break;
		case "canvas":
			buf.append("<h2>For the Instructor</h2>"
					+ "To add ChemVantage assignments to your course, go to the Assignments page:<ol>"
					+ "<li>Click the red '+ Assignment' button</li>"
					+ "<li>For Submission Type select 'External Tool'</li>"
					+ "<li>Click Find and select ChemVantage from preconfigured tools</li>"
					+ "<li>Choose a ChemVantage assignment and topic, then click 'Create this assignment' and then 'Select'</li>"
					+ "<li>Click 'Save' or 'Save and Publish'</li>"
					+ "</ol>");
			break;
		default:
			buf.append("<h2>For the Course Instructor:</h2>"
					+ "Although we do not have specific instructions for how to add a ChemVantage assignment to your course in " + lms + ", "
					+ "in general you should navigate to your course page and<ol>"
					+ "<li>Add a new assignment, content or resource</li>"
					+ "<li>Select ChemVantage from a list of preconfigured tools</li>"
					+ "<li>Select one or more ChemVantage assignments to add</li>"
					+ "<li>Enable grading. Recommended points is 10 for quizzes or homework, 100 for practice exams.</li>"
					+ "</ol>");	
		}
		buf.append(	"If you need assistance, contact us at admin@chemvantage.org");
		
		buf.append(Subject.footer);
		return buf.toString();
	}
	
	static void sendApprovalEmail(Deployment d, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String project_id = Subject.getProjectId();
		String iss = null;
		switch (project_id) {
		case "dev-vantage-hrd":
			iss = "https://dev-vantage-hrd.appspot.com";
			break;
		case "chem-vantage-hrd":
			iss = "https://www.chemvantage.org";
		}
		
		buf.append("<h2>ChemVantage Registration Success</h2>"
				+ "Congratulations! Your LTI registration has been completed:<br/>"
				+ "Tool URL: " + iss + "<br/>"
				+ "LMS Platform: " + d.getPlatformId() + "<br/>"
				+ "Deployment ID: " + d.getDeploymentId() + "<br/>"
				+ "Client ID: " + d.client_id + "<br/><br/>");
		
		if (iss.contains("dev-vantage-hrd.appspot.com")) {
			buf.append("ChemVantage is pleased to provide free access to our software development server for testing LTI connections. "
					+ "Please note that the server is sometimes in an unstable state, and accounts may be reset or even deleted at any time. ");
		} else {
			buf.append("<h3>Getting Started</h3>"
					+ "Your ChemVantage account is now active, and you may create new placement exams and assignments, or just expolore the "
					+ "site without limitations. "
					+ "Students must purchase a ChemVantage subscription for $" + price + ".00 USD per month to access the assignments. "
					+ "As a reminder, access to ChemVantage by instructors and LMS account administrators is always free.");
		}
		
		buf.append("<h3>Helpful Hints</h3>"
				+ "ChemVantage supports two types of LTI launches from your LMS:<ol>"
				+ "<li>Deep Linking - used by the instructor, course designer or administrator to select ChemVantage assignments.</li>"
				+ "<li>Resource Link - used by students to launch an existing assignment.</li>"
				+ "</ol>"
				+ "You should configure the ChemVantage placements in your LMS with the appropriate locations and permissions.<br/><br/>");
		
		switch (d.lms_type) {
		case "canvas":
			buf.append("To the Course Instructor:<ol>"
					+ "<li>Create a new Canvas assignment with the following recommended parameters:" 
					+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)</li>"
					+ " <li>Points: 10 for quiz or homework; 5 for video; 100 for practice exam</li>"
					+ " <li>Submission Type: External Tool</li>"
					+ " <li>External Tool URL: Find ChemVantage or enter " + iss + "/lti/launch</li>"
					+ " <li>Save or Save and Publish</li>"
					+ "</ul></li>"
					+ "<li>When you launch the assignment, you may use the highlighted link to customize it for your class.</li>"
					+ "</ol>");
			break;
		case "blackboard":
			buf.append("To the Course Instructor:");
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
		case "moodle":
			buf.append("To the Course Instructor:<ol>"
					+ "<li>On your course page, turn editing on and click 'Add an activity or resource'</li>"
					+ "<li>Click 'External Tool'</li>"
					+ "<li>Select ChemVantage from preconfigured tools and click 'Select content'</li>"
					+ "<li>Choose one or more ChemVantage assignments, click 'Submit' and then 'Continue'</li>"
					+ "</ol>");
			break;
		default:
			buf.append("To the Course Instructor:<br>"
					+ "Although we do not have specific instructions for how to add a ChemVantage assignment to your course in " + d.lms_type + ", "
					+ "in general you should navigate to your course page and<ol>"
					+ "<li>Add a new assignment, content or resource</li>"
					+ "<li>Select ChemVantage from a list of preconfigured tools</li>"
					+ "<li>Select one or more ChemVantage assignments to add</li>"
					+ "<li>Enable grading. Recommended points is 10 for quizzes or homework, 100 for practice exams.</li>"
					+ "</ol>");	
		}
		buf.append("If you need additional assistance, please contact us at admin@chemvantage.org<br/>Thank you.");
		
		try {
			Utilities.sendEmail(d.contact_name,d.email,"ChemVantage Registration",buf.toString());
		} catch (Exception e) {
		}
	}
}
