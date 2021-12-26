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
	private static final long serialVersionUID = 137L;
	QuestionCache qcache = new QuestionCache();
	
	public String getServletInfo() {
		return "This servlet presents a quiz for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "ShowScores":
				String forUserId = request.getParameter("ForUserId");
				User forUser = forUserId==null?user:new User(user.platformId, forUserId);
				out.println(Subject.header("ChemVantage Scores") + showScores(user,forUser) + Subject.footer);
				break;
			case "ShowSummary":
				out.println(Subject.header("Your Class ChemVantage Scores") + showSummary(user,request) + Subject.footer);
				break;
			case "AssignQuizQuestions":
				if (user.isInstructor()) out.println(Subject.header("Customize ChemVantage Quiz Assignment") + selectQuestionsForm(user) + Subject.footer);
				break;
			case "PrintQuiz":
				out.println(Subject.header("ChemVantage Quiz") + printQuiz(user,request) + Subject.footer);
				break;
			default: 
				if (user.isInstructor()) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,request) + Subject.footer);
				else out.println(Subject.header("ChemVantage Quiz") + printQuiz(user,request) + Subject.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Assignment a = qcache.getAssignment(user.getAssignmentId());
			
			switch (userRequest) {
			case "UpdateAssignment":
				a.updateQuestions(request);
				qcache.putAssignment(a);
				doGet(request,response);
				break;
			case "Set Allowed Time":
				try {
					double minutes = Double.parseDouble(request.getParameter("TimeAllowed"));
					if (minutes > 60.) minutes = 60.;
					a.timeAllowed = minutes<1.0?60:(int)(minutes*60);
				} catch (Exception e) {
					a.timeAllowed = 900;
				}
				qcache.putAssignment(a);
				ofy().save().entity(a).now();
				doGet(request,response);
				break;
			case "Synchronize Scores":
				if (synchronizeScores(user,request)) doGet(request,response);
				else out.println("Synchronization request failed.");
				break;
			default:
				out.println(Subject.header("ChemVantage Quiz Results") + printScore(user,request) + Subject.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}
	
	String instructorPage(User user,HttpServletRequest request) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();		
		try {
			long assignmentId=user.getAssignmentId();
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
			boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
			
			buf.append("<h2>General Chemistry Quiz - Instructor Page</h2>");
			buf.append("Topic covered on this quiz: " + t.getTitle() + "<br/>");
			
			buf.append("From here, you may<UL>"
					+ "<LI><a href='/Quiz?UserRequest=AssignQuizQuestions&sig=" + user.getTokenSignature() + "'>Customize this quiz</a> to set the time allowed and select the available question items.</LI>"
					+ (supportsMembership?"<LI><a href='/Quiz?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>Review your students' quiz scores</a></LI>":"")
					+ "</UL>");
			buf.append("<a style='text-decoration: none' href='/Quiz?UserRequest=PrintQuiz&sig=" + user.getTokenSignature() + "'>"
					+ "<button style='display: block; width: 500px; border: 1 px; background-color: #00FFFF; color: black; padding: 14px 28px; font-size: 18px; text-align: center; cursor: pointer;'>"
					+ "Show This Assignment (recommended)</button></a>");
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + e.getMessage());
		}
		return buf.toString();
	}
	
	String printQuiz(User user, HttpServletRequest request) { // for anonymous users accessing Quiz servlet directly
		try {
			long assignmentId = user.getAssignmentId();
			long topicId = 0L;
			if (assignmentId > 0) topicId = qcache.getAssignment(assignmentId).topicId;
			else topicId = Long.parseLong(request.getParameter("TopicId"));
			return printQuiz(user,topicId);
		} catch (Exception e) { // prompt the user to choose a topic
			List<Topic> topics = ofy().load().type(Topic.class).order("orderBy").list();
			StringBuffer buf = new StringBuffer();
			buf.append(Subject.banner);
			buf.append("<h2>Please select a topic for this quiz</h2>"
					+ "<form method=get>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<SELECT NAME='TopicId'><OPTION Value='0' SELECTED>Select one topic</OPTION>");
			for (Topic t:topics) buf.append(t.topicGroup!=1 || t.orderBy.equals("Hide")?"":"<OPTION VALUE='" + t.id + "'>" + t.title + "</OPTION>");
			buf.append("</SELECT>");
			buf.append("<input type=submit value='Start this quiz' /></form>");
			return buf.toString();
		}
	}

	String printQuiz(User user, long tId) {
		if (user == null) return "<h2>Launch failed because user was not authorized.</h2>";
		
		StringBuffer buf = new StringBuffer();
		try {
			Assignment qa = qcache.getAssignment(user.getAssignmentId());
			long assignmentId = qa==null?0L:qa.id;
			long topicId = qa==null?tId:qa.topicId;
			Topic topic = qcache.getTopic(topicId);
			
			// Check to see if the timeAllowed has been modified by the instructor:
			int timeAllowed = 900; // default value in seconds
			if (qa != null && qa.timeAllowed != null) {
				timeAllowed = qa.timeAllowed; // instructor option, e.g. for student disability accommodations
				user = User.getUser(user.getTokenSignature(), timeAllowed / 60);
			}

			// Check to see if this user has any pending quizzes on this topic:
			Date now = new Date();
			Date t15minAgo = new Date(now.getTime() - timeAllowed * 1000); // 15 minutes ago or whatever time was allowed
			QuizTransaction qt = null;
			if (qa == null)
				qt = ofy().load().type(QuizTransaction.class).filter("userId", user.getHashedId()).filter("topicId", topicId)
						.filter("graded", null).filter("downloaded >", t15minAgo).first().now();
			else
				qt = ofy().load().type(QuizTransaction.class).filter("userId", user.getHashedId())
						.filter("assignmentId", assignmentId).filter("graded", null).filter("downloaded >", t15minAgo)
						.first().now();

			String lis_result_sourcedid = user.getLisResultSourcedid();
			if (qt == null || qt.getGraded() != null) {
				qt = new QuizTransaction(topicId, topic.getTitle(), user.getId(), now, null, 0, assignmentId, 0,
						user.getLisResultSourcedid());
				ofy().save().entity(qt).now(); // creates a long id value to use in random number generator
			} else if (qt.getLisResultSourcedid() == null && lis_result_sourcedid != null) {
				qt.putLisResultSourcedid(lis_result_sourcedid);
				ofy().save().entity(qt);
			}
			
			// Insert javascript code for timers and form submission
			buf.append(timers());
			
			buf.append("<h2>Quiz - " + topic.title + "</h2>");
			
			if (user.isAnonymous()) buf.append("<h3 style='color:red'>Anonymous User</h3>");			
			else {
				buf.append("Quiz Rules"
						+ "	<OL>"
						+ "	<LI>Each quiz must be completed within " + (timeAllowed / 60) + " minutes of the time when it is first downloaded.</LI>"
						+ "	<LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>"
						+ "	<LI>ChemVantage always reports your best score on this assignment to your class LMS.</LI>"
						+ "	</OL>");
			}
			
			buf.append("<div id='timer0' style='color: red'></div>"
					+ "	<div id='ctrl0' style='font-size: 50%; color: red'><a href=javascript:toggleTimers() >hide timers</a><p></div>");
			
			buf.append("<FORM NAME=Quiz id=quizForm METHOD=POST ACTION='/Quiz' onSubmit='return confirmSubmission()' >"
					+ "<INPUT TYPE=HIDDEN NAME='sig' VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME='AssignmentId' VALUE='" + assignmentId + "' />"
					+ "<input type=hidden name='QuizTransactionId' value='" + qt.getId() + "' />"
					+ "<input type=hidden name='TopicId' value='" + topicId + "' />"
					+ "<input type=submit value='Grade This Quiz' />");
			
			//create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = null;
			try { // check for assigned questions
				questionKeys = new ArrayList<Key<Question>>(qa.getQuestionKeys());  // clone the list of questions for the assignment
			} catch (Exception e) { // no assignment exists
				questionKeys = new ArrayList<Key<Question>>(qcache.getQuizQuestionKeys(topicId));
			}
			
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			Random rand = new Random(); // create random number generator to select quiz questions
			rand.setSeed(qt.getId()); // random number generator seeded with QuizTransaction id value
			int possibleScore = 0;
			int nQuestions = questionKeys.size() > 10 ? 10 : questionKeys.size();

			int i = 0;
	
			buf.append("<OL>");
			// Randomly reduce the size of questionKeys to the required number of questions	
			while (questionKeys.size()>nQuestions) questionKeys.remove(rand.nextInt(questionKeys.size()));
		
			Map<Key<Question>, Question> quizQuestions = qcache.getQuestionMap(questionKeys);
			
			while (i < nQuestions && questionKeys.size() > 0) {
				Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
				Question q = quizQuestions.get(k);
				if (q == null) { // this catches cases where an assigned question no longer exists (rare)
					qa.questionKeys.remove(k);
					qcache.putAssignment(qa);
					ofy().save().entity(qa);
					continue;
				}
				
				// by this point we should have a valid question
				i++; // this counter keeps track of the number of questions presented so far
				possibleScore += q.getPointValue();
				// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
				// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
				long seed = Math.abs(qt.getId() - q.getId());
				if (seed == -1)
					seed--; // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
				q.setParameters(seed); // the values are subtracted to prevent (unlikely) overflow
				
				buf.append("<li>" + q.print() +  "<br/></li>");
			}
			qt.putPossibleScore(possibleScore);
			ofy().save().entity(qt);
			buf.append("</OL>");
		
			buf.append("<div id='timer1' style='color: red'></div>"
					+ "	<div id='ctrl1' style='font-size: 50%; color: red'><a href=javascript:toggleTimers() >hide timers</a><p></div>");
			
			buf.append("<input type=submit value='Grade This Quiz'/>"
					+ "</FORM>");
			
			buf.append("<script>startTimers(" + new Date(qt.getDownloaded().getTime() + timeAllowed * 1000).getTime() + ");</script>");
			
		} catch (Exception e) {
			return "<h2>Launch failed because no topic ID was specified.</h2>"; 
		}
		
		return buf.toString();
	}
	
	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		boolean premiumUser = true;
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
						+ "Take this quiz again</a> or go back to the <a href=/>ChemVantage home page</a>.":"You may repeat this "
							+ "assignment by launching it from your class learning management system.");
			}

			// Check to see if the time limit (15 minutes) for taking the Quiz has expired:
			Assignment qa = qcache.getAssignment(user.getAssignmentId());
			long assignmentId = qa==null?0L:qa.id;
			
			int timeAllowed = 900;  // default time to complete the quiz, in seconds
			try {
				timeAllowed = qa.timeAllowed>0?qa.timeAllowed:900;  // override the default timeAllowed if qa.tinmeAllowed exists
			} catch (Exception e) {}
			
			if (now.getTime() - qt.downloaded.getTime() > (timeAllowed*1000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeAllowed/60 + " minute time limit for this quiz has expired.";
			
			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + "</h2>");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			buf.append(ajaxJavaScript(user.getTokenSignature())); // load javascript for AJAX problem reporting form
			
			// Create a StringBuffer to contain correct answers to questions answered correctly
			List<String> missedQuestions = new ArrayList<String>();	 // questions with correct answers
			
			// For each question the form contains a parameter: (questionId,studentAnswer)
			// Make a list of the question keys. Non-numeric inputs are ignored (catch and continue).
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			Map<Key<Question>,Question> quizQuestions = qcache.getQuestionMap(questionKeys);
			
			List<Response> responses = new ArrayList<Response>();
			
			// This is the main scoring loop:
			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] = studentAnswer[i].compareTo(studentAnswer[0])>0?studentAnswer[0]+studentAnswer[i]:studentAnswer[i]+studentAnswer[0];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = quizQuestions.get(k);
							long seed = Math.abs(qt.id - q.id);
							if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
							q.setParameters(seed);
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							
							responses.add(new Response("Quiz",qt.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.getId(),now));

							studentScore += score;
							if (score == 0) {  
								// include question in list of incorrectly answered questions
								wrongAnswers++;
								missedQuestions.add("<LI>" + q.printAllToStudents(studentAnswer[0],premiumUser) + "</LI>");
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			if (responses.size()>0) ofy().save().entities(responses);  // batch save of Response entities
			qt.graded = now;
			qt.score = studentScore;
			ofy().save().entity(qt);
			
			// Try to post the score to the student's LMS:
			boolean reportScoreToLms = false;
			try {
				if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
				Score.updateQuizScore(user.getId(),qt);
				reportScoreToLms = qa.lti_ags_lineitem_url != null || (qa.lis_outcome_service_url != null && user.getLisResultSourcedid() != null);
				if (reportScoreToLms) {
					QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(assignmentId)).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue
				}
			} catch (Exception e) {}

			buf.append("<h4>Your score on this quiz is " + studentScore 
					+ " point" + (studentScore==1?"":"s") + " out of a possible " + qt.possibleScore + " points.</h4>");

			if (studentScore == qt.possibleScore) {
				buf.append("<H2>Congratulations on a perfect score! Good job.</H2>");
			} else {
				int leftBlank = qt.possibleScore - studentScore - wrongAnswers;
				if (leftBlank>0) buf.append(leftBlank + " question" 
						+ (leftBlank>1?"s were":" was") + " left unanswered (blank).<br/>");
				if (wrongAnswers>0) buf.append(wrongAnswers + " question" + (wrongAnswers>1?"s were":" was") + " answered incorrectly. ");

				if (wrongAnswers>0 && !user.isAnonymous()) {
					// Display the correct answers to missed problems. However, discourage submission of empty or deliberately wrong answers:
					// If no answers were correct, give no correct answers.
					// If 1 answer was correct, give up to 2 correct answers.
					// If 2 answers were correct, give up to 4 correct answers.
					// If 3 answers were correct, give up to 5 correct answers.
					// If 4 answers were correct, give up to 6 correct answers.
					int nAnswersEligible = 0;
					switch (studentScore) {
					case 0: nAnswersEligible = 0; break;
					case 1: nAnswersEligible = Math.min(wrongAnswers,2); break;
					case 2: nAnswersEligible = Math.min(wrongAnswers,4); break;
					case 3: nAnswersEligible = Math.min(wrongAnswers,5); break;
					default: nAnswersEligible = wrongAnswers;
					if (!premiumUser) nAnswersEligible = wrongAnswers; // but correct answers are not displayed...
					}
					
					if (nAnswersEligible > 0) {
						buf.append("The correct answer" + (nAnswersEligible>1?"s ":" ") + (nAnswersEligible<wrongAnswers?"to " + nAnswersEligible + " of these ":"") + (nAnswersEligible==1?"is":"are") + " shown below. ");
						if (nAnswersEligible < wrongAnswers) buf.append("<br/>The more questions you answer correctly, the more correct answers to missed questions will be displayed.");
						buf.append("<OL>");
						for (int i=0;i<wrongAnswers;i++) {
							if (nAnswersEligible > 0) buf.append(missedQuestions.get(i));
							nAnswersEligible --;
						}
						buf.append("</OL>");
					}  else buf.append("You must answer at least one question correctly to view the correct answers to questions that you missed. ");
				}

				// print some words of encouragement:
				buf.append("<h4>Improve Your Score</h4>");
				if (studentScore<6) {
					buf.append("If you get stuck on a difficult question, "
							+ "you may refer to your textbook during the quiz. Please keep the " + timeAllowed/60
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
			+ "or <a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a>.");

		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
	String timers() {
		return "<SCRIPT>"
				+ "function toggleTimers() {"
				+ "	var timer0 = document.getElementById('timer0');"
				+ "	var timer1 = document.getElementById('timer1');"
				+ "	var ctrl0 = document.getElementById('ctrl0');"
				+ "	var ctrl1 = document.getElementById('ctrl1');"
				+ "	if (timer0.style.display=='') {"
				+ "		timer0.style.display='none';timer1.style.display='none';"
				+ "		ctrl0.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
				+ "		ctrl1.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
				+ "	} else {"
				+ "		timer0.style.display='';"
				+ "		timer1.style.display='';"
				+ "		ctrl0.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
				+ "		ctrl1.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
				+ "	}"
				+ "}"
				+ "var seconds;"
				+ "var minutes;"
				+ "var oddSeconds;"
				+ "var endMillis;"
				+ "function countdown() {"
				+ "	var nowMillis = new Date().getTime();"
				+ "	var seconds=Math.round((endMillis-nowMillis)/1000);"
				+ "	var minutes = seconds<0?Math.ceil(seconds/60.):Math.floor(seconds/60.);"
				+ "	var oddSeconds = seconds%60;"
				+ "	for (i=0;i<2;i++) document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
				+ "	if (seconds==30) alert('30 seconds remaining');"
				+ "	if (seconds < 0) document.Quiz.submit();"
				+ "	else setTimeout('countdown()',1000);"
				+ "}"
				+ "function startTimers(m) {"
				+ "	endMillis = m;"
				+ "	countdown();"
				+ "}"
				+ "function confirmSubmission() {"
				+ "	var elements = document.getElementById('quizForm').elements;"
				+ "	var nAnswers;"
				+ "	var i;"
				+ "	var checkboxes;"
				+ "	var lastCheckboxIndex;"
				+ "	nAnswers = 0;"
				+ "	for (i=0;i<elements.length;i++) {"
				+ "		if (isNaN(elements[i].name)) continue;"
				+ "		if (elements[i].type=='text' && elements[i].value.length>0) nAnswers++;"
				+ "		else if (elements[i].type=='radio' && elements[i].checked) nAnswers++;"
				+ "		else if (elements[i].type=='checkbox') {"
				+ "			checkboxes = document.getElementsByName(elements[i].name);"
				+ "			lastCheckboxIndex = i + checkboxes.length - 1;"
				+ "			for (j=0;j<checkboxes.length;j++) if (checkboxes[j].checked==true) {"
				+ "				nAnswers++;"
				+ "				i = lastCheckboxIndex;"
				+ "				break;"
				+ "			}    "
				+ "		}  "
				+ "	}  "
				+ "	if (nAnswers<10) return confirm('Submit this quiz for scoring now? ' + (10-nAnswers) + ' answers may be left blank.');"
				+ "	else return true;"
				+ "}"
				+ "function showWorkBox(qid) {}"
				+ "</SCRIPT>"
				+ "";
	}
	
	String fiveStars() {
		StringBuffer buf = new StringBuffer();

		buf.append("<script type='text/javascript'>"
				+ "  var star1 = new Image(); star1.src='images/star1.gif';"
				+ "  var star2 = new Image(); star2.src='images/star2.gif';"
				+ "  var set = false;"
				+ "  function showStars(n) {"
				+ "    if (!set) {"
				+ "      document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));"
				+ "      for (i=1;i<6;i++) {document.getElementById(i).src=(i<=n?star2.src:star1.src)}"
				+ "    }"
				+ "  }"
				+ "  function setStars(n) {"
				+ "    if (!set) {"
				+ "      ajaxStars(n);"
				+ "      set = true;"
				+ "      document.getElementById('sliderspan').style='display:none';"
				+ "    }"
				+ "  }"
				+ "</script>");

		buf.append("Please rate your overall experience with ChemVantage:<br />"
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

	String showScores (User user,User forUser) {
		if (!user.isInstructor() && !user.getId().equals(forUser.getId())) return "<H1>Access denied.</H1>";
		
		StringBuffer buf = new StringBuffer("<h2>Quiz Transactions</h2>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		Assignment a = qcache.getAssignment(user.getAssignmentId());
		Topic t = qcache.getTopic(a.getTopicId());
		try {
			buf.append("Assignment Number: " + a.id + "<br>");
			buf.append("Topic: "+ t.title + "<br>");
			buf.append("Valid: " + df.format(now) + "<p>");
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",forUser.getHashedId()).filter("assignmentId",a.id).order("downloaded").list();
			
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for this user on this assignment.<p>");
			} else {				
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					s = ofy().load().key(Key.create(Key.create(User.class,forUser.getHashedId()),Score.class,a.id)).safe();
					if (s.numberOfAttempts != qts.size()) throw new Exception();
				} catch (Exception e) { // create a fresh Score entity from scratch
					s = Score.getInstance(forUser.getId(), a);
					ofy().save().entity(s);
				}
				
				buf.append("This user's best score on the assignment is " + Math.round(10*s.getPctScore())/10. + "%.<br>");

				if (!forUser.isAnonymous()) {  // try to validate the score with the LMS grade book entry
					String lmsScore = null;
					try {
						double lmsPctScore = 0;
						boolean gotScoreOK = false;

						if (a.lti_ags_lineitem_url != null) {  // LTI version 1.3
							lmsScore = LTIMessage.readUserScore(a,forUser.getId());
							try {
								lmsPctScore = Double.parseDouble(lmsScore);
								gotScoreOK = true;
							} catch (Exception e) {
								//buf.append("LMS returned: " + lmsScore + "<br/>");
							}
						}
						else if (a.lis_outcome_service_url != null && s.lis_result_sourcedid != null) {  // LTI version 1.1
							String messageFormat = "application/xml";
							String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
							String oauth_consumer_key = forUser.getId().substring(0, forUser.getId().indexOf(":"));
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
									+ "If you think this may be due to a stale score, the user may submit this assignment for grading,<br>"
									+ "even for a score of zero, and ChemVantage will try to refresh the best score to the LMS.<p>");
						} else throw new Exception();
					} catch (Exception e) {
						buf.append("ChemVantage was unable to retrieve the score for this assignment from the LMS.<br>"
								+ "Sometimes it takes several seconds for a score to be posted in the LMS grade book.<br>");
						if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that the assignment may not have been submitted for a score yet. ");
						if (forUser.isInstructor()) buf.append("Some LMS providers do not accept score submissions for instructors or test students.");
						buf.append("<p>");
					}
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
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();
		Assignment a = qcache.getAssignment(user.getAssignmentId());
		if (a==null) return "No assignment was specified for this request.";
		
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		if (a.lti_ags_lineitem_url != null && a.lti_nrps_context_memberships_url != null) {
			try { // code for LTI version 1.3
				Topic t = qcache.getTopic(a.topicId);

				buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
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
					String hashedUserId = Subject.hashId(platform_id + id);
					keys.put(id,Key.create(Key.create(User.class,hashedUserId),Score.class,a.id));
				}
				Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
				
				buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th></tr>");
				int i=0;
				boolean synched = true;
				for (Map.Entry<String,String[]> entry : membership.entrySet()) {
					if (entry == null) continue;
					String s = scores.get(entry.getKey());
					Score cvScore = cvScores.get(keys.get(entry.getKey()));
					i++;
					buf.append("<tr><td>" + i + ".&nbsp;</td>"
							+ "<td>" + entry.getValue()[1] + "</td>"
							+ "<td>" + entry.getValue()[2] + "</td>"
							+ "<td>" + entry.getValue()[0] + "</td>"
							+ "<td align=center>" + (s == null?" - ":s + "%") + "</td>"
							+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td></tr>");
					// Flag this score set as unsynchronizde only if there is one or more non-null ChemVantage Learner score that is not equal to the LMS score
					// Ignore Instructor scores because the LMS often does not report them, and ignore null cvScore entities because they cannot be reported.
					synched = synched && (!"Learner".equals(entry.getValue()[0]) || (cvScore!=null?String.valueOf(cvScore.getPctScore()).equals(s):true));
				}
				buf.append("</table><br/>");
				if (!synched) {
					buf.append("If any of the Learner scores above are not synchronized, you may use the button below to launch a background task " 
						+ "where ChemVantage will resubmit them to your LMS. This can take several seconds to minutes depending on the "
						+ "number of scores to process. Please note that you may have to adjust the settings in your LMS to accept the "
						+ "revised scores. For example, in Canvas you may need to change the assignment settings to Unlimited Submissions. "
						+ "This may also cause the submission to be counted as late if the LMS assignment deadline has passed.<br/>"
						+ "<form method=post action=/Quiz >"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
						+ "<input type=submit value='Synchronize Scores' />"
						+ "</form>");
				}
				return buf.toString();
			} catch (Exception e) {
				buf.append(e.toString());
			}
		} else {
			buf.append("Sorry, there is not enough information available from your LMS to support this request.<p>");			
		}
		return buf.toString();
	}
	
	boolean synchronizeScores(User user,HttpServletRequest request) {
		// This method looks for assignment scores that are different from the LMS scores and resubmits the score to the LMS
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
			Assignment a = qcache.getAssignment(user.getAssignmentId());
			if (a==null) throw new Exception();  // can only do this for a known assignment
			if (a.lti_ags_lineitem_url == null || a.lti_nrps_context_memberships_url == null) throw new Exception(); // need both of these to work
			Map<String,String> scores = LTIMessage.readMembershipScores(a);
			if (scores==null || scores.size()==0) throw new Exception();  // this only works if we can get info from the LMS
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			if (membership==null || membership.size()==0) throw new Exception();  // there must be some members of this class
			Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
			String platform_id = d.getPlatformId() + "/";
			for (String id : membership.keySet()) {
				String hashedUserId = Subject.hashId(platform_id + id);
				keys.put(id,Key.create(Key.create(User.class,hashedUserId),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				if (cvScore==null) continue;
				String s = scores.get(entry.getKey());
				if (String.valueOf(cvScore.getPctScore()).equals(s)) continue;  // the scores match (good!)
				QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(platform_id + entry.getKey(),"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	String selectQuestionsForm(User user) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = qcache.getAssignment(user.getAssignmentId());
			Topic topic = qcache.getTopic(a.topicId);
			
			buf.append("<h3>Customize Quiz Assignment</h3>");
			buf.append("<b>Topic: " + topic.title + "</b><p>");
					
			if (a.timeAllowed==null) a.timeAllowed = 900; // default time for completing the exam
			
			// Allow instructor to pick individual question items from all active questions:
			buf.append("Each quiz consists of 10 questions selected at random from the items below. The default time allowed "
					+ "to complete each quiz is 15 minutes, but you may change this (e.g., to create a special assignment for "
					+ "a student requiring extended time up to 60 minutes).<br>");
			buf.append("<form action=/Quiz method=post><input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "Time allowed for this assignment: <input type=text size=5 name=TimeAllowed value=" + a.timeAllowed/60. + "> minutes. "
					+ "<input type=submit name=UserRequest value='Set Allowed Time'><br>"
					+ "</form><p>");
			buf.append("You may select the items that will be used for this group by checking the boxes in the left column. "
					+ "Students are provided answers to the items that they answer incorrectly. "
					+ "Therefore, the total number of questions should be "
					+ "larger than 10, but not much larger than 50.  Experience shows that 30 items is about right in most cases.<p>"
					+ "If you don't see a question you want to include, you may "
					+ "<a href=/Contribute?TopicId=" + topic.id + "&AssignmentType=Quiz&sig=" + user.getTokenSignature() + ">contribute a new question item</a> to the database.<p>");

			List<Question> questions = new ArrayList<Question>(qcache.getQuestionMap(qcache.getQuizQuestionKeys(topic.id)).values());
			//Query<Question> questions = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("topicId",topic.id).filter("isActive",true);
			
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=/Quiz>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "'>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions) {
				i++;
				q.setParameters();
				buf.append("<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM>");
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage());
		}
		return buf.toString();
	}

}
