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

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Logout {

	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	static String now(HttpServletRequest request,Exception exception) {
		StringBuffer buf = new StringBuffer("<h1>Logout</h1><h2>You are logged out of ChemVantage.</h2>");
		buf.append(exception.getMessage()==null?exception.toString():exception.getMessage());
		buf.append("<br/><br/>Most likely, your access token expired after a period of inactivity. You can simply relaunch the "
				+ "assignment from your class learning management system. If you think it's a more serious problem, please take "
				+ "a moment to <a href=/Feedback>leave us feedback</a>. Copy the error message above and tell us what you were "
				+ "trying to do at the time (e.g., download a quiz or submit the answer to a homework problem). We will fix it ASAP.<br/><br/>"
				+ "Thank you<br/><br/>");

		String sig = request.getParameter("sig");
		if (sig != null) try {
			ofy().delete().key(key(User.class, Long.parseLong(sig))).now();
		} catch (Exception e) {}

		return buf.toString();
	}

	static String now(User user) {
		StringBuffer buf = new StringBuffer("<h1>Logout</h1><h2>Sorry, there was an unexpected error.</h2>");
		try {
			ofy().delete().entity(user).now();
		} catch (Exception e) {}
		return buf.toString();
	}

	static String message = Subject.header() 
			+ "<h1>Logout</h1>"
			+ "<h2>You have successfully signed out of ChemVantage</h2>" 
			+ "If this happened unexpectedly, it is likely that your browser's web "
			+ "session timed out after a period of inactivity, or the access token "
			+ "exchanged between your learning management system (LMS) and ChemVantage "
			+ "has expired (after a period of typically 90 minutes)."
			+ "<p>"
			+ "You can activate a new session and token by returning to your learning "
			+ "management system (LMS) and clicking the link for any assignment there.<p>"
			+ "If you are having technical difficulty using ChemVantage, <a href=/Feedback>"
			+ "please tell us</a> so we can fix the problem."	
			+ Subject.footer;
}