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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class Homework extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	static Map<Key<Question>,Question> hwQuestions = new HashMap<Key<Question>,Question>();
	int retryDelayMinutes = 2;  // minimum time between answer submissions for any single question

	public String getServletInfo() {
		return "This servlet presents a homework assignment for the user.";
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
			
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String nonce = session.isNew()?Nonce.createInstance(user):null;
			out.println(Home.header + printHomework(user,request,nonce) + Home.footer);
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
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String nonce = session.isNew()?Nonce.createInstance(user):null;
			out.println(Home.header + printScore(user,request,nonce) + Home.footer);
		} catch (Exception e) {}
	}
/*
	String instructorPage(HttpServletRequest request,long assignmentId,String nonce) {
		// this page is displayed by default when the instructor accesses this assignment
		// to view the homework assignment itself, include ShowHomework=true as one of the GET parameters
		StringBuffer buf = new StringBuffer();
		try {
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			Topic topic = ofy().load().type(Topic.class).id(assignment.topicId).safe();

			DateFormat dfShort = DateFormat.getDateInstance(DateFormat.SHORT);
			DateFormat dfLong = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			dfShort.setTimeZone(group.getTimeZone());
			dfLong.setTimeZone(group.getTimeZone());
			boolean noDeadline = assignment.getDeadline().getTime()==0L;
			
			buf.append("<h2>Homework - " + topic.title + " (" + subject.title + ")</h2>");
			buf.append("<FONT SIZE=-1>This is the instructor page; students will <a href=/Homework?TopicId=" + topic.id + "&ShowHomework=true&Nonce=" + nonce + ">go directly to the assignment</a>.</FONT><p>");
			
			buf.append("<FORM ACTION='/Groups' METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Homework>"
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE=" + topic.id + ">"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
					+ "<b>Homework Deadline:</b> " + (noDeadline?"<FONT COLOR=RED>none</FONT>":dfLong.format(assignment.getDeadline()))
					+ " <a href=# onClick=document.getElementById('deadlineForm').style.display='inLine'><FONT SIZE=-2>change this</FONT></a><p>");
			buf.append("<div id='deadlineForm' style='display:none'>"
					+ "Enter a date below and select your local time zone.<br/>"
					+ "After the deadline ChemVantage will not report scores on this assignment to the LMS,<br/>"
					+ "but students may still use the assignment link to practice solving problems.<br/>"
					+ "<INPUT TYPE=TEXT SIZE=15 NAME=HWDeadline VALUE='" + (noDeadline?"none":dfShort.format(assignment.getDeadline())) 
					+ "' onFocus=if(HWDeadline.value=='none'){HWDeadline.value='" + dfShort.format(new Date()) + "';document.getElementById('esBox').checked=true}>"
					+ "at 11:59:59 PM in the " + Groups.timeZoneSelectBox(group.timeZone,false) + " time zone.<br/>"
					+ "<label><INPUT TYPE=CHECKBOX ID=esBox NAME=EmailScores VALUE=true" + (assignment.emailScoresToInstructor?" CHECKED>":">") + " Email scores to me after the deadline.</lable><br>"
					+ "<label><INPUT TYPE=CHECKBOX NAME=UpdateLMSScores VALUE=true> Update scores in the LMS grade book.</label><br>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Set Deadline'></FORM></div><p>");
		
			buf.append("<b>Customize This Homework Assignment</b> <a href=/Groups?UserRequest=AssignHomeworkQuestions&GroupId=" + group.id + "&TopicId=" + topic.id + "&Nonce=" + nonce + "><FONT SIZE=-2>select questions</FONT></a><p>");
				
			buf.append("<b>Homework Scores</b> <a id=slink href=# onClick=document.getElementById('details').style.display='inLine';document.getElementById('slink').style.display='none'><FONT SIZE=-2>show details</FONT></a><br/>"
					+ "<div id='details' style='display:none'>The following is a list of best pre-deadline scores on this assignment. In most cases, these scores have been reported to the grade book "
					+ "in the class learning management system. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
					+ "is possible that these scores may be different from those in the LMS grade book. "
					+ "A red dot indicates a score that is low enough to be a concern. If a student completes this assignment satisfactorily after the deadline, the red dot will "
					+ "disappear, but the score will remain unchanged. If you change the deadline, all scores will be recalculated to reflect the revised deadline. (Try it!)</div><p>");
			
			if (group.validatedMemberCount()==0) return buf.toString();
			Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
			
			// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
			Set<Key<Score>> keys = new HashSet<Key<Score>>();
			for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
			Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
			int i = 0;
			Score s = null;
			
			buf.append("Instructors and Teaching Assistants<br>"
					+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
			for (String id:group.memberIds) {
				try {
					User u = members.get(id);
					if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) {
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
						s = scoresMap.get(k);
						if (s==null) {
							s = Score.getInstance(u.id,assignment);
							ofy().save().entity(s).now();
						}
						i++;
						buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getScore() + "</TD></TR>");
						members.remove(id);
					}
				} catch (Exception e2) {}
			}
			buf.append("</TABLE><p>");

			// display the table of student scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
			i=0;
			Date now = new Date();
			buf.append("Students<p>");
			if (members.size()>0) {
				buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
				for (String id:group.memberIds) {
					try {
						User u = members.get(id);
						if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) continue;
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
						s = scoresMap.get(k);
						if (s==null) {
							s = Score.getInstance(u.id,assignment);
							ofy().save().entity(s).now();
						}
						i++;
						buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(noDeadline?now:assignment.getDeadline(),group.rescueThresholdScore) + "</TD></TR>");
					} catch (Exception e2) {}
				}
				buf.append("</TABLE>");			
			} else buf.append("No students are registered in this group.");

		} catch (Exception e) {
			return buf.toString() + e.getMessage();
		}
		return buf.toString();
	}
*/
	String printHomework(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {
				return "<h2>No Homework Assignment Selected</h2>You must return to the <a href=Home>Home Page</a> "
				+ "and select a topic for this assignment using the drop-down box.";
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid"); // used for reporting score back to the LMS
			
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy().load().type(Group.class).id(user.myGroupId).now():null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);
//			Date now = new Date();

			Assignment hwa = null;
			try {
				hwa = ofy().load().type(Assignment.class).filter("groupId",user.myGroupId).filter("assignmentType","Homework").filter("topicId",topicId).first().now();
				//if (user.isInstructor() && request.getParameter("ShowHomework")==null) return instructorPage(request,hwa.id,nonce);
/*				
				if (user.isInstructor() && hwa!=null) {
					buf.append("<br><span style='color:red'>Instructor Only: "
							+ "<a href=Groups?UserRequest=AssignHomeworkQuestions&GroupId=" 
							+ myGroup.id + "&TopicId=" + topicId 
							+ ">customize this homework assignment</a></span>");
				}
*/
			} catch (Exception e) {}

			buf.append("\n<h2>Homework Exercises - " + topic.title + " (" + subject.title + ")</h2>");
			if (user.isInstructor()) buf.append("Instructor: you may <a href=/Groups?UserRequest=AssignHomeworkQuestions&GroupId=" + myGroup.id + "&TopicId=" + topic.id + "&Nonce=" + nonce + ">"
					+ "customize this homework assignment</a> by selecting/deselecting the available question items.<p>");
			
						//buf.append("\n<b>" + user.getBothNames() + "</b><br>");
/*
			// Gather profile information if needed; otherwise just print the user's name.
			buf.append("<FORM METHOD=POST ACTION=Verification>");
			boolean submitNeeded = user.needsFirstName() || user.needsEmail();
			if (user.needsFirstName()) buf.append("First name: <input type=text name=FirstName><br/>"); else buf.append("<b>" + user.getFirstName() + "</b><br/>");
			if (user.needsEmail()) buf.append("Email: <input type=text name=Email><br>");
			if (submitNeeded) buf.append("<INPUT TYPE=SUBMIT Name=UserRequest VALUE='Save My Information'><br>");
			buf.append("</FORM>");

			buf.append(df.format(now) + "<p>");
*/			
			if (!user.isAnonymous()) {
				buf.append("\nHomework Rules<UL>");
				buf.append("\n<LI>You may rework problems and resubmit answers as many times as you wish, to improve your score.</LI>");
				buf.append("\n<LI>There is a retry delay of " + retryDelayMinutes + " minutes between answer submissions for any single question.</LI>");
				buf.append("\n<LI>Most questions are customized, so the correct answers are different for each student.</LI>");
				buf.append("\n<LI>For each topic, the server tracks your total score and the total number of submissions.</LI>");
				buf.append("\n<LI>A checkmark will appear to the left of each correctly solved problem.</LI>");
				if (myGroup != null) {
					buf.append("However, class credit for assigned problems is awarded only if the answer is submitted prior to the deadline.</LI>");
					buf.append("\n<LI>Your instructor can view scores and submissions by date/time in order to enforce homework deadlines.</LI>");
				}
				buf.append("</UL>");
			}
			
			List<Key<Question>> optionalQuestionKeys = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",topicId).filter("isActive",true).keys().list();
			if (optionalQuestionKeys.size()==0) buf.append("<h2>Sorry, there are no homework questions for this topic.</h2>");
			
			if (hwa != null) { // use hwa.questionIds to move assigned questions to the other list
				optionalQuestionKeys.removeAll(hwa.questionKeys);
				buf.append("\nAssigned Exercises<br>");
				int i = 1;
				buf.append("<TABLE>");
				for (Key<Question> k : hwa.questionKeys) {
					try {
						Question q = hwQuestions.get(k);
						if (q==null) {
							try {
								q = ofy().load().key(k).safe();
								hwQuestions.put(k,q);
							} catch (Exception e) {
								continue;  // this catches cases where an assigned question no longer exists
							}
						}
						q.setParameters(user.id.hashCode());
						buf.append("\n<TR VALIGN=TOP><TD>");
						
						boolean solved = ofy().load().type(HWTransaction.class).filter("userId",user.id).filter("questionId",q.id).filter("score >",0).count() > 0;					
						if (solved) buf.append("<IMG SRC=/images/checkmark.gif ALT='This problem was solved previously.'>");
						
						buf.append("&nbsp;<a id=" + q.id + " /></TD>"
								+ "<FORM METHOD=POST ACTION=Homework>"
								+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE='" + nonce + "'>"
								+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
								+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
								+ (lis_result_sourcedid==null?"":"<INPUT TYPE=HIDDEN NAME=lis_result_sourcedid VALUE='" + lis_result_sourcedid + "'>")
								+ "<TD><b>" + i + ". </b></TD><TD>" + q.print() 
								+ (Long.toString(q.id).equals(request.getParameter("Q"))?"Hint:<br>" + q.getHint():"")
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
				Question q = hwQuestions.get(k);
				if (q==null) {
					try {
						q = ofy().load().key(k).safe();
						hwQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				q.setParameters(user.id.hashCode());
				buf.append("\n<TR VALIGN=TOP><TD>");
				
				boolean solved = ofy().load().type(HWTransaction.class).filter("userId",user.id).filter("questionId",q.id).filter("score >",0).count() > 0;					
				if (solved) buf.append("<IMG SRC=/images/checkmark.gif ALT='This problem was solved previously.'>");
				
				buf.append("&nbsp;<a id=" + q.id + " /></TD>"
						+ "<FORM METHOD=POST ACTION=Homework>"
						+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE='" + nonce + "'>"
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

	String printScore(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			Question q = hwQuestions.get(k);
			if (q==null) {
				q = ofy().load().key(k).safe();
				hwQuestions.put(k,q);
			}
			Topic topic = ofy().load().type(Topic.class).id(q.topicId).safe();
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId>0?ofy().load().type(Group.class).id(user.myGroupId).now():null;
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);
			String studentAnswer[] = request.getParameterValues(Long.toString(questionId));
			if (studentAnswer == null || studentAnswer.length==0) {
				studentAnswer = new String[1];
				studentAnswer[0] = "";
			}
			else for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
			
			Date minutesAgo = new Date(now.getTime()-retryDelayMinutes*60000);  // about 2 minutes ago
			List<HWTransaction> recentTransactions = ofy().load().type(HWTransaction.class).filter("questionId",q.id).filter("userId",user.id).filter("graded >",minutesAgo).list();
			long secondsRemaining = 0;
			if (recentTransactions.size()>0) {  // may be more than one if multiple browser sessions are active for one user!
				Date lastSubmission = new Date(0L);
				for (HWTransaction ht : recentTransactions) if (ht.graded.after(lastSubmission)) lastSubmission = ht.graded;
				secondsRemaining = retryDelayMinutes*60 - (now.getTime()-lastSubmission.getTime())/1000;
			}
			if (secondsRemaining > 0) {  
				buf.append("<h2>Please Wait For The Retry Delay To Complete</h2>");
				//buf.append("<b>" + user.getBothNames() + "</b><br>\n");
				buf.append(df.format(now));
				buf.append("<p>The retry delay for this homework problem is <span id=delay style='color: red'></span><p>");
				buf.append("Please take these few moments to check your work carefully.  You can sometimes find alternate routes to the<br>"
						+ "same solution, or it may be possible to use your answer to back-calculate the data given in the problem.<p>"
						+ "Alternatively, you may wish to <a href=Homework?TopicId=" + topic.id + "&r=" + new Random().nextInt(9999)
						+ ">return to this homework assignment</a> to work on another problem.<p>");
		
				buf.append("<FORM NAME=Homework METHOD=POST ACTION=Homework>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
						+ (lis_result_sourcedid==null?"":"<INPUT TYPE=HIDDEN NAME=lis_result_sourcedid VALUE='" + lis_result_sourcedid + "'>")
						+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE='" + nonce + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ q.print(studentAnswer[0]) + "<br>");
				
				buf.append("<INPUT TYPE=SUBMIT id='RetryButton' DISABLED=true VALUE='Grade This Exercise'></FORM>");
				buf.append("<SCRIPT language='JavaScript'>"
						+ "var seconds;var minutes;var oddSeconds;"
						+ "var endTime = new Date().getTime() + " + secondsRemaining + "*1000;"
						+ "function countdown() {"
						+ " var now = new Date().getTime();"
						+ " seconds=Math.round((endTime-now)/1000);"
						+ " minutes = seconds<0?Math.ceil(seconds/60):Math.floor(seconds/60);"
						+ " oddSeconds = seconds%60;"
						+ " if (seconds > 0) {"
						+ "  document.getElementById('delay').innerHTML = minutes + ' minutes ' + oddSeconds + ' seconds.';"
						+ "  setTimeout('countdown()',1000);"
						+ " }"
						+ " else {"
						+ "  document.getElementById('delay').innerHTML = minutes + ' minutes ' + oddSeconds + ' seconds.';"
						+ "  document.getElementById('RetryButton').disabled=false;"
						+ " }"
						+ "}"
						+ "countdown();"
						+ "</SCRIPT>"); 
				return buf.toString();
			}
			
			buf.append("<h2>Homework Results - " + topic.title + " (" + subject.title + ")</h2>\n");
//			buf.append("<b>" + user.getFirstName() + "</b><br>\n");
//			buf.append(df.format(now));
			
			q.setParameters(user.id.hashCode());
			int studentScore = 0;
			int possibleScore = q.pointValue;
			HWTransaction ht = null;
			
			if (studentAnswer[0].length() > 0) { // an answer was submitted
				// record the response in the Responses table for question debugging:
				studentScore = q.isCorrect(studentAnswer[0])?q.pointValue:0;
				Queue queue = QueueFactory.getDefaultQueue();
				queue.add(withUrl("/ResponseServlet")
						.param("AssignmentType","Homework")
						.param("TopicId", Long.toString(topic.id))
						.param("QuestionId", Long.toString(q.id))
						.param("StudentResponse", studentAnswer[0])
						.param("CorrectAnswer", q.getCorrectAnswer())
						.param("Score", Integer.toString(studentScore))
						.param("PossibleScore", Integer.toString(possibleScore))
						.param("UserId", user.id));

				ht = new HWTransaction(q.id,topic.id,topic.title,user.id,now,0L,studentScore,possibleScore,request.getRequestURI());
				if (lis_result_sourcedid != null) ht.lis_result_sourcedid = lis_result_sourcedid;
				ofy().save().entity(ht).now();
				// create/update/store a HomeworkScore object
				try {
					myGroup.setGroupTopicIds();
					long assignmentId = myGroup.getAssignmentId("Homework",topic.id);
					if (assignmentId > 0) { // assignment exists; save a Score object
						Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
						Score s = Score.getInstance(user.id,a);
						ofy().save().entity(s).now();
						if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",a.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
					}	
				} catch (Exception e2) {
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
						double dAnswer = Double.parseDouble(q.parseString(studentAnswer[0]));  // throws exception for non-numeric answer
						buf.append("<h3>Incorrect Answer</h3>");
						if (!q.hasCorrectSigFigs(studentAnswer[0])) buf.append("Your answer does not have the number of significant figures appropriate for the data given in the question. ");
						if (!q.agreesToRequiredPrecision(studentAnswer[0])) buf.append("Your answer does not " + (q.requiredPrecision==0?"exactly match the answer in the database.":"agree with the answer in the database to within the required precision (" + q.requiredPrecision + "%)"));
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
				buf.append("<p>The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. "));
			}  
			else {
				buf.append("<h3>The answer to the question was left blank.</h3>");
			}

			// embed the detailed solution or hint to the exercise in the response, if appropriate
			buf.append(ajaxJavaScript(user.verifiedEmail));
			if (user.isInstructor() || user.isTeachingAssistant() || (studentScore > 0)) {
				buf.append("<p><div id=exampleLink>"
						+ "<a href=# onClick=javascript:document.getElementById('example').style.display='';"
						+ "document.getElementById('exampleLink').style.display='none';>"
						+ "<FONT COLOR=RED>View the detailed solution for this homework exercise</FONT></a><p></div>");
				buf.append("<div id=example style='display: none'><b>Detailed Solution</b><p>" 
						+ q.printAllToStudents(studentAnswer[0]) + "</div>");
			}
			
			boolean offerHint = studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id);
			// if the user response was correct, seek five-star feedback:
			if (studentScore > 0) buf.append(fiveStars());
			
			buf.append("<p>We welcome comments about your ChemVantage experience <a href=/Feedback>here</a>.<p>");
			
			buf.append("<a href=/Homework?TopicId=" + ht.topicId 
					+ (nonce==null?"":"&Nonce=" + nonce) 
					+ (offerHint?"&Q=" + q.id + "><span style='color:red'>Please give me a hint</span>":">Return to this homework assignment") + "</a>");
			
			if (user.isAnonymous()) buf.append(" or go back to the <a href=/>ChemVantage home page</a>.");
			

/*
			buf.append("<p><a href=Homework?TopicId=" + topic.id + "&r=" + random
					+ "&Nonce=" + nonce
					+ (offerHint?"&Q=" + q.id:"")
					+ "#" + q.id + (offerHint?"><span style='color:red'>Please give me a hint</span>":">Return to this homework assignment") + "</a>");
*/		
		}
		catch (Exception e) {
			buf.append("Sorry, we were unable to score this question.<br>" + e.toString());
		}
		return buf.toString();
	}

	String ajaxJavaScript(boolean verifiedEmail) {
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
//		+ (!verifiedEmail?"However, no response is possible unless you verify the email address in your <a href=/Verification>user profile</a>.":"") 
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
