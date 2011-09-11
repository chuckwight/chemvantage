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

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class Homework extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet presents a homework assignment for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			out.println(Home.getHeader(user) + printHomework(user,request) + Home.footer);
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			out.println(Home.getHeader(user) + printScore(user,request) + Home.footer);
		} catch (Exception e) {}
	}

	String printHomework(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {
				return "<h2>No Homework Assignment Selected</h2>You must return to the <a href=Home>Home Page</a> "
				+ "and select a topic for this assignment using the drop-down box.";
			}
			Topic topic = ofy.get(Topic.class,topicId);

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);
			Date now = new Date();

			buf.append("\n<h2>Homework Exercises - " + topic.title + " (" + subject.title + ")</h2>");
			buf.append("\n<b>" + user.getBothNames() + "</b><br>");
			buf.append(df.format(now) + "<p>");

			buf.append("\nHomework Rules<UL>");
			buf.append("\n<LI>You may rework problems and resubmit answers as many times as you wish, to improve your score.</LI>");
			buf.append("\n<LI>Most questions are customized, so the correct answers are different for each student.</LI>");
			buf.append("\n<LI>For each topic, the server reports your total score and the total number of submissions.</LI>");
			buf.append("\n<LI>A checkmark will appear to the left of each correctly solved problem.</LI>");
			if (myGroup != null) {
				buf.append("However, class credit for assigned problems is awarded only if the answer is submitted prior to the deadline.</LI>");
				buf.append("\n<LI>Your instructor can view scores and submissions by date/time in order to enforce homework deadlines.</LI>");
			}
			buf.append("</UL>");

			List<Key<Question>> optionalQuestionKeys = ofy.query(Question.class).filter("assignmentType","Homework").filter("topicId",topicId).filter("isActive",true).listKeys();
			long hi = myGroup==null?0L:myGroup.getAssignmentId("Homework",topic.id);
			Assignment hwa = hi>0?ofy.get(Assignment.class,hi):null;
			if (hwa != null) { // use hwa.questionIds to move assigned questions to the other list
				optionalQuestionKeys.removeAll(hwa.questionKeys);
				buf.append("\nAssigned Exercises<br>");
				int i = 1;
				buf.append("<TABLE>");
				for (Key<Question> k : hwa.questionKeys) {
					try {
						Question q = ofy.get(k);
						q.setParameters(user.id.hashCode());
						buf.append("\n<TR VALIGN=TOP><TD>");
						if (user.getHWQuestionScore(q.id) > 0) buf.append("<IMG SRC=/images/checkmark.gif ALT='OK'>");
						buf.append("&nbsp;<a id=" + q.id + " /></TD>"
								+ "<FORM METHOD=POST ACTION=Homework>"
								+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=GradeHomework>"
								+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
								+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
								+ "<TD><b>" + i + ". </b></TD><TD>" + q.print() 
								+ (Long.toString(q.id).equals(request.getParameter("Q"))?"Hint:<br>" + q.hint:"")
								+ "<br><INPUT TYPE=SUBMIT VALUE='Grade This Exercise'><p>&nbsp;</FORM></TD></TR>\n");
						i++;
					} catch (Exception e) {
					}
				}
				buf.append("</TABLE>");
				if (i == 1) buf.append("(none)<p>");
				if (optionalQuestionKeys.size() > 0) buf.append("\nOptional Exercises<br>");
			}
						
			// Print the table of optional problems (the whole set if none are assigned)
			int i = 1;
			buf.append("<TABLE>");
			for (Key<Question> k : optionalQuestionKeys) {
				Question q = ofy.get(k);
				q.setParameters(user.id.hashCode());
				buf.append("\n<TR VALIGN=TOP><TD>");
				if (user.getHWQuestionScore(q.id) > 0) buf.append("<IMG SRC=/images/checkmark.gif ALT='OK'>");
				buf.append("&nbsp;<a id=" + q.id + " /></TD>"
						+ "<FORM METHOD=POST ACTION=Homework>"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=GradeHomework>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ "<TD><b>" + i + ". </b></TD><TD>" + q.print() 
						+ (Long.toString(q.id).equals(request.getParameter("Q"))?"Hint:<br>" + q.hint:"")
						+ "<br><INPUT TYPE=SUBMIT VALUE='Grade This Exercise'><p>&nbsp;</FORM></TD></TR>\n");
					i++;
			}
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Question q = ofy.get(Question.class,questionId);
			Topic topic = ofy.get(Topic.class,q.topicId);

			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			buf.append("<h2>Homework Results - " + topic.title + " (" + subject.title + ")</h2>\n");
			buf.append("<b>" + user.getBothNames() + "</b><br>\n");
			buf.append(df.format(now));
			
			// Check submissions for guessing behavior:
			 if (user.moreThan1RecentAttempts(questionId,2)) { // in the past 2 minutes
				buf.append("<h3><FONT COLOR=RED>Your Response Was Not Graded</FONT></h3>"
						+ "It appears that you are guessing the answers to the questions because you submitted at least "
						+ "2 other responses to this question in the past couple of minutes.<p>Please slow down, "
						+ "check your work carefully, and then resubmit your answer.  You will find that by working through "
						+ "the problems in a careful, deliberate way will enhance your learning and your performance on "
						+ "the examinations.");
				buf.append("<p><a href=Homework?TopicId=" + topic.id + ">Return to this homework assignment</a>");
				return buf.toString();
			};
			
			q.setParameters(user.id.hashCode());
			int studentScore = 0;
			int possibleScore = q.pointValue;
			String studentAnswer[] = request.getParameterValues(Long.toString(questionId));
			if (studentAnswer != null) {
				for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
				if (studentAnswer[0].length() > 0) { // an answer was submitted
					// record the response in the Responses table for question debugging:
					studentScore = q.isCorrect(studentAnswer[0])?q.pointValue:0;
					Response r = new Response("Homework",topic.id,q.id,studentAnswer[0],q.getCorrectAnswer(),studentScore,possibleScore,user.id,now);
					ofy.put(r);
					// save the Homework Transaction in the datastore
					HWTransaction ht = new HWTransaction(q.id,topic.id,topic.title,user.id,now,r.id,studentScore,possibleScore,request.getRequestURI());
					ofy.put(ht);
					// create/update/store a HomeworkScore object
					try {
						long assignmentId = myGroup.getAssignmentId("Homework",topic.id);
						if (assignmentId > 0) { // assignment exists; save a Score object
							Assignment a = ofy.find(Assignment.class,assignmentId);
							ofy.put(Score.getInstance(user.id,a));
						}	
					} catch (Exception e2) {
					}
				}
			}
			// Send response to the user:
			if (studentScore > 0) {
				buf.append("<h3>Congratulations. You answered the question correctly.</h3>");
			}
			else if (studentAnswer[0].length() > 0) {
				switch (q.getQuestionType()) {
				case 5:  // Numeric question
					try {
						@SuppressWarnings("unused")
						double dAnswer = Double.parseDouble(studentAnswer[0]);  // throws exception for non-numeric answer
						buf.append("<h3>Incorrect Answer</h3>Your answer was scored incorrect because it does not "
								+ "agree with the answer in the database to within the required precision (" + q.requiredPrecision + "%). "
								+ "Please be sure that your response has enough significant figures to assure this level of agreement.");
					}
					catch (Exception e2) {
						buf.append("<h3>Wrong Format</h3>This question requires a numeric response expressed as an integer, decimal number, "
								+ "or number in scientific notation. Your answer was scored incorrect because the program was unable to recognize "
								+ "your answer as one of these types.");
					}
					break;
				default:  // All other types of questions
					buf.append("<h3>Incorrect Answer</h3>Your answer was scored incorrect because it does not agree with the "
							+ "answer in the database.");
				}
			}  
			else {
				buf.append("<h3>The answer to the question was left blank.</h3>");
			}

			// embed the detailed solution or hint to the exercise in the response, if appropriate
			buf.append(ajaxJavaScript());
			if (user.isInstructor() || user.isTeachingAssistant() || (studentScore > 0)) {
				buf.append("<p><div id=exampleLink>"
						+ "<a href=# onClick=javascript:document.getElementById('example').style.display='';"
						+ "document.getElementById('exampleLink').style.display='none';>"
						+ "<FONT COLOR=RED>View the detailed solution for this homework exercise</FONT></a><p></div>");
				buf.append("<div id=example style='display: none'><b>Detailed Solution</b><p>" 
						+ q.printAllToStudents(studentAnswer[0]) + "</div>");
			}
			
			boolean offerHint = studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id);
			
			/*if (studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id)) { 
				// embed a hint into this page if the user is eligible
				buf.append("<p><div id=hintLink>"
						+ "<a href=# onClick=javascript:document.getElementById('hint').style.display='';"
						+ "document.getElementById('hintLink').style.display='none'>"
						+ "<FONT COLOR=RED>Give me a hint</FONT></a></div>"
						+ "<div id=hint style='display: none'>"
						+ "<h3>Hint</h3>" + q.hint
						+ "<p>If you're still stuck after considering this hint, please see your instructor "
						+ "or a teaching assistant for further assistance. They have access to the complete "
						+ "solutions to the homework exercises.</div>");
			}
			*/
			

			// if the user response was correct, seek five-star feedback:
			if (studentScore > 0) buf.append(fiveStars());

			buf.append("<p><a href=Homework?TopicId=" + topic.id 
					+ (offerHint?"&Q=" + q.id:"")
					+ "#" + q.id + (offerHint?"><span style='color:red'>Please give me a hint</span>":">Return to this homework assignment") + "</a>");
		}
		catch (Exception e) {
			buf.append("Sorry, we were unable to score this question.<br>" + e.toString());
		}
		return buf.toString();
	}

	String ajaxJavaScript() {
		return "<SCRIPT TYPE='text/javascript'>\n"
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
		+ "function ajaxStars(nStars) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (nStars==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    var msg;\n"
		+ "    switch (nStars) {\n"
		+ "      case '1': msg='1 star - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=Feedback>tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=Feedback>tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=Feedback>Click here</a> '"
		+ "                + 'to provide additional feedback.';"
		+ "                break;\n"
		+ "      case '4': msg='4 stars - Thank you';"
		+ "                break;\n"
		+ "      case '5': msg='5 stars - Thank you!';"
		+ "                break;\n"
		+ "      default: msg='You clicked ' + nStars + ' stars.';\n"
		+ "    }\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('vote').innerHTML=msg;\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','Feedback?UserRequest=AjaxRating&NStars='+nStars,true);\n"
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
	}

	String fiveStars() {
		StringBuffer buf = new StringBuffer();

		buf.append("<TABLE><TR><TD><div id='FiveStars'>\n"
				+ "<script type='text/javascript'>\n"
				+ "<!--\n"
				+ "  var star1 = new Image(); star1.src='images/star1.gif';"
				+ "  var star2 = new Image(); star2.src='images/star2.gif';"
				+ "  var set = false;\n"
				+ "  function showStars(n) {"
				+ "    if (!set) {"
				+ "      document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));"
				+ "      for (i=1;i<6;i++) {document.getElementById(i).src=(i<=n?star2.src:star1.src)}"
				+ "    }"
				+ "  }\n"
				+ "  function setStars(n) {"
				+ "    if (!set) {"
				+ "      ajaxStars(n);"
				+ "      set = true;"
				+ "    }"
				+ "  }\n"
				+ "// -->\n"
				+ "</script>\n");

		buf.append("Please rate your overall experience with ChemVantage:<br />\n"
				+ "<div id='vote' style='font-family:tahoma; color:red;'>(click a star):</div>\n");

		for (int iStar=1;iStar<6;iStar++) {
			buf.append("<img src='images/star1.gif' id='" + iStar + "' "
					+ "style='width:30px; height:30px; float:left;' "
					+ "onmouseover=showStars(this.id); onClick=setStars(this.id); onmouseout=showStars(0); />");
		}

		buf.append("</div></TD></TR></TABLE><p>");

		return buf.toString(); 
	}

}
