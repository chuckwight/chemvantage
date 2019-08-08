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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		
		User user = null;
		try {
			HttpSession session = request.getSession();
			if (session.isNew()) user = User.getUser(request.getParameter("CvsToken"));
			else user = User.getInstance(session);
			if (user==null) throw new Exception("Authentication failed, probably because your web session timed out.");
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			if ("ShowScores".contentEquals(userRequest)) out.println(Home.header + showScores(user,request) + Home.footer);
			else if ("ShowSummary".contentEquals(userRequest)) out.println(Home.header + showSummary(user,request) + Home.footer);
			else out.println(Home.header + printHomework(user,request) + Home.footer);
		} catch (Exception e) {
			String cvsToken = request.getParameter("CvsToken");
			response.sendRedirect("/Logout" + (cvsToken==null?"":"?CvsToken=" + cvsToken));
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		User user = null;
		try {
			HttpSession session = request.getSession();
			if (session.isNew()) user = User.getUser(request.getParameter("CvsToken"));
			else user = User.getInstance(session);
			if (user==null) throw new Exception("Authentication failed, probably because your web session timed out.");
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			out.println(Home.header + printScore(user,request) + Home.footer);
		} catch (Exception e) {
			response.sendRedirect("/Logout?CvsToken=" + user.getCvsToken());
		}
	}

	String printHomework(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		try {
			Assignment hwa = null;
			long topicId = 0;
			try {  // normal process for LTI assignment launch
				long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				hwa = ofy().load().type(Assignment.class).id(assignmentId).now();
				if (hwa==null) { // assignment has been deleted
					return "<h2>Assignment Reset</h2>"
						+ "This ChemVantage assignment was deleted, probably because all of the "
						+ "question items were deselected. The course instructor may return to the Learning Management "
						+ "System (LMS) and click on the assignment link there to reassociate the class assignment "
						+ "with a new ChemVantage homework set.";
				} else topicId = hwa.topicId;
			} catch (Exception e) {  // alternative process for anonymous user
				try {
					topicId = Long.parseLong(request.getParameter("TopicId"));
				} catch (Exception e2) {
					return "<h2>No Homework Assignment Selected</h2>You must return to the <a href=Home>Home Page</a> "
							+ "and select a topic for this quiz using the drop-down box.";
				}
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid"); // used for reporting score back to the LMS
			
			Group myGroup = user.myGroupId>0?ofy().load().type(Group.class).id(user.myGroupId).now():null;
			
			buf.append("\n<h2>Homework Exercises - " + topic.title + " (" + subject.title + ")</h2>");
			
			if (user.isInstructor() && myGroup != null && hwa != null) {
				buf.append("<table style='border: 1px solid black'><tr><td>");
				buf.append("As the course instructor you may<ul>"
						+ "<li><a href=/Groups?UserRequest=AssignHomeworkQuestions&AssignmentId=" + hwa.id + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">"
						+ "customize this homework assignment</a> by selecting/deselecting the available question items."
						+ "<li>view a <a href=/Homework?UserRequest=ShowSummary&AssignmentId=" + hwa.id + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">summary of scores</a> for this assignment"
						+ "</ul></td></tr></table><p>");
			} else if (user.isAnonymous()) {
				buf.append("<h3><font color=red>Anonymous User</font></h3>");
			}
			
			if (!user.isAnonymous()) {
				buf.append("\nHomework Rules<UL>");
				buf.append("\n<LI>You may rework problems and resubmit answers as many times as you wish, to improve your score.</LI>");
				buf.append("\n<LI>There is a retry delay of " + retryDelayMinutes + " minutes between answer submissions for any single question.</LI>");
				buf.append("\n<LI>Most questions are customized, so the correct answers are different for each student.</LI>");
				buf.append("\n<LI>A checkmark will appear to the left of each correctly solved problem. "
						+ (hwa==null?"":"<a href=/Homework?UserRequest=ShowScores&AssignmentId=" + hwa.id 
								+ (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">View the details here</a>.")
						+ "</LI>");
				buf.append("</UL>");
			}
			
			List<Key<Question>> optionalQuestionKeys = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",topicId).filter("isActive",true).keys().list();
			if (optionalQuestionKeys.size()==0) buf.append("<h2>Sorry, there are no homework questions for this topic.</h2>");
			
			if (hwa != null) { // use hwa.questionIds to move assigned questions to the other list
				optionalQuestionKeys.removeAll(hwa.questionKeys);
				int score = 0;
				
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
						String hashMe = user.id + hwa.id;
						q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
						buf.append("\n<TR VALIGN=TOP><TD>");
						
						boolean solved = ofy().load().type(HWTransaction.class).filter("userId",user.id).filter("assignmentId",hwa.id).filter("questionId",q.id).filter("score >",0).count() > 0;					
						if (solved) {
							buf.append("<IMG SRC=/images/checkmark.gif ALT='This problem was solved previously.'>");
							score++;
						}
						
						buf.append("&nbsp;<a id=" + q.id + " /></TD>"
								+ "<FORM METHOD=POST ACTION=Homework>"
								+ (cvsToken==null?"":"<INPUT TYPE=HIDDEN NAME=CvsToken VALUE=" + cvsToken + ">")
								+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
								+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
								+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>"
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
				
				// Check to see if the database has the correct score for this assignment
				Score s = null;
				Key<Score> k = Key.create(Key.create(User.class, user.id),Score.class,hwa.id);
				s = ofy().load().key(k).now();
				if (score>0 && score != s.score) {
					s = Score.getInstance(user.id, hwa);
					ofy().save().entity(s).now();
					QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",hwa.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));
				}

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
				String hashMe = user.id + (hwa==null?"":hwa.id);
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				buf.append("\n<TR VALIGN=TOP><TD>");
				
				boolean solved = ofy().load().type(HWTransaction.class).filter("userId",user.id).filter("questionId",q.id).filter("score >",0).count() > 0;					
				if (solved) buf.append("<IMG SRC=/images/checkmark.gif ALT='This problem was solved previously.'>");
				
				buf.append("&nbsp;<a id=" + q.id + " /></TD>"
						+ "<FORM METHOD=POST ACTION=Homework>"
						+ (cvsToken==null?"":"<INPUT TYPE=HIDDEN NAME=CvsToken VALUE=" + cvsToken + ">")
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
						+ (hwa==null?"" : "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
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
		String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
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
			long assignmentId = 0;
			Assignment hwa = null;
			try {
				assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				hwa = ofy().load().type(Assignment.class).id(assignmentId).safe();
			} catch (Exception e) {}
			
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			
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
						+ "Alternatively, you may wish to "
						+ "<a href=/Homework?" + (hwa==null?"TopicId=" + topic.id : "AssignmentId=" + hwa.id 
								+ (lis_result_sourcedid==null?"":"&lis_result_sourcedid=" + lis_result_sourcedid)) 
						+ (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">" 
						+ "return to this homework assignment</a> to work on another problem.<p>");
		
				buf.append("<FORM NAME=Homework METHOD=POST ACTION=Homework>"
						+ (hwa==null?"<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>":"<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
						+ (lis_result_sourcedid==null?"":"<INPUT TYPE=HIDDEN NAME=lis_result_sourcedid VALUE='" + lis_result_sourcedid + "'>")
						+ (cvsToken==null?"":"<INPUT TYPE=HIDDEN NAME=CvsToken VALUE=" + cvsToken + ">")
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
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			String hashMe = user.id + (hwa==null?"":hwa.id);
			q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
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

				ht = new HWTransaction(q.id,topic.id,topic.title,user.id,now,studentScore,assignmentId,possibleScore);
				if (lis_result_sourcedid != null) ht.lis_result_sourcedid = lis_result_sourcedid;
				ofy().save().entity(ht).now();
				
				// create/update/store a HomeworkScore object
				try {  // throws exception if hwa==null
					if (hwa.questionKeys.contains(k)) {
						Score s = Score.getInstance(user.id,hwa);
						ofy().save().entity(s).now();
						if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",hwa.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue	
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
			buf.append(ajaxJavaScript(cvsToken));
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
			
			buf.append("<p>We welcome comments about your ChemVantage experience <a href=/Feedback" + (cvsToken==null?"":"?CvsToken=" + cvsToken) + ">here</a>.<p>");
			buf.append("<a href=/Homework?"
					+ (assignmentId>0?"AssignmentId=" + assignmentId : "TopicId=" + ht.topicId)
					+ (lis_result_sourcedid==null?"":"&lis_result_sourcedid=" + lis_result_sourcedid)
					+ (cvsToken==null?"":"&CvsToken=" + cvsToken)  
					+ (offerHint?"&Q=" + q.id + "><span style='color:red'>Please give me a hint</span>":">Return to this homework assignment") + "</a> or "
					+ "<a href=/Logout" + (cvsToken==null?"":"?CvsToken=" + cvsToken) + ">logout of ChemVantage</a> ");
			
			if (user.isAnonymous()) buf.append(" or go back to the <a href=/>ChemVantage home page</a>.");
			}
		catch (Exception e) {
			buf.append("Sorry, we were unable to score this question.<br>" + e.toString());
		}
		return buf.toString();
	}

	String ajaxJavaScript(String cvsToken) {
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
		+ "                + 'please take a moment to <a href=/Feedback?CvsToken=" + cvsToken + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=/Feedback?CvsToken=" + cvsToken + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=/Feedback?CvsToken=" + cvsToken + ">Click here</a> '"
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

	protected String showScores(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Your Homework Transactions</h2>");
		String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
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
			
			List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).filter("userId",user.id).order("graded").list();
			
			if (hwts.size()==0) {
				buf.append("Sorry, we did not find any records for you in the database for this assignment.<p>");
				buf.append("<a href=Homework?AssignmentId=" + a.id + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">Take me back to the homework assignment.</a><p>");
					return buf.toString();
			}
			
			// create a fresh Score entity to calculate the best score on this assignment
			Score s = Score.getInstance(user.id, a);
    		
    		buf.append("Your overall score on this assignment is " + 10.*Math.round(s.getPctScore())/10. + "%.<br>");

    		if (s.numberOfAttempts>0 && s.lis_result_sourcedid != null) {  // try to validate the score with the LMS grade book entry
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
					} else throw new Exception("We attempted to validate the score contained in your class LMS grade book, but the operation failed.<p>");
				} catch (Exception e) {
					buf.append("We attempted to validate the score contained in your class LMS grade book, but it did not contain a valid score for this assignment.<p>");
				}
			}
    		buf.append("<a href=Homework?AssignmentId=" + a.id + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">Take me back to the homework assignment.</a><p>");

			buf.append("<table><tr><th>Transaction Number</th><th>QuestionID</th><th>Graded</th><th>Score</th></tr>");
			for (HWTransaction hwt : hwts) {
				buf.append("<tr align=center><td>" + hwt.id + "</td><td>" + hwt.questionId + "</td><td>" + df.format(hwt.graded) + "</td><td>" + hwt.score +  "</td></tr>");
			}
			buf.append("</table><p>");		
		} catch (Exception e) {
			buf.append(e.toString());
		}

		return buf.toString();
	}
	
	String showSummary(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		
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
			+ "teaching assistants and test students created by your LMS, if applicable.<p>");
			
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
			buf.append("<a href=/Homework?AssignmentId=" + a.id + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">Return to this assignment</a>.<p>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

}
