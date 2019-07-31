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
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class Quiz extends HttpServlet {
	// parameters that determine the properties of the quiz program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for quiz overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printQuiz()
	static int timeLimit = 15;                  // minutes; set to zero for no time limit to complete the quiz
	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	static Map<Key<Question>,Question> quizQuestions = new HashMap<Key<Question>,Question>();

	public String getServletInfo() {
		return "This servlet presents a quiz for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = CSRFToken.getUser(request.getParameter("JWT"));
			if (user==null) throw new Exception("Authentication failed, probably because your web session timed out.");
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			String jwt = CSRFToken.getNewJWT(user.id);
			if ("ShowScores".contentEquals(userRequest)) out.println(Home.header + showScores(user,request,jwt) + Home.footer);
			else if ("ShowSummary".contentEquals(userRequest)) out.println(Home.header + showSummary(user,request,jwt) + Home.footer);
			else out.println(Home.header + printQuiz(user,request,jwt) + Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = CSRFToken.getUser(request.getParameter("JWT"));
			if (user==null) throw new Exception("Authentication failed, probably because your web session timed out.");
					
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String jwt = CSRFToken.getJWT(user.id);
			out.println(Home.header + printScore(user,request,jwt) + Home.footer);
		} catch (Exception e) {}
	}

	public String printQuiz(User user,HttpServletRequest request,String jwt) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment qa = null;
			long topicId = 0L;
			long assignmentId = 0L;
			try {  // normal process for LTI assignment launch
				assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				qa = ofy().load().type(Assignment.class).id(assignmentId).now();
				topicId = qa.topicId;
			} catch (Exception e) {  // alternative process for anonymous user
				try {
					topicId = Long.parseLong(request.getParameter("TopicId"));
				} catch (Exception e2) {
					return "<h2>No Quiz Selected</h2>You must return to the <a href=/>Home Page</a> "
							+ "and select a topic for this quiz using the drop-down box.";
				}
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			Date now = new Date();

			// Check to see if this user has any pending quizzes on this topic:
			Date then = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).filter("userId",user.id).filter("topicId",topic.id).filter("graded",null).filter("downloaded >",then).first().now();
			if (qt == null || qt.graded != null) {
				qt = new QuizTransaction(topic.id,topic.title,user.id,now,null,0,assignmentId,0,request.getParameter("lis_result_sourcedid"));
				ofy().save().entity(qt).now();  // creates a long id value to use in random number generator
			}
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - qt.downloaded.getTime())/1000);

			buf.append("\n<h2>Quiz - " + topic.title + " (" + subject.title + ")</h2>");

			if (user.isInstructor() && qa != null) {
				buf.append("<table style='border: 1px solid black'><tr><td>");
				buf.append("As the course instructor you may<ul>"
						+ "<li><a href=/Groups?UserRequest=AssignQuizQuestions&AssignmentId=" + qa.id + "&JWT=" + jwt + ">"
						+ "customize this quiz</a> by selecting/deselecting the available question items"
						+ "<li>view a <a href=/Quiz?UserRequest=ShowSummary&AssignmentId=" + qa.id + "&JWT=" + jwt + ">summary of scores</a> for this assignment"
						+ "</ul></td></tr></table><p>");
			} else if (user.isAnonymous()) {
				buf.append("<h3><font color=red>Anonymous User</font></h3>");
			}
			
			buf.append("\n<FORM NAME=Quiz METHOD=POST ACTION=Quiz onSubmit=\"javascript: return confirmSubmission()\">");
			buf.append("<INPUT TYPE=HIDDEN NAME=JWT VALUE=" + jwt + ">");
			if (qa!=null) buf.append("<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + qa.id + "'>");
			if (!user.isAnonymous()) {
				buf.append("\nQuiz Rules<OL>");
				buf.append("\n<LI>Each quiz must be completed within " + timeLimit + " minutes of the time when it is first downloaded.</LI>");
				buf.append("\n<LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>");
				buf.append("\n<LI>For each quiz topic, the server reports your best quiz score. "
						+ (qa==null?"":"<a href=/Quiz?UserRequest=ShowScores&AssignmentId=" + qa.id + "&JWT=" + jwt + ">View the details here</a>.") 
						+ "</LI>");
				buf.append("</OL>");
			}
			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");

			buf.append("\n<input type=submit value='Grade This Quiz'>");

			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = null;
			try {  // check for assigned questions
				questionKeys = qa.questionKeys;
			} catch (Exception e) {  // no assignment exists
				questionKeys = ofy().load().type(Question.class).filter("topicId", topicId).filter("assignmentType","Quiz").filter("isActive",true).keys().list();
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
						q = ofy().load().key(k).safe();
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
				long seed = Math.abs(qt.id - q.id);
				if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
				q.setParameters(seed); // the values are subtracted to prevent (unlikely) overflow

				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
			buf.append("</OL>");

			// update and store the QuizTransaction for this quiz
			QueueFactory.getDefaultQueue().add(withUrl("/TransactionServlet")
					.param("AssignmentType","Quiz")
					.param("TransactionId", Long.toString(qt.id))
					.param("Action", "Download")
					.param("PossibleScore", Integer.toString(possibleScore)));

			buf.append("\n<input type=hidden name='QuizTransactionId' value=" + qt.id + ">");
			buf.append("\n<input type=hidden name='TopicId' value=" + topic.id + ">");
			buf.append("<div id='timer1' style='color: red'></div><div id=ctrl1 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Quiz'>");
			buf.append("\n</form>");

			// this code for displaying/hiding timers and a quiz submit confirmation box
			buf.append(timerScripts(secondsRemaining)); 

		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String timerScripts(int secondsRemaining) {
		return "<SCRIPT language='JavaScript'>"
				+ "function toggleTimers() {"
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
				+ "}"
				+ "function confirmSubmission() {"
				+ "if (seconds>0) return confirm('Submit this quiz for scoring now?');"
				+ "}"
				+ "var seconds;var minutes;var oddSeconds;"
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
				+ "</SCRIPT>"; 
	}
	
	String printScore(User user,HttpServletRequest request,String jwt) {
		StringBuffer buf = new StringBuffer();
		try {
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			
			Assignment qa = null;
			long transactionId = Long.parseLong(request.getParameter("QuizTransactionId"));
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).id(transactionId).safe();
			if (qt.graded != null) {
				return "<h2>No Score</h2>"
						+ "Sorry, this quiz was graded on " + df.format(qt.graded) + " and cannot be regraded.<p>"
						+ "Your score on this quiz was " + qt.score + " out of a possible " + qt.possibleScore + " points.<p>"
						+ (user.isAnonymous()?"<p><a href=/Quiz?TopicId=" + qt.topicId + "&JWT=" + jwt
								+ ">Take this quiz again</a>"
								+ " or go back to the <a href=/>ChemVantage home page</a>.":"");
			}

			if (now.getTime() - qt.downloaded.getTime() > (timeLimit*60000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeLimit + " minute time limit for this quiz has expired.";

			
			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + " (" + subject.title + ")</h2>\n");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			buf.append(ajaxScoreJavaScript(jwt)); // load javascript for AJAX problem reporting form
			
			StringBuffer missedQuestions = new StringBuffer();			
			missedQuestions.append("<OL>");
			
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
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
									q = ofy().load().key(k).safe();
									quizQuestions.put(k,q);
								} catch (Exception e) {
									continue;
								}
							}
							long seed = Math.abs(qt.id - q.id);
							if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
							q.setParameters(seed);
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
			qt.graded = now;
			qt.score = studentScore;
			ofy().save().entity(qt).now();
			
			try {
				long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				qa = ofy().load().type(Assignment.class).id(assignmentId).safe();
				Score s = Score.getInstance(user.id,qa);
				ofy().save().entity(s).now();
				if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",qa.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
			} catch (Exception e) {}

			buf.append("<h4>Your score on this quiz is " + studentScore 
					+ " point" + (studentScore==1?"":"s") + " out of a possible " + qt.possibleScore + " points.</h4>\n");

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
				if (leftBlank>0) buf.append(leftBlank + " question" 
						+ (leftBlank>1?"s were":" was") + " left unanswered (blank).<p>");
				if (wrongAnswers>0) buf.append(wrongAnswers + " question" 
						+ (wrongAnswers>1?"s were":" was") + " answered incorrectly:<p>" + missedQuestions.toString());
			
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
			buf.append("<p>We welcome comments about your ChemVantage experience <a href=/Feedback?JWT=" + jwt + ">here</a>.<p>");
			
			buf.append("<p>");
			
			buf.append("<a href=/Quiz?"
					+ (qa==null?"TopicId=" + qt.topicId : "AssignmentId=" + qa.id)
					+ "&JWT=" + jwt
					+ (qt.lis_result_sourcedid==null?"":"&lis_result_sourcedid=" + qt.lis_result_sourcedid)
					+ ">Take this quiz again</a>&nbsp;");
			if (user.isAnonymous()) buf.append(" or go back to the <a href=/>ChemVantage home page</a>.");
					
		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
	String ajaxScoreJavaScript(String jwt) {
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
		+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment. "
		+ "</b></FONT><p>';\n"
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
		+ "                + 'please take a moment to <a href=/Feedback?JWT=" + jwt + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=Feedback?JWT=" + jwt + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=Feedback?JWT=" + jwt + ">Click here</a> '"
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
	
	String showScores (User user, HttpServletRequest request, String nonce) {
		StringBuffer buf = new StringBuffer("<h2>Your Quiz Transactions</h2>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		Assignment a = null;
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		} catch (Exception e) {
			buf.append("Invalid assignment.");
			return buf.toString();
		}

		try {
			buf.append("ChemVantage UserID: " + user.getIdHash() + "<br>");
			buf.append("Assignment Number: " + a.id + "<br>");
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("Topic: "+ t.title + "<br>");
			buf.append("Valid: " + df.format(now) + "<p>");
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).filter("userId",user.id).order("downloaded").list();
			
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for you in the database for this assignment.<p>"
						+ "<a href=Quiz?AssignmentId=" + a.id + (nonce==null?"":"&Nonce=" + nonce)+ ">Take me back to the quiz now.</a>");
			} else {
				// create a fresh Score entity to calculate the best score on this assignment
				Score s = Score.getInstance(user.id, a);

				buf.append("Your best score on this assignment is " + Math.round(10*s.getPctScore())/10. + "%.<br>");

				if (s.score>0 && s.lis_result_sourcedid != null) {  // try to validate the score with the LMS grade book entry
					try {
						Group g = ofy().load().type(Group.class).id(user.myGroupId).safe();
						String messageFormat = g.getLisOutcomeFormat();
						String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
						String oauth_consumer_key = g.domain;
						String replyBody = new LTIMessage(messageFormat,body,g.lis_outcome_service_url,oauth_consumer_key).send();

						if (replyBody.contains("success")) {
							int beginIndex = replyBody.indexOf("<textString>") + 12;
							int endIndex = replyBody.indexOf("</textString>");
							replyBody = replyBody.substring(beginIndex,endIndex);
							double lmsPctScore = 100.*Double.parseDouble(replyBody);
							if (Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
								buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
							} else { // there is a significant difference between LMS and ChemVantage scores. Please explain:
								buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
										+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<br>"
										+ "If you think this may be due to a stale score, you may submit this assignment for grading,<br>"
										+ "even for a score of zero, and ChemVantage will try to refresh your best score to the LMS.<p>");
							}
						} else throw new Exception();
					} catch (Exception e) {
						buf.append("We attempted to validate the score contained in your class LMS grade book, but the operation failed.<p>");
					}
				}

				buf.append("<a href=Quiz?AssignmentId=" + a.id + (nonce==null?"":"&Nonce=" + nonce)+ ">Take me back to the quiz now.</a><p>");
			
				buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Quiz Score</th></tr>");
				for (QuizTransaction qt : qts) {
					buf.append("<tr><td>" + qt.id + "</td><td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":100.*qt.score/qt.possibleScore + "%") +  "</td></tr>");
				}
				buf.append("</table><br>Missing scores indicate quizzes that were downloaded but not submitted for scoring.<p>");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String showSummary(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		
		try {
			if (!user.isInstructor()) throw new Exception("You must be logged in as the instructor to view this page.");
			
			Assignment a = ofy().load().type(Assignment.class).id(Long.parseLong(request.getParameter("AssignmentId"))).safe();
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
			
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			buf.append(df.format(new Date()) + "<p>");
			
			buf.append("To protect the privacy of our users, ChemVantage does not collect any personally identifiable information. "
					+ "Therefore, we are unable to display a traditional grade book with names and scores. Instead, we rely on a "
					+ "robust system of reporting scores back to the grade book inside your LMS. Please check the information below "
					+ "and let us know if you have any questions or problems. Thank you for using ChemVantage for your class.<p>");
			
			Group g = ofy().load().type(Group.class).id(a.groupId).now();
			
			buf.append("There are " + g.validatedMemberCount() + " members of this group, including instructors, "
			+ "teaching assistants and test students created by your LMS, if applicable.<br>");
			
			Score s = null;
			int count = 0;
			int pctScoreSum = 0;
			int attempts = 0;
			Date mostRecent = new Date(0);
			boolean scoresReported = false;
			int scoresNotReported = 0;
			List<String> removeUsers = new ArrayList<String>();
			for (String id : g.memberIds) {
				try {
					Key<Score> k = Key.create(Key.create(User.class,id),Score.class,a.id);
					s = ofy().load().key(k).safe();       // throws an exception if no Score entity exists yet
					if (s.numberOfAttempts==0) continue;  // skip the averaging for those who have not attempted the quiz yet
					pctScoreSum += s.getPctScore();
					attempts += s.numberOfAttempts;
					count++;
					if (s.mostRecentAttempt.after(mostRecent)) mostRecent = s.mostRecentAttempt;
					if (s.lisReportComplete) scoresReported = true;
					if (s.needsLisReporting()) {  // found a stale Score that apparently needs reporting
						scoresNotReported++;
						try {  // attempt to read the user's score, then post the stale one, otherwise remove the user from this group
							String messageFormat = g.getLisOutcomeFormat();
							String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
							String oauth_consumer_key = g.domain;
							String replyBody = new LTIMessage(messageFormat,body,g.lis_outcome_service_url,oauth_consumer_key).send();

							if (replyBody.contains("success")) {  // the lis_result_sourcedid is valid, so post the stale score
								Queue queue = QueueFactory.getDefaultQueue();  // default task queue
								queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",id));
								buf.append("<br>We found a user score that may not have been posted previously, and we're sending it to the LMS now.");
							} else {  // this user may have dropped the class or the Test Student was reset, so remove the user from this group
								removeUsers.add(id);
								buf.append("<br>We found a user who no longer has a valid grade book entry point in your LMS, so we removed this user from your group.");
							}
						} catch (Exception e) {
							buf.append("<br>We attempted to validate a user score, but the operation failed." + e.toString());
						}
					}
				} catch (Exception e) {}
			}
			for (String id : removeUsers) { // remove these users from the group
				try {
					User u = ofy().load().type(User.class).id(id).safe();
					u.changeGroups(0);
				} catch (Exception e) {}
			}
			buf.append("<p>There " + (count==1?"is ":"are ") + count + " score" + (count==1?"":"s") + " for this assignment in the ChemVantage database.<br>");
			if (count>0) {
				buf.append("The average score is " + pctScoreSum/count + "%.<br>");
				buf.append("The average number of attempts (including downloads not submitted for scoring) is " + Math.round(10.*attempts/count)/10. + ".<br>");
				buf.append("The most recent attempt of this assignment was on " + df.format(mostRecent) + ".<p>");
			} else buf.append("<br>");
			
			if (!g.isUsingLisOutcomeService) buf.append("Your LMS is not configured for ChemVantage to report scores to the LMS grade book.<p>");
			else if (scoresNotReported>0) {
				buf.append("It appears that " + scoresNotReported + (scoresNotReported==1?" score":" scores") + " may not have been reported to your LMS correctly. "
						+ "We have automatically initiated a programmed task to correct this. "
						+ "Please check back in a few minutes to ensure that the situation has been resolved.<p>");
			}
			else if (scoresReported) buf.append("All scores for students have been reported to your LMS successfully.<p>");
			
			buf.append("If you have any questions or need assistance, please contact <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<p>");			
			buf.append("<a href=/Quiz?AssignmentId=" + a.id + (nonce==null?"":"&Nonce=" + nonce) + ">Return to this quiz</a>.<p>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
}
