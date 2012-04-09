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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class CalculateScores extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

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
		
		try {  // recalculate all of the Score objects for the entire group
			long groupId = Long.parseLong(request.getParameter("GroupId"));
			Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",groupId);
			Queue queue = QueueFactory.getDefaultQueue();
			for (Assignment a : assignments) queue.add(withUrl("/CalculateScores").param("AssignmentId",Long.toString(a.id)));
		} catch (Exception e) {  // recalculate Score objects for one assignment
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment assignment = ofy.get(Assignment.class,assignmentId);
			Group group = ofy.get(Group.class,assignment.groupId);
			group.calculateScores(assignment);
		}
	}
}