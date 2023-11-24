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
import java.net.URLEncoder;
import java.util.ArrayList;
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
import com.googlecode.objectify.Key;

@WebServlet(urlPatterns = {"/itembank","/items"})
public class ItemBank extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Text text = null;
	
/*
 * This servlet provides access to the ChemVantage question items for use by instructors in their class quizzes, homework sets and exams.
 * 1) Normal access is via GET request to /itembank for 10 items without correct answers or detailed solutions (q.print())
 * 2) Instructors may apply for a coded link to access items with solutions
 */
	
    public ItemBank() {
        super();
    }

   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if (text==null) text =  ofy().load().type(Text.class).filter("title","View All Topics").first().now();
		
		String p = request.getParameter("p");
		if ("iframe".equals(p)) out.println(itemBank(request));
		else out.println(Subject.header("ChemVantage Item Bank") + Subject.banner + "<h4>Question Item Bank</h4>" + itemBank(request) + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		StringBuffer debug = new StringBuffer("Debug: ");
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) doGet(request,response);
		
		Contact c = null;
		switch (userRequest) {
		case "Register":
			try {
				c = registerContact(request);
				// respond to 3 cases: 
				// 1) contact is already vetted as instructor
				// 2) contact is applicant pending approval
				
				if ("faculty".equals(c.role) || "chair".equals(c.role)) {
					String url = "https://www.chemvantage.org/itembank?code=" + encode(c.email); 
					String messageBody = Subject.banner + "Thank you for your interest in using ChemVantage question items for your teaching. "
							+ "Please use the personalized coded link below to access the ChemVantage bank of question items.<br/><br/>"
							+ "<a href='" + url + "'>" + url + "</a>";
					sendEmail(c,messageBody);
					String msg = "Thank+you.+A+personalized+coded+access+link+has+been+sent+to+you+at+" + c.email;
					response.sendRedirect("/itembank?msg=" + msg);
				} else {
					debug.append("applicant:");
					String messageBody = "The person below has requested access to the ChemVantage question item bank:<br/>"
							+ "Name: " + c.getFullName() + "<br/>"
							+ "Email: " + c.getEmail() + "<br/>"
							+ "Institution: " + c.institution + "<br/>";
					debug.append("1");
					Contact a = ofy().load().type(Contact.class).id("admin@chemvantage.org").now();
					debug.append("2");
					sendEmail(a,messageBody);
					debug.append("3");
					String msg = URLEncoder.encode("Thank you. We will evaluate your request and send a personalized access link to " + c.email,"UTF-8");
					debug.append("4");
					response.sendRedirect("/itembank?msg=" + msg);
				}
			} catch (Exception e) {
				response.sendRedirect("/itembank?msg=" + URLEncoder.encode("Registration failed. " + (e.getMessage()==null?e.toString():e.getMessage()),"UTF-8"));
			}
			return;
		case "Approve":
			User user = User.getUser(request.getParameter("sig"));
			if (user==null || !user.isChemVantageAdmin()) break;
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
			c.role = "faculty";
			c.vetted = true;
			ofy().save().entity(c);
			String url = "https://www.chemvantage.org/itembank?code=" + encode(c.email); 
			String messageBody = Subject.banner + "<h3>ChemVantage Question Item Bank</h3>"
					+ "Thank you for your interest in ChemVantage. Your request has been "
					+ "approved, and you may now access our item bank, including the correct "
					+ "responses and complete solutions, using the personalized coded link below. "
					+ "Please reply to this email if you have questions.<br/><br/>"
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
			return;
		case "Deny":
			user = User.getUser(request.getParameter("sig"));
			if (user==null || !user.isChemVantageAdmin()) break;
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
			c.role = "learner";
			c.vetted = false;
			ofy().save().entity(c);
			messageBody = Subject.banner + "<h3>ChemVantage Question Item Bank</h3>"
					+ "Thank you for your interest in ChemVantage. Unfortunately, we were unable to independently "
					+ "confirm your role as an instructor using the information that you provided. If we made a mistake, "
					+ "please reply to this email with any additional information. Thanks.<br/><br/>";
			try {
				sendEmail(c,messageBody);
				out.println(Subject.header("Success") + Subject.banner 
						+ "<h2>The denial email was sent to " + c.email + "</h2>" 
						+ Subject.footer);
			} catch (Exception e) {
				out.println(Subject.header("Failure") + Subject.banner 
						+ "<h2>Failed</h2>The denial email could not be sent to " + c.email + "<br/><br/>" 
						+ Subject.footer);
			}
			return;
		}
	}

	Contact registerContact(HttpServletRequest request) throws Exception {
		String firstName = request.getParameter("FirstName");
		String lastName = request.getParameter("LastName");
		String orgURL = request.getParameter("OrgURL");
		String email = request.getParameter("Email");
		if (!reCaptchaOK(request)) throw new Exception("Bad ReCAPTCHA. Please try again.");
		Contact c = ofy().load().type(Contact.class).id(email).now();
		if (c==null) {  // create a new Contact with the role "applicant"
			c = new Contact(firstName,lastName,email);
			c.institution = orgURL;
			c.role = "applicant";
			c.vetted = false;
		} else {  // update an existing contact but change role only if not vetted already
			c.firstName = firstName;
			c.lastName = lastName;
			c.institution = orgURL;
			if (!c.vetted) c.role="applicant";
		}
		ofy().save().entity(c).now();
		return c;
	}
	
	boolean reCaptchaOK(HttpServletRequest request) throws Exception {
		try {
			String queryString = "secret=" + Subject.getReCaptchaSecret() + "&response=" 
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
		} catch (Exception e) {
			throw new Exception("Bad ReCaptcha. Please try again.");
		}
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
		
	String itemBank(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<style>"
				+ " body {background-color: white; font-family: Calibri,Arial,sans-serif;}"
				+ "</style>");
		StringBuffer debug = new StringBuffer("Debug: ");
		
		try {
			String msg = request.getParameter("msg");
			if (msg!=null) buf.append("<span style='color:red;'>" + msg + "</span><br/><br/>");
			
			String code = null;
			String p = null;
			Contact contact = null;
			boolean isInstructor = false;
			try {
				code = request.getParameter("code");
				p = request.getParameter("p");
				String email = decode(code);
				contact = ofy().load().type(Contact.class).id(email).safe();
				isInstructor = contact.role!=null && (contact.role.equals("faculty") || contact.role.equals("chair"));
			} catch (Exception e) {}
			
			Chapter chapter = null;
			try {
				int chapterNumber = Integer.parseInt(request.getParameter("Topic"));
				for (Chapter c : text.chapters) {
					if (c.chapterNumber == chapterNumber) {
						chapter = c;
						break;
					}
				}
			} catch (Exception e) {
			}
			
			String assignmentType = request.getParameter("Type");
			if (assignmentType==null) assignmentType = "";
			switch (assignmentType) {
			case "Quiz":
			case "Homework":
				break;
			default: assignmentType = "";
			}
			
			boolean showQuestions = (chapter != null && !assignmentType.isEmpty());
			
			buf.append("More than 5000 ChemVantage question items are free to use under the terms of a "
					+ "<a href=https://creativecommons.org/licenses/by/4.0/ target=_blank>Creative Commons CC-BY License</a>. "
					+ "Instructors can access the correct answers and full solutions through their campus "
					+ "LMS. <a href=Registration.jsp target=_blank>Registration is free</a>.<br/><br/>");

			buf.append("<FORM NAME=TopicSelect METHOD=GET ACTION=/itembank>");
			buf.append(p==null?"":"<input type=hidden name=p value=iframe />");
			buf.append(code==null?"":"<input type=hidden name=code value='" + code + "' />");
			buf.append("<b>Topic: </b>" + chapterSelectBox(text,chapter,true));
			buf.append("&nbsp;&nbsp;<b>Assignment Type: </b>" + assignmentTypeDropDownBox(assignmentType,true));
			buf.append("&nbsp;&nbsp;<input type=submit value='View Items' /><br/><br/>");
			
			if (!showQuestions) return buf.toString() + "<br/><br/>";

			List<Key<Question>> keys = new ArrayList<Key<Question>>();
			for (Long cId : chapter.conceptIds)	keys.addAll(ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("conceptId",cId).keys().list());
			if (keys.size()==0) buf.append("Sorry, this topic contains no questions of this type.");
			
			Random rand = new Random();
			List<Key<Question>> itemKeys = new ArrayList<Key<Question>>();
			while (itemKeys.size() < 3 && keys.size() > 0) {
				itemKeys.add(keys.remove(rand.nextInt(keys.size())));
			}
			
			List<Question> questions = new ArrayList<Question>(ofy().load().keys(itemKeys).values());
			for (Question q : questions) {
				q.setParameters();
				buf.append("<br/>" + (isInstructor?q.printAll():q.print()) + "<hr>");
			}
		} catch (Exception e) {
			buf.append("<br/>Error: " + e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString());
		}
		return buf.toString();
	}
	
	String assignmentTypeDropDownBox(String assignmentType,boolean autoSubmit) {
		if (assignmentType==null) assignmentType="";
		StringBuffer buf = new StringBuffer("<SELECT id=type NAME=Type>");
		try {
			if (assignmentType.isEmpty()) buf.append("<OPTION VALUE=''>Select a type</OPTION>");
			buf.append("<OPTION" + (assignmentType.equals("Quiz")?" SELECTED":"") + ">Quiz</OPTION>"
					+ "<OPTION" + (assignmentType.equals("Homework")?" SELECTED":"") + ">Homework</OPTION>"
					+ "</SELECT>");
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}

	String chapterSelectBox(Text text,Chapter chapter,boolean autoSubmit) throws Exception {
		if (text == null) return "";
		StringBuffer buf = new StringBuffer("<SELECT id=topic NAME=Topic>");
		try {
			if (chapter==null) buf.append("<OPTION VALUE=''>Select a topic</OPTION>");
			for (Chapter c : text.chapters) {
				buf.append("<OPTION VALUE='" 
						+ c.chapterNumber + "'" + (chapter!=null && c.chapterNumber==chapter.chapterNumber?" SELECTED>":">") + c.title 
						+ "</OPTION>");
			}
			buf.append("</SELECT>");
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
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

	String decode(String code) throws Exception { 
		/* This method reverses the encrypt method above by converting each pair of hexadecimal characters in the input
		 * to an integer, XORing that with a pseudo-random integer based on sig, converting to a single byte and 
		 * finally to a String character, which is appended to the output for each pair of hexadecimal input characters.
		 */
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
	}

}
