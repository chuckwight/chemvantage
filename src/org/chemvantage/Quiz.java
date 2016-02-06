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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) {
				user = Nonce.getUser(request.getParameter("Nonce"));
				session.setAttribute("UserId", user.id);
			} else user = User.getInstance(session);
			if (user==null || Login.lockedDown && !user.isAdministrator()) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String nonce = session.isNew()?Nonce.createInstance(user):null;
			out.println(Home.getHeader(user,nonce) + printQuiz(user,request,nonce) + Home.footer);
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) {
				user = Nonce.getUser(request.getParameter("Nonce"));
				session.setAttribute("UserId", user.id);
			} else user = User.getInstance(session);
			if (user==null || Login.lockedDown && !user.isAdministrator()) {
				response.sendRedirect("/");
				return;
			}
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String nonce = session.isNew()?Nonce.createInstance(user):null;
			out.println(Home.getHeader(user) + printScore(user,request,nonce) + Home.footer);
		} catch (Exception e) {}
	}

	String instructorPage(HttpServletRequest request,long assignmentId,String nonce) {
		// this page is displayed by default when the instructor accesses this assigned quiz
		// to view the quiz itself, include ShowQuiz=true as one of the GET parameters
		StringBuffer buf = new StringBuffer();
		try {
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			Topic topic = ofy().load().type(Topic.class).id(assignment.topicId).safe();
			DateFormat dfShort = DateFormat.getDateInstance(DateFormat.SHORT);
			DateFormat dfLong = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			dfShort.setTimeZone(group.getTimeZone());
			dfLong.setTimeZone(group.getTimeZone());
			
			buf.append("<h2>Quiz - " + topic.title + " (" + subject.title + ")</h2>");
			buf.append("<FONT SIZE=-1>This is the instructor page; students will <a href=/Quiz?TopicId=" + topic.id + "&ShowQuiz=true&Nonce=" + nonce + ">go directly to the quiz</a>.</FONT><p>");
			
			buf.append("<FORM ACTION='/Groups' METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Quiz>"
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE=" + topic.id + ">"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
					+ "<b>Quiz Deadline: " + dfLong.format(assignment.deadline) + "</b> <a href=# onClick=document.getElementById('deadline').style.display='inLine'><FONT SIZE=-2>change this</FONT></a><p>"
					+ "<div id='deadline' style='display:none'>After the deadline, ChemVantage will no longer report scores on this quiz to the LMS. "
					+ "However, students may still use the assignment link to take practice quizzes.<br/>"
					+ "<INPUT TYPE=TEXT SIZE=15 NAME=QuizDeadline VALUE='" + dfShort.format(assignment.deadline) + "'> at 11:59:59 PM in the " + Groups.timeZoneSelectBox(group.timeZone,false) + " time zone."
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Set Deadline'></FORM></div><p>");
			
			buf.append("<b>Customize This Quiz</b> <a id=slink href=/Groups?UserRequest=AssignQuizQuestions&GroupId=" + group.id + "&TopicId=" + topic.id + "&Nonce=" + nonce + "><FONT SIZE=-2>select questions</FONT></a><p>");
				
			buf.append("<b>Quiz Rules</b> <a id=rlink href=# onClick=document.getElementById('rules').style.display='inLine';document.getElementById('rlink').style.display='none'><FONT SIZE=-2>show more</FONT></a><br/>"
					+ "<div id='rules' style='display:none'><OL><LI>Each quiz must be completed within " + timeLimit + " minutes of the time when it is first downloaded.</LI>"
					+ "<LI>Quizzes may be retaken as many times as desired, to improve the score.</LI>"
					+ "<LI>For each quiz topic, the server reports the student's best score.</LI>"
					+ "<LI>Quizzes must be submitted for scoring prior to the deadline below in order to receive class credit.</LI>"
					+ "</OL></div><p>");

			buf.append("<b>Please Rate Your Experience with ChemVantage</b> <a href=/Feedback><FONT SIZE=-2>here</FONT></a><p>");
			
			buf.append("<b>Quiz Scores</b> <a href=# onClick=document.getElementById('details').style.display='inLine'><FONT SIZE=-2>show details</FONT></a><br/>"
					+ "<div id='details' style='display:none'>The following is a list of maximum pre-deadline scores on this quiz. In most cases, these scores have been reported to the grade book "
					+ "in the class learning management system. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
					+ "is possible that these scores may be different from those in the LMS grade book. The number of quiz attempts is shown in parentheses for your reference. "
					+ "A red dot indicates a score that is low enough to be a concern. If a student completes this quiz satisfactorily after the quiz deadline, the red dot will "
					+ "disappear, but the score will remain unchanged. If you change the deadline, all scores will be recalculated to reflect the revised deadline. (Try it!)</div><p>");
			
			if (group.memberIds.size()==0) return buf.toString();
			Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH></TH><TH>Name</TH><TH>Email</TH><TH>Score</TH></TR>");
			// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
			Set<Key<Score>> keys = new HashSet<Key<Score>>();
			for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
			Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
			
			// display the table of scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
			for (String id:group.memberIds) {
				User u = members.get(id);
				Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
				Score s = scoresMap.get(k);
				if (s==null) {
					s = Score.getInstance(u.id,assignment);
	    			ofy().save().entity(s);
	    		}
				buf.append("<TR><TD>" + group.memberIds.indexOf(id) + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
			}
			buf.append("</TABLE>");
		} catch (Exception e) {
				return buf.toString() + e.getMessage();
		}
		return buf.toString();
	}
	
	String printQuiz(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {
				return "<h2>No Quiz Selected</h2>You must return to the <a href=Home>Home Page</a> "
				+ "and select a topic for this quiz using the drop-down box.";
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy().load().type(Group.class).id(user.myGroupId).now():null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);
			Date now = new Date();

			// Check to see if this user has any pending quizzes on this topic:
			Date then = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).filter("userId",user.id).filter("topicId",topic.id).filter("graded",null).filter("downloaded >",then).first().now();
			if (qt == null || qt.graded != null) {
				qt = new QuizTransaction(topic.id,topic.title,user.id,now,null,0,0,request.getRemoteAddr());
				if (request.getParameter("lis_result_sourcedid")!=null) qt.lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
				ofy().save().entity(qt).now();  // creates a long id value to use in random number generator
			}
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - qt.downloaded.getTime())/1000);
			
			Assignment a = null;
			try {
				a = ofy().load().type(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Quiz").filter("topicId",topicId).first().safe();
				if (user.isInstructor() && request.getParameter("ShowQuiz")==null) return instructorPage(request,a.id,nonce);
			/*	
				{
					buf.append("<br><span style='color:red'>Instructor Only: "
							+ "<a href=Groups?UserRequest=AssignQuizQuestions&GroupId=" 
							+ myGroup.id + "&TopicId=" + topicId 
							+ ">customize this quiz assignment</a></span>");
				}
		*/
			} catch (Exception e) {}


			buf.append("\n<h2>Quiz - " + topic.title + " (" + subject.title + ")</h2>");
			
			buf.append("\n<FORM NAME=Quiz METHOD=POST ACTION=Quiz onSubmit=\"javascript: return confirmSubmission()\">");
			if (nonce!=null) buf.append("<INPUT TYPE=HIDDEN NAME=Nonce VALUE='" + nonce + "'>");
			// Gather profile information if needed; otherwise just print the user's name.
			if (user.needsFirstName()) buf.append("First name: <input type=text name=FirstName><br/>"); else buf.append("<b>" + user.getFirstName() + "</b><br/>");
			if (user.needsEmail()) buf.append("Email: <input type=text name=Email><br>");
			
			buf.append(df.format(qt.downloaded) + "<p>"); // Print the date/time the quiz was first downloaded (may be up to timeLimit minutes ago)

			buf.append("\nQuiz Rules<OL>");
			buf.append("\n<LI>Each quiz must be completed within " + timeLimit + " minutes of the time when it is first downloaded.</LI>");
			buf.append("\n<LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>");
			buf.append("\n<LI>For each quiz topic, the server reports your best quiz score.</LI>");

			if (myGroup != null) {
				buf.append("\n<LI>You must submit the quiz for scoring before the indicated deadline in order to receive class credit.</LI>");
				buf.append("\n<LI>Instructors can view best scores and downloads by date/time in order to enforce quiz deadlines.</LI>");
			}
			buf.append("</OL>");
			
			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
					
			buf.append("\n<input type=submit value='Grade This Quiz'>");

			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = null;
			try {  // check for assigned questions
				questionKeys = a.questionKeys;
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
	
	String printScore(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			// Update profile information, if necessary
			if (user.needsFirstName() || user.needsLastName() || user.needsEmail()) {
				if (request.getParameter("FirstName")!=null) user.setFirstName(request.getParameter("FirstName"));
				if (request.getParameter("LastName")!=null) user.setLastName(request.getParameter("LastName"));
				if (request.getParameter("Email")!=null) user.setEmail(request.getParameter("Email"));
				ofy().save().entity(user);
			}
			Date now = new Date();
			long transactionId = Long.parseLong(request.getParameter("QuizTransactionId"));
			QuizTransaction qt = ofy().load().type(QuizTransaction.class).id(transactionId).safe();
			if (qt.graded != null) {
				return "<h2>No Score</h2>"
				+ "Sorry, this quiz has been scored already and cannot be scored again. Please consult the <a href=Scores>scores page</a>.";
			}
			if (now.getTime() - qt.downloaded.getTime() > (timeLimit*60000+10000)) // includes 10 second grace period
				return "Sorry, the " + timeLimit + " minute time limit for this quiz has expired.";

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy().load().type(Group.class).id(user.myGroupId).now():null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			int studentScore = 0;
			int wrongAnswers = 0;

			buf.append("<h2>Quiz Results - " + qt.topicTitle + " (" + subject.title + ")</h2>\n");
			buf.append("<b>" + user.getBothNames() + "</b><br>\n");
			buf.append(df.format(now));
			buf.append(ajaxScoreJavaScript(user.verifiedEmail)); // load javascript for AJAX problem reporting form
			
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
				Assignment a = ofy().load().type(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Quiz").filter("topicId",qt.topicId).first().safe();
				Score s = Score.getInstance(user.id,a);
				ofy().save().entity(s).now();
				if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",a.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
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
			buf.append("<FORM METHOD=GET Action=Quiz>"
					+ (nonce!=null?"<INPUT TYPE=HIDDEN NAME=Nonce VALUE='" + nonce + "'>":"")
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + qt.topicId + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=r VALUE=" + new Random().nextInt(9999) + ">"
					+ "<INPUT TYPE=SUBMIT VALUE='Take this quiz again'></FORM>\n");
			
		} catch (Exception e) {
			buf.append("Sorry, this quiz could not be scored.<br>" + e.getMessage());
		}
		return buf.toString();
	}
	
	String ajaxScoreJavaScript(boolean verifiedEmail) {
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
		+ (!verifiedEmail?"However, no response is possible unless you verify the email address in your <a href=/Verification>user profile</a>.":"") 
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
	
}
