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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xeustechnologies.googleapi.spelling.SpellChecker;
import org.xeustechnologies.googleapi.spelling.SpellCorrection;
import org.xeustechnologies.googleapi.spelling.SpellResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
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
	Map<Key<Question>,Question> quizQuestions = new HashMap<Key<Question>,Question>();

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
			if ("SpellCheck".equals(request.getParameter("UserRequest"))) {
				out.println(correctedSpelling(request.getParameter("Answer")));
			}
			else out.println(Home.getHeader(user) + printQuiz(user,request) + Home.footer);
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
			if (qt == null) {
				qt = new QuizTransaction(topic.id,topic.title,user.id,now,null,0,0,request.getRemoteAddr());
				ofy.put(qt);  // creates a long id value to use in random number generator
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
			
			if (user.hasPremiumAccount()) buf.append(ajaxQuizJavaScript());  // this code allows premium users to use the Google SOAP search spell checking function
			
			buf.append("\n<FORM NAME=Quiz METHOD=POST ACTION=Quiz onSubmit=\"javascript: return confirmSubmission()\">");

			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
					
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
			int nQuestions = (nQuestionsPerSubjectArea < questionKeys.size()?nQuestionsPerSubjectArea:questionKeys.size());

			int i = 0;
			buf.append("<OL>\n");
			while (i<nQuestions && questionKeys.size()>0) {
				Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
				Question q = quizQuestions.get(k);
				if (q==null) {
					try {
						q = ofy.get(k);
						quizQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				// by this point we should have a valid question
				i++;  // this counter keeps track of the number of questions presented so far
				possibleScore += q.pointValue;
				// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
				// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
				q.setParameters((int)(qt.id - q.id));
				//buf.append("\n<li>" + selected.print() + "<br></li>\n");
				buf.append("\n<li>" + (user.hasPremiumAccount()?q.printPremium():q.print()) + "<br></li>\n");
			}
			buf.append("</OL>");
			
			// update and store the QuizTransaction for this quiz
			QueueFactory.getDefaultQueue().add(withUrl("/TransactionServlet")
					.param("AssignmentType","Quiz")
					.param("TransactionId", Long.toString(qt.id))
					.param("Action", "Download")
					.param("PossibleScore", Integer.toString(possibleScore)));
			//qt.possibleScore = possibleScore;
			//ofy.put(qt);
			
			buf.append("\n<input type=hidden name='QuizTransactionId' value=" + qt.id + ">");
			buf.append("\n<input type=hidden name='TopicId' value=" + topic.id + ">");
			buf.append("<div id='timer1' style='color: red'></div><div id=ctrl1 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Quiz'>");
			buf.append("\n</form>");
			buf.append("<SCRIPT language='JavaScript'>");
			if (user.hasPremiumAccount()) {
				buf.append("function toggleTimers() {"
						+ "  var timer0 = document.getElementById('timer0');"
						+ "  var timer1 = document.getElementById('timer1');"
						+ "  var ctrl0 = document.getElementById('ctrl0');"
						+ "  var ctrl1 = document.getElementById('ctrl1');"
						+ "  if (timer0.style.display=='') {" 
						+ "    timer0.style.display='none';timer1.style.display='none';"
						+ "    ctrl0.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
						+ "    ctrl1.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
						+ "  } else {"
						+ "    timer0.style.display='';timer1.style.display='';"
						+ "    ctrl0.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
						+ "    ctrl1.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
						+ "  }"
						+ "}");
			} else {
				buf.append("document.getElementById('timer0').style.visibility='hidden';"
						+ "document.getElementById('timer1').style.visibility='hidden';"
						+ "document.getElementById('ctrl0').style.visibility='hidden';"
						+ "document.getElementById('ctrl1').style.visibility='hidden';");
			}
			buf.append("function confirmSubmission() {"
					+ "if (seconds>0) return confirm('Submit this quiz for scoring now?');"
					+ "}");
			buf.append("var seconds;var minutes;var oddSeconds;"
					+ "var endTime = new Date().getTime() + " + secondsRemaining + "*1000;"
					+ "function countdown() {"
					+ "var now = new Date().getTime();"
					+ "seconds=Math.round((endTime-now)/1000);"
					+ "minutes = seconds<0?Math.ceil(seconds/60):Math.floor(seconds/60);"
					+ "oddSeconds = seconds%60;"
					+ "for(i=0;i<2;i++)"
					+ "document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
					+ "if (seconds==30) alert('30 seconds remaining');"
					+ "if (seconds < 0) document.Quiz.submit();"
					+ "setTimeout('countdown()',1000);"
					+ "}"
					+ "countdown();"
					+ "</SCRIPT>"); 

		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			Date now = new Date();
			long transactionId = Long.parseLong(request.getParameter("QuizTransactionId"));
			QuizTransaction qt = ofy.get(QuizTransaction.class,transactionId);
			if (qt.graded != null) {
				return "<h2>No Score</h2>"
				+ "Sorry, this quiz has been scored already and cannot be scored again. Please consult the <a href=Scores>scores page</a>.";
			}
			if (now.getTime() - qt.downloaded.getTime() > (timeLimit*60000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeLimit + " minute time limit for this quiz has expired.";

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			long myGroupId = myGroup==null?0L:myGroup.id;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + " (" + subject.title + ")</h2>\n");
			buf.append("<b>" + user.getBothNames() + "</b><br>\n");
			buf.append(df.format(now));
			buf.append(ajaxScoreJavaScript()); // load javascript for AJAX problem reporting form
			StringBuffer missedQuestions = new StringBuffer();
			
			missedQuestions.append("<OL>");
			
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(new Key<Question>(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = quizQuestions.get(k);
							if (q==null) {
								try {
									q = ofy.get(k);
									quizQuestions.put(k,q);
								} catch (Exception e) {
									continue;
								}
							}
							q.setParameters((int)(qt.id - q.id));
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							queue.add(withUrl("/ResponseServlet")
									.param("AssignmentType","Quiz")
									.param("TopicId", Long.toString(qt.topicId))
									.param("QuestionId", Long.toString(q.id))
									.param("StudentResponse", studentAnswer[0])
									.param("CorrectAnswer", q.getCorrectAnswer())
									.param("Score", Integer.toString(score))
									.param("PossibleScore", Integer.toString(q.pointValue))
									.param("UserId", user.id));
							//responses.add(new Response("Quiz",qt.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));
							studentScore += score;
							if (score == 0) {  
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
			queue.add(withUrl("/TransactionServlet")
					.param("AssignmentType","Quiz")
					.param("TransactionId", Long.toString(qt.id))
					.param("Action", "Graded")
					.param("UserId",user.id)
					.param("GroupId",Long.toString(myGroupId))
					.param("Score", Integer.toString(studentScore)));
			buf.append("End");
			//qt.score = studentScore;
			//qt.graded = now;
			//ofy.put(qt); // quiz transaction is stored to the database before calculating the quiz score
			//Assignment a = ofy.query(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Quiz").filter("topicId",qt.topicId).get();
			//if (a != null) ofy.put(Score.getInstance(user.id,a));
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
				buf.append("</div></TD></TR></TABLE><p>");
			}
			else {
				int leftBlank = qt.possibleScore - studentScore - wrongAnswers;
				if (leftBlank>0) buf.append("<h4>" + leftBlank + " question" 
						+ (leftBlank>1?"s were":" was") + " left unanswered (blank).</h4>");
				if (wrongAnswers>0) buf.append("<h4>" + wrongAnswers + " question" 
						+ (wrongAnswers>1?"s were":" was") + " answered incorrectly:</h4>" + missedQuestions.toString());
			
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
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + qt.topicId + "'>"
					+ "<INPUT TYPE=SUBMIT VALUE='Take this quiz again'></FORM>\n");
			
		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}

	String ajaxQuizJavaScript() {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSpellCheck(id) {\n"
		+ "  var xmlhttp;\n"
		+ "  var answer = document.getElementById(id).value;\n"
		+ "  if (answer.length==0) {\n"
		+ "    document.getElementById('status'+id).innerHTML='Nothing to check';\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    var status=document.getElementById('status'+id);\n"
		+ "    var answerField=document.getElementById(id);\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      status.innerHTML += 'done.';\n"
		+ "      if (xmlhttp.responseText.length==1) status.innerHTML='Spelling is OK.';\n"
		+ "      else {"
		+ "        status.innerHTML = 'Did you mean:';\n"
		+ "	       answerField.value=xmlhttp.responseText;\n"
		+ "      }\n"
		+ "    }\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','Quiz?UserRequest=SpellCheck&Answer='+answer,true);\n"
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
	
	String ajaxScoreJavaScript() {
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

	static String correctedSpelling(String answer) {
		StringBuffer buf = new StringBuffer();
		try {
			if (answer==null) return "";
			answer = answer.trim();
			if (answer.isEmpty()) return "";
			SpellChecker sc = new SpellChecker();
			SpellResponse sr = sc.check(answer);
			SpellCorrection[] scorr = sr.getCorrections();
			if (scorr==null) return "";  // no corrections needed
			int i = 0; // position index in original submission
			int j = 0; // offset of correction
			for(int k = 0;k<scorr.length;k++) {
				j = scorr[k].getOffset();
				buf.append(j>i?answer.substring(i,j):"");
				buf.append(scorr[k].getWords()[0]);
				i = answer.indexOf(" ",j+1);
			}
			buf.append(i>0?answer.substring(i):"");
		} catch (Exception e) {
			return "Spell checker is offline, sorry";
		}
		return buf.toString().trim();
	}
	
}
