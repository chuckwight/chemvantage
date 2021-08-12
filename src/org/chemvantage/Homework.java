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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/Homework")
public class Homework extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	static Map<Long,Topic> topics = new HashMap<Long,Topic>();
	static Map<Long,Assignment> assignments = new HashMap<Long,Assignment>();
	static Map<Long,Map<Key<Question>,Question>> hwQuestions = new HashMap<Long,Map<Key<Question>,Question>>();
	static Map<Key<Question>,Integer> successPct = new HashMap<Key<Question>,Integer>();
	
	static int retryDelayMinutes = 2;  // minimum time between answer submissions for any single question

	public String getServletInfo() {
		return "This servlet presents a homework assignment for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "ShowScores":
				out.println(Home.header("Your ChemVantage Scores") + showScores(user) + Home.footer);
				break;
			case "ShowSummary":
				out.println(Home.header("Your Class ChemVantage Scores") + showSummary(user,request) + Home.footer);
				break;
			case "AssignHomeworkQuestions":
				if (user.isInstructor()) out.println(Home.header("Customize ChemVantage Homework Assignment") + selectQuestionsForm(user) + Home.footer);
				else out.println(Home.header("Customize ChemVantage Homework Assignment") + "<h2>Forbidden</h2>You must be signed in as the instructor to perform this functuon." + Home.footer);
				break;
			default: out.println(Home.header("ChemVantage Homework") + printHomework(user,request) + Home.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig") + "&e=" + e.toString());
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

			switch (userRequest) {
			case "UpdateAssignment":
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				a.updateQuestions(request);
				out.println(Home.header("ChemVantage Homework") + printHomework(user,request) + Home.footer);
				break;
			case "AddQuestion":
			case "UpdateQuestion":
				if (user.isEditor()) {
					Key<Question> key = Key.create(Question.class,Long.parseLong(request.getParameter("QuestionId")));
					Question q = ofy().load().key(key).safe();
					if (hwQuestions.containsKey(q.topicId)) hwQuestions.get(q.topicId).put(key, q);
				}
				break;
			case "DeleteQuestion":
				if (user.isEditor()) {
					Key<Question> key = Key.create(Question.class,Long.parseLong(request.getParameter("QuestionId")));
					Question q = ofy().load().key(key).safe();
					if (hwQuestions.containsKey(q.topicId)) hwQuestions.get(q.topicId).remove(key);
				}
				break;
			default: out.println(Home.header("ChemVantage Homework Grading Results") + printScore(user,request) + Home.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	static String printHomework(User user, HttpServletRequest request) {
		try {
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			long hintQuestionId = 0L;
			String hqi = request.getParameter("Q"); // questionId for offering a hint
			if (hqi != null) hintQuestionId = Long.parseLong(hqi);
			
			return printHomework(user,topicId,hintQuestionId);
		} catch (Exception e) {
			return "<h2>Launch failed because no quiz topic was specified.</h2>";
		}
	}

	static String printHomework(User user, long topicId, long hintQuestionId) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment hwa = null;
			long assignmentId = user.getAssignmentId(); // should be non-zero for LTI user
			if (assignmentId > 0) {
				hwa = assignments.get(assignmentId);
				if (hwa == null) {
					hwa = ofy().load().type(Assignment.class).id(assignmentId).now();
					assignments.put(hwa.id, hwa);
				}
				topicId = hwa.getTopicId();
			}

			Topic topic = topics.get(topicId);
			if (topic == null) {
				topic = ofy().load().type(Topic.class).id(topicId).safe();
				topics.put(topic.id,topic);
			}

			//  Load the Question items for this topic, if necessary:
			if (hwQuestions.get(topic.id) == null) { // load all of the Question items for this topic
				List<Key<Question>> topicQuestionKeys = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",topic.id).keys().list();
				Map<Key<Question>,Question> topicQuestions = new HashMap<Key<Question>,Question>();
				topicQuestions.putAll(sortByValue(ofy().load().keys(topicQuestionKeys)));
				if (topicQuestions.size()>0) hwQuestions.put(topic.id,topicQuestions);
			}

			// START the presentation of the Homework assignment
			buf.append("\n<h2>Homework Exercises - " + topic.title + "</h2>");

			if (hwQuestions.get(topic.id)==null || hwQuestions.get(topic.id).isEmpty()) {
				buf.append("<h3>Sorry, there are no homework questions for this topic.</h3>");
				return buf.toString();
			}

			if (user.isAnonymous())	buf.append("<h3><font color=red>Anonymous User</font></h3>");
			
			if (user.isInstructor() && hwa != null) {
				buf.append("<mark>As the course instructor you may "
						+ "<a href=/Homework?UserRequest=AssignHomeworkQuestions&sig=" + user.getTokenSignature() + ">"
						+ "customize this assignment</a>.");
				if (hwa.lti_nrps_context_memberships_url != null && hwa.lti_ags_lineitem_url != null) 
					buf.append("<br>You may also view a <a href=/Homework?UserRequest=ShowSummary&sig=" 
							+ user.getTokenSignature() + ">summary of student scores</a> for this assignment.");
				buf.append("</mark><p>");
			}	

			buf.append("\nHomework Rules<UL>");
			buf.append("\n<LI>You may rework problems and resubmit answers as many times as you wish, to improve your score.</LI>");
			buf.append("\n<LI>There is a retry delay of " + retryDelayMinutes + " minutes between answer submissions for any single question.</LI>");
			buf.append("\n<LI>Most questions are customized, so the correct answers are different for each student.</LI>");
			if (!user.isAnonymous()) buf.append("\n<LI>A checkmark will appear to the left of each correctly solved problem.</LI>");
			buf.append("</UL>");

			// Review the HWTransactions for this user to record which problems have been solved for this assignment and retrieve the current showWork strings:
			List<Long> solvedQuestions = new ArrayList<Long>();
			Map<Long,String> workStrings = new HashMap<Long,String>();
			List<HWTransaction> hwTransactions = new ArrayList<HWTransaction>();
			if (hwa!=null) hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",hwa.id).order("-graded").list();
			
			for (HWTransaction ht : hwTransactions) {
				if (solvedQuestions.contains(ht.questionId)) continue;
				if (ht.score > 0) solvedQuestions.add(ht.questionId);
				if (workStrings.containsKey(ht.questionId)) continue;
				workStrings.put(ht.questionId,ht.showWork);
			}
			
			StringBuffer assignedQuestions = new StringBuffer();
			assignedQuestions.append("<div style='display:table'>");
			StringBuffer optionalQuestions = new StringBuffer();
			optionalQuestions.append("<div style='display:table'>");
			
			// this script displays a box for the user to show their work
			buf.append("<script>"
					+ "function showWorkBox(qid) {"
					+ "document.getElementById('showWork'+qid).style.display='';"
					+ "document.getElementById('answer'+qid).placeholder='Enter your answer here';"
					+ "}"
					+ "</script>");
			
			// This is the main loop for presenting assigned and optional questions in order of increasing difficulty:
			int i=1;
			int j=1;
			for (Map.Entry<Key<Question>,Question> entry : hwQuestions.get(topic.id).entrySet()) {
				boolean assigned = (hwa != null) && (hwa.questionKeys.contains(entry.getKey()));
				StringBuffer questionBuffer = new StringBuffer("<div style='display:table-row'><div style='display:table-cell;font-size:small'>");
				String hashMe = user.id + (hwa==null?"":hwa.id);
				Question q = entry.getValue().clone();
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				
				if (solvedQuestions.contains(q.id)) questionBuffer.append("<IMG SRC=/images/checkmark.gif ALT='Check mark' align=top>&nbsp;");
				else if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) questionBuffer.append("<br/><a href='" + q.learn_more_url + "' target=_blank><img src=/images/learn_more.png alt='learn more here' align=top /><br/>learn</a>&nbsp;");
				
				questionBuffer.append("</div>");

				questionBuffer.append("<FORM METHOD=POST ACTION=/Homework>"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ (hwa==null?"":"<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
						+ "<div style='display:table-cell'><b>" + (assigned?i:j) + ".&nbsp;</b></div>"
						+ "<div style='display:table-cell'>" + q.print(workStrings.get(q.id),"") 
						+ (q.id == hintQuestionId?"Hint:<br>" + q.getHint():"")
						+ "<INPUT TYPE=SUBMIT VALUE='Grade This Exercise'><p>"
						+ "</div></div></FORM>\n");
				if (assigned) {
					assignedQuestions.append(questionBuffer);
					i++; 
				} else {
					optionalQuestions.append(questionBuffer);
					j++;
				}
			}
			buf.append((i>1?"<h4>Assigned Exercises</h4>":"") + assignedQuestions + "</div>" + (hwa!=null && j>1?"<h4>Optional Exercises</h4>":"") + optionalQuestions + "</div>");
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage());
		}
		return buf.toString();
	}

	static String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Start...");
		try {
			// The Homework grader scores only one Question at a time, so first identify and load it
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			Question q = null;
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
			debug.append("topic:"+topic.title+"...");
			
			try {
				q = hwQuestions.get(topicId).get(k).clone(); // make a copy of the question so we can parameterize it
				q.id = questionId;
			} catch (Exception e) {
				q = ofy().load().key(k).now(); // a fresh copy is only needed if the servlet restarted while the user was working on it
				if (q==null) return "<h3>Sorry, this question has been deleted from the ChemVantage database.</h3>";
			}
			
			String lis_result_sourcedid = user.getLisResultSourcedid();
			debug.append("lis_result_sourcedid="+lis_result_sourcedid+"...");
			
			long assignmentId = 0;
			Assignment hwa = null;
			try {
				assignmentId = user.getAssignmentId();
				hwa = ofy().load().type(Assignment.class).id(assignmentId).safe();
			} catch (Exception e) {}
			
			// Set the Question parameters for this user (this is why we made a copy, to prevent thread collisions with a class variable)
			String hashMe = user.id + (hwa==null?"":hwa.id);
			q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
			debug.append("question parameters set with "+hashMe.hashCode()+"...");
			
			String studentAnswer[] = request.getParameterValues(Long.toString(questionId));
			if (studentAnswer == null || studentAnswer.length==0) {
				studentAnswer = new String[1];
				studentAnswer[0] = "";
			}
			else for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
			
			String showWork = request.getParameter("ShowWork"+q.id);
			if (showWork==null) showWork="";  // required because later we check to see if showWork.isEmpty()
			
			debug.append("student answer:"+studentAnswer[0]+"...");
			
			// This section checks for recent submissions to enforce the retry delay (discourages guessing)
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Date now = new Date();
			Date minutesAgo = new Date(now.getTime()-retryDelayMinutes*60000);  // about 2 minutes ago
			List<HWTransaction> recentTransactions = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("questionId",q.id).filter("graded >",minutesAgo).list();
			long secondsRemaining = 0;
			boolean solvedRecently = false;
			
			if (recentTransactions.size()>0) {  // may be more than one if multiple browser sessions are active for one user!
				Date lastSubmission = new Date(0L);
				for (HWTransaction ht : recentTransactions) {
					if (ht.graded.after(lastSubmission)) lastSubmission = ht.graded;
					if (ht.score>0) solvedRecently=true;
				}
				secondsRemaining = retryDelayMinutes*60 - (now.getTime()-lastSubmission.getTime())/1000;
			}
			debug.append("recent transactions = "+recentTransactions.size() + "...");
			if (secondsRemaining > 0 && !solvedRecently) {  
				buf.append("<h2>Please Wait For The Retry Delay To Complete</h2>");
				buf.append(df.format(now));
				buf.append("<p>The retry delay for this homework problem is <span id=delay style='color: red'></span><p>");
				buf.append("Please take these few moments to check your work carefully.  You can sometimes find alternate routes to the "
						+ "same solution, or it may be possible to use your answer to back-calculate the data given in the problem.<br/><br/>");
				if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) 
					buf.append("<img src=/images/learn_more.png alt='learn more here' /> You can learn more about this topic at <a href='" 
					+ q.learn_more_url + "' target=_blank>" + q.learn_more_url + "</a><br/><br/>");
				buf.append("Alternatively, you may wish to "
						+ "<a href=/Homework?" + (hwa==null?"TopicId=" + topic.id : "AssignmentId=" + hwa.id)
						+ "&sig=" + user.getTokenSignature() + ">" 
						+ "return to this homework assignment</a> to work on another problem.<p>");
		
				buf.append("<FORM NAME=Homework METHOD=POST ACTION=Homework>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + assignmentId + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ q.print(showWork,studentAnswer[0]) + "<br>");
				
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
						+ "countdown();");
				buf.append("function showWorkBox(qid) {"  // this script displays a box for the user to show their work
						+ "document.getElementById('showWork'+qid).style.display='';"
						+ "document.getElementById('answer'+qid).placeholder='Enter your answer here';"
						+ "}"
						+ "showWorkBox(" + q.id + ");");
				buf.append("</SCRIPT>"); 
				return buf.toString();
			}
			
			buf.append("<h2>Homework Results - " + topic.title + "</h2>\n");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			buf.append(df.format(now));
			
			int studentScore = q.isCorrect(studentAnswer[0])?q.pointValue:0;
			int possibleScore = q.pointValue;
			
			debug.append("score is " + studentScore + " out of " + possibleScore + " points...");
			HWTransaction ht = null;
			showWork = request.getParameter("ShowWork"+questionId);
			
			if (studentAnswer[0].length() > 0) { // an answer was submitted
				
				// create and store a Response entity:
				Response r = new Response("Homework",topic.id,q.id,studentAnswer[0],q.getCorrectAnswer(),studentScore,possibleScore,user.id,now);
				ofy().save().entity(r);
				
				// create a new HWTransaction entity:
				ht = new HWTransaction(q.id,topic.id,topic.title,user.id,now,studentScore,assignmentId,possibleScore,showWork);
				if (lis_result_sourcedid != null) ht.lis_result_sourcedid = lis_result_sourcedid;
				ofy().save().entity(ht).now();

				debug.append("HW transaction id="+ht.id+"...");
				
				// create/update/store a HomeworkScore object
				try {  // throws exception if hwa==null
					boolean reportScoreToLms = hwa.lti_ags_lineitem_url != null || (hwa.lis_outcome_service_url != null && user.getLisResultSourcedid() != null);
					debug.append("score " + (reportScoreToLms?"will ":"will not ") + "be reported to the LMS...");
					if (hwa.questionKeys.contains(k)) {
						Score s = Score.getInstance(user.id,hwa);
						ofy().save().entity(s).now();
						if (reportScoreToLms) QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",hwa.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue	
					}
				} catch (Exception e2) {
				}
			}
			// Send response to the user:
			if (studentScore > 0) {
				buf.append("<h3>Congratulations. You answered the question correctly.</h3><p>");
			}
			else if (studentAnswer[0].length() > 0) {
				switch (q.getQuestionType()) {
					case 5:  // Numeric question
						try {
							@SuppressWarnings("unused")
							double dAnswer = Double.parseDouble(q.parseString(studentAnswer[0]));  // throws exception for non-numeric answer
							if (!q.agreesToRequiredPrecision(studentAnswer[0])) buf.append("<h3>Incorrect Answer</h3>Your answer does not " + (q.requiredPrecision==0?"exactly match the answer in the database.":"agree with the answer in the database to within the required precision (" + q.requiredPrecision + "%).<p>"));
							else if (!q.hasCorrectSigFigs(studentAnswer[0])) buf.append("<h3>Almost there!</h3>It appears that you've done the calculation correctly, but your answer does not have the correct number of significant figures appropriate for the data given in the question.<p>");
						}
						catch (Exception e2) {
							buf.append("<h3>Wrong Format</h3>This question requires a numeric response expressed as an integer, decimal number, "
									+ "or number in scientific notation. Your answer was scored incorrect because the program was unable to recognize "
									+ "your answer as one of these types.<br/>");
						}
						break;
					default:  // All other types of questions
						buf.append("<h3>Incorrect Answer</h3>Your answer was scored incorrect because it does not agree with the "
							+ "answer in the database.<br/>");
				}
				
				if (!user.isAnonymous() && user.isEligibleForHints(q.id)) {
					buf.append("<form method=post action=Help>"
							+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
							+ "<input type=hidden name=AssignmentType value=Homework>"
							+ "<input type=hidden name=TransactionId value=" + ht.id + ">");
					buf.append("<font color=red>Do you need some help from your instructor or teaching assistant? </font>");
					buf.append("<input type=submit value='Get Some Help Here'></form><br/>");
				}
			
				buf.append("The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. ") + "<br/>");
			}  
			else {
				buf.append("<h3>The answer to the question was left blank.</h3>");
			}

			if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) 
				buf.append("<img src=/images/learn_more.png alt='learn more here' /> You can learn more about this topic at <a href='" + q.learn_more_url + "' target=_blank>" + q.learn_more_url + "</a><br/><br/>");
			
			buf.append(ajaxJavaScript(user.getTokenSignature()));
			
			// embed the detailed solution or hint to the exercise in the response, if appropriate
			if (user.isInstructor() || user.isTeachingAssistant() || (studentScore > 0)) {
				buf.append("<div id=exampleLink>"
						+ "<a href=# onClick=javascript:document.getElementById('example').style.display='';"
						+ "document.getElementById('exampleLink').style.display='none';>"
						+ "<FONT COLOR=RED>View the detailed solution for this homework exercise</FONT></a><p></div>");
				buf.append("<div id=example style='display: none'><b>Detailed Solution</b><p>" 
						+ q.printAllToStudents(studentAnswer[0]) + "</div>");
			}
			
			boolean offerHint = studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id);
			
			// if the user response was correct, seek five-star feedback:
			if (studentScore > 0) buf.append(fiveStars());
			else buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");
			
			if (hwa != null) buf.append("You may <a href=/Homework?UserRequest=ShowScores&sig=" + user.getTokenSignature() + ">review your scores on this assignment</a>.<p>");

			buf.append("<a href=/Homework?"
					+ (assignmentId>0?"AssignmentId=" + assignmentId : "TopicId=" + topic.id)
					+ "&sig=" + user.getTokenSignature()  
					+ (offerHint?"&Q=" + q.id + "><span style='color:red'>Please give me a hint</span>":">Return to this homework assignment") + "</a> or "
					+ "<a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a> ");
			
			if (user.isAnonymous()) buf.append(" or go back to the <a href=/>ChemVantage home page</a>.");
			}
		catch (Exception e) {
			buf.append("<p>Sorry, we were unable to score this question: " + e.toString() + "<p>" + debug.toString());
		}
		return buf.toString();
	}

	static String ajaxJavaScript(String signature) {
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

	static String fiveStars() {
		StringBuffer buf = new StringBuffer();

		buf.append("<script type='text/javascript'>\n"
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
				+ "      document.getElementById('sliderspan').style='display:none';"
				+ "    }"
				+ "  }\n"
				+ "// -->\n"
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

	static String showScores(User user) {
		StringBuffer buf = new StringBuffer("<h2>Your Homework Transactions</h2>");
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
			
			List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).order("graded").list();
			
			if (hwts.size()==0) {
				buf.append("Sorry, we did not find any records for this user in the database for this assignment.<p>");
				return buf.toString();
			} else {
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					s = ofy().load().key(Key.create(Key.create(User.class,user.id),Score.class,a.id)).safe();
					if (s.numberOfAttempts != hwts.size()) throw new Exception();
				} catch (Exception e) { // create a fresh Score entity from scratch
					s = Score.getInstance(user.id, a);
					ofy().save().entity(s);
				}
				
				buf.append("This user's overall score on the assignment is " + 10.*Math.round(s.getPctScore())/10. + "%.<br>");

				if (!user.isAnonymous()) {  // try to validate the score with the LMS grade book entry
					try {
						double lmsPctScore = 0;
						String lmsScore = null;
						boolean gotScoreOK = false;

						//buf.append("Retrieving score for user " + User.getRawId(user.id) + " in group " + user.myGroupId + " for this " + a.assignmentType + " assignment " + a.id + "<p>");
						if (a.lti_ags_lineitem_url != null) {  // LTI version 1p3
							lmsScore = LTIMessage.readUserScore(a, user.id);
							try {
								lmsPctScore = Double.parseDouble(lmsScore);
								gotScoreOK = true;
							} catch (Exception e) {
							}
						}
						else if (a.lis_outcome_service_url != null && s.lis_result_sourcedid != null) { // LTI version 1p1
							String messageFormat = "application/xml";
							String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
							String oauth_consumer_key = a.domain;
							String replyBody = new LTIMessage(messageFormat,body,a.lis_outcome_service_url,oauth_consumer_key).send();

							if (replyBody.contains("success")) {
								int beginIndex = replyBody.indexOf("<textString>") + 12;
								int endIndex = replyBody.indexOf("</textString>");
								replyBody = replyBody.substring(beginIndex,endIndex);
								lmsPctScore = 100.*Double.parseDouble(replyBody);
								gotScoreOK = true;
							}
						}

						if (gotScoreOK && Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
							buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
						} else if (gotScoreOK) { // there is a significant difference between LMS and ChemVantage scores. Please explain:
							buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
									+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<br>"
									+ "If you think this may be due to a stale score, th3e user may submit this assignment for grading,<br>"
									+ "even for a score of zero, and ChemVantage will try to refresh the best score to the LMS.<p>");
						} else throw new Exception();
					} catch (Exception e) {
						buf.append("ChemVantage was unable to retrieve the score for this assignment from the LMS.<br>"
								+ "Sometimes it takes several seconds for the score to be posted in the LMS grade book.<br>");
						if (s.score==0 && s.numberOfAttempts==0) buf.append("It appears that this assignment may not have been submitted for a score yet.<br>");
						if (user.isInstructor()) buf.append("Some LMS providers do not accept score submissions for instructors or test students.<br>");
						buf.append("<br>");
					}
				}
				buf.append("<table><tr><th>Transaction Number</th><th>QuestionID</th><th>Graded</th><th>Score</th></tr>");
				for (HWTransaction hwt : hwts) {
					buf.append("<tr align=center><td>" + hwt.id + "</td><td>" + hwt.questionId + "</td><td>" + df.format(hwt.graded) + "</td><td>" + hwt.score +  "</td></tr>");
				}
				buf.append("</table><p>");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	static String showSummary(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String lti_version = null;
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		if (a==null) return "No assignment was specified for this request.";
		if (a.lis_outcome_service_url!=null) lti_version = "1p1";
		else if (a.lti_ags_lineitem_url != null) lti_version = "1p3";
		else return "Could not determine the LTI version for this request.";
		
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		if ("1p3".equals(lti_version)) {
			try { // code for LTI version 1.3
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();

				if (a.lti_nrps_context_memberships_url==null) throw new Exception("No Names and Roles Provisioning support.");

				buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
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
				
				Map<String,String[]> membership = LTIMessage.getMembership(a);
				if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails
				
				Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
				Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
				String platform_id = d.getPlatformId() + "/";
				for (String id : membership.keySet()) {
					keys.put(id,Key.create(Key.create(User.class,Subject.hashId(platform_id+id)),Score.class,a.id));
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
		} else if ("1p1".equals(lti_version)) {
			try {  // code for LTI version 1.1
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");

				DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
				buf.append(df.format(new Date()) + "<p>");

				buf.append("To protect the privacy of our users, ChemVantage does not collect any personally identifiable information. "
						+ "Therefore, we are unable to display a traditional grade book with names and scores. Instead, we rely on a "
						+ "robust system of reporting scores back to the grade book inside your LMS.<p>");
				
				List<Score> scores = ofy().load().type(Score.class).filterKey("=", a.id).list();
				int count = scores.size();
				Date mostRecent = new Date(0);
				boolean allScoresReported = true;
				double pctScoreSum = 0;
				int scoresNotReported = 0;
				Queue queue = QueueFactory.getDefaultQueue();  // default task queue
				int attempts = 0;
				for (Score s : scores) {
					if (s.numberOfAttempts==0) continue;  // skip the averaging for those who have not attempted the quiz yet
					attempts += s.numberOfAttempts;
					if (s.mostRecentAttempt.after(mostRecent)) mostRecent = s.mostRecentAttempt;
					if (s.needsLisReporting()) {
						scoresNotReported++;
						allScoresReported = false;
						pctScoreSum += s.getPctScore();
						queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",s.owner.getName()));
					}
					
				}
						
				buf.append("<p>There " + (count==1?"is ":"are ") + count + " score" + (count==1?"":"s") + " for this assignment in the ChemVantage database.<br>");
				if (count>0) {
					buf.append("The average score is " + pctScoreSum/count + "%.<br>");
					buf.append("The average number of attempts (including downloads not submitted for scoring) is " + Math.round(10.*attempts/count)/10. + ".<br>");
					buf.append("The most recent attempt of this assignment was on " + df.format(mostRecent) + ".<p>");
				} else buf.append("<br>");

				if (a.lis_outcome_service_url==null) buf.append("Your LMS is not configured for ChemVantage to report scores to the LMS grade book.<p>");
				else if (scoresNotReported>0) {
					buf.append("It appears that " + scoresNotReported + (scoresNotReported==1?" score":" scores") + " may not have been reported to your LMS correctly. "
							+ "We have automatically initiated a programmed task to correct this. "
							+ "Please check back in a few minutes to ensure that the situation has been resolved.<p>");
				}
				else if (allScoresReported) buf.append("All scores for students have been reported to your LMS successfully.<p>");

				buf.append("If you have any questions or need assistance, please contact <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<p>");			
				buf.append("<a href=/Homework?sig=" + user.getTokenSignature() + ">Return to this homework assignment</a>.<p>");
			} catch (Exception e) {
				buf.append("ChemVantage was unable to access the LISMembershipContainer REST service on your LMS, so a summary of scores cannot be provided "
						+ "at this time, sorry. We are working to resolve this problem in the near future.<p>");
			}
		}
		return buf.toString();
	}
	
	static Map<Key<Question>, Question> sortByValue(Map<Key<Question>, Question> unsortMap) {
		List<Map.Entry<Key<Question>, Question>> list = new LinkedList<Map.Entry<Key<Question>, Question>>(unsortMap.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<Key<Question>, Question>>() {
			public int compare(Map.Entry<Key<Question>, Question> o1, Map.Entry<Key<Question>, Question> o2) {
				Integer success1 = successPct.get(o1.getKey());
				if (success1==null) {
					int totalResponses = ofy().load().type(Response.class).filter("questionId",o1.getKey().getId()).count();
					if (totalResponses==0) success1 = 100;
					else {
						int successResponses = ofy().load().type(Response.class).filter("questionId",o1.getKey().getId()).filter("score >",0).count();
						success1 = successResponses*100/totalResponses;
					}
					successPct.put(o1.getKey(),success1);
				}
				Integer success2 = successPct.get(o2.getKey());
				if (success2==null) {
					int totalResponses = ofy().load().type(Response.class).filter("questionId",o2.getKey().getId()).count();
					if (totalResponses==0) success2 = 100;
					else {
						int successResponses = ofy().load().type(Response.class).filter("questionId",o2.getKey().getId()).filter("score >",0).count();
						success2 = successResponses*100/totalResponses;
					}
					successPct.put(o2.getKey(),success2);
				}
				int rank = success2-success1; // this reverses the normal Comparator to give higher rank to lower successPct
				if (rank==0) rank = o1.getKey().compareTo(o2.getKey()); // tie breaker required
				return rank;  
			}
		});

		Map<Key<Question>, Question> result = new LinkedHashMap<Key<Question>, Question>();
		for (Map.Entry<Key<Question>, Question> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}

		return result;
	}

	static String selectQuestionsForm(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			Topic topic = ofy().load().type(Topic.class).id(a.topicId).safe();
			
			buf.append("<h3>Customize Homework Assignment</h3>");
			buf.append("<b>Topic: " + topic.title + "</b><p>");
					
			if (a.timeAllowed==null) a.timeAllowed = 900; // default time for completing the exam
			
			// Allow instructor to pick individual question items from all active questions:
			buf.append("Select the homework questions to be assigned for grading. The questions are presented below in "
					+ "approximate order of increasing difficuly, as measured by the percentage of correct submissions. "
					+ "Then click the 'Use Selected Items' button. Each question is worth 1 point, so the maximum possible "
					+ "score is equal to the number of questions selected. Students may work the optional problems; "
					+ "however, these are not included in the scores reported to the class LMS.<p>"
					+ "If you don't see a question you want to include, you may "
					+ "<a href=/Contribute?sig=" + user.getTokenSignature() 
					+ ">contribute a new question item</a> to the database.<p>");

			if (hwQuestions.get(topic.id) == null) { // load all of the Question items for this topic
				List<Key<Question>> topicQuestionKeys = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",topic.id).keys().list();
				Map<Key<Question>,Question> topicQuestions = new HashMap<Key<Question>,Question>();
				topicQuestions.putAll(sortByValue(ofy().load().keys(topicQuestionKeys)));
				if (topicQuestions.size()>0) hwQuestions.put(topic.id,topicQuestions);
			}

			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick='for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}'"
					+ "> Select/Unselect All</FORM>");

			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=/Homework>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "'>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			
			int i=0;
			for (Map.Entry<Key<Question>,Question> entry : hwQuestions.get(topic.id).entrySet()) {
				Question q = entry.getValue().clone();
				q.id = entry.getValue().id;
				q.setParameters();  // creates randomly selected parameters
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				i++;
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

