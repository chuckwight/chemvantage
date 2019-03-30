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
import javax.servlet.http.HttpSession;

public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		try {
			HttpSession session = request.getSession();
			session.invalidate();  // now the user is logged out of ChemVantage
		} catch (Exception e) {}
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("<h2>You have successfully signed out of ChemVantage</h2>" + Login.footer);
	}
}