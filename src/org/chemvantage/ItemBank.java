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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
			String email = decode(code);
			Contact c = ofy().load().type(Contact.class).id(email).safe();
			if (c.itemLicensed) out.println(Subject.header("ChemVantage Item Bank") + itemBank(code,request) + Subject.footer);
			else out.println(Subject.header("ChemVantage Item Bank") + licenseForm(c) + Subject.footer);
		} catch (Exception e) {
			out.println(Subject.header("ChemVantage Item Bank") + registrationForm() + Subject.footer);
		}	
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String userRequest = request.getParameter("UserRequest");
		Contact c = null;
		switch (userRequest) {
		case "Register":
			c = registerContact(request);
			response.sendRedirect("/items" + (c==null?"":"?code="+encode(c.email)));
			break;
		case "ConfirmLicense":
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).now();
			confirmLicense(c, request);
			break;
		default: doGet(request,response);
		}
	}

	String registrationForm() {
		StringBuffer buf = new StringBuffer(Subject.banner);
		buf.append("<h2>Question Item Bank</h2>"
				+ "ChemVantage is pleased to share the question items in its database with instructors for their private "
				+ "noncommercial use in teaching chemistry courses. To access the question item bank, please complete the "
				+ "form below using your institutional email address. A coded link will be sent that address for viewing "
				+ "any of the thousands of quiz questions or hundreds of homework questions (with millions of variations) "
				+ "in our database.<br/><br/>");
		buf.append("<form method=post action=/items>"
				+ "<input type=hidden name=UserRequest value=Register />"
				+ "<label>First Name: <input type=text name=FirstName /></label> "
				+ "<label>Last Name: <input type=text name=LastName /></label><br/>"
				+ "<label>Your Institution\'s URL: <input type=text name=OrgURL placeholder=myschool.edu /></label><br/>"
				+ "<label>Your Institutional Email Address: <input type=text name=Email /></label><br/>"
				+ "<label><input type=checkbox name=License value=True /> "
				+ "I understand that if approved, ChemVantage LLC will grant me a non-exclusive license to use ChemVantage question items "
				+ "without attribution for private, non-commercial use in my teaching activities (e.g., lecture examples, class quizzes, "
				+ "homework problem sets and exams). I agree not to share the items publicly outside of my teaching activities. "
				+ "I also understand that the copyright to these materials belongs to ChemVantage LLC and "
				+ "may not be otherwise shared publicly except under the terms of a <a href=https://creativecommons.org/licenses/by/3.0/us/>"
				+ "Creative Commons Attribution 3.0 License</a>.</label><br/>"
				+ "<input type=submit /><br/><br/>"
				+ "</form>");
		return buf.toString();
	}
	
	Contact registerContact(HttpServletRequest request) {
		String firstName = request.getParameter("FirstName");
		String lastName = request.getParameter("LastName");
		String orgURL = request.getParameter("OrgURL");
		String email = request.getParameter("Email");
		boolean licensed = Boolean.parseBoolean(request.getParameter("License"));
		Contact c = null;
		if (firstName!=null && lastName!=null && orgURL!=null && email!=null) {
			c = new Contact(firstName,lastName,email);
			c.institution = orgURL;
			c.itemLicensed = licensed;
			ofy().save().entity(c).now();
		}
		return c;
	}
	
	String licenseForm(Contact c) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		buf.append("<h3>Please accept the terms of the ChemVantage non-attribution license</h3>"
				+ "<form method=post action=/items>"
				+ "<input type=hidden name=UserRequest value=ConfirmLicense />"
				+ "<input type=hidden name=Email value='" + c.email + "' />"
				+ "Name: " + c.firstName + " " + c.lastName + "<br/>"
				+ "<label><input type=checkbox name=License value=True /> "
				+ "I understand that if approved, ChemVantage LLC will grant me a non-exclusive license to use ChemVantage question items "
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
		buf.append("<FONT" + (request.getParameter("TopicId")!=null && topicId==0?" COLOR=RED>":">") + "<b>Topic:</b></FONT>" + topicSelectBox(topicId,showQuestions));
		buf.append("<FONT" + (assignmentType!=null && assignmentType.length()==0?" COLOR=RED>":">") + "<b> Assignment Type:</b></FONT>" + assignmentTypeDropDownBox(assignmentType,true));
		buf.append("</FORM><br/>");
				
		if (!showQuestions) return buf.toString();
		
		buf.append("For parameterized questions, you can view another version by refreshing your browser page.<br/><br/><hr>");

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
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=AssignmentType" + (autoSubmit?" onChange=submit()>":">"));
		if (defaultType.length() == 0) buf.append("\n<OPTION VALUE=''>Select a type</OPTION>");
		buf.append("<OPTION" + (defaultType.equals("Quiz")?" SELECTED":"") + ">Quiz</OPTION>"
		+ "<OPTION" + (defaultType.equals("Homework")?" SELECTED":"") + ">Homework</OPTION>"
		+ "</SELECT>");
		return buf.toString();
	}

	String topicSelectBox(long topicId,boolean autoSubmit) {
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=TopicId" + (autoSubmit?" onChange=submit()>":">"));
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
