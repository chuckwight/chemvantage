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
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet(urlPatterns = {"/lti/registration","/lti/registration/"})
public class LTIRegistration extends HttpServlet {

	Map<String,String> sharedSecrets = new HashMap<String,String>();
	private static final long serialVersionUID = 137L;
	// the following string constants are associated with the ChemVantage Tool Profile and other
	// constants needed to construct a valid Tool Proxy
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	String banner = "<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
			+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT>"
			+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>";
			
	String welcomeMessage = "<h2>Learning Management System Integration</h2>"
			+ "<a href=http://imscert.org><img alt='IMS Global Certified' style='border-width:0' align=left hspace=10 vspace=5 "
			+ "src='/images/imscertifiedfinalsmall.png'/></a> ChemVantage is certified by the "
			+ "<a href=http://imsglobal.org>IMS Global Learning Consortium</a> to be conformant with the "
			+ "LTI v1.0, v1.1 and Outcomes Service 1.X standards for learning tools interoperability. The IMS registration "
			+ "number for ChemVantage is <a href=https://site.imsglobal.org/certifications/chemvantage/36981/chemvantage>IMSB2C2C3B2ce2019W1</a>.<p>"
			+ "This means that you can configure <a href=https://site.imsglobal.org/certifications/chemvantage/36981/chemvantage/180501/compatibility>"
			+ "compatible learning management systems</a> with an LTI link to ChemVantage.  This allows your LMS to establish "
			+ "and maintain ChemVantage accounts automatically, without having to maintain separate usernames and "
			+ "passwords.<p>"
			+ "It's important to verify that your LMS supports the LTI Outcomes Service so ChemVantage will report the assignment scores "
			+ "back to the LMS grade book. This is important because ChemVantage does not collect any personally identifiable information from users, "
			+ "so the Outcomes Service is the only way to track student scores.<p>"
			+ "ChemVantage supports two different LTI protocols for connecting to your LMS. "
			+ "Please choose the version of LTI supported by your learning management system:<p>"
			+ "<a href=/lti/registration?lti_version=1p1>LTI version 1.1</a> or "
			+ "<a href=/lti/registration?lti_version=1p3>LTI version 1.3</a><p>";
			
