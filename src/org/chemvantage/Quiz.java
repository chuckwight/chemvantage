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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/Quiz")
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
			User user = User.getUser((String)request.getSession().getAttribute("Token"));
			if (!user.signatureIsValid(request.getParameter("sig"))) throw new Exception();
			
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			if ("ShowScores".contentEquals(userRequest)) out.println(Home.header("Your ChemVantage Scores") + showScores(user,request) + Home.footer);
			else if ("ShowSummary".contentEquals(userRequest)) out.println(Home.header("Your Class ChemVantage Scores") + showSummary(user,request) + Home.footer);
			else if ("AssignQuizQuestions".contentEquals(userRequest) && user.isInstructor()) {
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				out.println(Home.header("Customize ChemVantage Quiz Assignment") + a.selectQuestionsForm(user) + Home.footer);
			}
			else out.println(Home.header("ChemVantage Quiz") + printQuiz(user,request) + Home.footer);
		} catch (Exception e) {
			response.sendRedirect("/Logout");
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser((String)request.getSession().getAttribute("Token"));
			if (!user.signatureIsValid(request.getParameter("sig"))) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			if ("UpdateAssignment".contentEquals(userRequest)) {
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				a.updateQuestions(request);
				out.println(Home.header("ChemVantage Quiz") + printQuiz(user,request) + Home.footer);
			} else out.println(Home.header("ChemVantage Quiz Results") + printScore(user,request) + Home.footer);
		} catch (Exception e) {
			response.sendRedirect("/Logout");
		}
	}

	public String printQuiz(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment qa = null;
			long topicId = 0L;
			long assignmentId = 0L;
			try { 
				assignmentId = user.getAssignmentId();  // should be non-zero for LTI user
				if (assignmentId > 0) {
					qa = ofy().load().type(Assignment.class).id(assignmentId).now();
					topicId = qa.topicId;
				} else {  // get the requested topicId for anonymous user
					topicId = Long.parseLong(request.getParameter("TopicId"));
				}
			} catch (Exception e) {  // alternative process for anonymous user
				return "<h2>No Quiz Selected</h2>You must return to the <a href=/>Home Page</a> "
					+ "and select a topic for this quiz using the drop-down box.";
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			Date now = new Date();

			// Check to see if this user has any pending quizzes on this topic:
			Date then = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).filter("userId",user.id).filter("topicId",topic.id).filter("graded",null).filter("downloaded >",then).first().now();
			String lis_result_sourcedid = user.getLisResultSourcedid();
			if (qt == null || qt.graded != null) {
				qt = new QuizTransaction(topic.id,topic.title,user.id,now,null,0,assignmentId,0,user.getLisResultSourcedid());
				ofy().save().entity(qt).now();  // creates a long id value to use in random number generator
			} else if (qt.lis_result_sourcedid == null && lis_result_sourcedid != null) {
				qt.lis_result_sourcedid = lis_result_sourcedid;
				ofy().save().entity(qt);
			}
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - qt.downloaded.getTime())/1000);
			/*
			//=================== TEMPORARY DEBUGGING SECTION =======================
			try {  
				Deployment d = ofy().load().type(Deployment.class).id(qa.domain).safe();
				String lineitem = LTIMessage.getLineItem(d, qa.lti_ags_lineitem_url).toString();
				buf.append("Debug lineitem: " + lineitem);
			} catch (Exception e) {}
			// ======================================================================
			*/
			buf.append("\n<h2>Quiz - " + topic.title + " (" + subject.title + ")</h2>");

			if (user.isInstructor() && qa != null) {
				buf.append("<mark>As the course instructor you may "
						+ "<a href=/Quiz?UserRequest=AssignQuizQuestions&sig=" + user.getTokenSignature() + ">"
						+ "customize this quiz</a> by selecting/deselecting the available question items.");
				if (qa.lti_nrps_context_memberships_url != null && qa.lti_ags_lineitem_url != null) 
					buf.append("<br>You may also view a <a href=/Quiz?UserRequest=ShowSummary&sig=" 
							+ user.getTokenSignature() + ">summary of student scores</a> for this assignment.");
				buf.append("</mark><p>");
			} else if (user.isAnonymous()) {
				buf.append("<h3><font color=red>Anonymous User</font></h3>");
			}
			
			buf.append("\n<FORM NAME=Quiz METHOD=POST ACTION=Quiz onSubmit='return confirmSubmission()'>");
			
			buf.append("<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">");
			if (qa!=null) buf.append("<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + qa.id + "'>");
			if (!user.isAnonymous()) {
				buf.append("\nQuiz Rules<OL>");
				buf.append("\n<LI>Each quiz must be completed within " + timeLimit + " minutes of the time when it is first downloaded.</LI>");
				buf.append("\n<LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>");
				buf.append("\n<LI>ChemVantage always reports your best score on this assignment to your class LMS.</LI> ");
				buf.append("</OL>");
			}
			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");

			buf.append("\n<input type=submit value='Grade This Quiz'>");

			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = null;
			try {  // check for assigned questions
				questionKeys = qa.questionKeys;
			} catch (Exception e) {  // no assignment exists
				questionKeys = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("topicId", topicId).filter("isActive",true).keys().list();
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

			qt.possibleScore = possibleScore;
			ofy().save().entity(qt);
			
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
				+ "var seconds;var minutes;var oddSeconds;"
				+ "var endTime = new Date().getTime() + " + secondsRemaining + "*1000;"
				+ "function countdown() {"
				+ "  var now = new Date().getTime();"
				+ "  seconds=Math.round((endTime-now)/1000);"
				+ "  minutes = seconds<0?Math.ceil(seconds/60):Math.floor(seconds/60);"
				+ "  oddSeconds = seconds%60;"
				+ "  for(i=0;i<2;i++)"
				+ "    document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
				+ "  if (seconds==30) alert('30 seconds remaining');"
				+ "  if (seconds < 0) document.Quiz.submit();"
				+ "  setTimeout('countdown()',1000);"
				+ "}"
				+ "countdown();"
				+ "function confirmSubmission() {"
				+ "  return confirm('Submit this quiz for scoring now?');"
				+ "}"
				+ "</SCRIPT>"; 
	}
	
	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		try {
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			
			long transactionId = Long.parseLong(request.getParameter("QuizTransactionId"));
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).id(transactionId).safe();
			
			// if this Quiz has already been graded, stop here.
			if (qt.graded != null) {
				return "<h2>No Score</h2>"
						+ "Sorry, this quiz was graded on " + df.format(qt.graded) + " and cannot be regraded.<p>"
						+ "Your score on this quiz was " + qt.score + " out of a possible " + qt.possibleScore + " points.<p>"
						+ (user.isAnonymous()?"<p><a href=/Quiz?TopicId=" + qt.topicId + "&sig=" + user.getTokenSignature() + ">"
						+ "Take this quiz again</a> or go back to the <a href=/>ChemVantage home page</a>.":"");
			}

			// Check to see if the time limit (15 minutes) for taking the Quiz has expired:
			if (now.getTime() - qt.downloaded.getTime() > (timeLimit*60000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeLimit + " minute time limit for this quiz has expired.";
			
			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + " (" + subject.title + ")</h2>\n");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			buf.append(ajaxJavaScript(user.getTokenSignature())); // load javascript for AJAX problem reporting form
			
			// Create a StringBuffer to contain correct answers to questions answered correctly
			StringBuffer missedQuestions = new StringBuffer();			
			missedQuestions.append("<OL>");
			
			// For each question the form contains a parameter: (questionId,studentAnswer)
			// Make a list of the question keys. Non-numeric inputs are ignored (catch and continue).
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			//Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
			List<Response> responses = new ArrayList<Response>();
			
			// This is the main scoring loop:
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
							
							responses.add(new Response("Quiz",qt.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));

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
			if (responses.size()>0) ofy().save().entities(responses);  // batch save of Response entities
			missedQuestions.append("</OL>\n");
			qt.graded = now;
			qt.score = studentScore;
			ofy().save().entity(qt);
			
			// Try to post the score to the student's LMS:
			Assignment qa = null;
			boolean reportScoreToLms = false;
			try {
				if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
				qa = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				Score.updateQuizScore(user.id,qt);
				reportScoreToLms = qa.lti_ags_lineitem_url != null || (qa.lis_outcome_service_url != null && user.getLisResultSourcedid() != null);
				if (reportScoreToLms) {
					QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(qa.id)).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
				}
			} catch (Exception e) {}

			buf.append("<h4>Your score on this quiz is " + studentScore 
					+ " point" + (studentScore==1?"":"s") + " out of a possible " + qt.possibleScore + " points.</h4>\n");

			if (studentScore == qt.possibleScore) {
				buf.append("<H2>Congratulations on a perfect score! Good job.</H2>\n");
			} else {
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
							+ "higher scores and better grades.<p>");
				}
				else {
					buf.append("You're working hard and making great progress.  ");
					if (wrongAnswers > 0) buf.append("Be sure to read and understand the "
							+ "correct answers to the problems that you missed (above) so that you can get them "
							+ "right the next time.<p>");
					else buf.append("Be sure to attempt all the questions so that we can show you "
							+ "the correct answers to problems that you missed.<p>");
				}
			}
			
			// if the user response was correct, seek five-star feedback:
			if (studentScore == qt.possibleScore) buf.append(fiveStars());
			else buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");

			if (qa != null) buf.append("You may <a href=/Quiz?UserRequest=ShowScores&sig=" + user.getTokenSignature() + ">review all your scores on this assignment</a>.<p>") ;

			if (!reportScoreToLms && !user.isAnonymous()) {
				buf.append("<b>Please note:</b> Your score was not reported back to the grade book of your class "
						+ "LMS because the LTI launch request did not contain enough information to do this. "
						+ (user.isInstructor()?"For instructors this is common.":"") + "<p>");				
			}
			
			// If qa==null this is an anonymous user, otherwise is an LTI user:
			buf.append((qa==null?"<a href=/Quiz?TopicId=" + qt.topicId + "&sig=" + user.getTokenSignature() + ">Take this quiz again</a> or go back to the <a href=/>ChemVantage home page</a> " :
			"You may take this quiz again by clicking the assignment link in your learning management system ")			
			+ "or <a href=/Logout>logout of ChemVantage</a>.");

		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
	String fiveStars() {
		StringBuffer buf = new StringBuffer();

		buf.append("<script type='text/javascript'>\n"
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
				+ "      document.getElementById('sliderspan').style='display:none';"
				+ "    }"
				+ "  }\n"
				+ "</script>\n");

		buf.append("Please rate your overall experience with ChemVantage:<br />\n"
				+ "<span id='vote' style='font-family:tahoma; color:red;'>(click a star):</span><br>");

		for (int iStar=1;iStar<6;iStar++) {
			buf.append("<img src='images/star1.gif' id='" + iStar + "' "
					+ "style='width:30px; height:30px;' "
					+ "onmouseover=showStars(this.id); onClick=setStars(this.id); onmouseout=showStars(0); />");
		}
		buf.append("<span id=sliderspan style='opacity:0'>"
				+ "<input type=range id=slider min=1 max=5 value=3 onfocus=document.getElementById('sliderspan').style='opacity:1';showStars(this.value); oninput=showStars(this.value);>"
				+ "<button onClick=setStars(document.getElementById('slider').value);>submit</button>"
				+ "</span>");
		buf.append("<p>");

		return buf.toString(); 
	}

	String ajaxJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,note,email) {\n"
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
		+ "  url += '&QuestionId=' + id + '&sig=" + signature + "&Notes=' + note + '&Email=' + email;\n"
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
		+ "                + 'please take a moment to <a href=/Feedback?sig=" + signature + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=/Feedback?sig=" + signature + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=/Feedback?sig=" + signature + ">Click here</a> '"
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

	String showScores (User user, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Your Quiz Transactions</h2>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		Assignment a = null;
		try {
			long assignmentId = user.getAssignmentId();
			a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		} catch (Exception e) {
			buf.append("Invalid assignment.");
			return buf.toString();
		}

		try {
			buf.append("Assignment Number: " + a.id + "<br>");
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("Topic: "+ t.title + "<br>");
			buf.append("Valid: " + df.format(now) + "<p>");
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",user.id).filter("assignmentId",a.id).order("downloaded").list();
			
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for you in the database for this assignment.<p>"
						+ "<a href=Quiz?AssignmentId=" + a.id 
						+ "&sig=" + user.getTokenSignature() 
						+ ">Take me back to the quiz now.</a>");
			} else {				
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					s = ofy().load().key(Key.create(Key.create(User.class,user.id),Score.class,a.id)).safe();
					if (s.numberOfAttempts != qts.size()) throw new Exception();
				} catch (Exception e) { // create a fresh Score entity from scratch
					s = Score.getInstance(user.id, a);
					ofy().save().entity(s);
				}
				
				buf.append("Your best score on this assignment is " + Math.round(10*s.getPctScore())/10. + "%.<br>");

				// try to validate the score with the LMS grade book entry
				String lmsScore = null;
				try {
					double lmsPctScore = 0;
					boolean gotScoreOK = false;
					
					if (a.lti_ags_lineitem_url != null) {  // LTI version 1.3
						lmsScore = LTIMessage.readUserScore(a,user.id);
						try {
							lmsPctScore = Double.parseDouble(lmsScore);
							gotScoreOK = true;
						} catch (Exception e) {
						}
					}
					else if (a.lis_outcome_service_url != null && s.lis_result_sourcedid != null) {  // LTI version 1.1
						String messageFormat = "application/xml";
						String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
						String oauth_consumer_key = user.id.substring(0, user.id.indexOf(":"));
						String replyBody = new LTIMessage(messageFormat,body,a.lis_outcome_service_url,oauth_consumer_key).send();

						if (replyBody.contains("success")) {
							int beginIndex = replyBody.indexOf("<textString>") + 12;
							int endIndex = replyBody.indexOf("</textString>");
							lmsScore = replyBody.substring(beginIndex,endIndex);
							lmsPctScore = 100.*Double.parseDouble(lmsScore);
							gotScoreOK = true;
						}
					}
					
					if (gotScoreOK && Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
						buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
					} else if (gotScoreOK) { // there is a significant difference between LMS and ChemVantage scores. Please explain:
						buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
								+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<br>"
								+ "If you think this may be due to a stale score, you may submit this assignment for grading,<br>"
								+ "even for a score of zero, and ChemVantage will try to refresh your best score to the LMS.<p>");
					} else throw new Exception();
				} catch (Exception e) {
					buf.append("ChemVantage was unable to retrieve your score for this assignment from the LMS.<br>"
							+ "Sometimes it takes several seconds for the score to be posted in the LMS grade book.<br>");
					if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that you may not have submitted a score for this quiz yet. ");
					if (user.isInstructor()) buf.append("Some LMS providers do not store scores for instructors.");
					buf.append("<p>");
				}

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
	
	String showSummary(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		if (a==null) return "No assignment was specified for this request.";
		
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		if (a.lti_ags_lineitem_url != null && a.lti_nrps_context_memberships_url != null) {
			try { // code for LTI version 1.3
				Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();

				buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
				//buf.append("Group: " + g.description + "<br>");
				buf.append("Assignment ID: " + a.id + "<br>");
				buf.append("Valid: " + new Date() + "<p>");
				buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
						+ "and may or may not include user's names or emails, depending on the settings of your LMS. The easiest way to "
						+ "resolve any discrepancies between scores reported by the LMS grade book and ChemVantage is for the user to "
						+ "submit the assignment again (even for a score of zero). This causes ChemVantage to recalculate the "
						+ "user's best score and report it to the LMS. However, some discrepancies are to be expected, for example "
						+ "if the instructor adjusts a score in the LMS manually or if an assignment was submitted after the "
						+ "deadline and was not accepted by the LMS.<p>");

				Map<String,String> scores = LTIMessage.readMembershipScores(a);
				if (scores==null) scores = new HashMap<String,String>();  // in case service call fails
				
				buf.append("We downloaded " + scores.size() + " scores from your LMS.<br>");
				
				Map<String,String[]> membership = LTIMessage.getMembership(a);
				if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails
				
				buf.append("There are " + membership.size() + " members of this group.<p>");
				
				Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
				Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
				String platform_id = d.getPlatformId() + "/";
				
				for (String id : membership.keySet()) {
					String userId = platform_id + id;
					keys.put(id,Key.create(Key.create(User.class,userId),Score.class,a.id));
				}
				Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
				
				buf.append("<table><tr><th>User ID</th><th>Role</th><th>Name</th><th>Email</th><th>LMS Score</th><th>CV Score</th></tr>");
				for (Map.Entry<String,String[]> entry : membership.entrySet()) {
					if (entry == null) continue;
					String s = scores.get(entry.getKey());
					Score cvScore = cvScores.get(keys.get(entry.getKey()));
					buf.append("<tr><td>" + entry.getKey() + "</td>"
							+ "<td>" + entry.getValue()[0] + "</td>"
							+ "<td>" + entry.getValue()[1] + "</td>"
							+ "<td>" + entry.getValue()[2] + "</td>"
							+ "<td align=center>" + (s == null?" - ":s + "%") + "</td>"
							+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td></tr>");
				}
				buf.append("</table>");
				return buf.toString();
			} catch (Exception e) {
				buf.append(e.toString());
			}
		} else {
			buf.append("Sorry, there is not enough information available from your LMS to support this request.<p>");			
			buf.append("<a href=/Quiz?sig=" + user.getTokenSignature() + ">Return to this quiz</a>.<p>");
		}
		return buf.toString();
	}
}
