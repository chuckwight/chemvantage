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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import javax.servlet.http.HttpSession;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

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
			+ "<li>Privacy: Public (ChemVantage will use only email address, instructor full names and student first names)"
			+ "<li>Custom Fields: (leave blank)"
			+ "<li>Description: ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry.</ul></ul>"
			+ "<li>Create a new Canvas assignment with the following recommended parameters:"
			+ "<ul><li>Name: (as appropriate, e.g. Quiz - Heat and Enthalpy)"
			+ "<li>Points: 10 for quiz or homework; 100 for practice exam"
			+ "<li>Submission Type: External Tool"
			+ "<li>External Tool URL: https://www.chemvantage.org/lti/ (or select ChemVantage from the list of installed apps)</ul>"
			+ "<li>After you update the assignment, you should see the ChemVantage Assignment Setup Page (if not, click the "
			+ "assignment link to connect to ChemVantage). Select the appropriate Quiz, Homework or Practice Exam for the assignment."
			+ "<li>The instructor page for the assignment is displayed showing options for viewing a sample assignment, "
			+ "setting the time zone and deadline in ChemVantage, selecting question items to be shown to your students for that assignment, "
			+ "viewing student scores and more."
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
		
		// reCaptcha tool
		buf.append("<TR><TD COLSPAN=2>");		
		buf.append("<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div>");
		buf.append("</TD>");
		
		buf.append("<TR><TD>&nbsp;</TD><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send My Free LTI Credentials'></TD></TR>");
		buf.append("</TABLE></FORM>");
		buf.append(instructions);
		out.println(buf.toString() + Login.footer);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		StringBuffer debug = new StringBuffer();
		String lti_message_type = request.getParameter("lti_message_type");
		StringBuffer base_url = request.getRequestURL();
		base_url = base_url.delete(base_url.indexOf("/lti"),base_url.length()).delete(0, base_url.indexOf("://") + 3);
		List<String> capability_offered = new ArrayList<String>();
		List<String> tool_service_offered = new ArrayList<String>();
		String launch_presentation_return_url = request.getParameter("launch_presentation_return_url");
		String reg_key = null;
		String reg_password = null;
		String tc_profile_url = null;
		String lti_version = null;
		String tool_proxy_guid = null;
		
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
				BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(key).now();			
				if (c==null) {
					c = new BLTIConsumer(key,email);
					if (sendLTICredentials(email,c)) {  // credentials sent successfully
						ofy().save().entity(c);
						out.println(Login.header + banner + successMessage + Login.footer);				
					}
				} else doError(request,response,"Sorry, the LTI registration attempt failed, probably because the consumer key is already in use.",null,null);			
				return;	// successful LTI v1.x registration attempts and those failed due to duplicate consumer_key values should exit here.
			} else { // incomplete registration form
				doError(request,response,"Sorry, the LTI registration request failed. All form fields are required.",null,null);
				return;
			}
		} else if ("basic-lti-launch-request".equals(lti_message_type)) {
			doError(request,response,"LTI Launch Failed. The correct launch URL for the ChemVantage production server is https://" + base_url + "/lti/",null,null);
			return;
		} else if ("ToolProxyRegistrationRequest".equals(lti_message_type)){
			reg_key = request.getParameter("reg_key");
			reg_password = request.getParameter("reg_password");
			tc_profile_url = request.getParameter("tc_profile_url");
			tool_proxy_guid = request.getParameter("tool_proxy_guid");
			
			try {
				if (reg_key==null || reg_key.isEmpty()) throw new Exception("Required reg_key parameter is missing.");
				if (reg_password==null || reg_password.isEmpty()) throw new Exception("Required reg_password parameter is missing.");
				if (tc_profile_url==null || tc_profile_url.isEmpty()) throw new Exception("Required tc_profile_url parameter is missing.");
				if (launch_presentation_return_url==null || launch_presentation_return_url.isEmpty()) throw new Exception("Required launch_presentation_return_url parameter is missing.");

				JSONObject toolConsumerProfile = fetchToolConsumerProfile(tc_profile_url);
				lti_version = toolConsumerProfile.getString("lti_version");
				
				// store reg_key, reg_password and tc_profile_url in the user's session for later:
				HttpSession session = request.getSession();
				session.setAttribute("tc_profile_url", tc_profile_url);
				session.setAttribute("reg_key", reg_key);
				session.setAttribute("reg_password", reg_password);
				session.setAttribute("lti_version", lti_version);
				session.setAttribute("tool_proxy_guid", tool_proxy_guid);
				
				JSONArray tcpco = toolConsumerProfile.getJSONArray("capability_offered");
				for (int i=0; i<tcpco.size();i++) capability_offered.add(tcpco.getString(i));
				JSONArray tcpso = toolConsumerProfile.getJSONArray("service_offered");
				for (int i=0;i<tcpso.size();i++) tool_service_offered.add(tcpso.getJSONObject(i).getString("@id"));
				
				// separate and select enabled capabilities and tool services by type:
				List<String> message_types = getCapabilities(toolConsumerProfile,"message_types",capability_offered);
				List<String> substitution_variables = getCapabilities(toolConsumerProfile,"substitution_variables",capability_offered);
				List<String> outcomes_capabilities = getCapabilities(toolConsumerProfile,"outcomes_capabilities",capability_offered);
				JSONArray tool_services = getToolServices(toolConsumerProfile);
				
				// choose selected capabilities based on ChemVantage capabilities and preferences
				if (substitution_variables.contains("Context.id")) substitution_variables.remove("CourseSection.sourcedId");
				
				response.setContentType("text/html");
				PrintWriter out = response.getWriter();
				
				StringBuffer buf = new StringBuffer(Login.header + banner);
				buf.append("<h3>LTI2 Registration</h3>");
				buf.append("All ChemVantage services are offered at no cost whatsoever. Please review the proposed capabilities<br>"
						+ "and tool services selected below. Then submit this registration form to complete the process.<p>");
				
				buf.append("<form action=/lti/registration method=post encType='application/x-www-form-urlencoded'>");
				buf.append("<input type=hidden name=lti_version value='" + lti_version + "'>");
				buf.append("<input type=hidden name=launch_presentation_return_url value=" + launch_presentation_return_url + ">");
				
				buf.append("<table><tr><td valign=top><b>Offered by LMS</b><br/>");
				for (String s:capability_offered) buf.append(s + "<br/>");
				for (String s:tool_service_offered) buf.append(s + "<br/>");
				buf.append("</td>");
						
				buf.append("<td valign=top><b>Capabilities Selected</b><br/>");
				buf.append("<select name=capabilities multiple style='padding: 0 5px;' size=15>");
				buf.append("<optgroup label='Message Types'>");
				for (String s:message_types) buf.append("<option value='" + s + "' selected>" + s + "</option>");
				buf.append("</optgroup>");
				
				buf.append("<optgroup label='Substitution Variables Enabled'>");
				for (String s:substitution_variables) buf.append("<option value='" + s + "' selected>" + s + "</option>");
				buf.append("</optgroup>");
				buf.append("<optgroup label='Outcomes Capabilities Enabled'>");
				for (String s:outcomes_capabilities) buf.append("<option value='" + s + "' selected>" + s + "</option>");
				buf.append("</optgroup>");
				buf.append("</select><br/>");
								
				buf.append("<b>Tool Services</b><br/>");
				buf.append("<select name=tool_services multiple style='padding: 0 5px;' size=5>");
				for (int i=0;i<tool_services.size();i++) {
					String s = tool_services.getJSONObject(i).getString("@id");
					buf.append("<option value='" + s + "' selected>" + s + "</option>");
				}
				buf.append("</select><br/>");
				
				buf.append("<input type=submit name=UserRequest value='Cancel'>" 
						+ "<input type=submit name='UserRequest' value='Submit Registration'>");

				buf.append("</td></tr></table></form>");
				buf.append(Login.footer);
				
				out.println(buf.toString());
				return;
			} catch (Exception e){
				doError(request,response,"Sorry, the Tool Proxy Registration failed.<br>" + e.getMessage() + "<br>You may try manual LTI registration using credentials that can be obtained at <a href=http://www.chemvantage.org/lti/registration>http://www.chemvantage.org/lti/registration/</a>.<br>",null,null);
				return;
			}
		} else if (request.getParameter("UserRequest").equals("Submit Registration")) {
			try {
				// retrieve capabilities and tool services to be enabled from the registration form:
				for (String s:request.getParameterValues("capabilities")) capability_offered.add(s);
				if (capability_offered.isEmpty()) throw new Exception("You must select at least one capability to be enabled.");
				for (String s:request.getParameterValues("tool_services")) tool_service_offered.add(s);
				if (tool_service_offered.isEmpty()) throw new Exception("You must select at least one tool service to be enabled.");
				if (launch_presentation_return_url==null || launch_presentation_return_url.isEmpty()) throw new Exception("Required launch_presentation_return_url parameter is missing.");

				HttpSession session = request.getSession();
				reg_key = (String)session.getAttribute("reg_key");
				reg_password = (String)session.getAttribute("reg_password");
				tc_profile_url = (String)session.getAttribute("tc_profile_url");
				lti_version = (String)session.getAttribute("lti_version");
				tool_proxy_guid = (String)session.getAttribute("tool_proxy_guid");
				
				if (reg_key==null || reg_key.isEmpty() || reg_password==null || reg_password.isEmpty() || tc_profile_url==null || tc_profile_url.isEmpty() || lti_version==null || lti_version.isEmpty()) 
					throw new Exception("HttpSession may have timed out, or your browser may delete the session if you are viewing this in a frame.");
				
				JSONObject toolConsumerProfile = fetchToolConsumerProfile(tc_profile_url);
				if (toolConsumerProfile.size()==0) throw new Exception("Could not retrieve the tool consumer profile using the URL provided.");
				String oauth_secret = BLTIConsumer.generateSecret();
				
				JSONObject toolProxy = constructToolProxy(toolConsumerProfile,tc_profile_url,base_url,reg_key,oauth_secret,capability_offered,tool_service_offered,tool_proxy_guid);
				debug.append(toolProxy.toString());
				
				String serviceEndpoint = getTCServiceEndpoint("application/vnd.ims.lti.v2.toolproxy+json",toolConsumerProfile);
				if (serviceEndpoint==null) throw new Exception("Could not find a tool proxy registration endpoint in the Tool Consumer profile.");
				
				String tool_proxy_url = "";	
				String tool_settings_url = "";

				LTIMessage msg = new LTIMessage("application/vnd.ims.lti.v2.toolproxy+json","",toolProxy.toString(),serviceEndpoint,reg_key,reg_password);
				String reply = msg.send();

				try {
					JSONObject replyBody = JSONObject.fromObject(reply);
					String guid = replyBody.getString("tool_proxy_guid");
					tool_proxy_url = replyBody.getString("@id");
					if (guid.isEmpty() || tool_proxy_url.isEmpty()) throw new Exception("Tool Proxy guid and/or URL was missing.");
				} catch (Exception e) {
					throw new Exception ("Could not parse response to tool proxy registration request.");
				}
				toolProxy.element("@id", tool_proxy_url);
				//toolProxy.element("guid", tool_proxy_guid);
				
				// check to make sure that this is the first registration for this tool consumer
				BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(tool_proxy_guid).now();
				if (c==null) {  // this registration is for a new oath_consumer_key
					c = new BLTIConsumer(tool_proxy_guid,oauth_secret,toolConsumerProfile.getString("guid"),"LTI-2p0");
					c.putToolProxyURL(tool_proxy_url);
					c.putToolProxy(toolProxy);
					c.putToolSettingsURL(tool_settings_url);
					c.putCapabilities(getCapabilitiesEnabled(toolProxy));
					c.putToolService(getToolServices(toolConsumerProfile));
					String[] result_format = {"application/vnd.ims.lis.v2.result+json","application/vnd.ims.lti.v1.outcome+xml"};
					for (String rf:result_format) {
						String result_endpoint = getTCServiceEndpoint(rf,toolConsumerProfile);
						if (result_endpoint != null) {
							c.putResultServiceFormat(rf);
							c.putResultServiceEndpoint(result_endpoint);
							break;
						}
					}
					ofy().save().entity(c);
				}
				else throw new Exception("A Tool Consumer was previously registered with this key.");

				// all steps completed successfully with no exceptions thrown, so report success back to TC administrator
				response.sendRedirect(launch_presentation_return_url + "?status=success&tool_proxy_guid=" + tool_proxy_guid);

			} catch (Exception e) {
				doError(request,response,"Sorry, the Tool Proxy Registration failed.<br>" + e.getMessage() + "<br>You may try manual LTI registration using credentials that can be obtained at <a href=http://www.chemvantage.org/lti/registration>http://www.chemvantage.org/lti/registration/</a>.<br>",null,null);
				return;	
			}
		} else if (request.getParameter("UserRequest").equals("Cancel")) {
			doError(request,response,"The Tool Proxy Registration request was cancelled by the tool consumer.<br>You may try manual LTI registration using credentials that can be obtained at <a href=http://www.chemvantage.org/lti/registration>http://www.chemvantage.org/lti/registration/</a>.<br>",null,null);			
			return;
		}
		doError(request,response,"The Tool Proxy Registration failed due to an unexpected error.<br>You may try manual LTI registration using credentials that can be obtained at <a href=http://www.chemvantage.org/lti/registration>http://www.chemvantage.org/lti/registration/</a>.<br>",null,null);
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
			
			JSONObject reCaptchaValidation = JSONObject.fromObject(res.toString());
			
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
			return_url += "&status=failure";
			response.sendRedirect(return_url);
			return;
		} catch (Exception e2) {
			// in case no return URL was provided, show the error to the user
			PrintWriter out = response.getWriter();
			out.println(Login.header + banner + s + Login.footer);
		}
	}

	JSONObject fetchToolConsumerProfile(String tc_profile_url) throws Exception {
		if (!tc_profile_url.contains("lti_version")) { //append the required lti_version parameter
			tc_profile_url += (tc_profile_url.contains("?")?"&":"?") + "lti_version=LTI-2p0";
		}
		JSONObject tc_profile = new JSONObject().element("tc_profile_url", tc_profile_url);		
		URL u = new URL(tc_profile_url);
		HttpURLConnection connection = (HttpURLConnection) u.openConnection();
		connection.setRequestProperty("Accept", "application/vnd.ims.lti.v2.toolconsumerprofile+json");
		connection.connect();
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			tc_profile = JSONObject.fromObject(res.toString());	
		} else {
			throw new Exception("Response code " + connection.getResponseCode() + " received from " + tc_profile_url);
		}
		return tc_profile;
	}
	
	List<String> getCapabilities(JSONObject toolConsumerProfile, String type, List<String> capability_offered) throws JSONException {
		List<String> capability_enabled = new ArrayList<String>();
		
		if (type.equals("message_types")) {
			List<String> chemvantage_message_types = Arrays.asList(
					"basic-lti-launch-request");
			for (String s : chemvantage_message_types) if (capability_offered.contains(s)) capability_enabled.add(s);	
			return capability_enabled;
		} else if (type.equals("substitution_variables")) {
			List<String> chemvantage_substitution_variables = Arrays.asList(
					"BasicOutcome.url",
					"Context.id",
					"Context.title",
					"Membership.role",
					"Person.email.primary",
					"Person.name.family",
					"Person.name.given",
					"Person.name.full",
					"ResourceLink.id",
					"Result.sourcedId",
					"ToolConsumerProfile.url",
					"User.id");
			for (String s : chemvantage_substitution_variables) if (capability_offered.contains(s)) capability_enabled.add(s);	
			return capability_enabled;			
		} else if (type.equals("outcomes_capabilities")) {
			List<String> chemvantage_outcomes_capabilities = Arrays.asList(
					"Result.autocreate");
			for (String s : chemvantage_outcomes_capabilities) if (capability_offered.contains(s)) capability_enabled.add(s);	
			return capability_enabled;			
		}

		return capability_enabled;
	}
	
	JSONArray getToolServices(JSONObject toolConsumerProfile) throws JSONException {
		JSONArray tool_service_enabled = new JSONArray();
		JSONArray service_offered = toolConsumerProfile.getJSONArray("service_offered");
		for (int i=0;i<service_offered.size();i++) {
			JSONObject service = service_offered.getJSONObject(i);
			// check for tool_consumer_profile
			if (service.getJSONArray("format").toString().contains("application/vnd.ims.lti.v2.toolconsumerprofile+json")
					&& service.getJSONArray("action").getString(0).equals("GET")) { // found tool_consumer_profile service
				tool_service_enabled.add(service);
				continue;
			}		
			// check for tool_proxy_collection
			if (service.getJSONArray("format").toString().contains("application/vnd.ims.lti.v2.toolproxy+json")
					&& service.getJSONArray("action").getString(0).equals("POST")) { // found tool_proxy_collection service
				tool_service_enabled.add(service);
				continue;
			}
			// check for lis_result_service
			if (service.getJSONArray("format").toString().contains("application/vnd.ims.lis.v2.result+json")) { // found LTI2 result_service
				tool_service_enabled.add(service);
				continue;
			} else if (service.getJSONArray("format").toString().contains("application/vnd.ims.lti.v1.outcome+xml")) { // found LTI1 lis_result_service
				tool_service_enabled.add(service);
				continue;
			} else if (toolConsumerProfile.toString().contains("Result.autocreate") && service.getJSONArray("format").toString().contains("application/xml")) { // found basic lis_result_service
				tool_service_enabled.add(service);
				continue;
			}
		}
		return tool_service_enabled;	
	}

	JSONObject constructToolProxy(JSONObject toolConsumerProfile,String tc_profile_url,StringBuffer base_url,String reg_key,String shared_secret,List<String> capability_enabled,List<String> tool_service_enabled,String tool_proxy_guid) 
			throws Exception {
		JSONObject toolProxy = new JSONObject()
			.element("@context", new JSONArray().element("http://purl.imsglobal.org/ctx/lti/v2/ToolProxy"))
			.element("@id", tc_profile_url)
			.element("@type", "ToolProxy")
			.element("enabled_capability", new JSONArray())		// this section is required but empty
			.element("lti_version", toolConsumerProfile.getString("lti_version"))
			.element("security_contract", getSecurityContract(toolConsumerProfile,shared_secret,capability_enabled,tool_service_enabled))
			.element("tool_consumer_profile", tc_profile_url)
			.element("tool_profile", getToolProfile(base_url,capability_enabled))
			.element("custom", new JSONObject().element("at", new Date().toString()));
		if (tool_proxy_guid!=null) toolProxy.element("tool_proxy_guid", tool_proxy_guid); // option tool_proxy_guid specification
		return toolProxy;
	}

	JSONObject getToolProfile(StringBuffer base_url,List<String> capability_enabled) throws Exception {  // this is the (mostly static) tool profile for ChemVantage
		JSONObject toolProfile = new JSONObject();
		toolProfile.element("lti_version", "LTI-2p0");
		toolProfile.element("product_instance", new JSONObject()
			.element("guid", base_url)
			.element("support", new JSONObject()
				.element("email", "admin@chemvantage.org"))
			.element("product_info", new JSONObject()
				.element("product_name", new JSONObject()
					.element("default_value", "ChemVantage"))
				.element("product_version", "3.0")
				.element("description", new JSONObject()
					.element("default_value", "ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry"))
				.element("product_family", new JSONObject()
					.element("code", "tools")
					.element("vendor", new JSONObject()
						.element("code", "www.chemvantage.org")
						.element("vendor_name", new JSONObject()
							.element("default_value", "ChemVantage LLC"))
						.element("timestamp", DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL).format(new Date()))
						.element("website", "https://www.chemvantage.org")
						.element("contact", new JSONObject()
							.element("email", "admin@chemvantage.org"))))));

		JSONArray urls = new JSONArray();
		urls.add(new JSONObject().element("default_base_url", "https://" + base_url.toString()));  // always use secure URL

		toolProfile.element("base_url_choice", urls);

		capability_enabled.remove("basic-lti-launch-request"); // not to be explicitly included in resourceHandler object except as message type

		JSONArray icon_info = new JSONArray();
		icon_info.add(new JSONObject()
				.element("key", "iconStyle.default.path")
				.element("default_location", new JSONObject()
					.element("path", "/images/CVLogo_thumb.jpg")));

		JSONArray message = new JSONArray();
		message.add(new JSONObject()
				.element("message_type", "basic-lti-launch-request")
				.element("path", "/lti")
				.element("enabled_capability", JSONArray.fromObject(capability_enabled)));
						
		JSONArray resourceHandler = new JSONArray();
		resourceHandler.add(new JSONObject()
					.element("resource_name", new JSONObject()
						.element("default_value", "ChemVantage")
						.element("key", "assessment.resource.name"))
					.element("resource_type", new JSONObject().element("code", "test"))
					.element("description", new JSONObject()
						.element("default_value", "An Open Education Resource for teaching and learning college-level General Chemistry")
						.element("key", "assessment.resource.description"))
					.element("icon_info", icon_info)
					.element("message", message));
		
		toolProfile.element("resource_handler", resourceHandler);
		
		return toolProfile;
	}
		
	JSONObject getSecurityContract(JSONObject toolConsumerProfile, String shared_secret,List<String> capability_enabled,List<String> tool_service_enabled) throws Exception {
		JSONObject securityContract = new JSONObject().element("shared_secret",shared_secret);
		
		JSONArray toolService = new JSONArray();
		toolService.add(new JSONObject()
						.element("@type", "RestServiceProfile")
						.element("service", "tcp:ToolProxy.collection")
						.element("action", new JSONArray().element("POST")));
		
		if (tool_service_enabled.contains("tcp:ToolConsumerProfile"))
			toolService.add(new JSONObject()
						.element("@type", "RestServiceProfile")
						.element("service", "tcp:ToolConsumerProfile")
						.element("action", new JSONArray().element("GET")));
		
		if (capability_enabled.contains("Result.autocreate") && tool_service_enabled.contains("tcp:Outcomes.LTI1")) {
			toolService.add(new JSONObject()
				.element("@type", "RestServiceProfile")
				.element("service", "tcp:Outcomes.LTI1")
				.element("action", new JSONArray().element("POST")));
		} else if (capability_enabled.contains("Result.autocreate") && tool_service_enabled.contains("tcp:Result.item")) {
			toolService.add(new JSONObject()
				.element("@type", "RestServiceProfile")
				.element("service", "tcp:Result.item")
				.element("action", new JSONArray().element("POST")));
		}		
		
		securityContract.element("tool_service",toolService);
		return securityContract;
	}

	String getTCServiceEndpoint(String formatString,JSONObject toolConsumerProfile) throws Exception {
		formatString = formatString.toLowerCase();
		JSONArray service_offered = toolConsumerProfile.getJSONArray("service_offered");
		for (int i=0; i<service_offered.size(); i++) {
			try {
				JSONObject s = service_offered.getJSONObject(i);
				if (s.getString("@type").equals("RestService")) {
					JSONArray formats = s.getJSONArray("format");
					for (int j=0; j<formats.size(); j++) if (formats.getString(j).toLowerCase().equals(formatString)) return s.getString("endpoint");
				}
			} catch (Exception e) {
			}
		}
		return null;
	}

	List<String> getCapabilitiesEnabled(JSONObject toolProxy) {
		List<String> capabilitiesEnabled = new ArrayList<String>();
		try {
			JSONArray resource_handler = toolProxy.getJSONObject("tool_profile").getJSONArray("resource_handler");
			for (int i=0;i<resource_handler.size();i++) {
				JSONObject rh = resource_handler.getJSONObject(i);
				if (rh.has("message")) {
					JSONArray message = rh.getJSONArray("message");
					for (int j=0;j<message.size();j++) {
						JSONObject m = message.getJSONObject(j);
						if (m.getString("message_type").equals("basic-lti-launch-request")) {
							JSONArray enabledCapability = m.getJSONArray("enabled_capability");
							for (int k=0;k<enabledCapability.size();k++) capabilitiesEnabled.add(enabledCapability.getString(k));
							break;
						}
					}
					break;
				}
			}
		} catch (Exception e) {
		}
		return capabilitiesEnabled;
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
