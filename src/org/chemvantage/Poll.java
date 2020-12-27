/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2020 ChemVantage LLC
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Poll")
public class Poll extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static Map<Long,Integer> activePolls = new HashMap<Long,Integer>(); // key=assignmentId and value=state {0, 1, or 2}
	private Map<Long,Assignment> assignments = new HashMap<Long,Assignment>();
	private Map<Key<Question>,Question> pollQuestions = new HashMap<Key<Question>,Question>();
	private Date nextPurge = new Date(new Date().getTime() + 3600000L); // 1 hour from now
   
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			int state = activePolls.containsKey(user.getAssignmentId())?activePolls.get(user.getAssignmentId()):0;
			
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "EditPoll":
				out.println(Home.header() + editPage(user,request) + Home.footer);
				break;
			case "NewQuestion":
				out.println(Home.header() + newQuestionForm(user,request) + Home.footer);
				break;
			case "Preview":
				out.println(Home.header() + previewQuestion(user,request) + Home.footer);
				break;
			default:
				switch (state) {
				case 0:
					out.println(Home.header() + welcomePage(user,request) + Home.footer);
					break;
				case 1:
					if (responsesRecorded(user)) out.println(Home.header() + waitPage(user) + Home.footer);
					else out.println(Home.header() + showPollQuestions(user) + Home.footer);
					break;
				case 2:
					out.println(Home.header() + resultsPage(user) + Home.footer);
					break;
				}
			}			
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();

			Date now = new Date();
			if (nextPurge.before(now) && user.isInstructor()) purgeActivePolls();
	
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "LaunchPoll":
				if (!user.isInstructor()) break;
				Assignment a = getAssignment(user.getAssignmentId());
				PollTransaction pt = getPollTransaction(user);
				pt.downloaded = new Date();
				ofy().save().entity(pt);  // essential for purgeActivePolls to work properly			
				pollQuestions.putAll(ofy().load().keys(a.questionKeys));
				activePolls.put(a.id, 1);
				out.println(Home.header() + showPollQuestions(user) + Home.footer);
				return;
			case "ClosePoll":
				if (!user.isInstructor()) break;
				activePolls.put(user.getAssignmentId(), 2);
				out.println(Home.header() + resultsPage(user) + Home.footer);
				return;
			case "ResetPoll":
				if (!user.isInstructor()) break;
				a = getAssignment(user.getAssignmentId());
				for (Key<Question> k : a.questionKeys) pollQuestions.remove(k);
				assignments.remove(a.id);
				activePolls.remove(a.id);
				out.println(Home.header() + welcomePage(user,request) + Home.footer);
				return;
			case "Save New Question":
				long qid = createQuestion(user,request);
				if (qid > 0) {
					a = getAssignment(user.getAssignmentId());
					a.questionKeys.add(Key.create(Question.class,qid));
					saveAssignment(a);
				}
				out.println(Home.header() + editPage(user,request) + Home.footer);
				return;
			case "SubmitResponses":
				pt = submitResponses(user,request);
				out.println(Home.header() + waitPage(user,pt) + Home.footer);
				return;
			case "AddQuestions":
				if (!user.isInstructor()) break;
				addQuestions(user,request);
				out.println(Home.header() + editPage(user,request) + Home.footer);
				return;
			case "DeleteQuestions":
				if (!user.isInstructor()) break;
				deleteQuestions(user,request);
				out.println(Home.header() + editPage(user,request) + Home.footer);
				return;
			}
			doGet(request,response);
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	boolean responsesRecorded(User user) {
		return getPollTransaction(user).completed != null;
	}
	
	String welcomePage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Welcome to the Class Poll</h2>");
		if (user.isInstructor()) {
			Assignment a = getAssignment(user.getAssignmentId());
			if (a.questionKeys.size()==0) return editPage(user,request);
			else {
				buf.append("You may review and edit the questions for this poll by <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">clicking this link</a>.<p></p>");
				
				buf.append("When your class is ready for this poll, launch it by pressing the button below. "
						+ "You must then tell your students that the poll is open so they can click the link "
						+ "to view the poll question items.<br/>");
				buf.append("<form method=post>"
						+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
						+ "<input type=hidden name=UserRequest value='LaunchPoll' />"
						+ "<input type=submit value='Launch This Poll Now' />");
			}
		} else {
			buf.append("This poll is currently closed. Please wait until your instructor tells you that the poll is open.<br/>"
					+ "Then click the button below to view the poll question items.<br/>"
					+ "<form method=get>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=submit value='View the Poll' /></form><p></p>");
		}
		return buf.toString();
	}
			
	void purgeActivePolls() {
		// This method is used to manage the Map<Long,Integer> activePolls 
		// Every hour or so, when an Instructor executes a doPost operation, this method checks each Map entry for recent PollTransaction entities
		// If it finds none, it will remove the entry<assignmentId,state> from the Map
		// Cases for no recent PollTransactions:
		//  1) all transactions are old (usual case) - remove the entry because it's not needed, but the poll could be relaunched later OK
		//  2) at least some transactions are new - poll is less than 1 hour old, or was relaunched recently, so don't remove yet
		//  3) there are no transactions because the poll was abandoned after launching (e.g., just a test by instructor) this case can be 
		//     prevented by ensuring that a PollTransaction is created for the instructor at launch time
		
		Date oneHourAgo = new Date(new Date().getTime() - 3600000L); 
		List<Long> expiredAssignmentIds = new ArrayList<Long>();
		for (Map.Entry<Long,Integer> entry : activePolls.entrySet()) {
			boolean isRecent = ofy().load().type(PollTransaction.class).filter("assignmentId",entry.getKey()).filter("downloaded >",oneHourAgo).count()>1;
			if (!isRecent) expiredAssignmentIds.add(entry.getKey());
		}
		for (Long exp : expiredAssignmentIds) {
			Assignment a = getAssignment(exp);
			for (Key<Question> k : a.questionKeys) pollQuestions.remove(k);
			assignments.remove(exp);
			activePolls.remove(exp);
		}
		nextPurge = new Date(new Date().getTime() + 86400000L);        // this time tomorrow
	}
	
	String showPollQuestions(User user) {
		/*
		 * This method is reached by Learners or Instructors who launch the correct LTI Resource Link in the LMS, thereby binding
		 * the assignmentId to their User entity. The GET request lands here if and only if the PollRepo parameter state == 1.
		 */
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Class Poll</h2>");
		
		if (user.isInstructor()) {
			buf.append("<b>Please tell your students that the poll is now open.</b> "
				+ "They will have to refresh their browsers to view the questions.<br/>");
			buf.append("<form method=post><div id=timer0 style='display: inline'></div>&nbsp;When you are ready, please&nbsp;"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value='ClosePoll' />"
					+ "<input type=submit value='click here to close the poll and view the results' />"
					+ "</form>"
					+ "You must then instruct your students to refresh their browsers to see the results.");
		}
		
		Assignment a = getAssignment(user.getAssignmentId());
		
		buf.append("<OL>");
		int possibleScore = 0;
		buf.append("<form id=pollForm method=post onSubmit='return confirmSubmission(" + a.questionKeys.size() + ")'>"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />");
		
		for (Key<Question> k : a.questionKeys) {  // main loop to present questions
			Question q = getQuestion(k); // this should nearly always work
			q.setParameters(a.id % Integer.MAX_VALUE);
			buf.append("<li>" + q.print() + "<br /></li>");
			possibleScore += q.correctAnswer==null || q.correctAnswer.isEmpty()?0:q.pointValue;
		}
		buf.append("</OL>");
		buf.append(javaScripts()); 

		buf.append("<input type=hidden name=PossibleScore value='" + possibleScore + "' />");
		buf.append("<input type=hidden name=UserRequest value='SubmitResponses' />");
		buf.append("<input type=submit id=pollSubmit value='Submit My Responses Now' />");
		buf.append("</form>");
		
		return buf.toString();
	}
	
	String javaScripts() {
		return "<SCRIPT language='JavaScript'>"
				+ "var start = new Date();"
				+ "function countup() {"
				+ "  var now = new Date();"
				+ "  var elapsedSeconds = (now.getTime() - start.getTime())/1000;"
				+ "  var minutes = Math.floor(elapsedSeconds/60);"
				+ "  var oddSeconds = Math.floor(elapsedSeconds % 60);"
				+ "  document.getElementById('timer0').innerHTML='Elapsed Time: ' + minutes + ':' + (oddSeconds<10?'0':'') + oddSeconds;"
				+ "  setTimeout('countup()',1000);"
				+ "}"
				+ "countup();"
				+ "function confirmSubmission(nQuestions) {"
				+ "  var elements = document.getElementById('pollForm').elements;"
				+ "  var nAnswers;"
				+ "  var i;"
				+ "  var checkboxes;"
				+ "  var lastCheckboxIndex;"
				+ "  nAnswers = 0;"
				+ "  for (i=0;i<elements.length;i++) {"
				+ "    if (isNaN(elements[i].name)) continue;"
				+ "    if (elements[i].type=='text' && elements[i].value.length>0) nAnswers++;"
				+ "    else if (elements[i].type=='radio' && elements[i].checked) nAnswers++;"
				+ "    else if (elements[i].type=='checkbox') {"
				+ "      checkboxes = document.getElementsByName(elements[i].name);"
				+ "      lastCheckboxIndex = i + checkboxes.length - 1;"
				+ "      for (j=0;j<checkboxes.length;j++) if (checkboxes[j].checked==true) {"
				+ "        nAnswers++;"
				+ "        i = lastCheckboxIndex;"
				+ "        break;"
				+ "      }"
				+ "    }"
				+ "  }"
				+ "  if (nAnswers<nQuestions) return confirm('Submit your responses now? ' + (nQuestions-nAnswers) + ' answers may have been left blank.');"
				+ "  else return true;"
				+ "}"
				+ "function showWorkBox(qid) {}" 
				+ "</SCRIPT>"; 
	}

	PollTransaction submitResponses(User user,HttpServletRequest request) {
		PollTransaction pt = getPollTransaction(user);
		pt.completed = new Date();
		pt.score = 0;
		pt.possibleScore = 0;
		pt.responses = new HashMap<Key<Question>,String>();
		pt.lis_result_sourcedid = user.getLisResultSourcedid();
		
		Assignment a = getAssignment(user.getAssignmentId());
		for (Key<Question> k : a.questionKeys) {
			try {
				Question q = getQuestion(k);
				pt.possibleScore += q.pointValue;
				String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
				if (studentAnswer != null) {
					for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
					if (studentAnswer[0].length() > 0) { // an answer was submitted
						pt.responses.put(k, studentAnswer[0]);
						q.setParameters(a.id % Integer.MAX_VALUE);
						pt.score += q.isCorrect(studentAnswer[0])?q.pointValue:0;
					}
				}
			} catch (Exception e) {}
		}
		ofy().save().entity(pt).now();
		try {
			if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
			//Score.getInstance(user.id, a);
			boolean reportScoreToLms = a.lti_ags_lineitem_url != null || (a.lis_outcome_service_url != null && user.getLisResultSourcedid() != null);
			if (reportScoreToLms) {
				QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {}
		return pt;
	}
	
	Assignment getAssignment(Long assignmentId) {
		if (assignments.containsKey(assignmentId)) return assignments.get(assignmentId);
		else return ofy().load().type(Assignment.class).id(assignmentId).safe();
	}
	
	void saveAssignment(Assignment a) {
		assignments.put(a.id,a);
		ofy().save().entity(a);
	}
	
	PollTransaction getPollTransaction(User user) {
		PollTransaction pt = ofy().load().type(PollTransaction.class).filter("assignmentId =",user.getAssignmentId()).filter("userId =",user.id).first().now();
		if (pt == null) pt = new PollTransaction(user.id,new Date(),user.getAssignmentId());
		return pt;
	}
	
	Question getQuestion(Key<Question> k) {
		Question q = pollQuestions.get(k);
		if (q==null) {
			q = ofy().load().key(k).now();
			pollQuestions.put(k,q);
		}
		return q;
	}
	
	String waitPage(User user) {
		return waitPage(user, getPollTransaction(user));
	}
	
	String waitPage(User user,PollTransaction pt) {
		StringBuffer buf = new StringBuffer();
		if (pt != null && pt.possibleScore>0) {
			buf.append("<h3>Thank you for submitting your responses to this class poll</h3>");
			buf.append("Your responses were submitted at " + pt.completed + "<br />");
			buf.append("Your score was " + pt.score + " points out a possible " + pt.possibleScore + " points.");
		}
		
		if (user.isInstructor()) {
			buf.append("<h3>The poll is still open</h3>"
					+ "<form method=post><div id=timer0 style='display: inline'></div>&nbsp;When you are ready, please&nbsp;"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value='ClosePoll' />"
					+ "<input type=submit value='click here to close the poll and view the results' />"
					+ "</form>"
					+ "You must then instruct your students to refresh their browsers to see the results.");
		} else {
			buf.append("<h3>Please wait until the poll closes</h3>Your instructor will tell you "
					+ "when you can click the button below to view the class results for the poll.<br/>"
					+ "<form method=get>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=submit value='View the Poll Results' />"
					+ "</form>");
		}
		return buf.toString();	
	}
	
	String resultsPage(User user) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug:");
		Assignment a = getAssignment(user.getAssignmentId());
		debug.append("a.");
		
		buf.append("<h2>Poll Results</h2>");
		if (user.isInstructor()) {
			buf.append("<b>Be sure to tell your students that the poll is now closed</b> and to refresh their browsers to view these results. ");
			buf.append("<form method=post>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value='ResetPoll' />"
					+ "When you have finished viewing the results, please<input type=submit value='click here to reset the poll' />"
					+ "</form><p></p>");
		}
		
		debug.append("b.");
		
		PollTransaction pt = getPollTransaction(user);	
		List<PollTransaction> pts = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).list();
		buf.append("\n");
		buf.append("<script>"
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
				+ "</b></FONT><p></p>';\n"
				+ "    }\n"
				+ "  }\n"
				+ "  url += '&QuestionId=' + id + '&sig=" + user.getTokenSignature() + "&Notes=' + note + '&Email=' + email;\n"
				+ "  xmlhttp.open('GET',url,true);\n"
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
				+ "</script>");	
		buf.append("\n");
		
		int i=0;
		buf.append("<div style='display: table'>"); // big-table
		buf.append("<div style='display: table-row;'>"
				+ "<div style='display: table-cell'></div>"
				+ "<div style='display: table-cell'><h3>Questions</h3></div>"
				+ "<div style='display: table-cell'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</div>"  // horizontal buffer
				+ "<div style='display: table-cell'><h3>Responses</h3></div>"
				+ "</div>");  // end of header row
		debug.append("c.");
		for (Key<Question> k : a.questionKeys) {
			buf.append("\n");
			try {
				Question q = getQuestion(k);
				q.setParameters(a.id % Integer.MAX_VALUE);
				if (q.correctAnswer==null) q.correctAnswer = "";
				i++;
				buf.append("<div style='display: table-row;vertical-align: top;'>");
				buf.append("<div style='display: table-cell;vertical-align: top;'>" + i + ".&nbsp;</div>"); // number cell
				
				buf.append("<div style='display: table-cell;vertical-align: top;width: 400px;'>"); // question cell
				
				String userResponse = pt.responses==null?"":(pt.responses.get(k)==null?"":pt.responses.get(k));
				
				buf.append(q.correctAnswer.isEmpty()?q.print():q.printAllToStudents(userResponse));
				//buf.append(q.printAll());
				
				buf.append("</div>"   // end of question cell
						+ "<div style='display: table-cell;vertical-align: top;'></div>");  // horizontal buffer
				
				// This is where we will construct a histogram showing the distribution of responses
				debug.append("start.");
				
				Map<String,Integer> histogram = new HashMap<String,Integer>();
				//String correctResponse = q.getCorrectAnswer();
				String otherResponses = null;
				char choice = 'a';
				//int chart_height = 150;
				debug.append("1.");
				
				switch (q.getQuestionType()) {
				case Question.MULTIPLE_CHOICE:
					for (int j = 0; j < q.nChoices; j++) {
						histogram.put(String.valueOf(choice),0);
						choice++;
					}
					debug.append("2a.");
					for (PollTransaction t : pts) {
						if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
						histogram.put(t.responses.get(k),histogram.get(t.responses.get(k))+1);
					}
					break;
				case Question.TRUE_FALSE:
					histogram.put("true", 0);
					histogram.put("false", 0);
					//chart_height = 100;
					debug.append("2b.");
					for (PollTransaction t : pts) {
						if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
						histogram.put(t.responses.get(k),histogram.get(t.responses.get(k))+1);
					}
					break;
				case Question.SELECT_MULTIPLE:
					for (int j = 0; j < q.nChoices; j++) {
						histogram.put(String.valueOf(choice),0);
						choice++;
					}
					debug.append("2c.");
					for (PollTransaction t : pts) {
						if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
						String response = t.responses.get(k);
						debug.append(response + ".");
						for (int m=0; m<response.length();m++) {
							debug.append("4.");
							histogram.put(String.valueOf(response.charAt(m)),histogram.get(String.valueOf(response.charAt(m)))+1);
						}
					}
				break;
				case Question.FILL_IN_WORD:
					histogram.put("correct", 0);
					histogram.put("incorrect", 0);
					//chart_height = 100;
					debug.append("2d.");
					for (PollTransaction t : pts) {
						if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
						if (q.isCorrect(t.responses.get(k))) histogram.put("correct",histogram.get("correct")+1);
						else {
							histogram.put("incorrect", histogram.get("incorrect") + 1);
							if (otherResponses==null) otherResponses = t.responses.get(k);
							else if (otherResponses.length()<500 && t.responses.get(k) != null) otherResponses += "; " + t.responses.get(k);
						}
					}
					break;
				case Question.NUMERIC:
					histogram.put("correct", 0);
					histogram.put("incorrect", 0);
					//chart_height = 100;
					debug.append("2e.");
					for (PollTransaction t : pts) {
						if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
						if (q.isCorrect(t.responses.get(k))) histogram.put("correct",histogram.get("correct")+1);
						else {
							histogram.put("incorrect", histogram.get("incorrect") + 1);
							if (otherResponses==null) otherResponses = t.responses.get(k);
							else if (otherResponses.length()<500 && t.responses.get(k) != null) otherResponses += "; " + t.responses.get(k);
							}
					}
					break;
				default:
				}
				debug.append("histogram initialized.");
				
				// Calculate a scale factor for the maximum width of the graph bars based on the max % response
				
				int maxValue = 0;
				int totalValues = 0;
				for (Entry<String,Integer> e : histogram.entrySet()) {
					totalValues += e.getValue();
					if (e.getValue() > maxValue) maxValue = e.getValue();
				}
				debug.append("maxValue="+maxValue+".totalValues="+totalValues+".");
				buf.append("\n");
				
				buf.append("<div id=chart_div" + i + " style='display: table-cell;vertical-align: top;'>");  // histogram cell
				if (totalValues>0) {
					// Print a histogram as a table containing a horizontal bar graph:
					switch (q.getQuestionType()) {
					case Question.MULTIPLE_CHOICE:
					case Question.TRUE_FALSE:
					case Question.SELECT_MULTIPLE:
						buf.append("Summary of responses received for this question:<p></p>");
						buf.append("<table>");
						for (Entry<String,Integer> e : histogram.entrySet()) {
							buf.append("<tr><td>");
							buf.append(e.getKey() + "&nbsp;");
							buf.append("</td><td>");
							buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*e.getValue()/(totalValues+1) + "px;'>&nbsp;</div>");
							buf.append("&nbsp;" + e.getValue() + "</td></tr>");
						}
						buf.append("</table>");
						break;
					case Question.FILL_IN_WORD:
					case Question.NUMERIC:
						buf.append("Summary of responses received for this question:<p></p>");
						buf.append("<table>");
							buf.append("<tr><td>");
							buf.append("correct" + "&nbsp;");
							buf.append("</td><td>");
							buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*histogram.get("correct")/(totalValues+1) + "px;'>&nbsp;</div>");
							buf.append("&nbsp;" + histogram.get("correct") + "</td></tr>");
							buf.append("<tr><td>");
							buf.append("incorrect" + "&nbsp;");
							buf.append("</td><td>");
							buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*histogram.get("incorrect")/(totalValues+1) + "px;'>&nbsp;</div>");
							buf.append("&nbsp;" + histogram.get("incorrect") + "</td></tr>");
							if (otherResponses != null) buf.append("<tr><td colspan=2>Incorrect Responses: " + otherResponses + "</td></tr>");							
						buf.append("</table>");
						break;	
					}
				} else buf.append("No responses were submitted for this question.");
				
				buf.append("</div></div>"); // end of table cell and row
				debug.append("endOfHistogram.");
			
			} catch (Exception e) {
				buf.append(e.toString() + " " + e.getMessage() + "" + debug.toString() + "</div>");
			}
		}
		buf.append("</div>");  // end of table
		
		return buf.toString();
	}
	
	String editPage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		if (!user.isInstructor()) return "Not Authorized";
		Assignment a = getAssignment(user.getAssignmentId());
		if (a == null) return "Assignment could not be fund.";
		
		if (a.getQuestionKeys().size() == 0) buf.append("<h2>Create a New Class Poll</h2>");
		else buf.append("<h2>Edit Class Poll</h2>");
		
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e2) {}
		String assignmentType = request.getParameter("AssignmentType");
		if (assignmentType == null) assignmentType = "";
		
		// Display a selector to display candidate questions by topic and assignmentType
		buf.append("Add questions to this poll by selecting from existing question items:");

		buf.append("<FORM NAME=TopicSelect METHOD=GET><INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />");
		buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='EditPoll' />");
		buf.append("Topic:" + topicSelectBox(topicId) + " Assignment Type:" + assignmentTypeDropDownBox(assignmentType));
		buf.append(" <INPUT TYPE=SUBMIT VALUE='Display Questions' />");
		buf.append("</FORM><br />");
		
		boolean selecting = (topicId>0 && !assignmentType.isEmpty());
		
		if (!selecting) buf.append("<form method=get action='/Poll'>"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='NewQuestion' />"
				+ "or <input type=submit value='Create a custom question of your own' />"
				+ "</form><p></p>");

		int i = 0;		
		if (selecting) {  // show a list of existing question items
			buf.append("<h3>Available Questions for this Topic</h3>");
			
			List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId", topicId).order("pointValue").list();
			buf.append("<form method=post action='/Poll'><input type=hidden name=sig value='" + user.getTokenSignature() + "' />");
			buf.append("<input type=hidden name=UserRequest value='AddQuestions' />");
			buf.append("<input type=submit value='Include the selected items below in the poll' /><p></p>");
			for (Question q : questions) {
				i++;
				q.setParameters(a.id % Integer.MAX_VALUE);
				buf.append("<div style='display: table-row'>");
				buf.append("<div style='display: table-cell;width: 55px;'><input type=checkbox name=QuestionId value='" + q.id + "' />&nbsp;" + i + ".</div>");
				buf.append("<div style='display: table-cell'>" + q.printAll() + "</div>");
				buf.append("</div>");  // end of row
			}
			buf.append("<input type=submit value='Include the selected items below in the poll' /><p></p>");
			buf.append("</form>");
		} else if (a.getQuestionKeys().size() > 0) {  // Print a copy of the current poll questions here:
			buf.append("<form method=post action='/Poll'><input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value='DeleteQuestions' />"
					+ "or <input type=submit value='Remove the selected items below from this poll' />");
			
			buf.append("<h3>Current Questions For This Poll</h3>");
			int possibleScore = 0;
			for (Key<Question> k : a.questionKeys) {  // main loop to present questions
				i++;
				Question q = getQuestion(k);
				q.setParameters(a.id % Integer.MAX_VALUE);
				buf.append("<div style='display: table-row'>");
				buf.append("<div style='display: table-cell;width: 55px;'><input type=checkbox name=QuestionId value='" + q.id + "' />&nbsp;" + i + ".</div>");
				buf.append("<div style='display: table-cell'>" + q.print() + "</div>");
				buf.append("</div><br />"); // end of row
				possibleScore += q.correctAnswer==null || q.correctAnswer.isEmpty()?0:q.pointValue;
			}
			buf.append("</form>");
			
			if (a.questionKeys.size()>0) buf.append("<hr />This poll is worth a possible " + possibleScore + " points.");
		
			// Click here when done editing:
			buf.append("<form method=get><input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=submit value='Click here when you are finished editing this poll' /></form>");
		}
		return buf.toString();
	}

	String assignmentTypeDropDownBox(String defaultType) {
		if (defaultType == null) defaultType = "";
		StringBuffer buf = new StringBuffer("<SELECT NAME=AssignmentType>");
		if (defaultType.isEmpty()) buf.append("<OPTION VALUE=''>Select a type</OPTION>");
		buf.append("<OPTION" + (defaultType.equals("Quiz")?" SELECTED":"") + ">Quiz</OPTION>"
		+ "<OPTION" + (defaultType.equals("Homework")?" SELECTED":"") + ">Homework</OPTION>"
		+ "<OPTION" + (defaultType.equals("Exam")?" SELECTED":"") + ">Exam</OPTION>"
		+ "<OPTION" + (defaultType.equals("Video")?" SELECTED":"") + ">Video</OPTION>"
		+ "</SELECT>");
		return buf.toString();
	}

	String topicSelectBox(long topicId) {
		StringBuffer buf = new StringBuffer("<SELECT NAME=TopicId>");
		if (topicId == 0) buf.append("<OPTION VALUE=''>Select a topic</OPTION>");
		Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
		for (Topic t : topics) buf.append("<OPTION VALUE=" + t.id + (t.id.equals(topicId)?" SELECTED>":">") + t.title + "</OPTION>");
		buf.append("</SELECT>");
		return buf.toString();
	}
	

	void addQuestions(User user,HttpServletRequest request) {
		String[] qids = request.getParameterValues("QuestionId");
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		for (String qid : qids) questionKeys.add(Key.create(Question.class,Long.parseLong(qid)));
		if (questionKeys.size()>0) {
			Assignment a = getAssignment(user.getAssignmentId());
			if (a.questionKeys == null) a.questionKeys = questionKeys;
			else a.questionKeys.addAll(questionKeys);
			saveAssignment(a);
		}
	}
	
	void deleteQuestions(User user,HttpServletRequest request) {
		String[] qids = request.getParameterValues("QuestionId");
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		for (String qid : qids) {
			Key<Question> k = Key.create(Question.class,Long.parseLong(qid));
			questionKeys.add(k);
			pollQuestions.remove(k);
		}
		if (questionKeys.size()>0) {
			Assignment a = getAssignment(user.getAssignmentId());
			a.questionKeys.removeAll(questionKeys);
			saveAssignment(a);
		}
	}
	
	String newQuestionForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String assignmentType = "Poll";
		int questionType = 0;
		try {
			questionType = Integer.parseInt(request.getParameter("QuestionType"));
			switch (questionType) {
			case (1): buf.append("<h3>New Multiple-Choice " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to select the single best "
					+ "answer to the question."); break;
			case (2): buf.append("<h3>New True-False " + assignmentType + " Question</h3>");
			buf.append("Write the question as an affirmative statement. Then "
					+ "indicate below whether the statement is true or false."); break;
			case (3): buf.append("<h3>New Select-Multiple " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to "
					+ "select all of the correct answers to the question."); break;
			case (4): buf.append("<h3>New Fill-in-Word " + assignmentType + " Question</h3>");
			buf.append("Start the question text in the upper textarea box. Indicate "
					+ "the correct answer (and optionally, an alternative correct answer) in "
					+ "the middle boxes, and the end of the question text below that.  The answers "
					+ "are not case-sensitive or punctuation-sensitive, but spelling must "
					+ "be exact."); break;
			case (5): buf.append("<h3>New Numeric " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text in the upper textarea box and "
					+ "the correct numeric answer below. Also indicate the required precision "
					+ "of the student's response in percent (default = 2%). Use the bottom "
					+ "textarea box to finish the question text and/or to indicate the "
					+ "expected dimensions or units of the student's answer."); break;
			default: buf.append("An unexpected error occurred. Please try again.");
			}
			Question question = new Question(questionType);
			buf.append("<p><FORM METHOD=GET ACTION='/Poll'>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + user.id + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + " />");
			
			buf.append("Point Value: <input type=text size=2 name=PointValue /><br />");
			buf.append(question.edit());
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview' />"
					+ "</FORM>");
		} catch (Exception e) {
			buf.append("<h2>Create a Custom Question</h2>");
			buf.append("<FORM NAME=NewQuestion METHOD=GET ACTION='/Poll'>");
			buf.append("Select one of the following question types:<br />"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=NewQuestion />"
					+ "<INPUT TYPE=HIDDEN NAME=QuestionType />"
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=1;submit()\" VALUE='Multiple Choice' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=2;submit()\" VALUE='True/False' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=3;submit()\" VALUE='Select Multiple' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=4;submit()\" VALUE='Fill in Word' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=5;submit()\" VALUE='Numeric' />"
					+ "</FORM>");
		}
		return buf.toString();
	}

	String previewQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = 0;
			boolean current = false;
			boolean proposed = false;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
				current = true;
			} catch (Exception e2) {}
			long proposedQuestionId = 0;
			try {
				proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				proposed = true;
				current = false;
			} catch (Exception e2) {}
			
			Question q = assembleQuestion(request);
			if (q.requiresParser()) q.setParameters();
			
			buf.append("<h3>Preview Custom Poll Question</h3>");
			
			q.assignmentType = "Poll";
				
			buf.append("Author: " + q.authorId + "<br />");
			buf.append("Editor: " + user.id + "<p>");
			
			buf.append("<FORM Action='/Poll' METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />");
			
			buf.append(q.printAll());
			
			if (q.authorId==null) q.authorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + user.id + "' />");
			
			
			if (current) {
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + " />");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Question' />");
			}
			if (proposed) {
				buf.append("<INPUT TYPE=HIDDEN NAME=ProposedQuestionId VALUE=" + proposedQuestionId + " />");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Activate This Question' />");
			} else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save New Question' />");
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit' />");
			
			buf.append("<hr><h3>Continue Editing</h3>");
			buf.append("Assignment Type: Poll<br />");
			
			buf.append("<br />");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			
			if (q.assignmentType.equals("Exam")) {
				if (q.pointValue!=2 && q.pointValue!=10 && q.pointValue!=15) q.pointValue = 2;
			} else q.pointValue = 1;
			buf.append(" Point Value: <input type=text size=2 name=PointValue value='" + q.pointValue + "' /><br />");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview />");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	private Question assembleQuestion(HttpServletRequest request) {
		try {
			int questionType = Integer.parseInt(request.getParameter("QuestionType"));
			return assembleQuestion(request,new Question(questionType)); 
		} catch (Exception e) {
			return null;
		}
	}
	
	private Question assembleQuestion(HttpServletRequest request,Question q) {
		String assignmentType = request.getParameter("AssignmentType");
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e) {}
		int type = q.getQuestionType();
		try {
			type = Integer.parseInt(request.getParameter("QuestionType"));
		}catch (Exception e) {}
		String questionText = request.getParameter("QuestionText");
		ArrayList<String> choices = new ArrayList<String>();
		int nChoices = 0;
		char choice = 'A';
		for (int i=0;i<5;i++) {
			String choiceText = request.getParameter("Choice"+ choice +"Text");
			if (choiceText==null) choiceText = "";
			if (choiceText.length() > 0) {
				choices.add(choiceText);
				nChoices++;
			}
			choice++;
		}
		double requiredPrecision = 0.; // percent
		int significantFigures = 0;
		int pointValue = 1;
		try {
			pointValue = Integer.parseInt(request.getParameter("PointValue"));
		} catch (Exception e) {
		}
		try {
			requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
		} catch (Exception e) {
		}
		try {
			significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
		} catch (Exception e) {
		}
		String correctAnswer = "";
		try {
			String[] allAnswers = request.getParameterValues("CorrectAnswer");
			for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
		} catch (Exception e) {
			correctAnswer = request.getParameter("CorrectAnswer");
		}
		String parameterString = request.getParameter("ParameterString");
		if (parameterString == null) parameterString = "";
		
		q.assignmentType = assignmentType;
		q.topicId = topicId;
		q.setQuestionType(type);
		q.text = questionText;
		q.nChoices = nChoices;
		q.choices = choices;
		q.requiredPrecision = requiredPrecision;
		q.significantFigures = significantFigures;
		q.correctAnswer = correctAnswer;
		q.tag = request.getParameter("QuestionTag");
		q.pointValue = pointValue;
		q.parameterString = parameterString;
		q.hint = request.getParameter("Hint");
		q.solution = request.getParameter("Solution");
		q.notes = "";
		q.authorId = request.getParameter("AuthorId");
		q.editorId = request.getParameter("EditorId");
		q.validateFields();
		return q;
	}
	
	String questionTypeDropDownBox(int questionType) {
		StringBuffer buf = new StringBuffer();
		buf.append("\n<SELECT NAME=QuestionType>"
				+ "<OPTION VALUE=1" + (questionType==1?" SELECTED>":">") + "Multiple Choice</OPTION>"
				+ "<OPTION VALUE=2" + (questionType==2?" SELECTED>":">") + "True/False</OPTION>"
				+ "<OPTION VALUE=3" + (questionType==3?" SELECTED>":">") + "Select Multiple</OPTION>"
				+ "<OPTION VALUE=4" + (questionType==4?" SELECTED>":">") + "Fill in word/phrase</OPTION>"
				+ "<OPTION VALUE=5" + (questionType==5?" SELECTED>":">") + "Numeric</OPTION>"
				+ "</SELECT>");
		return buf.toString();
	}
	
	long createQuestion(User user,HttpServletRequest request) { //previously type long
		try {
			Question q = assembleQuestion(request);
			q.isActive = true;
			ofy().save().entity(q).now();
			return q.id;
		} catch (Exception e) {
			return 0;
		}
	}


}
