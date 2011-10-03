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
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserServiceFactory;

public class LoginRequiredServlet extends HttpServlet {

	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "This servlet handles the URI /_ah/login_required and redirects the user to the Google OpenID endpoint for login. Normally, this is only required for accessing servlets restricted to Admin users.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		doPost(request,response);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		Set<String> attributes = new HashSet<String>();
		attributes.add("email");
		String destinationURL = request.getParameter("continue");
		if (destinationURL==null || destinationURL.isEmpty()) destinationURL = "/";
		response.sendRedirect(UserServiceFactory.getUserService().createLoginURL(destinationURL,null,"gmail.com",attributes));
	}

}
