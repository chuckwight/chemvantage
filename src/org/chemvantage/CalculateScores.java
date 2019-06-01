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
		try {
			if ("quiz".contentEquals(a.assignmentType)) {
				List<QuizTransaction> quizTransactions = ofy().load().type(QuizTransaction.class).filter("topicId",a.topicId).filter("userId",student.id).order("downloaded").list();
				if (quizTransactions.size()==0) return "This user has no transactions for this assignment in the database.";
				// construct a table of quiz transactions for this user on this topic
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("<h3>Complete list of quiz transactions on " + t.title + " for user " + student.id + "</h3>");
				buf.append("<table><th>Downloaded</th><th>Score</th>");
				for (QuizTransaction qt : quizTransactions) {
					buf.append("<td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":qt.score*100./qt.possibleScore + "%") + "</td>");
				}
				buf.append("</table>");
				buf.append("All times are given in Coordinated Universal Time (UTC). If the score is missing, it means that the quiz was downloaded but not submitted for grading within the 15 minute time limit.");
			}
		} catch (Exception e) {
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
			String name = null;
			int counter = 0;
			if (groupMembers.size()==0) buf.append("There are no students in this group yet.");
			else {
				if (recalculate) {
					buf.append("These scores have been recalculated just now:<p>");
				} else buf.append("In most cases ChemVantage does not store names or other personal identifiable information (PII), "
						+ "so scores are listed only by the user ID supplied by your Learning Management System. If these scores "
						+ "don't look right, you may click any student's Name/UserId below to get a complete list of " + a.assignmentType
						+ " transactions, or <a href=/CalculateScores?UserRequest=Recalculate&AssignmentId=" + a.id + ">"
						+ "click here to recalculate all student scores for this assignment</a> (this may take a few minutes).<p>");
				// print a table of scores for this group assignment
				buf.append("<table cellspacing=0 border=1><tr><th>Name/UserID</th><th>Score</th><th># attempts</th><th>Most Recent Attempt</th></tr>");
				Queue queue = QueueFactory.getDefaultQueue();
				for (User u : groupMembers) {
					counter++;
					s = u.getScore(a);
					if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",u.id));
					name = u.getFullName();
					if (name.isEmpty()) name = u.getId().substring(group.domain.length()+1);
					double pct = s.score*100./s.maxPossibleScore;
					buf.append("<tr><td><a href=/CalculateScores?UserRequest=ViewTransactions&AssignmentId=" + a.groupId + "&UserId=" + u.id + ">" + name + "</a></td><td>" + String.format("%.0f", pct) + "%</td><td>" + s.numberOfAttempts + "</td><td>" + (s.mostRecentAttempt==null?"":df.format(s.mostRecentAttempt)) + "</td></tr>");
				}
				buf.append("</table><br>" + counter + " student" + (counter!=1?"s":""));
			}

		} catch (Exception e) {
		}
		return buf.toString();
	}
}