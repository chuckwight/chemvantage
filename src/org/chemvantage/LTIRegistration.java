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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import com.googlecode.objectify.Objectify;

public class LTIRegistration extends HttpServlet {

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
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
			
	String welcomeMessage = "<h2>LTI Support Page</h2>"
			+ "<table><tr><td><a href=/About#certification><img alt='IMS Global Certified' src='/images/imscertifiedfinalsmall.png'/></a></td>"
			+ "<td>ChemVantage supports the IMS Global Learning Solutions LTI standard, versions 1.0, 1.1 and 2.0.<br>The IMS conformance registration "
			+ "number for ChemVantage is <a href=http://www.imsglobal.org/cc/detail.cfm?ID=259>IMSB2C2ce2014W1</a>.<p>"
			+ "All LTI connections and ChemVantage services are provided free of charge.</td?</tr></table>"
			+ "<h3>LTI version 2.0</h3>"
			+ "For LMS platforms that support LTI version 2.0, the system administrator may enter the ChemVantage LTI registration URL:<br> "
			+ "<b>https://www.chemvantage.org/lti/registration/</b><br>"
			+ "into the LTI Tool Proxy Registration page of your LMS. Your LMS will automatically negotiate the connection with ChemVantage.<p>"
			+ "<h3>LTI version 1.x</h3>"
			+ "If your LMS supports an older version of the LTI standard, you may obtain a free set of LTI credentials by entering<br>"
			+ "a consumer key value (any string of characters that uniquely identifies your LMS) into the form below.<br>"
			+ "Your LTI credentials will be emailed to you immediately.<br>"
			+ "For further assistance, contact Chuck Wight (admin@chemvantage.org).<p>";
	
	String instructions = "<h3>Implementation in Canvas (other LMS platforms may be similar)</h3>"
			+ "<ol>"
			+ "<li>Obtain a set of LTI credentials (see above)."
			+ "<li>Login to Canvas as a course Instructor, navigate to Settings, select the Apps tab, and click the "
			+ "blue Add New App button to create a new External Tool. Use the following parameters:<ul>"
			+ "<li>Name: ChemVantage"
			+ "<li>Consumer Key and Shared Secret: (cut/paste values from step 1; do not include any blank spaces)"
			+ "<li>Configuration URL (optional): https://www.chemvantage.org/lti_config.xml"
			+ "<li>Manual Configuration (optional):"
			+ "<ul><li>URL: https://www.chemvantage.org/lti/"
			+ "<li>Domain: (leave blank)"
			+ "<li>Privacy: Public (ChemVantage will use only email address and first name)"
			+ "<li>Custom Fields: (leave blank)"
			+ "<li>Description: ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry.</ul></ul>"
			+ "<li>Create a new Canvas assignment with the following recommended parameters:"
			+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)"
			+ "<li>Points: 10 for quiz or homework; 100 for practice exam"
			+ "<li>Submission Type: External Tool"
			+ "<li>External Tool URL: https://www.chemvantage.org/lti/ (or select ChemVantage from the list of installed apps)</ul>"
			+ "<li>After you update the assignment, you should see the ChemVantage Assignment Setup Page (if not, click the "
			+ "assignment link to connect to ChemVantage). Select the appropriate Quiz, Homework or Practice Exam for the assignment."
			+ "<li>A sample assignment should be displayed, including a link near the top (visible only to instructors) that "
			+ "can be used to select question items to be shown to your students for that assignment."
			+ "<li>Navigate to Settings, Student View to take the assignment and ensure that the score is posted correctly in the Canvas grade book."
			+ "</ol>"
			+ "<h3>Use these links to find general instructions for installing external tools with LTI:</h3>"
			+ "<a href=https://docs.moodle.org/22/en/External_tool_settings>Moodle</a><p>"
			+ "<a href=http://library.blackboard.com/ref/df5b20ed-ce8d-4428-a595-a0091b23dda3/Content/_admin_app_system/admin_app_basic_lti_tool_providers.htm>Blackboard</a><p>"
			+ "<a href=https://www.eduappcenter.com/tutorials/sakai>Sakai</a><p>"
			+ "<a href=https://community.brightspace.com/resources/additional_resources/scenarios/yourfirsttoolintegrationwithbasiclearningtoolsinteroperability>Desire2Learn</a><p>"
			;
			
	String successMessage = "<h2>Thank You</h2> Your LTI credentials have been sent to your email address.";
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Login.header + banner + welcomeMessage);
		StringBuffer buf = new StringBuffer();
		buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");
		buf.append("<FORM METHOD=POST><TABLE>");
		buf.append("<TR><TD ALIGN=RIGHT>Email Address: </TD><TD><INPUT TYPE=TEXT NAME=Email> (where the credentials will be sent)</TD></TR>");
		buf.append("<TR><TD ALIGN=RIGHT>Consumer Key: </TD><TD><INPUT TYPE=TEXT NAME=Key> (e.g., moodle257-myschool-edu)</TD></TR>");
		
