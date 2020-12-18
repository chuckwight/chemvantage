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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Poll")
public class Poll extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static Map<Long,Integer> activePolls = new HashMap<Long,Integer>(); // key=assignmentId and value=state {0, 1, or 2}
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
			default:
				switch (state) {
				case 0:
					out.println(Home.header() + welcomePage(user,request) + Home.footer);
					break;
				case 1:
					if (!user.isInstructor() && responsesRecorded(user.id)) out.println(Home.header() + waitPage(user) + Home.footer);
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

			Integer state = activePolls.get(user.getAssignmentId());
			if (state==null) state = 0;

			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "LaunchPoll":
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				PollTransaction pt = getPollTransaction(user);
				pt.downloaded = new Date();
				ofy().save().entity(new PollTransaction(user.id,new Date(),a.id));  // essential for purgeActivePolls to work properly			
				pollQuestions.putAll(ofy().load().keys(a.questionKeys));
				activePolls.put(a.id, 1);
				break;
			case "ClosePoll":
				activePolls.put(user.getAssignmentId(), 2);
				break;
			case "ResetPoll":
				a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				for (Key<Question> k : a.questionKeys) pollQuestions.remove(k);
				activePolls.remove(user.getAssignmentId());
				break;
			case "SubmitResponses":
				submitResponses(user,request);
				break;
			case "SubmitEdits":
				submitEdits(user,request);
				out.println(Home.header() + editPage(user,request) + Home.footer);
				return;
			}
			doGet(request,response);
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	boolean responsesRecorded(String userId) {
		return ofy().load().type(PollTransaction.class).filter("userId",userId).count() > 0;
	}
	
	String welcomePage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Welcome to the Class Poll</h2>");
		if (user.isInstructor()) {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			if (a.questionKeys.size()==0) return editPage(user,request);
			else {
				buf.append("You may review and edit the questions for this poll by <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">clicking this link</a>.<p>");
				
				buf.append("When your class is ready for this poll, launch it by pressing the button below. "
						+ "You must then tell your students that the poll is open so they can click the link "
						+ "to view the poll question items.<br>");
				buf.append("<form method=post>"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
						+ "<input type=hidden name=UserRequest value=LaunchPoll>"
						+ "<input type=submit value='Launch This Poll Now'>");
			}
		} else {
			buf.append("This poll is currently closed. Please wait until your instructor tells you that the poll is open.<br>"
					+ "Then click the button below to view the poll question items.<br>"
					+ "<form method=get>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "<input type=submit value='View the Poll'></form><p>");
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
		for (Long exp : expiredAssignmentIds) activePolls.remove(exp);
		nextPurge = new Date(new Date().getTime() + 3600000L);        // 1 hour from now
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
				+ "They will have to refresh their browsers to view the questions.<br>");
			buf.append("<form method=post><div id=timer0 style='display: inline'></div>&nbsp;When you are ready, please&nbsp;"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "<input type=hidden name=UserRequest value=ClosePoll>"
					+ "<input type=submit value='click here to close the poll and view the results'>"
					+ "</form>"
					+ "You must then instruct your students to refresh their browsers to see the results.");
		}
		
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		
		buf.append("<OL>");
		int possibleScore = 0;
		buf.append("<form id=pollForm method=post onSubmit='return confirmSubmission(" + a.questionKeys.size() + ")'>"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">");
		
		for (Key<Question> k : a.questionKeys) {  // main loop to present questions
			Question q = getQuestion(k); // this should nearly always work
			q.setParameters(a.id % Integer.MAX_VALUE);
			buf.append("<li>" + q.print() + "<br></li>");
			possibleScore += q.correctAnswer==null || q.correctAnswer.isEmpty()?0:q.pointValue;
		}
		buf.append("</OL>");
		buf.append(javaScripts()); 

		buf.append("<input type=hidden name=PossibleScore value=" + possibleScore + ">");
		buf.append("<input type=hidden name=UserRequest value=SubmitResponses>");
		buf.append("<input type=submit id=pollSubmit value='Submit My Responses Now'>");
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

	void submitResponses(User user,HttpServletRequest request) {
		PollTransaction pt = ofy().load().type(PollTransaction.class).filter("assignmentId",user.getAssignmentId()).filter("userId",user.id).first().now();
		if (pt == null) pt = new PollTransaction(user.id,new Date(),user.getAssignmentId());
		pt.completed = new Date();
		pt.score = 0;
		pt.possibleScore = 0;
		pt.responses = new HashMap<Key<Question>,String>();
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
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
	}
	
	PollTransaction getPollTransaction(User user) {
		PollTransaction pt = ofy().load().type(PollTransaction.class).filter("assignmentId",user.getAssignmentId()).filter("userId",user.id).first().now();
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
		StringBuffer buf = new StringBuffer();
		PollTransaction pt = getPollTransaction(user);
		if (pt != null && pt.possibleScore>0) {
			buf.append("<h3>Thank you for submitting your responses to this class poll</h3>");
			buf.append("Your score was " + pt.score + " points out a possible " + pt.possibleScore + " points.");
		}
		buf.append("<h3>Please wait until the poll closes</h3>Your instructor will tell you "
				+ "when you can click the button below to view the class results for the poll.<br>"
				+ "<form method=get>"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
				+ "<input type=submit value='View the Poll Results'>"
				+ "</form>");
		return buf.toString();	
	}
	
	String resultsPage(User user) {
		StringBuffer buf = new StringBuffer();
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		
		buf.append("<h2>Poll Results</h2>");
		if (user.isInstructor()) {
			buf.append("Be sure to tell your students that the poll is now closed and to refresh their browsers to view these results. ");
			buf.append("<form method=post>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "<input type=hidden name=UserRequest value=ResetPoll>"
					+ "When you have finished viewing the results, please<input type=submit value='click here to reset the poll'>"
					+ "</form><p>");
		}
		
		PollTransaction pt = getPollTransaction(user);
		
		int i=0;
		buf.append("<div style='display: table'>");
		for (Key<Question> k : a.questionKeys) {
			try {
				Question q = pollQuestions.get(k);  // this should almost always work
				if (q == null) {  // but just in case...
					q = ofy().load().key(k).now();
					pollQuestions.put(k,q);
				}
				q.setParameters(a.id % Integer.MAX_VALUE);
				if (q.correctAnswer==null) q.correctAnswer = "";
				i++;
				buf.append("<div style='display: table-row'>");
				buf.append("<div style='display: table-cell'>" + i + ".&nbsp;</div>");
				
				buf.append("<div style='display: table-cell'>");
				String studentResponse = pt.responses.get(k);
				if (studentResponse==null) studentResponse = "";
				
				buf.append(q.correctAnswer.isEmpty()?q.print():q.printAllToStudents(studentResponse));
				buf.append("</div><div style='display: table-cell'>");
				buf.append("Some sort of graph of the poll results goes here.");
				buf.append("</div></div><br>");


			} catch (Exception e) {
			}
		}
		buf.append("</div>");
		
		return buf.toString();
	}
	
	String editPage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		if (!user.isInstructor()) return "Not Authorized";
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
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
		buf.append("Select poll questions from among existing question items:<br>");

		buf.append("<FORM NAME=TopicSelect METHOD=GET><INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">");
		buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=EditPoll>");
		buf.append("Topic:" + topicSelectBox(topicId) + " Assignment Type:" + assignmentTypeDropDownBox(assignmentType));
		buf.append(" <INPUT TYPE=SUBMIT VALUE='Display Questions'>");
		buf.append("</FORM><p>");

		boolean selecting = (topicId>0 && !assignmentType.isEmpty());
		
		if (selecting) {  // show a list of existing question items
			List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId", topicId).order("pointValue").list();
			buf.append("<form method=post action=/Poll><input type=hidden name=sig value=" + user.getTokenSignature() + ">");
			buf.append("<input type=hidden name=UserRequest value=SubmitEdits>");
			buf.append("<input type=submit value='Include the selected items below in the poll'><p>");
			for (Question q : questions) {
				q.setParameters(a.id % Integer.MAX_VALUE);
				buf.append("<div style='display: table-row'>");
				buf.append("<div style='display: table-cell'><input type=checkbox name=QuestionId value=" + q.id + ">&nbsp;</div>");
				buf.append("<div style='display: table-cell'>" + q.printAll() + "</div>");
				buf.append("</div>");
			}
			buf.append("<input type=submit value='Include the selected items below in the poll'><p>");
			buf.append("</form>");
		} else {  // Print a copy of the current poll questions here:
			buf.append("<OL>");
			int possibleScore = 0;
			for (Key<Question> k : a.questionKeys) {  // main loop to present questions
				Question q = pollQuestions.get(k); // this should nearly always work
				if (q == null) {  // but just in case...
					q = ofy().load().key(k).now();
					pollQuestions.put(k,q);
				}
				q.setParameters(a.id % Integer.MAX_VALUE);
				buf.append("<li>" + q.print() + "<br></li>");
				possibleScore += q.correctAnswer==null || q.correctAnswer.isEmpty()?0:q.pointValue;
			}
			buf.append("</OL>");
			buf.append("This poll is worth a possible " + possibleScore + " points.<br><hr>");
		
			// Click here when done editing:
			buf.append("<form method=get><input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "<input type=submit value='Click here when you are finished editing this poll'></form>");
		}

		// If a topicId and assignmentType have been selected, display the question items:
		
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
		for (Topic t : topics) buf.append("<OPTION VALUE=" + t.id + (t.id.equals(topicId)?" SELECTED>":">") + t.title + "</OPTION>\n");
		buf.append("</SELECT>");
		return buf.toString();
	}
	

	void submitEdits(User user,HttpServletRequest request) {
		String[] qids = request.getParameterValues("QuestionId");
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		for (String qid : qids) questionKeys.add(Key.create(Question.class,Long.parseLong(qid)));
		if (questionKeys.size()>0) {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			if (a.questionKeys == null) a.questionKeys = questionKeys;
			else a.questionKeys.addAll(questionKeys);
			ofy().save().entity(a).now();
		}
	}
	
}
