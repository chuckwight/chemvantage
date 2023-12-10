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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.google.gson.JsonObject;
import com.googlecode.objectify.Key;

@WebServlet("/Quiz")
public class Quiz extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet presents a quiz for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();			
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			switch (userRequest) {
			case "ShowQuiz":
				String quizTransactionId = request.getParameter("QuizTransactionId");
				out.println(Subject.header("ChemVantage Quiz") + Subject.banner + showQuiz(user,a,quizTransactionId) + Subject.footer);
				break;
			case "ShowScores":
				String forUserHashedId = request.getParameter("ForUserHashedId");
				out.println(Subject.header("ChemVantage Scores") + Subject.banner + showScores(user,a,forUserHashedId,null) + Subject.footer);
				break;
			case "ShowSummary":
				out.println(Subject.header("Your Class ChemVantage Scores") + showSummary(user,a) + Subject.footer);
				break;
			case "AssignQuizQuestions":
				if (user.isInstructor()) out.println(Subject.header("Customize ChemVantage Quiz Assignment") + selectQuestionsForm(user,a,request) + Subject.footer);
				break;
			case "SynchronizeScore":
				out.println(synchronizeScore(user,a,request.getParameter("ForUserId")));
				break;
			default: 
				out.println(Subject.header("ChemVantage Quiz") + printQuiz(user,a) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			switch (userRequest) {
			case "UpdateAssignment":
				a.updateQuestions(request);
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Save New Title":
				a.title = request.getParameter("AssignmentTitle");
				ofy().save().entity(a).now();
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Set Allowed Time":
				try {
					double minutes = Double.parseDouble(request.getParameter("TimeAllowed"));
					if (minutes > 60.) minutes = 60.;
					a.timeAllowed = minutes<1.0?60:(int)(minutes*60);
				} catch (Exception e) {
					a.timeAllowed = 900;
				}
				ofy().save().entity(a).now();
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Set Allowed Attempts":
				a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				try {
					a.attemptsAllowed = Integer.parseInt(request.getParameter("AttemptsAllowed"));
					if (a.attemptsAllowed<1) a.attemptsAllowed = null;
				} catch (Exception e) {
					a.attemptsAllowed = null;
				}
				ofy().save().entity(a).now();
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Synchronize Scores":
				if (synchronizeScores(user,a)) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				else out.println("Synchronization request failed.");
				break;
			default:
				out.println(Subject.header("ChemVantage Quiz Results") + printScore(user,a,request) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}
	
	static String instructorPage(User user, Assignment a) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();		
		try {
			if (a.title==null) {  // legacy Quiz only provided topicId
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				a.title = t.title;
				if (a.conceptIds.isEmpty()) a.conceptIds = t.conceptIds;
				ofy().save().entity(a).now();
			}
			
			boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
			
			buf.append("<h2>Instructor Page</h2>");
			buf.append("Quiz - " + a.title + "<br/><br/>");
			
			buf.append("Each quiz draws 10 questions randomly from a bank of " + a.questionKeys.size() + " selected questions.<br/>");			
			if (a.timeAllowed != null) buf.append("Students are permitted " + a.timeAllowed/60 + " minutes to complete the quiz.<br/>");
			if (a.attemptsAllowed==null || a.attemptsAllowed<1) buf.append("Students may attempt this assignment an unlimited number of times to improve their score.<br/>");
			else buf.append("Students may only attempt this assignment " + a.attemptsAllowed + (a.attemptsAllowed==1?" time":" times") + ".<br/>");
			buf.append("<br/>");
			
			buf.append("From here, you may<UL>"
					+ "<LI><a href='/Quiz?UserRequest=AssignQuizQuestions&sig=" + user.getTokenSignature() + "'>Customize this quiz</a> to set the time allowed, number of attempts allowed, and select the available question items.</LI>"
					+ (supportsMembership?"<LI><a href='/Quiz?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>Review your students' quiz scores</a></LI>":"")
					+ "</UL>");
			buf.append("<a style='text-decoration: none' href='/Quiz?sig=" + user.getTokenSignature() + "'>"
					+ "<button style='display: block; width: 500px; border: 1 px; background-color: #00FFFF; color: black; padding: 14px 28px; font-size: 18px; text-align: center; cursor: pointer;'>"
					+ "Show This Assignment (recommended)</button></a>");
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + e.getMessage());
		}
		return buf.toString();
	}
	
	static String printQuiz(User user, Assignment qa) {
		if (user == null) return "<h2>Launch failed because user was not authorized.</h2>";
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		try {
			if (qa==null) {  // anonymous user; print a quiz on Chapter 1 of the first smartText entity
				qa = new Assignment();
				qa.id = 0L;
				qa.assignmentType = "Quiz";
				Text text = ofy().load().type(Text.class).filter("smartText",true).first().now();
				Chapter ch = text.chapters.get(0);
				qa.title = ch.title;
				for (Long cId : ch.conceptIds) qa.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("conceptId",cId).keys().list());
			} else if (qa.title==null) {  // legacy Quiz only provided topicId
				Topic t = ofy().load().type(Topic.class).id(qa.topicId).now();
				qa.title = t.title;
				if (qa.conceptIds.isEmpty()) qa.conceptIds = t.conceptIds;
				ofy().save().entity(qa).now();
			}
			debug.append("1");
			// Check to see if the timeAllowed has been modified by the instructor:
			int timeAllowed = 900; // default value in seconds
			if (qa.timeAllowed != null) {
				timeAllowed = qa.timeAllowed; // instructor option, e.g. for student disability accommodations
				user = User.getUser(user.getTokenSignature(), timeAllowed / 60);
			}
			debug.append("2");
			
			// Check to see if this user has any pending quizzes on this topic:
			Date now = new Date();
			Date t15minAgo = new Date(now.getTime() - timeAllowed * 1000); // 15 minutes ago or whatever time was allowed
			QuizTransaction qt = null;
			if (!user.isAnonymous()) qt = ofy().load().type(QuizTransaction.class).filter("userId", user.getHashedId())
						.filter("assignmentId", qa.id).filter("graded", null).filter("downloaded >", t15minAgo).first().now();
			debug.append("3");
			
			// check to see if attemptsAllowed has been reached
			int nAttempts = 0;
			if (qt==null && qa.attemptsAllowed != null) {
				nAttempts = ofy().load().type(QuizTransaction.class).filter("assignmentId",qa.id).filter("userId",user.getHashedId()).count();
				if (nAttempts >= qa.attemptsAllowed) {
					return Subject.banner + "<h2>Sorry, you are only allowed " + qa.attemptsAllowed + " attempt" + (qa.attemptsAllowed==1?"":"s") + " on this assignment.</h2>"
							+ showScores(user,qa,user.getId(),null) + "<br/><br/>";
				}
			}
			
			if (qt == null) {  // create a new quizTransation
				qt = new QuizTransaction(qa.id,user.getHashedId());
				ofy().save().entity(qt).now(); // creates a long id value to use in random number generator
				nAttempts++;
			}	
			
			// Insert javascript code for timers and form submission
			buf.append(timers());
			
			if (user.isAnonymous()) buf.append(Subject.banner);  // present the ChemVantage banner
			
			buf.append("<h2>Quiz - " + qa.title + "</h2>");
			
			if (user.isAnonymous()) buf.append("<h3 style='color:#EE0000'>Anonymous User</h3>");			
			else {
				buf.append("Quiz Rules"
						+ "	<OL>"
						+ "	<LI>Each quiz must be completed within " + (timeAllowed / 60) + " minutes of the time when it is first downloaded.</LI>"
						+ (qa.attemptsAllowed==null?"<LI>You may repeat this quiz as many times as you wish, to improve your score.</LI>":"<LI>You are allowed " + qa.attemptsAllowed + " attempts of this quiz assignment. This is attempt #" + nAttempts + ".</LI>")
						+ "	<LI>ChemVantage always reports your best score on this assignment to your class LMS.</LI>"
						+ "	</OL>");
			}
			
			buf.append("<div id='timer0' style='color: #EE0000'></div>"
					+ "	<div id='ctrl0' style='font-size: 50%; color: #EE0000'><a href=javascript:toggleTimers() >hide timers</a><p></div>");
			
			debug.append("5");
			
			buf.append("<FORM NAME=Quiz id=quizForm METHOD=POST ACTION='/Quiz' onSubmit='return confirmSubmission()' >"
					+ "<INPUT TYPE=HIDDEN NAME='sig' VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME='AssignmentId' VALUE='" + qa.id + "' />"
					+ "<input type=hidden name='QuizTransactionId' value='" + qt.getId() + "' />"
					+ "<input type=submit value='Grade This Quiz' />");
			
			// Randomly select the keys to questions to be presented
			Random rand = new Random(); // create random number generator to select quiz questions
			rand.setSeed(qt.getId()); // random number generator seeded with QuizTransaction id value
			List<Key<Question>> allQuestionKeys = new ArrayList<Key<Question>>(qa.questionKeys);  // clone the List
			List<Key<Question>> myQuestionKeys = new ArrayList<Key<Question>>();
			while (myQuestionKeys.size()<10 && allQuestionKeys.size()>0) {
				Key<Question> k = allQuestionKeys.get(rand.nextInt(allQuestionKeys.size()));
				myQuestionKeys.add(k);
				allQuestionKeys.remove(k);
			}
			debug.append("5");
			
			// At this point we should have 10 (or max number) randomly selected questionKeys
			Map<Key<Question>, Question> quizQuestions = ofy().load().keys(myQuestionKeys);
			
			// Housekeeping: if any of the questions does not exist, remove the key from the assignment:
			if (!quizQuestions.keySet().containsAll(myQuestionKeys)) {
				for (Key<Question> k : myQuestionKeys) if (!quizQuestions.keySet().contains(k)) qa.questionKeys.remove(k);
				ofy().save().entity(qa);
			}
			
			int possibleScore = 0;
			buf.append("<OL>");
			
			while (myQuestionKeys.size() > 0) {
				Key<Question> k = myQuestionKeys.get(rand.nextInt(myQuestionKeys.size()));
				myQuestionKeys.remove(k);
				Question q = quizQuestions.get(k);
				possibleScore += q.getPointValue();
				// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
				// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
				long seed = Math.abs(qt.getId() - q.getId());
				if (seed == -1) seed--; // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
				q.setParameters(seed); // the values are subtracted to prevent (unlikely) overflow
				
				buf.append("<li>" + q.print() +  "<br/></li>");
			}
			
			qt.putPossibleScore(possibleScore);
			ofy().save().entity(qt).now();
			buf.append("</OL>");
		
			buf.append("<div id='timer1' style='color: #EE0000'></div>"
					+ "	<div id='ctrl1' style='font-size: 50%; color: #EE0000'><a href=javascript:toggleTimers() >hide timers</a><p></div>");
			
			buf.append("<input type=submit value='Grade This Quiz'/>"
					+ "</FORM>");
			
			buf.append("<script>startTimers(" + new Date(qt.getDownloaded().getTime() + timeAllowed * 1000).getTime() + ");</script>");
			
		} catch (Exception e) {
			return "<h2>Sorry, the quiz failed</h2>" + e.getMessage()==null?e.toString():e.getMessage(); 
		}
		
		return buf.toString();
	}
	
	String printScore(User user,Assignment qa,HttpServletRequest request) {
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
						+ "Your score on this quiz was " + qt.score + " out of a possible " + qt.possibleScore + " points.<br/><br/>";
				}

			if (qa==null) {  // anonymous user; use the quiz on Chapter 1 of the first smartText entity
				qa = new Assignment();
				qa.id = 0L;
				qa.assignmentType = "Quiz";
				Text text = ofy().load().type(Text.class).filter("smartText",true).first().now();
				qa.title = text.chapters.get(0).title;
			}
			
			int timeAllowed = 900;  // default time to complete the quiz, in seconds
			try {
				timeAllowed = qa.timeAllowed>0?qa.timeAllowed:900;  // override the default timeAllowed if qa.tinmeAllowed exists
			} catch (Exception e) {}
			
			boolean timeExpired = now.getTime() - qt.downloaded.getTime() > (timeAllowed*1000+10000);
			
			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qa.title + "</h2>");
			
			if (user.isAnonymous()) buf.append("<h3><font color=#EE0000>Anonymous User</font></h3>");
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
			Map<Key<Question>,Question> quizQuestions = ofy().load().keys(questionKeys);
			
			List<Response> responses = new ArrayList<Response>();
			
			// This is the main scoring loop:
			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer = orderResponses(request.getParameterValues(Long.toString(k.getId())));
					if (!studentAnswer.isEmpty()) {
						Question q = quizQuestions.get(k);
						long seed = Math.abs(qt.id - q.id);
						if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
						q.setParameters(seed);
						int score = q.isCorrect(studentAnswer)?q.pointValue:0;

						responses.add(new Response("Quiz",qa.id,q.id,studentAnswer,q.getCorrectAnswer(),score,q.pointValue,user.getId(),qt.id,now));

						studentScore += score;
						if (score == 0) {  
							// include question in list of incorrectly answered questions
							wrongAnswers++;
							missedQuestions.add("<LI>" + q.printAllToStudents(studentAnswer,premiumUser) + "</LI>");
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			if (responses.size()>0) ofy().save().entities(responses);  // batch save of Response entities
			qt.graded = now;
			qt.score = timeExpired?0:studentScore;
			ofy().save().entity(qt);
			
			// Try to post the score to the student's LMS:
			try {
				if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
				Score.updateQuizScore(user.getId(),qt);
				if (qa.lti_ags_lineitem_url != null) {
					JsonObject payload = new JsonObject();
					payload.addProperty("AssignmentId",qa.id);
					payload.addProperty("UserId",user.getId());
					Utilities.createTask("/ReportScore",payload);
				}
					//QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(qa.id)).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue
			} catch (Exception e) {}

			if (timeExpired) buf.append("<h4>Your score on this quiz is 0 points because it was submitted after the allowed time of " + timeAllowed/60 + " minutes.</h4>");
			else buf.append("<h4>Your score on this quiz is " + studentScore 
					+ " point" + (studentScore==1?"":"s") + " out of a possible " + qt.possibleScore + " points.</h4>");

			if (studentScore == qt.possibleScore && !timeExpired) {
				buf.append("<H2>Congratulations on a perfect score! Good job.</H2>");
			} else {
				int leftBlank = qt.possibleScore - studentScore - wrongAnswers;
				if (leftBlank>0) buf.append(leftBlank + " question" 
						+ (leftBlank>1?"s were":" was") + " left unanswered (blank).<br/>");
				buf.append(wrongAnswers + " question" + (wrongAnswers==1?" was":"s were") + " answered incorrectly. ");

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
						buf.append("<a id=wrongAnsLink href=# onClick=document.getElementById('wrongAnsLink').style='display:none';document.getElementById('wrongAnsDiv').style='display:inline'>Show me</a>");
						buf.append("<div id=wrongAnsDiv style='display:none'>");
						buf.append("The correct answer" + (nAnswersEligible>1?"s ":" ") + (nAnswersEligible<wrongAnswers?"to " + nAnswersEligible + " of these ":"") + (nAnswersEligible==1?"is":"are") + " shown below. ");
						if (nAnswersEligible < wrongAnswers) buf.append("The more questions you answer correctly, the more correct answers to missed questions will be displayed.");
						buf.append("<OL>");
						for (int i=0;i<wrongAnswers;i++) {
							if (nAnswersEligible > 0) buf.append(missedQuestions.get(i));
							nAnswersEligible --;
						}
						buf.append("</OL>");
						buf.append("</div>");
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
			if (studentScore == qt.possibleScore && !timeExpired) buf.append(fiveStars());
			else buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");

			if (!user.isAnonymous()) buf.append("You may <a href=/Quiz?UserRequest=ShowScores&sig=" + user.getTokenSignature() + ">review all your scores on this assignment</a>.<p>") ;

			if (!user.isAnonymous() && qa.lti_ags_lineitem_url == null) {
				buf.append("<b>Please note:</b> Your score was not reported back to the grade book of your class "
						+ "LMS because the LTI launch request did not contain enough information to do this. "
						+ (user.isInstructor()?"For instructors this is common.":"") + "<p>");				
			}
			
			if (!user.isAnonymous()) buf.append("Use the assignment link in your learning management system to repeat this quiz, or <a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a>");
		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
	static String timers() {
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
				+ "<span id='vote' style='font-family:tahoma; color:#EE0000;'>(click a star):</span><br>");

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

	static String ajaxJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,params,studentAnswer,note,email) {\n"
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
		+ "      '<FONT COLOR=#EE0000><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  url += '&QuestionId=' + id + '&Params=' + params + '&sig=" + signature + "&Notes=' + note + '&Email=' + email + '&StudentAnswer=' + studentAnswer;\n"
		+ "  xmlhttp.open('GET',url,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function synchronizeScore(forUserId) {\n"
		+ "  let xmlhttp=GetXmlHttpObject();\n"
		+ "  let url = '/Quiz?UserRequest=SynchronizeScore&sig=" + signature + "&ForUserId=' + forUserId;\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      if (xmlhttp.responseText.includes('OK')) {\n"
		+ "        document.getElementById('cell'+forUserId).innerHTML='OK. Check grade book settings.';"
		+ "        setTimeout(() => {location.reload();}, 500);\n"
		+ "      } else {\n"
		+ "        document.getElementById('cell'+forUserId).innerHTML=xmlhttp.responseText;"
		+ "      }\n"
		+ "    }\n"
		+ "  }\n"
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
/*
	static String showScores (User user,User forUser,Assignment a) {
		if (!user.isInstructor() && !user.getId().equals(forUser.getId())) return "<H1>Access denied.</H1>";
		
		StringBuffer buf = new StringBuffer("<h3>Quiz Transactions</h3>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		try {
			buf.append("Assignment Number: " + a.id + "<br>");
			buf.append("Topic: "+ a.title + "<br>");
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
						if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that the assignment may not have been submitted for a score yet.<br/>");
						buf.append("<br/>");
					}
				}
				buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Quiz Score</th></tr>");
				for (QuizTransaction qt : qts) {
					buf.append("<tr><td>" + qt.id + "</td><td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":100.*qt.score/qt.possibleScore + "%") +  "</td></tr>");
				}
				buf.append("</table><br>Missing scores indicate quizzes that were downloaded but not submitted for scoring.<br/><br/>");
				
				if (a.attemptsAllowed != null) buf.append("The maximum number of attempts on this assignment is " + a.attemptsAllowed + ".<br/><br/>");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
*/	
	static String showQuiz (User user, Assignment a, String quizTransactionId) {
		StringBuffer buf = new StringBuffer();
		try {
			Long qtId = Long.parseLong(quizTransactionId);
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).id(qtId).safe();
			List<Response> responses = ofy().load().type(Response.class).filter("assignmentId",a.id).filter("transactionId",qtId).list();
			
			buf.append("<h2>Quiz Results</h2>");
			buf.append("Topic: " + a.title + "<br/>");
			buf.append("Submitted: " + qt.graded + "<br/>");
			buf.append("Score: " + String.valueOf(qt.score>0?qt.score*100/qt.possibleScore:0)+"%<br/>");
			
			buf.append("<ol>");
			for (Response r : responses) {
				Question q = ofy().load().type(Question.class).id(r.questionId).now();
				if (q==null) continue; // question might have been deleted
				q.setParameters(Math.abs(qt.getId() - q.getId()));
				buf.append("<li>" + q.printAllToStudents(r.studentResponse) + "</li>");
			}
			buf.append("</ol>");
			
			int nUnanswered = qt.possibleScore - responses.size();
			if (responses.size()==0) buf.append("The detailed responses to this quiz are not available.");
			else if (nUnanswered>0) buf.append(String.valueOf(nUnanswered) + " question" + (nUnanswered>1?"s were":" was") + " left blank.");
			
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}
	
	static String showScores(User user, Assignment a, String for_user_hashed_id, String for_user_name) {
		// This method shows a table of QuizTransaction entities for one user on the assignment
		// with links to view individual Quiz submissions
		StringBuffer buf = new StringBuffer();
		
		if (!user.isInstructor() || for_user_hashed_id == null) for_user_hashed_id = user.getHashedId();  // non-instructors can only view their own records
		
		try {
			buf.append("<h2>Quiz Transactions</h2>");
			if (for_user_name != null) buf.append("Name: " + for_user_name + "<br/>");
			buf.append("Topic: "+ a.title + "<br>");
			buf.append("Assignment ID: " + a.id + "<br/>");
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			buf.append("Valid: " + df.format(new Date()) + "<br/><br/>");
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",for_user_hashed_id).filter("assignmentId",a.id).order("downloaded").list();
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for this user on this assignment.<p>");
				return buf.toString();
			}	
		
			buf.append("<div style='display:table;text-align:center;'>");
			buf.append("<div style='display:table-row'>"  // header row
					+ "<div style='display:table-cell;font-weight:bold;padding-right:20px;'>Attempt</div>"
					+ "<div style='display:table-cell;font-weight:bold;padding-right:20px;'>Downloaded</div>"
					+ "<div style='display:table-cell;font-weight:bold;padding-right:20px;'>Elapsed Time</div>"
					+ "<div style='display:table-cell;font-weight:bold;padding-right:20px;'>Total Score</div>"
					+ "<div style='display:table-cell;font-weight:bold;padding-right:20px;'></div>"
					+ "</div>"); // end of header row
			for (QuizTransaction qt : qts) {
				String elapsedTime = qt.graded==null?" - ":String.valueOf((qt.graded.getTime()-qt.downloaded.getTime())/60000L) + " min " + String.valueOf((qt.graded.getTime()-qt.downloaded.getTime())/1000L%60L) + " s";
				String score = qt.graded==null?"no submission":String.valueOf(qt.score>0?qt.score*100/qt.possibleScore:0)+"%";
				int nResponses = ofy().load().type(Response.class).filter("assignmentId",a.id).filter("transactionId",qt.id).count();
				buf.append("<div style='display:table-row'>"  // row for one transaction
						+ "<div style='display:table-cell;padding-right:20px;'>" + (qts.indexOf(qt)+1) + "</div>"
						+ "<div style='display:table-cell;padding-right:20px;'>" + qt.downloaded + "</div>"
						+ "<div style='display:table-cell;padding-right:20px;'>" + elapsedTime + "</div>"
						+ "<div style='display:table-cell;padding-right:20px;'>" + score + "</div>"
						+ "<div style='display:table-cell;padding-right:20px;'>" + (nResponses>0?"<a href=/Quiz?UserRequest=ShowQuiz&QuizTransactionId=" + qt.id + "&sig=" + user.getTokenSignature() + "&ForUserHashedId=" + for_user_hashed_id + ">View Quiz</a>":"") + "</div>"
						+ "</div>");  // end of row
			}
			buf.append("</div><br/><br/>"); // end of table
		} catch (Exception e) {
			 buf.append(e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}

	static String showSummary(User user,Assignment a) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();
		if (a==null) return "No assignment was specified for this request.";
		
		if (a.lti_ags_lineitem_url != null && a.lti_nrps_context_memberships_url != null) {
			try { 
				buf.append("<h3>" + a.assignmentType + " - " + a.title + "</h3>");
				buf.append("Assignment ID: " + a.id + "<br>");
				buf.append("Valid: " + new Date() + "<p>");
				buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
						+ "and may or may not include user's names or emails, depending on the settings of your LMS.<br/><br/>");

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
				
				buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th><th>Details</th></tr>");
				int i=0;
				int nMismatched = 0;
				for (Map.Entry<String,String[]> entry : membership.entrySet()) {
					if (entry == null) continue;
					String s = scores.get(entry.getKey());
					String lmsScoreString = (s==null?" - ":s + "%");
					Score cvScore = cvScores.get(keys.get(entry.getKey()));
					String cvScoreString = cvScore==null?" - ":String.valueOf(cvScore.getPctScore() + "%");
					String forUserHashedId = keys.get(entry.getKey()).getParent().getName();
					boolean synched = !"Learner".equals(entry.getValue()[0]) || cvScoreString.equals(lmsScoreString);
					if (!synched) nMismatched++;  // number of Learner scores not same in CV and LMS
					String forUserId = platform_id + entry.getKey();  // only send hashed values through links					
					i++;
					buf.append("<tr><td>" + i + ".&nbsp;</td>"
							+ "<td>" + entry.getValue()[1] + "</td>"
							+ "<td>" + entry.getValue()[2] + "</td>"
							+ "<td>" + entry.getValue()[0] + "</td>"
							+ "<td align=center>" + (s == null?" - ":lmsScoreString) + "</td>"
							+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td>"
							+ "<td>" +  (cvScore == null?"":"<a href=/Quiz?UserRequest=ShowScores&sig=" + user.getTokenSignature() + "&ForUserHashedId=" + forUserHashedId + ">Show Scores</a>") + "</td>"
							+ (synched?"":"<td><span id='cell" + forUserId + "'><button onClick=this.disabled=true;synchronizeScore('" + forUserId + "'); >sync</button></span></td>")
							+ "</tr>");
				}
				buf.append("</table><br/>");
				if (nMismatched > 0) {
					buf.append(ajaxJavaScript(user.getTokenSignature()));
					buf.append("You may use the individual 'sync' buttons above to resubmit any ChemVantage score to the LMS. Note that in some cases, mismatched scores are expected (e.g., when "
							+ "the instructor overrides a score or when a late submission is not accepted by the LMS). You may have to adjust the settings in your LMS to accept the "
							+ "revised score (e.g., change the due date, grade override or allowed number of submissions). ");
				}
				if (nMismatched>1) {
					buf.append("Use the button below to synchronize all of the Learner scores. This might take a minute, depending on the number of mismatches.<br/>"
						+ "<form method=post action=/Quiz >"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
						+ "<input type=submit value='Synchronize All Scores' />"
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
	
	String synchronizeScore(User user, Assignment a, String forUserId) {
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
			if (a==null) throw new Exception();  // can only do this for a known assignment
			if (LTIMessage.postUserScore(Score.getInstance(forUserId,a), forUserId).contains("Success")) return "OK";
		} catch (Exception e) {}
		return "Failed. Check assignment settings in the LMS.";
	}
	
	boolean synchronizeScores(User user,Assignment a) {
		// This method looks for assignment scores that are different from the LMS scores and resubmits the score to the LMS
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
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
				JsonObject payload = new JsonObject();
				payload.addProperty("AssignmentId",a.id);
				payload.addProperty("UserId",platform_id + entry.getKey());
				Utilities.createTask("/ReportScore",payload);
				//QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(platform_id + entry.getKey(),"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	String selectQuestionsForm(User user,Assignment a, HttpServletRequest request) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h3>Customize Quiz Assignment</h3>");
			buf.append("<form action=/Quiz method=post>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<b>Title:</b>&nbsp;Quiz - <input type=text size=25 name=AssignmentTitle value='" + a.title + "' />&nbsp;"
					+ "<input type=submit name=UserRequest value='Save New Title' /></form><br/>");
					
			if (a.timeAllowed==null) a.timeAllowed = 900; // default time for completing the exam
			
			// Allow instructor to pick individual question items from all active questions:
			buf.append("Each quiz consists of 10 questions selected at random from the items below. The default time allowed "
					+ "to complete each quiz is 15 minutes, but you may change this (e.g., to create a special assignment for "
					+ "a student requiring extended time up to 60 minutes).<br>");
			buf.append("<form action=/Quiz method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "Time allowed for this assignment: <input type=text size=5 name=TimeAllowed value=" + a.timeAllowed/60. + "> minutes. "
					+ "<input type=submit name=UserRequest value='Set Allowed Time'><br>"
					+ "</form><p>");
			buf.append("By default, students may attempt this quiz as many times as they wish. This rewards students who persist "
					+ "to achieve a better score. However, you may limit the number of attempts here. Leave the field blank to permit unlimited attempts.<br/>"
					+ "<form action=/Quiz method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "Number of attempts allowed for this assignment: <input type=text size=10 name=AttemptsAllowed " 
					+ (a.attemptsAllowed==null?"placeholder=unlimited":"value=" + a.attemptsAllowed) + " /> "
					+ "<input type=submit name=UserRequest value='Set Allowed Attempts' />"
					+ "</form><br/>");
			
			buf.append("You may select the items that will be used for this assignment by checking the boxes in the left column below. "
					+ "Students are provided answers to the items that they answer incorrectly. Therefore, the total number of questions should be "
					+ "larger than 10, but not much larger than 50.  Experience shows that 40 items is about right in most cases. "
					+ "If you don't see a question you want to include, you may "
					+ "<a href=/Contribute?AssignmentType=Quiz&sig=" + user.getTokenSignature() + ">contribute a new question item</a> to the database.<br/><br/>");

			// make a List of conceptIds covered by this assignment
			List<Long> conceptIds = a.conceptIds;
			// Include any conceptId included in this request:
			Long newConceptId = null;
			try {
				newConceptId = Long.parseLong(request.getParameter("ConceptId"));
				conceptIds.add(newConceptId);
			} catch (Exception e) {}

			// Make a list of key concepts already covered by this assignment:
			List<Key<Concept>> conceptKeys = ofy().load().type(Concept.class).order("orderBy").keys().list();
			Map<Key<Concept>,Concept> keyConcepts = ofy().load().keys(conceptKeys);
			if (conceptIds.size()>0) {
				buf.append("The questions listed below cover the following key concepts:<ul>");
				for (Long cId : conceptIds) buf.append("<li>" + keyConcepts.get(Key.create(Concept.class,cId)).title + "</li>");
				buf.append("</ul>");
			}
			// Create a short form to select one additional key concept to include (will exclude the previous selection, if any)
			buf.append("<form method=get action=/Quiz>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "You may include additional question items from: "
					+ "<input type=hidden name=UserRequest value=AssignQuizQuestions />"
					+ "<select name=ConceptId onchange=this.form.submit();><option value='Select'>Select a key concept</option>");
			for (Key<Concept> k : conceptKeys) {
				try {
					if (conceptIds.contains(k.getId()) || keyConcepts.get(k).orderBy.startsWith(" 0")) continue;  // skip current and hidden conceptIds
					buf.append("<option value='" + k.getId() + "'" + (newConceptId!=null && k.getId()==newConceptId?" selected>":">") + keyConcepts.get(k).title + "</option>");
				} catch (Exception e) {}
			}
			buf.append("</select></form><hr><br/>");
						
			// now we have all of the relevant conceptIds. Make a list of questions carrying these attributes:
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Long cId : conceptIds) questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("conceptId",cId).keys().list());

			Map<Key<Question>,Question> questions = ofy().load().keys(questionKeys);

			if (!questionKeys.containsAll(a.questionKeys)) {  // might be missing a few questions due to customization
				questions.putAll(ofy().load().keys(a.questionKeys));
				questionKeys = new ArrayList<Key<Question>>(questions.keySet()); // this avoids duplicate keys
			}

			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT id=selectAll TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick='for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}'"
					+ "> Select/Unselect All</FORM>");
			buf.append("<script>document.getElementById('selectAll').indeterminate=true;</script>");
			
			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=/Quiz>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "'>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions.values()) {
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
	
	String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

}