	String instructions = "<h3>Implementation in Canvas (other LMS platforms may be similar)</h3>"
			+ "<ol>"
			+ "<li>Obtain a set of LTI credentials (see above)."
			+ "<li>Login to Canvas as a course Instructor, navigate to Settings, select the Apps tab, and click View App Configurations. "
			+ "Then click the red +App button and complete the following fields to create a new External Tool:"
			+ "<ul><li>Configuration Type: By URL"
			+ "<li>Name: ChemVantage"
			+ "<li>Consumer Key and Shared Secret: (cut/paste values from step 1; do not include any blank spaces)"
			+ "<li>Config URL: https://www.chemvantage.org/lti_config.xml"
			+ "<li>Submit"
			+ "</ul>"
			+ "Alternatively, you may create the app manually:"
			+ "<ul><li>Configuration Type: Manual Entry"
			+ "<li>Name: ChemVantage"
			+ "<li>Consumer Key and Shared Secret: (cut/paste values from step 1; do not include any blank spaces)"
			+ "<li>Launch URL: https://www.chemvantage.org/lti/"
			+ "<li>Domain: (leave blank)"
			+ "<li>Privacy: Anonymous"
			+ "<li>Custom Fields: (leave blank)"
			+ "<li>Description: ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry."
			+ "<li>Submit"
			+ "</ul>"
			+ "<li>Create a new Canvas assignment with the following recommended parameters:"
			+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)"
			+ "<li>Points: 10 for quiz or homework; 100 for practice exam"
			+ "<li>Submission Type: External Tool"
			+ "<li>External Tool URL: https://www.chemvantage.org/lti/ (or select ChemVantage from the list of installed apps)</ul>"
			+ "<li>After you update the assignment, you should see the ChemVantage Assignment Setup Page (if not, click the "
			+ "assignment link to connect to ChemVantage). Select the appropriate ChemVantage Quiz, Homework or Practice Exam for the assignment."
			+ "<li>On the ChemVantage quiz or homework page, the instructor will find a link to customizing the assignment for that "
			+ "class by selecting/deselecting question items to be presented to students. Instructors may also contribute new questions "
			+ "that will be reviewed by a ChemVantage editor before being incorporated into the ChemVantage database."
			+ "<li>Important! Navigate to Settings, Student View to take the assignment and ensure that the score is posted correctly in the Canvas grade book. "
			+ "ChemVantage does not collect any personally identifiable information (PII) from students, so returning scores to the Canvas grade book is the "
			+ "ONLY way to ensure that students get credit for their work."
			+ "</ol>"
			+ "<h3>Use these links to find general instructions for installing external tools with LTI:</h3>"
			+ "<a href=https://docs.moodle.org/22/en/External_tool_settings>Moodle</a><p>"
			+ "<a href=http://library.blackboard.com/ref/df5b20ed-ce8d-4428-a595-a0091b23dda3/Content/_admin_app_system/admin_app_basic_lti_tool_providers.htm>Blackboard</a><p>"
			+ "<a href=https://www.eduappcenter.com/tutorials/sakai>Sakai</a><p>"
			+ "<a href=https://community.brightspace.com/resources/additional_resources/scenarios/yourfirsttoolintegrationwithbasiclearningtoolsinteroperability>Desire2Learn</a><p>";
			
	String successMessage = "<h2>Thank You</h2> Your LTI credentials have been sent to your email address.";
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		String sig = request.getParameter("sig");
		if (sig != null) { // this is a Json configuration URL request
			try {
				Deployment d = ofy().load().type(Deployment.class).filter("client_id",sig).first().safe();
				JsonObject json = getConfigJson(d,request);
				out.println(new GsonBuilder().create().toJson(json));
				return;
			} catch (Exception e) {
				response.sendError(401,"The registration request was not valid.");
				return;
			}
		}
		String userRequest = request.getParameter("UserRequest");
		
		StringBuffer buf = new StringBuffer(Home.header + banner);
		if ("Finalize".equals(userRequest)) {
			buf.append("<h3>Finalize ChemVantage Registration</h3>");
			// validate the registration JWT
			try {
				String token = request.getParameter("Token");
				Deployment d = validateToken(token);
				
				
				buf.append("To the LMS Administrator:<p>");
				buf.append("There are three options to enter the ChemVantage configuration details into "
						+ "your LMS:<ol>"
						+ "<li>Copy/paste the Configuration JSON URL below into your LMS; or"
						+ "<li>Copy/paste the entire Configuration JSON into your LMS; or"
						+ "<li>Use the text in the Configuration JSON to input the values manually."
						+ "</ol>");
				buf.append("When the registration in your LMS is complete, it should generate values for "
						+ "the LTI client_id<br>and (optionally) a deployment_id, which is a local identifier "
						+ "for your LMS account. You must submit<br>at least the client_id in the form below "
						+ "to complete the registration process in ChemVantage.<p>");
				buf.append("<form action=/lti/registration method=post>");
				buf.append("<input type=hidden name=platform_deployment_id value='" + d.platform_deployment_id + "'>");
				buf.append("<input type=hidden name=signature value='" + JWT.decode(token).getSignature() + "'>");
				buf.append("Client ID: <input type=text name=ClientId size=20> (required)<br>");
				buf.append("Deployment ID: <input type=text name=DeploymentId size=20> (recommended)<br>");
				buf.append("<input type=submit name=UserRequest value='Submit Final'></form>");
				
				buf.append("<h4>Configuration JSON URL</h4>");
				String url = "https://" + request.getServerName() + "/lti/registration?sig=" + JWT.decode(token).getSignature();
				buf.append("<a href=" + url +  ">" + url + "</a>");
				
				buf.append("<h4>Configuration JSON</h4>");
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				JsonObject json = getConfigJson(d,request);
				if (json==null) throw new Exception();
				buf.append("<pre>" + gson.toJson(json) + "</pre>");	

				buf.append(Home.footer);
				
				
			} catch (Exception e) {
				response.sendError(401, "Sorry, this one-time URL is either invalid or has expired or has been activated previously. " + e.toString());
			}
			out.println(buf.toString());
			return;
		}
		
		buf.append(welcomeMessage);
		
		if ("1p1".equals(request.getParameter("lti_version"))) {
			buf.append("<h3>LTI version 1.1 Integration</h3>"
			+ "You may obtain a free set of LTI credentials by entering a consumer key value (any string of "
			+ "characters that uniquely identifies your LMS) along with your email address into the form below."
			+ "Your LTI credentials will be emailed to you immediately.<p>"
			+ "For further assistance, contact Chuck Wight (admin@chemvantage.org).<p>");
			buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");
			buf.append("<FORM METHOD=POST><INPUT TYPE=HIDDEN NAME=lti_version VALUE=1p1><TABLE>");
			buf.append("<TR><TD ALIGN=RIGHT>Email Address: </TD><TD><INPUT TYPE=TEXT NAME=Email> (where the credentials will be sent)</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Consumer Key: </TD><TD><INPUT TYPE=TEXT NAME=Key> (e.g., moodle257-myschool-edu)</TD></TR>");

			// reCaptcha tool
			buf.append("<TR><TD COLSPAN=2>");		
			buf.append("<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div>");
			buf.append("</TD>");

			buf.append("<TR><TD>&nbsp;</TD><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send My Free LTI Credentials'></TD></TR>");
			buf.append("</TABLE></FORM>");
			buf.append(instructions);
		} else if ("1p3".equals(request.getParameter("lti_version"))) {
			buf.append("<h3>LTI version 1.3 Integration</h3>"
			+ "LTI integration requires exchange of information needed to identify each party and to securely transmit "
			+ "information between ChemVantage and your LMS. This exchange will take place in three steps:"
			+ "<ol><li>Complete the form below with information about your LMS platform. When you submit the form, "
			+ "ChemVantage will send an email to you containing a link to complete the registration process."
			+ "<li>When you are ready to register ChemVantage as a client in your LMS, activate the link. This will "
			+ "create a configuration JSON file containing the information about ChemVantage required by your LMS. "
			+ "You can either enter the URL for the configuration JSON file into your LMS directly, or copy/paste "
			+ "the JSON text string into yur LMS, or use the JSON string to enter the values into your LMS manually, "
			+ "depending on which methods are supported by your LMS. "
			+ "<li>When you register ChemVantage in your LMS, it should generate values for a client_id (local identifier "
			+ "for ChemVantage) and (optionally) a deployment_id (local account identifier in your LMS). You will be "
			+ "prompted to send these values back to ChemVantage. Your LTI registration will not be complete without "
			+ "providing at least the client_id.<p>"
			+ "</ol>"
			+ "Please start by enter the requested information below:");
			buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");
			buf.append("<FORM METHOD=POST>");
			buf.append("<INPUT TYPE=HIDDEN NAME=lti_version VALUE=1p3>");
			buf.append("<TABLE>");
			buf.append("<TR><TD ALIGN=RIGHT>Email Address: </TD><TD><INPUT TYPE=TEXT NAME=Email> (where the credentials will be sent)</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Platform ID: </TD><TD><INPUT TYPE=TEXT NAME=PlatformId> (URL host name for your LMS, e.g., http://salisbury.instructure.com)");
			buf.append("<TR><TD ALIGN=RIGHT>Platform OIDC Auth URL: </TD><TD><INPUT TYPE=TEXT NAME=OIDCUrl> (OpenID Connect URL for your LMS)");
			buf.append("<TR><TD ALIGN=RIGHT>Platform OAuth Access Token URL: </TD><TD><INPUT TYPE=TEXT NAME=OauthTokenUrl> (Initial LTI service endpoint for your LMS)");			
			buf.append("<TR><TD ALIGN=RIGHT>Platform JSON Web Key Set URL: </TD><TD><INPUT TYPE=TEXT NAME=JWKSUrl> (.well-known/jwks URL for your LMS)");
			
			// reCaptcha tool
			buf.append("<TR><TD COLSPAN=2>");		
			buf.append("<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div>");
			buf.append("</TD>");

			buf.append("<TR><TD>&nbsp;</TD><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send My Free LTI Credentials'></TD></TR>");
			buf.append("</TABLE></FORM>");
		}
		buf.append(Home.footer);
		out.println(buf.toString());
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if ("Submit Final".equals(request.getParameter("UserRequest"))) {  // completing final step in registration process
			try {
				String platform_deployment_id = request.getParameter("platform_deployment_id");
				String signature = request.getParameter("signature");
				String client_id = request.getParameter("ClientId");
				String deployment_id = request.getParameter("DeploymentId");
				Deployment d = ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
				if (!d.client_id.equals(signature) && d.getDeploymentId() != null) throw new Exception("Registration failed.");
				d.client_id = client_id;
				if (deployment_id != null) {
					ofy().delete().entity(d);  // removes obsolete Entity from datastore
					d.platform_deployment_id = d.getPlatformId() + "/" + deployment_id;
				}
				ofy().save().entity(d);  // saves a new Entity with revised Id name
				out.println(Home.header + banner + "<h2>Congratulations. Registration is complete.</h2>" + Home.footer);
			} catch (Exception e) {
				out.println("An unexpected error occurred: " + e.toString());
			}
			return;
		}
		
		String lti_message_type = request.getParameter("lti_message_type");
		StringBuffer base_url = request.getRequestURL();
		base_url = base_url.delete(base_url.indexOf("/lti"),base_url.length()).delete(0, base_url.indexOf("://") + 3);
		
		if ("Send My Free LTI Credentials".equals(request.getParameter("UserRequest")) && "1p1".equals(request.getParameter("lti_version"))) {  // manual LTI registration request for version 1.x
			String email = request.getParameter("Email");
			String key = request.getParameter("Key").replaceAll("\\s", "");  // removes all whitespace from key
			
			if (!reCaptchaOK(request)) {
				doError(request,response,"Sorry, the reCaptcha response could not be validated. Please try again.", null, null);
				return;
			}
			
			if (email!=null && !email.isEmpty() && key!=null && !key.isEmpty()) {  // generate a new set of LTI credentials
				BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(key).now();			
				if (c==null) {
					c = new BLTIConsumer(key,email);
					if (sendLTICredentials(email,c)) {  // credentials sent successfully
						ofy().save().entity(c);
						out.println(Home.header + banner + successMessage + Home.footer);				
					}
				} else doError(request,response,"Sorry, the LTI registration attempt failed, probably because the consumer key is already in use.",null,null);			
				return;	// successful LTI v1.x registration attempts and those failed due to duplicate consumer_key values should exit here.
			} else { // incomplete registration form
				doError(request,response,"Sorry, the LTI registration request failed. All form fields are required.",null,null);
				return;
			}
		} else if ("Send My Free LTI Credentials".equals(request.getParameter("UserRequest")) && "1p3".equals(request.getParameter("lti_version"))) {
			try {
				String email = request.getParameter("Email");
				String platform_id = request.getParameter("PlatformId");
				if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
				String oidc_auth_url = request.getParameter("OIDCUrl");
				String oauth_access_token_url = request.getParameter("OauthTokenUrl");
				String well_known_jwks_url = request.getParameter("JWKSUrl");

				if(email == null) throw new Exception("Registration failed. Missing email address");
				new URL(oidc_auth_url).getHost().contentEquals(platform_id);	// OIDC Auth URL must be in the platform domain
				new URL(well_known_jwks_url).getHost().contentEquals(platform_id); // JsonWebKey URL must be in the platform domain

				if (!reCaptchaOK(request)) {
					doError(request,response,"Sorry, the reCaptcha response could not be validated. Please try again.", null, null);
					return;
				}	

				// Check to see if this Deployment entity already exists
				Deployment d = ofy().load().type(Deployment.class).id(platform_id + "/").now();
				if (d != null) throw new Exception ("This deployment has already been registered.");

				Date now = new Date();
				Date exp = new Date(now.getTime() + 259200000L); // token will expire in 3 days
				
				Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
				String token = JWT.create().withSubject(platform_id).withExpiresAt(exp).sign(algorithm);
				String client_id = JWT.decode(token).getSignature();  // temporary value during registration
				
				// All data have been validated; create a new Deployment entity			
				d = new Deployment(platform_id,"",client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,email);			
				ofy().save().entity(d).now();
				
				if (sendLTICredentials(email,token,request)) out.println(Home.header + banner + successMessage + Home.footer);				
			} catch (Exception e) {
				out.println(e.toString());
			}
		} else if ("basic-lti-launch-request".equals(lti_message_type)) {
			doError(request,response,"LTI Launch Failed. The correct launch URL for the ChemVantage production server is https://" + base_url + "/lti/",null,null);
			return;
		}
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
	
	public void doError(HttpServletRequest request, HttpServletResponse response, String s, String message, Exception e)
			throws java.io.IOException {
		try {
			String return_url = request.getParameter("launch_presentation_return_url");
			return_url += (return_url.indexOf('?')>1?"&lti_msg=":"?lti_msg=") + URLEncoder.encode(s,"UTF-8");
			return_url += "&status=failure";
			response.sendRedirect(return_url);
			return;
		} catch (Exception e2) {
			// in case no return URL was provided, show the error to the user
			PrintWriter out = response.getWriter();
			out.println(Home.header + banner + s + Home.footer);
		}
	}
	
	boolean sendLTICredentials(String email,BLTIConsumer c) {
		// send a response to a user feedback report
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = "Thank you for your interest in ChemVantage. Your LTI credentials are:<p>"
				+ "Launch URL: https://www.chemvantage.org/lti/ <br/>"
				+ "Consumer Key: " + c.oauth_consumer_key + " <br/>"
				+ "Shared Secret: " + c.secret + "<p>"
				+ "Please use the URL method of launching your LTI connection (not the domain method). <br/>"
				+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
				+ "-Chuck Wight"
				+ "<p><hr>" + instructions;
		try {
			Message msg = new MimeMessage(session);
			InternetAddress from = new InternetAddress("admin@chemvantage.org", "ChemVantage");
			msg.setFrom(from);
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(email,""));
			msg.addRecipient(Message.RecipientType.CC,from);
			msg.setSubject("ChemVantage LTI Credentials");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	boolean sendLTICredentials(String email, String token, HttpServletRequest request) {
		try {
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			DecodedJWT dt = JWT.decode(token);
			Date expires = dt.getExpiresAt();
			String chemvantage_host = "https://" + request.getServerName();
			String url = chemvantage_host + "/lti/registration?UserRequest=Finalize&Token=" + token;
					
			String msgBody = "Thank you for your ChemVantage registration request.<p> "
					+ "The next step is to enter the ChemVantage configuration details into your LMS. "
					+ "This will enable your LMS to communicate securely with ChemVantage. Normally, "
					+ "you must have administrator privileges in your LMS in order to do this. "
					+ "If you are NOT the LMS administrator, please stop here and forward this message "
					+ "to an administrator with a request to complete the registration process. The "
					+ "registration link below will be active for 3 days.<p>"
					+ "<hr>"
					+ "<br>To the LMS Administrator:<p>"
					+ "ChemVantage is a free Open Education Resource for teaching and learning college-"
					+ "level General Chemistry. Learn more about ChemVantage "
					+ "<a href=https://www.chemvantage.org/About>here</a>.<p>"
					+ "This request uses LTI version 1.3.0 to complete the registration. If your LMS "
					+ "does not support version 1.3.0, you may register using version 1.1 "
					+ "<a href=" + chemvantage_host + "/lti/registration?lti_version=1p1>here</a>.<p>"
					+ "To obtain a one-time configuration JSON URL to complete the LTI registration of "
					+ "ChemVantage using version 1.3.0, please <a href=" + url + ">click here</a>, or copy/paste "
					+ "the link below into your browser.<p>"
					+ url
					+ "<p>The link is active for 3 days and expires " + df.format(expires) + "<p>"
					+ "If your LMS does not support entering a configuration JSON URL, you will have an "
					+ "option to copy/paste the JSON itself or to enter the required values manually.<p>"
					+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
					+ "-Chuck Wight";

			Message msg = new MimeMessage(session);
			InternetAddress from = new InternetAddress("admin@chemvantage.org", "ChemVantage");
			msg.setFrom(from);
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(email,""));
			msg.addRecipient(Message.RecipientType.CC,from);
			msg.setSubject("ChemVantage LTI Registration");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	Deployment validateToken(String token) throws Exception {
		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
		DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
		String platform_deployment_id = jwt.getSubject() + "/";
		Deployment d = ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
		if (!jwt.getSignature().equals(d.client_id)) throw new Exception("Registration failed.");
	    return d;
	}
	
	JsonObject getConfigJson(Deployment d,HttpServletRequest request) {
		try {  
			String domain = request.getServerName();
			
			JsonObject config = new JsonObject();
			config.addProperty("title","ChemVantage");			
			config.addProperty("description", "ChemVantage is a free Open Education Resource for teaching and learning college-level General Chemistry");
				JsonArray scopes = new JsonArray();
				scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem");
				scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly");
				scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/score");
				scopes.add("https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly");
				scopes.add("https://purl.imsglobal.org/spec/lti-ags/scope/lineitem.readonly");
			config.add("scopes", scopes);			
				JsonArray extensions = new JsonArray();
					JsonObject extension = new JsonObject();
					extension.addProperty("domain", domain);
					extension.addProperty("tool_id", "ChemVantage");
					extension.addProperty("platform", d.getPlatformId());			
						JsonObject settings = new JsonObject();
						settings.addProperty("text", "ChemVantage");
						settings.addProperty("icon_url", "https://" + domain + "/images/CVLogo_thumb.jpg");			
							JsonArray placements = new JsonArray();
								JsonObject placement1 = new JsonObject();
								placement1.addProperty("text", "ChemVantage");
								placement1.addProperty("enabled", true);
								placement1.addProperty("icon_url", "https://" + domain + "/images/CVLogo_thumb.jpg");			
								placement1.addProperty("placement", "user_navigation");
								placement1.addProperty("message_type","LtiResourceLinkRequest");
								placement1.addProperty("target_link_uri", "https://" + domain + "/lti");
								placements.add(placement1);	
						settings.add("placements", placements);
					extension.add("settings", settings);
					extension.addProperty("privacy_level", "public");
				extensions.add(extension);
			config.add("extensions", extensions);
			config.addProperty("jwks_uri","https://" + domain + "/jwks");
			config.addProperty("key_id", d.rsa_key_id);
			config.add("public_jwk", KeyStore.getJwk(d.rsa_key_id));
			
			return config;			
		} catch (Exception e) {
			return null;
		}		
	}
}
