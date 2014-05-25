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
			+ "<td>ChemVantage supports the IMS Global Learning Solutions LTI standard, versions 1.0, 1.1 and 2.0.<p>"
			+ "All LTI connections and ChemVantage services are provided free of charge.</td?</tr></table>"
			+ "For LMS platforms that support LTI version 2.0, the system administrator may enter the ChemVantage LTI registration URL:<br> "
			+ "<b>https://chem-vantage.appspot.com/lti/registration/</b><br>into the LTI Tool Proxy Registration page of your LMS.<p>"
			+ "If your LMS supports an older version of the LTI standard, the LTI launch URL is<br>"
			+ "<b>http://chem-vantage.appspot.com/lti/</b><br>"
			+ "To obtain a set of LTI credentials, please enter an oauth_consumer_key value (any string of characters that uniquely<br>"
			+ "identifies your LMS) into the form below. Your LTI credentials will be emailed to you immediately.<br>"
			+ "For further assistance, contact Chuck Wight (admin@chemvantage.org).<p>";
	
	String successMessage = "<h2>Thank You</h2> Your LTI credentials have been sent to your email address.";
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Login.header + banner + welcomeMessage);
		StringBuffer buf = new StringBuffer();
		buf.append("<TABLE><FORM METHOD=POST>");
		buf.append("<TR><TD ALIGN=RIGHT>Email Address: </TD><TD><INPUT TYPE=TEXT NAME=Email></TD></TR>");
		buf.append("<TR><TD ALIGN=RIGHT>Consumer Key: </TD><TD><INPUT TYPE=TEXT NAME=Key></TD></TR>");
		buf.append("<TR><TD>&nbsp;</TD><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Generate Shared Secret'></TD></TR>");
		buf.append("</TABLE></FORM>");
		out.println(buf.toString() + Login.footer);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		String email = request.getParameter("Email");
		String key = request.getParameter("Key");
		if (email!=null && key!=null) {  // generate a new set of LTI credentials
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			BLTIConsumer c = ofy.find(BLTIConsumer.class,key);			
			if (c==null) {
				c = new BLTIConsumer(key,email);
				if (sendLTICredentials(email,c)) {  // credentials sent successfully
					ofy.put(c);
					out.println(Login.header + banner + successMessage + Login.footer);				
				}
			} else doError(request,response,"Sorry, the LTI registration attempt failed.",null,null);			
			return;	
		}
		
		StringBuffer debug = new StringBuffer("Debug:\n");
		String lti_message_type = request.getParameter("lti_message_type");
		String reg_key = request.getParameter("reg_key");
		String reg_password = request.getParameter("reg_password");
		String tc_profile_url = request.getParameter("tc_profile_url");
		String launch_presentation_return_url = request.getParameter("launch_presentation_return_url");
		
		try {
			if (!lti_message_type.equals("ToolProxyRegistrationRequest")) throw new Exception("Invalid message type");
			if (reg_key==null || reg_key.isEmpty()) throw new Exception("Required reg_key parameter is missing.");
			if (reg_password==null || reg_password.isEmpty()) throw new Exception("Required reg_password parameter is missing.");
			if (tc_profile_url==null || tc_profile_url.isEmpty()) throw new Exception("Required tc_profile_url parameter is missing.");
			if (launch_presentation_return_url==null || launch_presentation_return_url.isEmpty()) throw new Exception("Required launch_presentation_return_url parameter is missing.");
			
			JSONObject toolConsumerProfile = fetchToolConsumerProfile(tc_profile_url); 
			
			List<String> capability_enabled = getCapabilities(toolConsumerProfile);
			
			String oauth_secret = BLTIConsumer.generateSecret();
			StringBuffer base_url = request.getRequestURL();
			base_url.delete(base_url.indexOf("lti"),base_url.length()).delete(0, base_url.indexOf("://") + 3);
			
			JSONObject toolProxy = constructToolProxy(toolConsumerProfile,tc_profile_url,base_url,oauth_secret,capability_enabled);
			debug.append("tool_proxy_formed_ok.");			
			
			String reply = new LTIMessage("application/vnd.ims.lti.v2.toolproxy+json",toolProxy.toString(),getTCServiceEndpoint("application/vnd.ims.lti.v2.toolproxy+json",toolConsumerProfile),reg_key,reg_password).send();
			debug.append("tc_response_received.");
			
			String tool_proxy_guid = null;
			String tool_proxy_url = null;
			String tool_settings_url = null;
			try {
				JSONObject replyBody = new JSONObject(reply);		
				debug.append("json_reply_ok.");
				tool_proxy_guid = replyBody.getString("tool_proxy_guid");
				tool_proxy_url = replyBody.getString("@id");
				tool_settings_url = replyBody.getString("custom_uri");
				if (tool_proxy_guid.isEmpty() || tool_proxy_url.isEmpty()) throw new Exception();
			} catch (Exception e) {
				throw new Exception ("Could not parse response to tool proxy registration request.");
			}
			
			// check to make sure that this is the first registration for this tool consumer
			BLTIConsumer c = ofy.find(BLTIConsumer.class,reg_key);
			if (c==null) {  // this registration is for a new oath_consumer_key
				c = new BLTIConsumer(tool_proxy_guid,oauth_secret,toolConsumerProfile.getString("guid"),"LTI-2p0");
				c.putToolProxyURL(tool_proxy_url);
				c.putToolSettingsURL(tool_settings_url);
				String resultFormat = toolConsumerProfile.toString().contains("application/vnd.ims.lis.v2.Result+json")?"application/vnd.ims.lis.v2.Result+json":"application/xml";
				c.putResultServiceFormat(resultFormat);
				ofy.put(c);
			}
			else throw new Exception("A Tool Consumer is already registered with this key.");
						
			debug.append("LTI_credentials_formed_ok.");
						
			// all steps completed successfully with no exceptions thrown, so report success back to TC administrator
			response.sendRedirect(launch_presentation_return_url + "?status=success&tool_proxy_guid=" + toolProxy.getString("tool_proxy_guid"));
		} catch (Exception e) {
			doError(request,response,"Sorry, the Tool Proxy Registration failed.<br>" + e.getMessage() + "<br>" + debug.toString() + "<br>" + "PLEASE SEND THIS ERROR TO admin@chemvantage.org",null,null);
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
		JSONObject tc_profile = null;		
		URL u = new URL(tc_profile_url);
		HttpURLConnection connection = (HttpURLConnection) u.openConnection();
		connection.setRequestProperty("Content-Type", "application/json");
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(u.openStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			tc_profile = new JSONObject(res.toString());	
		}
		return tc_profile;
	}
	
	List<String> getCapabilities(JSONObject toolConsumerProfile) throws JSONException {
		// list of capabilities offered by the Tool Consumer
		List<String> capabilities_wanted = Arrays.asList("Person.email.primary","Person.name.given","Result.autocreate");
		List<String> capability_offered = new ArrayList<String>();
		List<String> capability_enabled = new ArrayList<String>();
		JSONArray tcp = toolConsumerProfile.getJSONArray("capability_offered");
		for (int i=0; i<tcp.length();i++) capability_offered.add(tcp.getString(i));

		for (String s : capabilities_wanted) if (capability_offered.contains(s)) capability_enabled.add(s);	
		
		return capability_enabled;
	}

	JSONObject constructToolProxy(JSONObject toolConsumerProfile,String tc_profile_url,StringBuffer base_url,String shared_secret,List<String> capability_enabled) 
			throws Exception {
		JSONObject toolProxy = new JSONObject();
		toolProxy.put("@context", "http://purl.imsglobal.org/ctx/lti/v2/ToolProxy");
		toolProxy.put("@type", "ToolProxy");
		toolProxy.put("@id", "");
		toolProxy.put("lti_version", toolConsumerProfile.getString("lti_version"));
		toolProxy.put("tool_proxy_guid", "");
		toolProxy.put("tool_consumer_profile", tc_profile_url);
		toolProxy.put("tool_profile", getToolProfile(base_url,capability_enabled));					
		toolProxy.put("security_contract", getSecurityContract(toolConsumerProfile,shared_secret,capability_enabled));				
		return toolProxy;
	}

	JSONObject getToolProfile(StringBuffer base_url,List<String> capability_enabled) throws Exception {  // this is the (mostly static) tool profile for ChemVantage
		JSONObject toolProfile = new JSONObject()
			.put("lti_version","LTI-2p0")
			.put("product_instance",new JSONObject()
				.put("product_info", new JSONObject()
					.put("product_name", new JSONObject("{'default_value':'ChemVantage'}"))
					.put("product_version", "3.0")
					.put("description", new JSONObject("{'default_value':'ChemVantage is an Open Education Resource for teaching and learning college-level General Chemistry.'}"))
					.put("product_family", new JSONObject()
						.put("@id", "http://chemvantage.org/about")
						.put("vendor", new JSONObject()
							.put("vendor_name", new JSONObject("{'default_value':'ChemVantage LLC'}"))
							.put("website", "http://chemvantage.org")
							.put("contact", new JSONObject("{'email':'admin@chem=vantage.org'}")))))
				.put("support", new JSONObject("{'email':'admin@chemvantage.org'}"))
				.put("service_provider", new JSONObject()
					.put("guid", "chemvantage.org")
					.put("timestamp", "2014-05-01T00:00:00-07:00")
					.put("service_provider_name", new JSONObject("{'default_value':'ChemVantage LLC'}")))
				.put("service_owner", new JSONObject()
					.put("guid", "chemvantage.org")
					.put("timestamp", "2014-05-01T00:00:00-07:00")
					.put("service_owner_name", new JSONObject("{'default_value':'ChemVantage LLC'}"))))
				.put("base_url_choice", new JSONArray()
				.put(new JSONObject()
					.put("selector", "DefaultSelector")
					.put("default_base_url", "http://" + base_url.toString())
					.put("secure_base_url", "https://" + base_url.toString())));
					
		// the following are parameters that are available only at the option of the Tool Consumer (LMS)
		JSONArray parameter = new JSONArray();
		if (capability_enabled.contains("Person.name.given")) parameter.put(new JSONObject("{'name':'lis_person_name_given','variable':'Person.name.given'}"));
		if (capability_enabled.contains("Person.email.primary")) parameter.put(new JSONObject("{'name':'lis_person_email_primary','variable':'Person.email.primary'}"));
		if (capability_enabled.contains("Result.autocreate")) {
			parameter.put(new JSONObject("{'name':'lis_result_sourcedid','variable':'Result.sourcedId'}"));
			parameter.put(new JSONObject("{'name':'lis_outcome_service_url','variable':'Result.uri'}"));
		}

		// construct a generic message object for every basic-lti-launch-request 
		JSONObject msg = new JSONObject("{'message_type':'basic-lti-launch-request','path':'lti/'}");
		if (capability_enabled.contains("Result.autocreate")) msg.put("enabled_capability", new JSONArray("['Result.autocreate']"));

		// construct an array of resource handlers for ChemVantage quiz, homework and exam assignments
		JSONArray resource_handler = new JSONArray();
		resource_handler.put(new JSONObject("{'name':{'default_value':'ChemVantage Quiz'},'description':{'default_value':'A 15-minute timed quiz on one topic in General Chemistry'}}")
		   	.put("message",new JSONArray()
				.put(msg
					.put("parameter",parameter
						.put(new JSONObject("{'name':'assignmentType','fixed':'Quiz'}"))))));
		resource_handler.put(new JSONObject("{'name':{'default_value':'ChemVantage Homework Assignment'},'description':{'default_value':'A set of quantitative problems on one topic in General Chemistry'}}")
			.put("message",new JSONArray()
				.put(msg
					.put("parameter",parameter
						.put(new JSONObject("{'name':'assignmentType','fixed':'Homework'}"))))));
/*		resource_handler.put(new JSONObject("{'name':{'default_value':'ChemVantage Practice Exam'},'description':{'default_value':'A 60-minute practice exam covering at least 3 topics in General Chemistry'}}")
			.put("message",new JSONArray()
				.put(msg
					.put("parameter",parameter
						.put(new JSONObject("{'name':'assignmentType','fixed':'PracticeExam'}"))))));
						*/
		toolProfile.put("resource_handler",resource_handler);
		return toolProfile;
	}

	JSONObject getSecurityContract(JSONObject toolConsumerProfile, String shared_secret,List<String> capabilities) throws Exception {
		JSONObject security_contract = new JSONObject();
		security_contract.put("shared_secret",shared_secret);
		
		if (capabilities.contains("Result.autocreate")) {
			security_contract.put("tool_service", new JSONArray().put(new JSONObject()
				.put("@type","RestServiceProfile")
				.put("service", getTCServiceEndpoint("application/vnd.ims.lis.v2.result+json",toolConsumerProfile))
				.put("action", new JSONArray("['GET','PUT']"))));
		}
		
		return security_contract;
		
	}

	String getTCServiceEndpoint(String formatString,JSONObject toolConsumerProfile) throws Exception {
		JSONArray service_offered = toolConsumerProfile.getJSONArray("service_offered");
		for (int i=0; i<service_offered.length(); i++) {
			try {
				JSONObject s = service_offered.getJSONObject(i);
				if (s.has("format")) {
					JSONArray formats = s.getJSONArray("format");
					for (int j=0; j<formats.length(); j++) {
						if (formats.getString(i).toLowerCase().equals(formatString)) {
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
				+ "Launch URL: http://chem-vantage.appspot.com/lti/ <br/>"
				+ "Consumer Key: " + c.oauth_consumer_key + " <br/>"
				+ "Shared Secret: " + c.secret + "<p>"
				+ "Please use the URL method of launching your LTI connection (not the domain method). <br/>"
				+ "If you  need additional assistance, please contact me at admin@chemvantage.org. <p>"
				+ "-Chuck Wight";
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
