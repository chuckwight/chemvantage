/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2014 ChemVantage LLC
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

public class OneQuestion extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();

	String ajaxSubmitScript = "<SCRIPT TYPE='text/javascript'>\n"
			+ "function ajaxSubmit(url,id,note) {\n"
			+ "  var xmlhttp;\n"
			+ "  if (url.length==0) return false;\n"
			+ "  xmlhttp=GetXmlHttpObject();\n"
			+ "  if (xmlhttp==null) {\n"
			+ "    alert ('Sorry, your browser does not support AJAX!');\n"
			+ "    return false;\n"
			+ "  }\n"
			+ "  xmlhttp.onreadystatechange=function() {\n"
			+ "    if (xmlhttp.readyState==4) {\n"
			+ "      document.getElementById('feedback' + id).innerHTML="
			+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
			+ "    }\n"
			+ "  }\n"
			+ "  url += '&QuestionId=' + id + '&Notes=' + note;\n"
			+ "  xmlhttp.open('GET',url,true);\n"
			+ "  xmlhttp.send(null);\n"
			+ "  return false;\n"
			+ "}\n"
			+ "function GetXmlHttpObject() {\n"
			+ "  if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari\n"
			+ "    return new XMLHttpRequest();\n"
			+ "  }\n"
			+ "  if (window.ActiveXObject) { // code for IE6, IE5\n"
			+ "    return new ActiveXObject('Microsoft.XMLHTTP');\n"
			+ "  }\n"
			+ "  return null;\n"
			+ "}\n"
			+ "</SCRIPT>";
			
	public String getServletInfo() {
		return "This servlet presents a single question for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			if (Login.lockedDown) {
				response.sendRedirect("/");
				return;
			}				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
		
			out.println(Login.header + printQuestion(request) + Login.footer);
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			out.println(Login.header + printScore(request) + Login.footer);
	}
	
	String printQuestion(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			String questionType = request.getParameter("QuestionType");
			if (questionType==null) questionType = "Quiz";
			List<Key<Question>> questionKeys;
			
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
				questionKeys = ofy().load().type(Question.class).filter("topicId", topicId).filter("assignmentType",questionType).filter("isActive",true).keys().list();
				if (questionKeys.size()==0) throw new Exception();  // accidentally chose a topic with no questions; choose a random topic instead
			} catch (Exception e2) {
				do {  // choose a random topic
					List<Key<Topic>> topicKeys = ofy().load().type(Topic.class).keys().list();
					int random = new Random().nextInt(topicKeys.size());
					topicId=ofy().load().key(topicKeys.get(random)).safe().id;
					questionKeys = ofy().load().type(Question.class).filter("topicId", topicId).filter("assignmentType",questionType).filter("isActive",true).keys().list();
				} while (questionKeys.size()==0);
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			
			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - " + subject.title + "</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");

			buf.append("\n<h2>" + topic.title + "</h2>");
			
			buf.append("\n<FORM METHOD=POST>");
			
			// Randomly select one question to be presented
			Random rand = new Random();  // create random number generator to select quiz questions
			Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
			Question q = ofy().load().key(k).safe();
			int param = rand.nextInt();
			q.setParameters(param);  // randomizes parameterized questions
			buf.append("\n" + q.print() + "<br>\n");
			
			buf.append("\n<input type=hidden name='TopicId' value=" + topic.id + ">");
			buf.append("\n<input type=hidden name='Param' value=" + param + ">");
			buf.append("\n<input type=submit>");
			buf.append("\n</form>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String printScore(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			long topicId = 0L;
			String questionType = "Quiz";
					
			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - " + subject.title + "</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");

			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = ofy().load().key(k).safe();
							int param = Integer.parseInt(request.getParameter("Param"));
							q.setParameters(param);
							topicId = q.topicId;
							questionType = q.assignmentType;
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							if (score>0) buf.append("<h2>Congratulations! Your answer is correct.</h2>");
							else {
								buf.append("<h2>Sorry, you did not submit the correct answer.</h2>");							
								buf.append("\n" + q.printAllToStudents(studentAnswer[0]) + "<p>\n");
								buf.append(ajaxSubmitScript);
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			if (topicId>0) buf.append("Try another question on "
					+ "<a href=/q?TopicId=" + topicId + "&QuestionType=" + questionType + ">this topic</a> "
					+ "or <a href=/q>any topic</a> in General Chemistry, or "
					+ "<a href=/Home>return to the home page</a>.");
			else buf.append("<a href=/q>Try another question</a>");
		} catch (Exception e) {
			buf.append("Sorry, this question could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
}
