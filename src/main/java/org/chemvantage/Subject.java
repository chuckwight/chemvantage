/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
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

import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Random;

import com.google.cloud.ServiceOptions;
import com.googlecode.objectify.NotFoundException;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class Subject {
	@Id Long id;
	private static Subject s;
	
	private String title;
	private String HMAC256Secret;
	private String reCaptchaSecret;
	private String reCaptchaSiteKey;
	private String openai_key;
	private String salt;
	private String announcement;
	private String sendGridAPIKey;
	private int nStarReports;
	private double avgStars;
	private String projectId;
	private String serverUrl;
	private String gptModel;
	private String payPalClientId;
	private String payPalClientSecret;
	
	private Subject() {}
	
	private static void refresh() {
		try {
			if (s==null) s = ofy().load().type(Subject.class).id(1L).safe();
		} catch (NotFoundException e) {  // runs only once at inception of datastore
			s = new Subject();
			s.id = 1L;
			s.title = "General Chemistry";
			s.HMAC256Secret = "ChangeMeInTheDatastoreManuallyForYourProtection";
			s.salt = "ChangeMeInTheDatastoreManuallyForYourProtection";
			s.reCaptchaSecret = "changeMe";
			s.reCaptchaSiteKey = "changeMe";
			s.openai_key = "changeMe";
			s.gptModel = "changeMe";
			s.sendGridAPIKey = "changeMe";
			s.payPalClientId = "changeMe";
			s.payPalClientSecret = "changeMe";
			s.projectId = ServiceOptions.getDefaultProjectId();
			s.serverUrl = "https://" + (s.projectId.equals("dev-vantage-hrd")?"dev-vantage-hrd.appspot.com":"www.chemvantage.org");
			ofy().save().entity(s);
		} catch (Exception e) {  // ofy() not ready
			try {
				Thread.sleep(1000);
				refresh();				// recursive wait for ofy() to be ready
			} catch (Exception e2) {}
		}
	}
	
	static String getTitle() {
		if (s==null) refresh();
		return s.title; 
	}
	
	static String getHMAC256Secret() { 
		if (s==null) refresh();
		return s.HMAC256Secret; 
	}
	
	static String getReCaptchaSecret() {
		if (s==null) refresh();
		return s.reCaptchaSecret;
	}
	
	static String getReCaptchaSiteKey() {
		if (s==null) refresh();
		return s.reCaptchaSiteKey;
	}
	
	static String getSalt() { 
		if (s==null) refresh();
		return s.salt; 
	}
	
	static String getAnnouncement() { 
		if (s==null) refresh();
		return s.announcement; 
	}
	
	static String getSendGridKey() {
		if (s==null) refresh();
		return s.sendGridAPIKey;
	}
	
	static String getPayPalClientId() {
		if (s==null) refresh();
		return s.payPalClientId;
	}
	
	static String getPayPalClientSecret() {
		if (s==null) refresh();
		return s.payPalClientSecret;
	}
	
	static int getNStarReports() { 
		if (s==null) refresh();
		return s.nStarReports; 
	}
	
	static void setAnnouncement(String msg) {
		if (s==null) refresh();
		s.announcement = msg;
		ofy().save().entity(s);
	}
	
	static double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		if (s==null) refresh();
		return Double.valueOf(df2.format(s.avgStars));
	}
	
	static void addStarReport(int stars) {
		if (s==null) refresh();
		s.avgStars = (s.avgStars*s.nStarReports + stars)/(s.nStarReports+1);
		s.nStarReports++;
		ofy().save().entity(s);
	}
		
	static String hashId(String userId) {
		try {
			if (s==null) refresh();
			MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] bytes = md.digest((userId + s.salt).getBytes("UTF-8"));
        	StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
		} catch (Exception e) {
        	return null;
        }
	}
	
	static String getGPTModel() {
		if (s==null) refresh(); 
		return s.gptModel;
	}
	
	static String getOpenAIKey() {
		if (s==null) refresh();
		return s.openai_key;
	}
	
	static String getProjectId() {
		if (s==null) refresh();
		return s.projectId;
	}
	
	static String getServerUrl() {
		if (s==null) refresh();
		return s.serverUrl;
	}
	
	public static String header() {
		return header("","");
	}

	static String header(String title) {
		return header(title, "");
	}
	
	static String header(String title, String customJSFile) {
		StringBuffer buf = new StringBuffer();
		String announcement = Subject.getAnnouncement();
		buf.append("<!DOCTYPE html>\n"
		+ "<html lang='en'>\n"
		+ "<head>\n"
		+ "  <meta charset='UTF-8'>\n"
		+ "  <title>ChemVantage" + (title==null || title.isEmpty()?"":" | " + title) + "</title>\n"
		+ "  <meta name='viewport' content='width=device-width, initial-scale=1'>\n"
		+ "  <meta name='description' content='ChemVantage provides standards-aligned homework, quizzes, and exams for General Chemistry with automatic grading and seamless LMS integration using LTI 1.3 Advantage.'>\n"
		+ "  <meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />\n"
		+ "  <meta http-equiv='Pragma' content='no-cache' />\n"
		+ "  <meta http-equiv='Expires' content='0' />\n"
		+ "  <link rel='icon' href='images/logo_sq.png'>\n"
		+ "  <link rel='preconnect' href='https://fonts.googleapis.com' crossorigin>\n"
		+ "  <link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>\n"
		+ "  <link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;900&family=Shantell+Sans:wght@300;400;500;600;700;800&display=swap'>\n"
		+ "  <link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' integrity='sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH' crossorigin='anonymous'>\n"
		+ "  <script src='/js/script-backend-v01.js'></script>\n"
		+ "  <style>\n"
		+ "    body { font-family: 'Poppins', Arial, sans-serif; }\n"
		+ "    .has-padding {"
		+ "      padding: 20px;"
		+ "    }"
		+ "    .bg-gradient-primary {\n"
		+ "      background: linear-gradient(180deg, #0b5ed7 0%, #0d6efd 100%);\n"
		+ "    }\n"
		+ "    .section-heading + p.lead {\n"
		+ "      max-width: 56rem;\n"
		+ "    }\n"
		+ "    /* Ensure visible focus for keyboard users on minimal devices */\n"
		+ "    :focus-visible {\n"
		+ "      outline: 3px solid #fd7e14;\n"
		+ "      outline-offset: 2px;\n"
		+ "    }\n"
		+ "  </style>\n");
		
		switch (customJSFile) {
		case "checkout":
			buf.append("<script src='https://www.paypal.com/sdk/js?client-id=" + getPayPalClientId() + "&enable-funding=venmo&disable-funding=paylater'></script>");
			buf.append("<script src='/js/checkout.js?r=" + new Random().nextInt() + "'></script>");
			break;
		}
		
		buf.append("</head>"
		+ "<body class='bg-white text-body'>\n"
		+ "  <a href='#main-content' class='visually-hidden-focusable position-absolute start-0 top-0 m-2 p-2 rounded text-dark'>Skip to main content</a>\n"
		+ ((announcement==null || announcement.isEmpty())?"":"<FONT style='color: #EE0000'>" + announcement + "</FONT><br/>\n"
		+ "<main id='main-content'>"));
		
		return buf.toString();
	}
	
	public static String getHeader(User user) {
		String announcement = Subject.getAnnouncement();
		String sig = user.getTokenSignature();
		return "<!DOCTYPE html>\n"
		+ "<html lang='en'>\n"
		+ "<head>\n"
		+ "  <meta charset='UTF-8'>\n"
		+ "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n"
		+ "  <meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />\n"
		+ "  <meta http-equiv='Pragma' content='no-cache' />\n"
		+ "  <meta http-equiv='Expires' content='0' />\n"
		+ "  <link rel='icon' href='images/logo_sq.png'>\n"
		+ "  <title>ChemVantage</title>\n"
		+ "  <link rel='preconnect' href='https://fonts.googleapis.com' crossorigin>\n"
		+ "  <link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>\n"
		+ "  <link rel='stylesheet' href='https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;900&family=Shantell+Sans:wght@300;400;500;600;700;800&display=swap'>\n"
		+ "  <link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' integrity='sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH' crossorigin='anonymous'>\n"
		+ "  <script src='/js/script-backend-v01.js'></script>\n"
		+ "  <style>\n"
		+ "    body { font-family: 'Poppins', Arial, sans-serif; }\n"
		+ "    .has-padding {"
		+ "      padding: 50px;"
		+ "    }"
		+ "    .bg-gradient-primary {\n"
		+ "      background: linear-gradient(180deg, #0b5ed7 0%, #0d6efd 100%);\n"
		+ "    }\n"
		+ "    .section-heading + p.lead {\n"
		+ "      max-width: 56rem;\n"
		+ "    }\n"
		+ "    /* Ensure visible focus for keyboard users on minimal devices */\n"
		+ "    :focus-visible {\n"
		+ "      outline: 3px solid #fd7e14;\n"
		+ "      outline-offset: 2px;\n"
		+ "    }\n"
		+ "  </style>\n"
		+ "</head>"
		+ "<body>\n"
		+ "  <a href='#main-content' class='visually-hidden-focusable position-absolute start-0 top-0 m-2 p-2  rounded text-dark'>Skip to main content</a>\n"
		+ "  <div style='padding: 20px;'>"
		+ "    <a href=/ style='padding-right:25px'>Home</a> "
		+ "    <a href='/Feedback?sig=" + sig + "' style='padding-right:25px'>Feedback</a> "
		+ "    <a href='/Contribute?sig=" + sig + "' style='padding-right:25px'>Authors</a> "
		+ "    <a href='/Edit?sig=" + sig + "' style='padding-right:25px'>Editors</a> "
		+ "    <a href='/Admin?sig=" + sig + "' style='padding-right:25px'>Admin</a> "
		+ "    <a href='/contacts' style='padding-right:25px'>Contacts</a> "
		+ "    <a href='/messages' style='padding-right:25px'>Messages</a> "
		+ "  </div><br/>"
		+ ((announcement==null || announcement.isEmpty())?"":"<FONT style='color: #EE0000'>" + announcement + "</FONT><br/>\n"
		+ "<main id='main-content'>");
	}
	
	static String banner = "<div style='font-size:2em;font-weight:bold;color:#000080;'><img src='/images/CVLogo_thumb.png' alt='ChemVantage Logo' style='vertical-align:middle;width:60px;'> ChemVantage</div>";
			
	public static String footer = "</main>"
			+ "<footer id=footer style='max-width: 600px'><hr/>"
			+ "<a style='text-decoration:none;color:#000080;font-weight:bold' href=/index.html><img src=/images/logo_sq.png alt='ChemVantage logo' style='vertical-align:middle;width:30px;' /> ChemVantage</a> | "
			+ "<a href=/terms_and_conditions.html>Terms and Conditions</a> | "
			+ "<a href=/privacy.html>Privacy</a> | "
			+ "<a href=/copyright.html>Copyright</a></footer>"
			+ "<script>if (window===window.top)document.body.classList.add('has-padding');</script>"
			+ "</body>\n</html>";

}
