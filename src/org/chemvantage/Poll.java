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

@WebServlet("/Poll")
public class Poll extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static Map<Long,PollRepo> activePolls = new HashMap<Long,PollRepo>();;
	private static Map<Key<Question>,Question> pollQuestions = new HashMap<Key<Question>,Question>();
   
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			PollRepo p = activePolls.get(user.getAssignmentId());
			if (p==null) p = new PollRepo();
			
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();			
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "EditPoll":
				out.println(Home.header() + editPage(user) + Home.footer);
				break;
			default:
				switch (p.state) {
				case 0:
					out.println(Home.header() + welcomePage(user,p) + Home.footer);
					break;
				case 1:
					if (p.userIds.contains(user.id)) out.println(Home.header() + waitPage(user) + Home.footer);
					else out.println(Home.header() + showPollQuestions(user,p) + Home.footer);
					break;
				case 2:
					out.println(Home.header() + resultsPage(user,p) + Home.footer);
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
			
			PollRepo p = activePolls.get(user.getAssignmentId());
			if (p==null) p = new PollRepo();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "LaunchPoll":
				p.a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				p.state = 1;
				activePolls.put(p.a.id, p);
				pollQuestions.putAll(ofy().load().keys(p.a.questionKeys));
			break;
			case "ClosePoll":
				p.state = 2;
				break;
			case "ResetPoll":
				activePolls.remove(p.a.id);
				break;
			case "SubmitResponses":
				submitResponses(user,p,request);
				break;
			case "SubmitEdits":
				submitEdits(user,p,request);
				break;
			}
			doGet(request,response);
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	String welcomePage(User user,PollRepo p) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Welcome to the Class Poll</h2>");
		if (user.isInstructor()) {
			if (p.a == null) p.a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			if (p.a.questionKeys.size()==0) return editPage(user);
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
			
	String showPollQuestions(User user,PollRepo p) {
		/*
		 * This method is reached by Learners or Instructors who launch the correct LTI Resource Link in the LMS, thereby binding
		 * the assignmentId to their User entity. The GET request lands here if and only if the PollRepo parameter state == 1.
		 */
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Class Poll</h2>");
		
		if (user.isInstructor()) {
			buf.append("Please tell your students that the poll is now open. "
				+ "They will have to refresh their browsers to view the questions.<br>"
				+ "<div id=timer0></div>");
		}
		
		buf.append("<OL>");
		int possibleScore = 0;
		buf.append("<form id=pollForm method=post onSubmit='return confirmSubmission(" + p.a.questionKeys.size() + ")'>"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">");
		
		for (Key<Question> k : p.a.questionKeys) {  // main loop to present questions
			Question q = pollQuestions.get(k); // this should nearly always work
			if (q == null) {  // but just in case...
				q = ofy().load().key(k).now();
				pollQuestions.put(k,q);
			}
			q.setParameters(p.a.id % Integer.MAX_VALUE);
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
				+ "  var minutes = elapsedSeconds/60;"
				+ "  var oddSeconds = elapsedSeconds % 60;"
				+ "  document.getElementById('timer0').innerHTML='Elapsed Time: ' + minutes + ':' + oddSeconds;"
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
				+ "  if (nAnswers<10) return confirm('Submit your responses now? ' + (nQuestions-nAnswers) + ' answers may have been left blank.');"
				+ "  else return true;"
				+ "}"
				+ "function showWorkBox(qid) {}" 
				+ "</SCRIPT>"; 
	}

	void submitResponses(User user,PollRepo p,HttpServletRequest request) {
		
	}
	
	String waitPage(User user) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Please wait until the poll closes</h2>Your instructor will tell you "
				+ "when you can click the button below to view the poll results.<br>"
				+ "<form method=get>"
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
				+ "<input type=submit value='View the Poll Results'>"
				+ "</form>");
		return buf.toString();	
	}
	
	String resultsPage(User user,PollRepo p) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Poll Results</h2>");
		
		buf.append("<OL>");
		for (Key<Question> k : p.a.questionKeys) {
			try {
				Question q = pollQuestions.get(k);  // this should almost always work
				if (q == null) {  // but just in case...
					q = ofy().load().key(k).now();
					pollQuestions.put(k,q);
				}
				q.setParameters(p.a.id % Integer.MAX_VALUE);
				if (q.correctAnswer==null) q.correctAnswer = "";

				buf.append("<li><div style='display: table-row'>");
				buf.append("<div style='display: table-cell'>");
				buf.append(q.correctAnswer.isEmpty()?q.print():q.printAll());
				buf.append("</div><div style='display: table-cell'>");
				buf.append(p.resultsGraph);
				buf.append("</div></div><br></li>");


			} catch (Exception e) {
			}
		}
		buf.append("</OL>");

		return buf.toString();
	}
	
	String editPage(User user) {
		StringBuffer buf = new StringBuffer();
		if (!user.isInstructor()) return "Not Authorized";
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		if (a == null) return "Assignment could not be fund.";
		
		if (a.getQuestionKeys().size() == 0) buf.append("<h2>Create a New Class Poll</h2>");
		else buf.append("<h2>Edit Class Poll</h2>");
		
		
		
		return buf.toString();
	}
	
	void submitEdits(User user,PollRepo p,HttpServletRequest request) {
		
	}
	
	
}

class PollRepo {
	int state = 0;
	Assignment a = null;
	List<String> userIds = new ArrayList<String>();
	Map<Key<Question>,List<String>> pollResults = new HashMap<Key<Question>,List<String>>();
	Date expires = null;
	Map<Key<Question>,String> resultsGraph = new HashMap<Key<Question>,String>();
	
	PollRepo() {}
}
