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
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/Logout")
public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
/*		
		try {
			request.getSession().invalidate();
			User.getUser(request.getParameter("CvsToken")).destroyCvsToken();
		} catch (Exception e) {}
*/		
		out.println(Home.header("ChemVantage Logout Successful") 
				+ "<h3>You have successfully signed out of ChemVantage</h3>" 
				+ "If this happened unexpectedly, it is likely that your browser's web "
				+ "session timed out after a period of inactivity, or the access token "
				+ "exchanged between your learning management system (LMS) and ChemVantage "
				+ "has expired (after a period of typically 90 minutes)."
				+ "<p>"
				+ "You can activate a new session and token by returning to your learning "
				+ "management system (LMS) and clicking the link for any assignment there."
				+ "<p>"
				+ "If you are seeing this page on every launch to ChemVantage, it's likely "
				+ "that your browser is preventing cross-site tracking. There are 2 ways to "
				+ "fix this:<ol><li>Allow this in your browser, e.g. in Safari go to "
				+ "Safari | Preferences | Privacy | uncheck Website Tracking<li>Ask your "
				+ "instructor to configure the assignment in your LMS to launch the assignment "
				+ "in a new browser tab or window</ol>"
				+ Home.footer);
	}
}