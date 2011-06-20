/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class Quiz extends HttpServlet {
	// parameters that determine the properties of the quiz program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for quiz overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printQuiz()
	int timeLimit = 15;                  // minutes; set to zero for no time limit to complete the quiz
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet presents a quiz for the user.";
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
			
			out.println(Home.getHeader(user) + printQuiz(user,request) + Home.footer);
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

	String printQuiz(User user,HttpServletRequest request) {
	StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {
				return "<h2>No Quiz Selected</h2>You must return to the <a href=Home>Home Page</a> "
				+ "and select a topic for this quiz using the drop-down box.";
			}
			Topic topic = ofy.get(Topic.class,topicId);

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);
			Date now = new Date();

			// Check to see if this user has any pending quizzes on this topic:
			Date then = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			QuizTransaction qt = ofy.query(QuizTransaction.class).filter("userId",user.id).filter("topicId",topic.id).filter("graded",null).filter("downloaded >",then).get();
			if (qt == null) {  // no pending quiz; create a new QuizTransaction
				qt = new QuizTransaction(topic.id,topic.title,user.id,now,null,0,0,request.getRequestURI());
				ofy.put(qt);
			}
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - qt.downloaded.getTime())/1000);

			buf.append("\n<h2>Quiz - " + topic.title + " (" + subject.title + ")</h2>");
			buf.append("\n<b>" + user.getBothNames() + "</b><br>");
			buf.append(df.format(qt.downloaded) + "<p>");

			buf.append("\nQuiz Rules<OL>");
			buf.append("\n<LI>Each quiz must be completed within " + timeLimit + " minutes of the time when it is first downloaded.</LI>");
			buf.append("\n<LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>");
			buf.append("\n<LI>For each quiz topic, the server reports your best quiz score and the number of quizzes that you downloaded.</LI>");

			if (myGroup != null) {
				buf.append("\n<LI>You must submit the quiz for scoring before the indicated deadline in order to receive class credit.</LI>");
				buf.append("\n<LI>Instructors can view best scores and downloads by date/time in order to enforce quiz deadlines.</LI>");
			}
			buf.append("</OL>");
			
			buf.append("\n<FORM METHOD=POST ACTION=Quiz "
					+ "onSubmit=\"return confirm('Submit this quiz for grading now. Are you sure?')\">");

			buf.append("<div id='timer0' style='color: red'></div>");
			buf.append("\n<input type=submit value='Grade This Quiz'>");

			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = null;
			try {  // check for assigned questions
				Assignment a = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Quiz").filter("topicId",topicId).get();
				questionKeys = a.questionKeys;
			} catch (Exception e) {  // no assignment exists
				questionKeys = ofy.query(Question.class).filter("topicId", topicId).filter("assignmentType","Quiz").filter("isActive",true).listKeys();
			}
			
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			Random rand = new Random();  // create random number generator to select quiz questions
			rand.setSeed(qt.id);  // random number generator seeded with QuizTransaction id value
			int possibleScore = 0;
			buf.append("<OL>\n");
			int nQuestions = (nQuestionsPerSubjectArea < questionKeys.size()?nQuestionsPerSubjectArea:questionKeys.size());
			for (int i = 0; i < nQuestions; i++) {
				int n = rand.nextInt(questionKeys.size());
				try {
					Question selected = ofy.get(questionKeys.remove(n));
					possibleScore += selected.pointValue;
					// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
					// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
					selected.setParameters((int)(qt.id - selected.id));
					buf.append("\n<li>" + selected.print() + "<br></li>\n");
				} catch (Exception e) { // most likely reason is that the question no longer exists in the database; try again.
					nQuestions = (nQuestionsPerSubjectArea < questionKeys.size()+i?nQuestionsPerSubjectArea:questionKeys.size()+i);
					i--;
				}
			}
			qt.possibleScore = possibleScore;
			ofy.put(qt);
			buf.append("</OL>");

			buf.append("\n<input type=hidden name='QuizTransactionId' value=" + qt.id + ">");
			buf.append("\n<input type=hidden name='TopicId' value=" + topic.id + ">");
			buf.append("\n<div id='timer1' style='color: red'></div>");
			buf.append("\n<input type=submit value='Grade This Quiz'>");
			buf.append("\n</form>");
			buf.append("<SCRIPT language='JavaScript'>"
					+ "function countdown(seconds) {"
					+ "var minutes = Math.floor(seconds/60);"
					+ "var oddSeconds = seconds%60;"
					+ "for(i=0;i<2;i++)"
					+ "document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
					+ "setTimeout('countdown(' + (seconds-1) + ')',1000);"
					+ "}"
					+ "countdown(" + secondsRemaining + ");"
					+ "</SCRIPT>"); 

		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic topic = ofy.get(Topic.class,topicId);

			Date now = new Date();
			long transactionId = Long.parseLong(request.getParameter("QuizTransactionId"));
			QuizTransaction qt = ofy.get(QuizTransaction.class,transactionId);
			
			if (qt.graded != null) {
				return "<h2>No Score</h2>"
				+ "Sorry, this quiz has been scored already and cannot be scored again. Please consult the <a href=Scores>scores page</a>.";
			}
			if (now.getTime() - qt.downloaded.getTime() > (timeLimit*60000+5000)) return "Sorry, the " + timeLimit + " minute time limit for this quiz has expired.";

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + topic.title + " (" + subject.title + ")</h2>\n");
			buf.append("<b>" + user.getBothNames() + "</b><br>\n");
			buf.append(df.format(now));

			buf.append(ajaxJavaScript()); // load javascript for AJAX problem reporting form
			StringBuffer missedQuestions = new StringBuffer("<h3>Questions Answered Incorrectly</h3>\n");
			missedQuestions.append("The following questions were answered incorrectly. "
					+ "There may be additional questions (not shown) that were ");
			missedQuestions.append(user.hasPremiumAccount()?"":"answered incorrectly or ");
			missedQuestions.append("left unanswered.");
			if (!user.hasPremiumAccount()) {
				missedQuestions.append("<br><a href=UpgradeAccount>View more answers</a>");
			}
			missedQuestions.append("<OL>");

			for (@SuppressWarnings("unchecked")
					Enumeration<String> e = request.getParameterNames() ; e.hasMoreElements() ;) {
				Key<Question> questionKey = null;
				try {
					questionKey = new Key<Question>(Question.class,Long.parseLong(e.nextElement()));
					String studentAnswer[] = request.getParameterValues(Long.toString(questionKey.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = ofy.get(questionKey);
							q.setParameters((int)(qt.id - q.id));
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							ofy.put(new Response("Quiz",topic.id,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));
							studentScore += score;
							if ((score == 0) && (user.hasPremiumAccount() || wrongAnswers < 2)) {  
								// include question in list of incorrectly answered questions
								wrongAnswers++;
								missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer[0]) + "</LI>\n");
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			missedQuestions.append("</OL>\n");
			qt.score = studentScore;
			qt.graded = now;
			Assignment a = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Quiz").filter("topicId",topicId).get();
			if (a != null) QuizScore.getInstance(a, user.id).update(studentScore);
			ofy.put(qt); // quiz transaction is put after updating score in case score had to be recalculated
			buf.append("<h3>Your score on this quiz is " + studentScore 
					+ " point" + (studentScore==1?"":"s") + " out of a possible " + qt.possibleScore + " points.</h3>\n");

			if (studentScore == qt.possibleScore) {
				buf.append("<H2>Congratulations on a perfect score! Good job.</H2>\n");

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

				// Insert a 5-star clickable user rating tool:
				buf.append("Please rate your overall experience with ChemVantage:<br />\n");
				buf.append("<div id='vote' style='font-family:tahoma; color:red;'>(click a star):</div>\n");
				for (int iStar=1;iStar<6;iStar++) {
					buf.append("<img src='images/star1.gif' id='" + iStar + "' "
							+ "style='width:30px; height:30px; float:left;' "
							+ "onmouseover=showStars(this.id); onClick=setStars(this.id); onmouseout=showStars(0); />");
				}
				buf.append("</div></TD></TR>"
						+ "<TR><TD>To leave a comment, please visit the <a href=Feedback>Feedback Page</a>"
						+ "</TD></TR></TABLE><p>");
			}
			else {
				if (wrongAnswers > 0) buf.append(missedQuestions.toString());
				else buf.append("<br>Some questions on this quiz were left unanswered.\n");

				// print some words of encouragement:
				buf.append("<h4>Improve Your Score</h4>\n");
				if (studentScore<6) {
					buf.append("If you get stuck on a difficult question, "
							+ "you may refer to your textbook during the quiz. Please keep the " + timeLimit
							+ " minute time limit in mind, though. Hard work and persistence will produce "
							+ "higher scores and better grades.\n");
				}
				else {
					buf.append("You're working hard and making great progress.  ");
					if (wrongAnswers > 0) buf.append("Be sure to read and understand the "
							+ "correct answers to the problems that you missed (above) so that you can get them "
							+ "right the next time.\n");
					else buf.append("Be sure to attempt all the questions so that we can show you "
							+ "the correct answers to problems that you missed.\n");
				}
			}
			buf.append("<FORM METHOD=GET Action=Quiz>"
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
					+ "<INPUT TYPE=SUBMIT VALUE='Take this quiz again'></FORM>\n");
		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
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
		+ "                + 'please take a moment to tell us why:'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=1><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - How we can serve you better?</a>'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=2><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
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
}
