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
import com.googlecode.objectify.Objectify;

public class OneQuestion extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

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
			+ "}\n</SCRIPT>";
			
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
		
			out.println(printQuestion(request));
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			out.println(printScore(request));
	}
	
	String printQuestion(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {  // choose a random topic
				List<Key<Topic>> topicKeys = ofy.query(Topic.class).listKeys();
				int random = new Random().nextInt(topicKeys.size());
				topicId=ofy.get(topicKeys.get(random)).id;
			}
			Topic topic = ofy.get(Topic.class,topicId);

			String questionType = request.getParameter("QuestionType");
			if (questionType==null) questionType = "Quiz";
			
			buf.append("\n<h2>" + topic.title + "</h2>");
			
			buf.append("\n<FORM METHOD=POST>");
			
			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = ofy.query(Question.class).filter("topicId", topicId).filter("assignmentType",questionType).filter("isActive",true).listKeys();
			if (questionKeys.size() == 0) {
				buf.append("No questions are available for this topic, sorry. <a href=/OneQuestion>Try Again.</a>");
				return buf.toString();
			}
			// Randomly select one questions to be presented
			Random rand = new Random();  // create random number generator to select quiz questions
			Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
			Question q = ofy.get(k);
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
					questionKeys.add(new Key<Question>(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			long topicId = 0L;
			String questionType = "Quiz";
					
			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = ofy.get(k);
							int param = Integer.parseInt(request.getParameter("Param"));
							q.setParameters(param);
							topicId = q.topicId;
							questionType = q.assignmentType;
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							if (score>0) buf.append("<h2>Congratulations! Your answer is correct.</h2>");
							else {
								buf.append("<h2>Sorry, you did not submit the correct answer.</h2>");							
								buf.append("\n" + q.printAllToStudents(studentAnswer[0]) + "\n");
								buf.append(ajaxSubmitScript);
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			buf.append("<a href=/OneQuestion?TopicId=" + topicId + "&QuestionType=" + questionType + ">Try another question on this topic</a>");
		} catch (Exception e) {
			buf.append("Sorry, this question could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
}
