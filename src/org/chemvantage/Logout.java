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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		request.getSession().invalidate();  // now the user is logged out of ChemVantage
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.header 
				+ "<h3>You have successfully signed out of ChemVantage</h3>" 
				+ "If this happened unexpectedly, it might have been caused by using your browser's BACK button while "
				+ "viewing ChemVantage in a frame contained by your learning management system (LMS). Some browsers do not permit "
				+ "using session variables in frames, and therefore do not support backward navigation.<p>"
				+ "You can avoid this either by using a browser that supports session variables in frames (e.g., Chrome), or by "
				+ "configuring your LMS to display third-party apps in a separate window."
				+ Home.footer);
	}
}