/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2022 ChemVantage LLC
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
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/items")
public class ItemBank extends HttpServlet {
	private static final long serialVersionUID = 1L;
/*
 * This servlet provides access to the ChemVantage question items for use by instructors in their class quizzes, homework sdets and exams.
 * 1) Normal access is via coded link (encrypted version of the user's email address)
 * 2) Users are required to accept the terms of a non-exclusive license that gives permission to use the items without attribution for
 *    non-commercial educational use. The licensee may not transfer the license to any other party.
 * 3) First access to the servlet requires completing a form for the user's email address, first name, last name and license agreement. The 
 *    coded link is then sent to the user's email address for confirmation.
 */
    public ItemBank() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			String code = request.getParameter("code");
			if (code==null) throw new Exception();
			
			String email = decode(code);
			Contact c = ofy().load().type(Contact.class).id(email).safe();
			if (c.itemLicensed) out.println(Subject.header("ChemVantage Item Bank") + itemBank(code,request) + Subject.footer);
			else out.println(Subject.header("ChemVantage Item Bank") + licenseForm(c) + Subject.footer);
		} catch (Exception e) {
			out.println(Subject.header("ChemVantage Item Bank") + registrationForm(request.getParameter("msg")) + Subject.footer);
		}	
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String userRequest = request.getParameter("UserRequest");
		Contact c = null;
		switch (userRequest) {
		case "Register":
			try {
				c = registerContact(request);
				if (c==null) throw new Exception();
				if (c.vetted) {
					String url = "https://www.chemvantage.org/items?code=" + encode(c.email); 
					String messageBody = "Thank you for your interest in using ChemVantage question items for your teaching. "
							+ "Please use the personalized coded link below to access the database of questions.<br/><br/>"
							+ "<a href='" + url + "'>" + url + "</a>";
					sendEmail(c,messageBody);
					out.println(Subject.header("Success") + Subject.banner 
							+ "<h3>Thank you</h3>"
							+ "A personalized coded link has been sent to you at " + c.email + "<br/>"
							+ Subject.footer);
				} else {
					String messageBody = "The person below has requested access to the ChemVantage question item bank:<br/>"
							+ "Name: " + c.getFullName() + "<br/>"
							+ "Email: " + c.getEmail() + "<br/>"
							+ "Institution: " + c.institution + "<br/>";
					Contact a = ofy().load().type(Contact.class).id("admin@chemvantage.org").now();
					sendEmail(a,messageBody);
					out.println(Subject.header("Success") + Subject.banner 
							+ "<h3>Thank you</h3>"
							+ "We will review your request and send a personalized coded access link to you at " + c.email + "<br/>"
							+ Subject.footer);
				}
			} catch (Exception e) {
				response.sendRedirect("/items?msg=Registration+failed.+Please+try+again.");
			}
			break;
		case "Approve":
			User user = User.getUser(request.getParameter("sig"));
			if (user==null || !user.isChemVantageAdmin()) break;
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
			if (c != null) {
				c.vetted = true;
				ofy().save().entity(c);
				String url = "https://www.chemvantage.org/items?code=" + encode(c.email); 
				String messageBody = Subject.banner + "<h3>Question Item Bank</h3>"
						+ "Thank you for your interest in ChemVantage. Your request has been "
						+ "approved, and you may now access our item bank using the personalized "
						+ "coded link below. Please reply to this email if you have questions.<br/><br/>"
						+ "<a href='" + url + "'>" + url + "</a>";
				try {
					sendEmail(c,messageBody);
					out.println(Subject.header("Success") + Subject.banner 
							+ "<h2>The approval email was sent to " + c.email + "</h2>" 
							+ Subject.footer);
				} catch (Exception e) {
					out.println(Subject.header("Failure") + Subject.banner 
							+ "<h2>Failed</h2>The approval email could not be sent to " + c.email + "<br/><br/>" 
							+ Subject.footer);
				}
			}
			break;
		case "Deny":
			user = User.getUser(request.getParameter("sig"));
			if (user==null || !user.isChemVantageAdmin()) break;
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
			if (c != null) ofy().delete().entity(c);
			out.println(Subject.header("Item Bank") + Subject.banner 
					+ "<h2>The contact " + c.getFullName() + " (" + c.email + ") was deleted.</h2>"
					+ Subject.footer);
			break;
		case "ConfirmLicense":
			try {
				c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
				confirmLicense(c, request);
			} catch (Exception e) {
			}
			response.sendRedirect("/items?code=" + encode(c.email));
			break;
		default: doGet(request,response);
		}
	}

	String registrationForm(String msg) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		if (msg != null) buf.append("<br/><div style='color:red'>" + msg + "</div>");
		buf.append("<h2>Question Item Bank</h2>"
				+ "ChemVantage is pleased to share the question items in its database with instructors for their private "
				+ "noncommercial use in teaching chemistry courses. To access the question item bank, please complete the "
				+ "form below using your institutional email address. A coded link will be sent that address for viewing "
				+ "any of the thousands of quiz questions or hundreds of homework questions (with millions of variations) "
				+ "in our database.<br/><br/>");
		
		buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'></script>");
		
		buf.append("<form method=post action=/items>"
				+ "<input type=hidden name=UserRequest value=Register />"
				+ "<label>First Name: <input type=text name=FirstName /></label> "
				+ "<label>Last Name: <input type=text name=LastName /></label><br/>"
				+ "<label>Your Institution\'s URL: <input type=text name=OrgURL placeholder=myschool.edu /></label><br/>"
				+ "<label>Your Institutional Email Address: <input type=text name=Email /></label><br/>"
				+ "<label><input type=checkbox name=License value=True /> "
				+ "I certify that I am a chemistry instructor and if approved, ChemVantage LLC will grant me a non-exclusive license to use ChemVantage question items "
				+ "without attribution for private, non-commercial use in my teaching activities (e.g., lecture examples, class quizzes, "
				+ "homework problem sets and exams). I agree not to share the items publicly outside of my teaching activities. "
				+ "I also understand that the copyright to these materials belongs to ChemVantage LLC and "
				+ "may not be otherwise shared publicly except under the terms of a <a href=https://creativecommons.org/licenses/by/3.0/us/>"
				+ "Creative Commons Attribution 3.0 License</a>.</label><br/>"
				+ "<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG' aria-label='Google Recaptcha'></div><br/>"
				+ "<input type=submit /><br/><br/>"
				+ "</form>");
		return buf.toString();
	}
	
	Contact registerContact(HttpServletRequest request) throws Exception {
		String firstName = request.getParameter("FirstName");
		String lastName = request.getParameter("LastName");
		String orgURL = request.getParameter("OrgURL");
		String email = request.getParameter("Email");
		if (!reCaptchaOK(request)) return null;
		boolean licensed = Boolean.parseBoolean(request.getParameter("License"));
		Contact c = ofy().load().type(Contact.class).id(email).now();
		if (c==null) {
			if (firstName!=null && lastName!=null && orgURL!=null && email!=null) {
				c = new Contact(firstName,lastName,email);
				c.institution = orgURL;
				c.itemLicensed = licensed;
				c.vetted = false;
				ofy().save().entity(c).now();
			}
		} else {
			c.itemLicensed = licensed;
			c.vetted = true;
			ofy().save().entity(c).now();
		}
		return c;
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

	void sendEmail(Contact c,String messageBody) throws Exception {
		Message msg = new MimeMessage(Session.getDefaultInstance(new Properties()));
		InternetAddress from = new InternetAddress("admin@chemvantage.org", "ChemVantage");
		msg.setFrom(from);
		msg.addRecipient(Message.RecipientType.TO,new InternetAddress(c.getEmail(),c.getFullName()));
		msg.addRecipient(Message.RecipientType.CC,from);
		msg.setSubject("ChemVantage Question Item Bank");
		msg.setContent(messageBody,"text/html");
		Transport.send(msg);
	}
		
	String licenseForm(Contact c) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		buf.append("<h3>Please accept the terms of the ChemVantage non-attribution license</h3>"
				+ "<form method=post action=/items>"
				+ "<input type=hidden name=UserRequest value=ConfirmLicense />"
				+ "<input type=hidden name=Email value='" + c.email + "' />"
				+ "Name: " + c.firstName + " " + c.lastName + "<br/>"
				+ "<label><input type=checkbox name=License value=True /> "
				+ "I certify that I am a chemistry instructor and if approved, ChemVantage LLC will grant me a non-exclusive license to use ChemVantage question items "
				+ "without attribution for private, non-commercial use in my teaching activities (e.g., lecture examples, class quizzes, "
				+ "homework problem sets and exams. I agree not to share the items publicly outside of my teaching activities. "
				+ "I also understand that the copyright to these materials belongs to ChemVantage LLC and "
				+ "may not be otherwise shared publicly except under the terms of a <a href=https://creativecommons.org/licenses/by/3.0/us/>"
				+ "Creative Commons Attribution 3.0 License</a>.</label><br/>"
				+ "<input type=submit value='I Agree' />"
				+ "</form><br/><br/>");
		return buf.toString();
	}
	
	void confirmLicense(Contact c, HttpServletRequest request) {
		c.itemLicensed=Boolean.parseBoolean(request.getParameter("License"));
		ofy().save().entity(c).now();
	}
	
	String itemBank(String code,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		buf.append("<h2>Question Item Bank</h2>");
		
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e2) {}
		String assignmentType = request.getParameter("AssignmentType");
		boolean showQuestions = (topicId >0 && assignmentType != null && assignmentType.length()>0);
		buf.append("<FORM NAME=TopicSelect METHOD=GET ACTION=/items>");
		buf.append("<input type=hidden name=code value='" + code + "' />");
		buf.append("<FONT" + (request.getParameter("TopicId")!=null && topicId==0?" COLOR=RED>":">") + "<b>Topic:</b></FONT>" + topicSelectBox(topicId,true));
		buf.append("<FONT" + (assignmentType!=null && assignmentType.length()==0?" COLOR=RED>":">") + "<b> Assignment Type:</b></FONT>" + assignmentTypeDropDownBox(assignmentType,true));
		buf.append("<span style='display:none' id=refreshing > Please wait...</span>");
		buf.append("</FORM><br/>");
				
		if (!showQuestions) return buf.toString();
		
		if ("Homework".equals(assignmentType)) buf.append("For parameterized questions, you can view another version by refreshing your browser page.<br/><br/><hr>");
		else buf.append("<br/><hr>");
		
		List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).list();
		if (questions.size()==0) buf.append("Sorry, this topic contains no questions of this type.");
		
		for (Question q : questions) {
			q.setParameters();
			buf.append("<br/>" + q.printAll() + "<hr>");
		}
		return buf.toString();	
	}
	
	String assignmentTypeDropDownBox(String defaultType,boolean autoSubmit) {
		if (defaultType == null) defaultType = "";
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=AssignmentType" + (autoSubmit?" onChange=document.getElementById('refreshing').style='display:inline';submit()>":">"));
		if (defaultType.length() == 0) buf.append("\n<OPTION VALUE=''>Select a type</OPTION>");
		buf.append("<OPTION" + (defaultType.equals("Quiz")?" SELECTED":"") + ">Quiz</OPTION>"
		+ "<OPTION" + (defaultType.equals("Homework")?" SELECTED":"") + ">Homework</OPTION>"
		+ "</SELECT>");
		return buf.toString();
	}

	String topicSelectBox(long topicId,boolean autoSubmit) {
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=TopicId" + (autoSubmit?" onChange=document.getElementById('refreshing').style='display:inline';submit()>":">"));
		if (topicId == 0) buf.append("\n<OPTION VALUE=''>Select a topic</OPTION>");
		Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
		for (Topic t : topics) {
			if ("Hide".equals(t.orderBy)) continue;
			buf.append("<OPTION VALUE=" + t.id + (t.id.equals(topicId)?" SELECTED>":">")
					+ t.title + "</OPTION>\n");
		}
		buf.append("</SELECT>");
		return buf.toString();
	}
		
	String encode(String email) {
		/* This method uses a simple one-time pad to encrypt a userId value prior to storing inthe database.
		 * The uses sig as a seed for Random. Each 8-bit random integer (0 to 127) is XORed with one byte
		 * of the input String and converted to a two-character hexadecimal number for storage as a printable value.
		 * The final output should have 2 characters for every byte of input, so the encryption is weak.
		 */
		try {
			byte[] input = email.getBytes("UTF-8");
			long seed = new Date().getTime();
			seed = seed%256;
			String output = (seed<16?"0":"") + Integer.toHexString((int)seed);
			Random rand = new Random(seed);
			for (int i=0;i<input.length;i++) {
				int a = rand.nextInt(256);
				int b = (int) input[i];
				int xor = a^b;
				output += (xor<16?"0":"") + Integer.toHexString(xor);  // retains leading zero, if present
			}
			return output;
		} catch (Exception e) {
			return null;
		}
	}

	String decode(String code) { 
		/* This method reverses the encrypt method above by converting each pair of hexadecimal characters in the input
		 * to an integer, XORing that with a pseudo-random integer based on sig, converting to a single byte and 
		 * finally to a String character, which is appended to the output for each pair of hexadecimal input characters.
		 */
		try {
			int seed = Integer.parseInt(code.substring(0,2),16);  // first 2 characters of code
			code = code.substring(2); // remainder of encoded string
			int length = code.length()/2;
			byte[] output = new byte[length];
			Random rand = new Random(seed);
			for (int i=0;i<length;i++) {
				int a = rand.nextInt(256);
				int b = Integer.parseInt(code.substring(2*i, 2*i+2),16);
				Integer xor = a^b;
				output[i] = xor.byteValue(); 
			}
			return new String(output,"UTF-8");	
		} catch (Exception e) {
			return e.toString() + " " + e.getMessage();
		}
	}

}
