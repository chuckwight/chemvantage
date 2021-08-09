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
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet("/Logout")
public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		String sig = request.getParameter("sig");
		if (sig!=null) try {
			ofy().delete().key(Key.create(User.class, Long.parseLong(sig))).now();
		} catch (Exception e) {}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		out.println(Home.header("ChemVantage Logout Successful") 
				+ "<h3>You have successfully signed out of ChemVantage</h3>" 
				+ "If this happened unexpectedly, it is likely that your browser's web "
				+ "session timed out after a period of inactivity, or the access token "
				+ "exchanged between your learning management system (LMS) and ChemVantage "
				+ "has expired (after a period of typically 90 minutes)."
				+ "<p>"
				+ "Session expired: " + User.decode(sig) + "<br/><br/>"
				+ "You can activate a new session and token by returning to your learning "
				+ "management system (LMS) and clicking the link for any assignment there.<p>"
				+ "If you are having technical difficulty using ChemVantage, <a href=Feedback>"
				+ "please tell us</a> so we can fix the problem."
		
				+ Home.footer);
	}
}