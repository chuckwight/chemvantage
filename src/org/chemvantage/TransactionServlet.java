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

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TransactionServlet extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This admin servlet creates and stores a QuizTransaction or HomeworkTransaction object (normally called from a Task queue).";
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			String assignmentType = request.getParameter("AssignmentType");
			if ("Quiz".equals(assignmentType)) {
				long transactionId = Long.parseLong(request.getParameter("TransactionId"));
				QuizTransaction qt = ofy().load().type(QuizTransaction.class).id(transactionId).safe();
				if ("Download".equals(request.getParameter("Action"))) {
					qt.possibleScore = Integer.parseInt(request.getParameter("PossibleScore"));
					ofy().save().entity(qt);
				} else if ("Graded".equals(request.getParameter("Action"))) {
					qt.score = Integer.parseInt(request.getParameter("Score"));
					qt.graded = new Date();
					ofy().save().entity(qt);
					long myGroupId = Long.parseLong(request.getParameter("GroupId"));
					Assignment a = myGroupId>0?ofy().load().type(Assignment.class).filter("groupId",myGroupId).filter("assignmentType","Quiz").filter("topicId",qt.topicId).first().now():null;
					if (a != null) ofy().save().entity(Score.getInstance(request.getParameter("UserId"),a));
				}
			} else if ("Homework".equals(assignmentType)) {
				HWTransaction ht = new HWTransaction(
						Long.parseLong(request.getParameter("QuestionId")),
						Long.parseLong(request.getParameter("TopicId")),
						request.getParameter("TopicTitle"),
						request.getParameter("UserId"),
						new Date(),  // graded
						0L,          // responseId (not used for now)
						Integer.parseInt(request.getParameter("Score")),
						Integer.parseInt(request.getParameter("PossibleScore")),
						request.getParameter("IPNumber"));
				ofy().save().entity(ht);
				long myGroupId = Long.parseLong(request.getParameter("GroupId"));
				Assignment a = myGroupId>0?ofy().load().type(Assignment.class).filter("groupId",Long.parseLong(request.getParameter("GroupId"))).filter("assignmentType","Homework").filter("topicId",ht.topicId).first().now():null;
				if (a != null) ofy().save().entity(Score.getInstance(request.getParameter("UserId"),a));
			}
		} catch (Exception e) {
		}
	}
}
