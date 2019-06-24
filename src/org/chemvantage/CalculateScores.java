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
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class CalculateScores extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet calculates Score objects for one assignment.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		doPost(request,response);
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		User user = User.getInstance(request.getSession());
		if (user==null || !(user.isInstructor() || user.isAdministrator() || user.isChemVantageAdmin())) {
			response.sendRedirect("/Logout");
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		Assignment assignment = null;
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			assignment = ofy().load().type(Assignment.class).id(assignmentId).now();
		} catch (Exception e) {
			out.println("No valid assignment found.");
			return;
		}
		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = ""; // prevents null pointer Exception
		
		boolean recalculate = (userRequest.contentEquals("Recalculate"))?true:false;
		
		if (userRequest.contentEquals("ViewTransactions")) {  // view assignment transactions for a single student
			User student = ofy().load().type(User.class).id(request.getParameter("UserId")).now();
			out.println(Home.header + viewTransactions(student,assignment) + Home.footer);
		} else {  // view group Score objects for one assignment
			out.println(Home.header + viewGroupScores(assignment,recalculate) + Home.footer); 
		}
	}
	
	public String viewTransactions(User student,Assignment a) {
		StringBuffer buf = new StringBuffer();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		try {
			if ("Quiz".contentEquals(a.assignmentType)) {
				buf.append("<h2>Quiz Transactions</h2>");
				buf.append("ChemVantage UserID: " + student.getIdHash() + "<br>");
				buf.append("Assignment Number: " + a.id + "<br>");
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("Topic: "+ t.title + "<br>");
				buf.append("Valid: " + df.format(now) + "<p>");
				
				List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).filter("userId",student.id).order("downloaded").list();
				
				if (qts.size()==0) {
					buf.append("Sorry, we did not find any records for this student in the database for this assignment.");
					return buf.toString();
				}
				
				Key<Score> k = Key.create(Key.create(User.class, student.id),Score.class,a.id);
	    		Score s = ofy().load().key(k).now();
	    		if (s==null) s = Score.getInstance(student.id, a);
	    		buf.append("This student's overall score on the assignment is " + Math.round(100.*s.score/s.maxPossibleScore) + "%.<p>");
				
				buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Quiz Score</th></tr>");
				for (QuizTransaction qt : qts) {
					buf.append("<tr><td>" + qt.id + "</td><td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":100.*qt.score/qt.possibleScore + "%") +  "</td></tr>");
				}
				buf.append("</table><br>Missing scores indicate quizzes that were downloaded but not submitted for scoring.<p>");
			} else if ("Homework".contentEquals(a.assignmentType)) {
				buf.append("<h2>Homework Transactions</h2>");
				buf.append("ChemVantage UserID: " + student.getIdHash() + "<br>");
				buf.append("Assignment Number: " + a.id + "<br>");
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("Topic: "+ t.title + "<br>");
				buf.append("Valid: " + df.format(now) + "<p>");
				
				List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).filter("userId",student.id).order("graded").list();
				
				if (hwts.size()==0) {
					buf.append("Sorry, we did not find any records for this student in the database for this assignment.");
					return buf.toString();
				}
				
				Key<Score> k = Key.create(Key.create(User.class, student.id),Score.class,a.id);
	    		Score s = ofy().load().key(k).now();
	    		if (s==null) s = Score.getInstance(student.id, a);
	    		buf.append("This student's overall score on the assignment is " + Math.round(100.*s.score/s.maxPossibleScore) + "%.<p>");
				
				buf.append("<table><tr><th>Transaction Number</th><th>QuestionID</th><th>Graded</th><th>Score</th></tr>");
				for (HWTransaction hwt : hwts) {
					buf.append("<tr align=center><td>" + hwt.id + "</td><td>" + hwt.questionId + "</td><td>" + df.format(hwt.graded) + "</td><td>" + hwt.score +  "</td></tr>");
				}
				buf.append("</table><p>");		
			} else buf.append("Sorry, we are unable to report transactions for this assignment type.");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();	
	}
	
	public String viewGroupScores(Assignment a,boolean recalculate) {
		StringBuffer buf = new StringBuffer();
		try {
			Group group = ofy().load().type(Group.class).id(a.groupId).now();
			if (recalculate) group.reviseScores(a);

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
			buf.append(df.format(new Date()) + "<p>");

			// make a list of student users, removing instructors, admins and TAs
			List<User> groupMembers = new ArrayList<User>(ofy().load().type(User.class).ids(group.memberIds).values());
			List<User> instructors = new ArrayList<User>();
			for (User u : groupMembers) if (u.isAdministrator() || u.isInstructor() || u.isTeachingAssistant()) instructors.add(u);
			groupMembers.removeAll(instructors);  // leaves only the members with a student role

			Score s = null;
			int counter = 0;
			if (groupMembers.size()==0) buf.append("There are no students in this group yet.");
			else {
				if (recalculate) {
					buf.append("These scores have been recalculated just now:<p>");
				} else buf.append("To protect the privacy of our users, ChemVantage does not store names or other personal identifiable information (PII). "
						+ "Scores are listed only by an opaque user ID that is derived from the value provided by your class learning management system. "
						+ "You may click any student's UserID below to get a complete list of transactions for this assignment. If you think there may be "
						+ "a problem with the scores, you may <a href=/CalculateScores?UserRequest=Recalculate&AssignmentId=" + a.id + ">"
						+ "click here to recalculate all student scores for this assignment</a> (this may take a few minutes).<p>");
				// print a table of scores for this group assignment
				buf.append("<table cellspacing=0 border=1><tr><th>ChemVantage UserID</th><th>Best Score</th><th>Attempts</th><th>Most Recent Attempt</th></tr>");
				Queue queue = QueueFactory.getDefaultQueue();
				for (User u : groupMembers) {
					counter++;
					s = u.getScore(a);
					if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",u.id));
					double pct = s.score*100./s.maxPossibleScore;
					buf.append("<tr><td align=center><a href=/CalculateScores?UserRequest=ViewTransactions&AssignmentId=" + a.id + "&UserId=" + u.id + ">" + u.getIdHash() + "</a></td><td align=center>" + (s.numberOfAttempts>0?String.format("%.0f", pct) + "%":"-") + "</td><td align=center>" + s.numberOfAttempts + "</td><td>" + (s.mostRecentAttempt==null?"":df.format(s.mostRecentAttempt)) + "</td></tr>");
				}
				buf.append("</table><br>" + counter + " student" + (counter!=1?"s":""));
			}

		} catch (Exception e) {
		}
		return buf.toString();
	}
}