//   ============== reCaptcha tool  =========================
		buf.append("<TR><TD COLSPAN=2>");		
		buf.append("<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div>");
		buf.append("</TD>");
//=============================================================*/
		
		buf.append("<TR><TD>&nbsp;</TD><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send My Free LTI Credentials'></TD></TR>");
		buf.append("</TABLE></FORM>");
		buf.append(instructions);
		out.println(buf.toString() + Login.footer);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		String lti_message_type = request.getParameter("lti_message_type");		

		if ("Send My Free LTI Credentials".equals(request.getParameter("UserRequest"))) {  // manual LTI registration request for version 1.x
			String email = request.getParameter("Email");
			String key = request.getParameter("Key").replaceAll("\\s", "");  // removes all whitespace from key
			
			if (!reCaptchaOK(request)) {
				doError(request,response,"Sorry, the reCaptcha response could not be validated. Please try again.", null, null);
				return;
			}
			
			if (email!=null && !email.isEmpty() && key!=null && !key.isEmpty()) {  // generate a new set of LTI credentials
				response.setContentType("text/html");
				PrintWriter out = response.getWriter();
				BLTIConsumer c = ofy.find(BLTIConsumer.class,key);			
				if (c==null) {
					c = new BLTIConsumer(key,email);
					if (sendLTICredentials(email,c)) {  // credentials sent successfully
						ofy.put(c);
						out.println(Login.header + banner + successMessage + Login.footer);				
					}
				} else doError(request,response,"Sorry, the LTI registration attempt failed, probably because the consumer key is already in use.",null,null);			
				return;	// successful LTI v1.x registration attempts and those failed due to duplicate consumer_key values should exit here.
			} else { // incomplete registration form
				doError(request,response,"Sorry, the LTI registration request failed. All form fields are required.",null,null);
				return;
			}
		} else if ("basic-lti-launch-request".equals(lti_message_type)) {
			doError(request,response,"LTI Launch Failed. The correct launch URL for the ChemVantage production server is https://www.chemvantage.org/lti/",null,null);
			return;
		} else if (!"ToolProxyRegistrationRequest".equals(lti_message_type)){
			doError(request,response,"Invalid message type.",null,null);
			return;
		}
		
		// only LTI 2.0 registration attempts should reach this point
		StringBuffer debug = new StringBuffer("Debug:\n");
		String reg_key = request.getParameter("reg_key");
		String reg_password = request.getParameter("reg_password");
		String tc_profile_url = request.getParameter("tc_profile_url");
		String launch_presentation_return_url = request.getParameter("launch_presentation_return_url");
		//debug.append(lti_message_type + "<br/>" + reg_key + "<br/>" + reg_password + "<br/>" + tc_profile_url + "<br/>" + launch_presentation_return_url + "<br/>");
		
		try {
			if (reg_key==null || reg_key.isEmpty()) throw new Exception("Required reg_key parameter is missing.");
			if (reg_password==null || reg_password.isEmpty()) throw new Exception("Required reg_password parameter is missing.");
			if (tc_profile_url==null || tc_profile_url.isEmpty()) throw new Exception("Required tc_profile_url parameter is missing.");
			if (launch_presentation_return_url==null || launch_presentation_return_url.isEmpty()) throw new Exception("Required launch_presentation_return_url parameter is missing.");
			
			JSONObject toolConsumerProfile = fetchToolConsumerProfile(tc_profile_url); 
			//debug.append(toolConsumerProfile.toString() + "<br/>");
			
			List<String> capability_enabled = getCapabilities(toolConsumerProfile);
			//for (String c:capability_enabled) debug.append(c + "<br/>");
			
			String oauth_secret = BLTIConsumer.generateSecret();
			StringBuffer base_url = request.getRequestURL();
			base_url.delete(base_url.indexOf("lti"),base_url.length()).delete(0, base_url.indexOf("://") + 3);
			
			JSONObject toolProxy = constructToolProxy(toolConsumerProfile,tc_profile_url,base_url,reg_key,oauth_secret,capability_enabled);
			//debug.append("tool_proxy_formed_ok.");
			//debug.append(toolProxy.toString());
			
			String toolProxyString = toolProxy.toString();
			String serviceEndpoint = getTCServiceEndpoint("application/vnd.ims.lti.v2.toolproxy+json",toolConsumerProfile);
			//debug.append("tc_service_endpoint:" + serviceEndpoint);
			debug.append("Tool Proxy: " + toolProxy.toString(2));
			String tool_proxy_guid = reg_key; 	// temporary values
			String tool_proxy_url = "";	
			String tool_settings_url = "";

			if (serviceEndpoint != null) {  // try to register the ToolProxy
				LTIMessage msg = new LTIMessage("application/vnd.ims.lti.v2.toolproxy+json","application/vnd.ims.v2.toolproxy.id+json",toolProxyString,serviceEndpoint,reg_key,reg_password);
				//debug.append("lti_msg_formed_ok");
				String reply = msg.send();

				debug.append("tc_response_received: " + reply);

				try {
					JSONObject replyBody = new JSONObject(reply);		
					//debug.append("json_reply_ok.");
					tool_proxy_guid = replyBody.getString("tool_proxy_guid");
					tool_proxy_url = replyBody.getString("@id");
					//tool_settings_url = replyBody.getString("custom_uri");
					if (tool_proxy_guid.isEmpty() || tool_proxy_url.isEmpty()) throw new Exception("Tool Proxy guid and/or URL was missing.");
				} catch (Exception e) {
					throw new Exception ("Could not parse response to tool proxy registration request.");
				}
			} else doError(request,response, "Could not find a tool proxy registration endpoint in the Tool Consumer profile.",null,null);
			
			// check to make sure that this is the first registration for this tool consumer
			BLTIConsumer c = ofy.find(BLTIConsumer.class,tool_proxy_guid);
			if (c==null) {  // this registration is for a new oath_consumer_key
				c = new BLTIConsumer(tool_proxy_guid,oauth_secret,toolConsumerProfile.getString("guid"),"LTI-2p0");
				c.putToolProxyURL(tool_proxy_url);
				c.putToolSettingsURL(tool_settings_url);
				String resultFormat = toolConsumerProfile.toString().contains("application/vnd.ims.lis.v2.Result+json")?"application/vnd.ims.lis.v2.Result+json":"application/xml";
				c.putResultServiceFormat(resultFormat);
				ofy.put(c);
			}
			else throw new Exception("A Tool Consumer was previously registered with this key.");
						
			//debug.append("LTI_credentials_formed_ok.");
						
			// all steps completed successfully with no exceptions thrown, so report success back to TC administrator
			response.sendRedirect(launch_presentation_return_url + "?status=success&tool_proxy_guid=" + tool_proxy_guid);
		} catch (Exception e) {
			doError(request,response,"Sorry, the Tool Proxy Registration failed.<br>" + e.getMessage() + "<br>You may try manual LTI registration using credentials that can be obtained at <a href=http://www.chemvantage.org/lti/registration>http://www.chemvantage.org/lti/registration/</a>.<br>" + debug.toString(),null,null);
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
			
			JSONObject reCaptchaValidation = new JSONObject(res.toString());
			
			return reCaptchaValidation.getBoolean("success");
			
		} catch (Exception e) {
			return false;
		}
	}
	
	public void doError(HttpServletRequest request, HttpServletResponse response, String s, String message, Exception e)
			throws java.io.IOException {
		try {
			String return_url = request.getParameter("launch_presentation_return_url");
			return_url += (return_url.indexOf('?')>1?"&lti_msg=":"?lti_msg=") + URLEncoder.encode(s,"UTF-8");
			response.sendRedirect(return_url);
			return;
		} catch (Exception e2) {
			// in case no return URL was provided, show the error to the user
			PrintWriter out = response.getWriter();
			out.println(Login.header + banner + s + Login.footer);
		}
	}

	JSONObject fetchToolConsumerProfile(String tc_profile_url) throws Exception {
		JSONObject tc_profile = new JSONObject().put("tc_profile_url", tc_profile_url);		
		URL u = new URL(tc_profile_url);
		HttpURLConnection connection = (HttpURLConnection) u.openConnection();
		connection.setRequestProperty("Accept", "application/vnd.ims.lti.v2.toolconsumerprofile+json");
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			tc_profile = new JSONObject(res.toString());	
		} else {
			throw new Exception("Response code " + connection.getResponseCode() + " received from " + tc_profile_url);
		}
		return tc_profile;
	}
	
	List<String> getCapabilities(JSONObject toolConsumerProfile) throws JSONException {
		// list of capabilities offered by the Tool Consumer
		List<String> capabilities_wanted = Arrays.asList("basic-lti-launch-request","User.id","Person.email.primary","Person.name.given","Result.autocreate","Result.sourcedId");
		List<String> capability_offered = new ArrayList<String>();
		List<String> capability_enabled = new ArrayList<String>();
		
		JSONArray tcp = toolConsumerProfile.getJSONArray("capability_offered");
		for (int i=0; i<tcp.length();i++) capability_offered.add(tcp.getString(i));

		for (String s : capabilities_wanted) if (capability_offered.contains(s)) capability_enabled.add(s);	
		
		return capability_enabled;
	}

	JSONObject constructToolProxy(JSONObject toolConsumerProfile,String tc_profile_url,StringBuffer base_url,String reg_key,String shared_secret,List<String> capability_enabled) 
			throws Exception {
		JSONObject toolProxy = new JSONObject()
			.put("@context", "http://purl.imsglobal.org/ctx/lti/v2/ToolProxy")
			.put("@type", "ToolProxy")
			.put("lti_version", toolConsumerProfile.getString("lti_version"))
			//.put("tool_proxy_guid", reg_key)
			.put("tool_consumer_profile", tc_profile_url)
			.put("tool_profile", getToolProfile(base_url,capability_enabled))
			.put("security_contract", getSecurityContract(toolConsumerProfile,shared_secret,capability_enabled))
			.put("enabled_capability", new JSONArray(capability_enabled));
		
			//toolProxy.put("capability_offered", toolConsumerProfile.getJSONArray("capability_offered"));
		return toolProxy;
	}

	JSONObject getToolProfile(StringBuffer base_url,List<String> capability_enabled) throws Exception {  // this is the (mostly static) tool profile for ChemVantage
		JSONObject toolProfile = new JSONObject();
		toolProfile.put("lti_version", "LTI-2p0");
		toolProfile.put("product_instance", new JSONObject()
			.put("guid", base_url)
			.put("support", new JSONObject()
				.put("email", "admin@chemvantage.org"))
			.put("product_info", new JSONObject()
				.put("product_name", new JSONObject()
					.put("default_value", "ChemVantage"))
				.put("product_version", "3.0")
				.put("description", new JSONObject()
					.put("default_value", "ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry"))
				.put("product_family", new JSONObject()
					.put("vendor", new JSONObject()
						.put("vendor_name", new JSONObject()
							.put("default_value", "ChemVantage LLC"))
						.put("website", "https://www.chemvantage.org")
						.put("contact", new JSONObject()
							.put("email", "admin@chemvantage.org"))))));
		toolProfile.put("base_url_choice", new JSONArray()
			.put(new JSONObject()
				.put("default_base_url", "http://" + base_url.toString())
				.put("secure_base_url", "https://" + base_url.toString())));
/*
		toolProfile.put("message", new JSONArray()
			.put(new JSONObject()
				.put("message_type", "basic-lti-launch-request")
				.put("path", "https://dev-vantage-hrd.appspot.com/lti/")));
/*
				.put("parameter", new JSONArray()
					.put(new JSONObject()
						.put("name","lis_person_name_given")
						.put("variable", "$Person.name.given"))
					.put(new JSONObject()
						.put("name","lis_person_contact_email_primary")
						.put("variable", "$Person.email.primary")))));
*/					

		JSONObject resourceHandler = new JSONObject()
					.put("resource_name", new JSONObject()
						.put("default_value", "ChemVantage")
						.put("key", "assessment.resource.name"))
					.put("description", new JSONObject()
						.put("default_value", "An Open Education Resource for teaching and learning college-level General Chemistry")
						.put("key", "assessment.resource.description"))
					.put("message", new JSONArray()
						.put(new JSONObject()
							.put("message_type", "basic-lti-launch-request")
							.put("path", "lti/")
							.put("format", "application/x-www-form-urlencoded")))
					.put("resource_type", new JSONObject()
						.put("code", "assessment"));
		
		if (capability_enabled.contains("Result.autocreate"))
			resourceHandler.put("enabled_capability", new JSONArray()
						.put("Result.autocreate")
						.put("Result.sourcedId"));
		
		toolProfile.put("resource_handler", new JSONArray().put(resourceHandler));
		
		return toolProfile;
	}
		
	JSONObject getSecurityContract(JSONObject toolConsumerProfile, String shared_secret,List<String> capability_enabled) throws Exception {
		JSONObject securityContract = new JSONObject()
			.put("shared_secret",shared_secret);
		
		JSONArray toolService = new JSONArray();
		
		if (capability_enabled.contains("Result.autocreate")) {
			JSONObject resultService = new JSONObject()
				.put("@type","RestServiceProfile")
				.put("service", getTCServiceEndpoint("application/vnd.ims.lis.v2.result+json",toolConsumerProfile))
				.put("format", "application/vnd.ims.lis.v2.result+json")
				.put("action", new JSONArray()
					.put("GET")
					.put("PUT"));
			toolService.put(resultService);
		}
		
		if (toolService.length()>0) securityContract.put("tool_service",toolService);
		
		return securityContract;
		
	}

	String getTCServiceEndpoint(String formatString,JSONObject toolConsumerProfile) throws Exception {
		JSONArray service_offered = toolConsumerProfile.getJSONArray("service_offered");
		for (int i=0; i<service_offered.length(); i++) {
			try {
				JSONObject s = service_offered.getJSONObject(i);
				if (s.has("format")) {
					JSONArray formats = s.getJSONArray("format");
					for (int j=0; j<formats.length(); j++) {
						if (formats.getString(j).toLowerCase().equals(formatString.toLowerCase())) {
							return s.getString("endpoint");
						}
					}
				}
			} catch (Exception e) {}
		}
		return null;
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

}
