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

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;

public class ResponseServlet extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Objectify ofy = new DAO().ofy();
	
	public String getServletInfo() {
		return "This admin servlet creates and stores a single instance of a Response object (normally called from a Task queue).";
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			Response r = new Response(
					request.getParameter("AssignmentType"),
					Long.parseLong(request.getParameter("TopicId")),
					Long.parseLong(request.getParameter("QuestionId")),
					request.getParameter("StudentResponse"),
					request.getParameter("CorrectAnswer"),
					Integer.parseInt(request.getParameter("Score")),
					Integer.parseInt(request.getParameter("PossibleScore")),
					request.getParameter("UserId"),
					new Date());
			ofy.put(r);		
		} catch (Exception e) {
		}
	}
}
