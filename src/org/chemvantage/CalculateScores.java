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
import com.googlecode.objectify.cmd.Query;

public class CalculateScores extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet calculates Score objects for groups as a Task.";
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
		if (!(user.isInstructor() || user.isAdministrator() || user.isChemVantageAdmin())) {
			response.sendRedirect("/Logout");
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		if (request.getParameter("GroupId") != null) {
			try {  // recalculate all of the Score objects for the entire group
				long groupId = Long.parseLong(request.getParameter("GroupId"));
				Query<Assignment> assignments = ofy().load().type(Assignment.class).filter("groupId",groupId);
				Queue queue = QueueFactory.getDefaultQueue();
				for (Assignment a : assignments) queue.add(withUrl("/CalculateScores").param("AssignmentId",Long.toString(a.id)));
				out.println("All group scores are being recalculated as a background task.<p>"
						+ "This could take a few minutes depending on the size of the group and the number of assignments.<p>"
						+ "Please visit each assignment separately to view the scores.");
			} catch (Exception e) {
			}
		} else {  // recalculate Score objects for one assignment
			try {
				long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
				Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).now();
				Group group = ofy().load().type(Group.class).id(assignment.groupId).now();
				group.reviseScores(assignment);

				DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
				Topic t = ofy().load().type(Topic.class).id(assignment.topicId).now();
				StringBuffer buf = new StringBuffer("<h3>" + assignment.assignmentType + " - " + t.title + "</h3>");
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
					buf.append("In most cases ChemVantage does not store names or other personal identifiable information (PII), so scores are listed only by the user ID supplied by your Learning Management System:<p>");
					// print a table of scores for this group assignment
					buf.append("<table cellspacing=0 border=1><tr><th>#</th><th>Name/UserID</th><th>Score</th><th># attempts</th><th>Most Recent Attempt</th></tr>");
					for (User u : groupMembers) {
						counter++;
						s = u.getScore(assignment);
						name = u.getFullName();
						if (name.isEmpty()) name = u.getId().substring(group.domain.length()+1);
						buf.append("<tr><td>" + counter + "</td><td>" + name + "</td><td>" + s.getScore() + "</td><td>" + s.numberOfAttempts + "</td><td>" + (s.mostRecentAttempt==null?"":df.format(s.mostRecentAttempt)) + "</td></tr>");
					}
					buf.append("</table>");
				}

				out.println(Home.header + buf.toString() + Home.footer);
			} catch (Exception e) {
				out.println(e.getMessage());
			}
		}
	}
}