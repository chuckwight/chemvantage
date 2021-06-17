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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Quiz")
public class Quiz extends HttpServlet {
	// parameters that determine the properties of the quiz program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for quiz overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printQuiz()
	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	
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
			
			if ("ShowScores".contentEquals(userRequest)) out.println(Home.header("Your ChemVantage Scores") + showScores(user) + Home.footer);
			else if ("ShowSummary".contentEquals(userRequest)) out.println(Home.header("Your Class ChemVantage Scores") + showSummary(user,request) + Home.footer);
			else if ("AssignQuizQuestions".contentEquals(userRequest) && user.isInstructor()) {
				out.println(Home.header("Customize ChemVantage Quiz Assignment") + selectQuestionsForm(user) + Home.footer);
			} else response.sendRedirect("/Quiz.jsp?sig=" + user.getTokenSignature());
			//else out.println(Home.header("ChemVantage Quiz") + printQuiz(user,request) + Home.footer);
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
			
			if ("UpdateAssignment".contentEquals(userRequest)) {
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				a.updateQuestions(request);
				response.sendRedirect("/Quiz.jsp?sig=" + user.getTokenSignature());
			} else if ("Set Allowed Time".contentEquals(userRequest)) {
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				try {
					double minutes = Double.parseDouble(request.getParameter("TimeAllowed"));
					if (minutes > 60.) minutes = 60.;
					a.timeAllowed = minutes<1.0?60:(int)(minutes*60);
				} catch (Exception e) {
					a.timeAllowed = 900;
				}
				ofy().save().entity(a).now();
				response.sendRedirect("/Quiz.jsp?sig=" + user.getTokenSignature());	
			} else out.println(Home.header("ChemVantage Quiz Results") + printScore(user,request) + Home.footer);
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
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
						+ (user.isAnonymous()?"<p><a href=/Quiz.jsp?TopicId=" + qt.topicId + "&sig=" + user.getTokenSignature() + ">"
						+ "Take this quiz again</a> or go back to the <a href=/>ChemVantage home page</a>.":"You may repeat this "
							+ "assignment by launching it from your class learning management system.");
			}

			// Check to see if the time limit (15 minutes) for taking the Quiz has expired:
			Assignment qa = null;
			try {
				qa = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			} catch (Exception e) {}
			
			int timeAllowed = 900;  // default time to complete the quiz, in seconds
			try {
				timeAllowed = qa.timeAllowed>0?qa.timeAllowed:900;  // override the default timeAllowed if qa.tinmeAllowed exists
			} catch (Exception e) {}
			
			if (now.getTime() - qt.downloaded.getTime() > (timeAllowed*1000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeAllowed/60 + " minute time limit for this quiz has expired.";
			
			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + " (" + subject.title + ")</h2>\n");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			buf.append(ajaxJavaScript(user.getTokenSignature())); // load javascript for AJAX problem reporting form
			
			// Create a StringBuffer to contain correct answers to questions answered correctly
			List<String> missedQuestions = new ArrayList<String>();			
			//missedQuestions.append("<OL>");
			
			// For each question the form contains a parameter: (questionId,studentAnswer)
			// Make a list of the question keys. Non-numeric inputs are ignored (catch and continue).
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			Map<Key<Question>,Question> quizQuestions = ofy().load().keys(questionKeys);
			
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
							long seed = Math.abs(qt.id - q.id);
							if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
							q.setParameters(seed);
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							
							responses.add(new Response("Quiz",qt.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));

							studentScore += score;
							if (score == 0) {  
								// include question in list of incorrectly answered questions
								wrongAnswers++;
								missedQuestions.add("<LI>" + q.printAllToStudents(studentAnswer[0]) + "</LI>");
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			if (responses.size()>0) ofy().save().entities(responses);  // batch save of Response entities
			//missedQuestions.append("</OL>\n");
			qt.graded = now;
			qt.score = studentScore;
			ofy().save().entity(qt);
			
			// Try to post the score to the student's LMS:
			boolean reportScoreToLms = false;
			try {
				if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
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
						+ (leftBlank>1?"s were":" was") + " left unanswered (blank).<br/>");
				if (wrongAnswers>0) {
					buf.append(wrongAnswers + " question" + (wrongAnswers>1?"s were":" was") + " answered incorrectly.<br/>");
					
					// Display the correct answers to missed problems. However, discourage submission of empty or deliberately wrong answers:
					// If no answers were correct, give no correct answers.
					// If 1 answer was correct, give up to 2 correct answers.
					// If 2 answers were correct, give up to 4 correct answers.
					// If 3 answers were correct, give up to 6 correct answers.
					int nAnswersEligible = 2 * studentScore;
					if (nAnswersEligible > wrongAnswers) nAnswersEligible = wrongAnswers;
					if (nAnswersEligible > 0) {
						buf.append("<OL>");
						for (int i=0;i<nAnswersEligible;i++) buf.append(missedQuestions.get(i));
						buf.append("</OL>");
					} else buf.append("You must answer at least one question correctly to view the correct answers to questions that you missed. ");
					if (wrongAnswers > nAnswersEligible) buf.append("The more questions you answer correctly, the more correct answers to missed questions will be displayed.");
				}
				
				// print some words of encouragement:
				buf.append("<h4>Improve Your Score</h4>\n");
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
			buf.append((qa==null?"<a href=/Quiz.jsp?TopicId=" + qt.topicId + "&sig=" + user.getTokenSignature() + ">Take this quiz again</a> or go back to the <a href=/>ChemVantage home page</a> " :
			"You may take this quiz again by clicking the assignment link in your learning management system ")			
			+ "or <a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a>.");

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

	protected static String showScores (User user) {
		StringBuffer buf = new StringBuffer("<h2>Quiz Transactions</h2>");
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
			
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).order("downloaded").list();
			
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for this user on this assignment.<p>");
			} else {				
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					s = ofy().load().key(Key.create(Key.create(User.class,user.getHashedId()),Score.class,a.id)).safe();
					if (s.numberOfAttempts != qts.size()) throw new Exception();
				} catch (Exception e) { // create a fresh Score entity from scratch
					s = Score.getInstance(user.id, a);
					ofy().save().entity(s);
				}
				
				buf.append("This user's best score on the assignment is " + Math.round(10*s.getPctScore())/10. + "%.<br>");

				if (!user.isAnonymous()) {  // try to validate the score with the LMS grade book entry
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
								buf.append("LMS returned: " + lmsScore + " for user " + user.id + "<br/>");
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
									+ "If you think this may be due to a stale score, the user may submit this assignment for grading,<br>"
									+ "even for a score of zero, and ChemVantage will try to refresh the best score to the LMS.<p>");
						} else throw new Exception();
					} catch (Exception e) {
						buf.append("ChemVantage was unable to retrieve the score for this assignment from the LMS.<br>"
								+ e.toString() + ": " + e.getMessage() + "<br/>"
								+ "Sometimes it takes several seconds for a score to be posted in the LMS grade book.<br>");
						if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that the assignment may not have been submitted for a score yet. ");
						if (user.isInstructor()) buf.append("Some LMS providers do not accept score submissions for instructors or test students.");
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
					String hashedUserId = Subject.hashId(platform_id + id);
					keys.put(id,Key.create(Key.create(User.class,hashedUserId),Score.class,a.id));
				}
				Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
				
				buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th></tr>");
				int i=0;
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
				}
				buf.append("</table>");
				return buf.toString();
			} catch (Exception e) {
				buf.append(e.toString());
			}
		} else {
			buf.append("Sorry, there is not enough information available from your LMS to support this request.<p>");			
			buf.append("<a href=/Quiz.jsp?sig=" + user.getTokenSignature() + ">Return to this quiz</a>.<p>");
		}
		return buf.toString();
	}
	
	String selectQuestionsForm(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			Topic topic = ofy().load().type(Topic.class).id(a.topicId).safe();
			
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
					+ "<a href=/Contribute?sig=" + user.getTokenSignature() + ">contribute a new question item</a> to the database.<p>");

			Query<Question> questions = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("topicId",topic.id).filter("isActive",true);
			
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
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM>");
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage());
		}
		return buf.toString();
	}

}
