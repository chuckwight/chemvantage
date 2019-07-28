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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
			+ "You may obtain a free set of LTI credentials by entering a consumer key value (any string of "
			+ "characters that uniquely identifies your LMS) along with your email address into the form below."
			+ "Your LTI credentials will be emailed to you immediately.<p>"
			+ "For further assistance, contact Chuck Wight (admin@chemvantage.org).<p>";
	
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
			+ "<a href=https://community.brightspace.com/resources/additional_resources/scenarios/yourfirsttoolintegrationwithbasiclearningtoolsinteroperability>Desire2Learn</a><p>"
			;
			
	String successMessage = "<h2>Thank You</h2> Your LTI credentials have been sent to your email address.";
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.header + banner + welcomeMessage);
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
		out.println(buf.toString() + Home.footer);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		String lti_message_type = request.getParameter("lti_message_type");
		StringBuffer base_url = request.getRequestURL();
		base_url = base_url.delete(base_url.indexOf("/lti"),base_url.length()).delete(0, base_url.indexOf("://") + 3);
		
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
						out.println(Home.header + banner + successMessage + Home.footer);				
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

}
