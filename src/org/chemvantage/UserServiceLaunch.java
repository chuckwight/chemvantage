/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*	This servlet file is adapted from an open-source Java servlet 
*	LTIProviderServlet written by Charles Severance at imsglobal.org
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
import java.util.Random;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Objectify;

public class UserServiceLaunch extends HttpServlet {

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	private static final long serialVersionUID = 137L;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		HttpSession session = request.getSession();
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			if (userId == null) response.sendRedirect("/");
			try { // check to see if this user is in the datastore...
				ofy.get(User.class,userId);
			} catch (Exception e2) { // ...and if not, create a new record.
				User.createUserServiceUser(userService.getCurrentUser());
			}
			session.setAttribute("UserId", userId);
			
			// try to set a Cookie with the user's ID provider:
			String authDomain = userService.getCurrentUser().getAuthDomain();
			String providerName = "";
			if (authDomain.equals("gmail.com")) providerName = "Google";
			else for (String p : Login.openIdProviders.keySet()) {
				if (authDomain.contains(p.toLowerCase())) {
					providerName = p; break;
				}
			}
			Cookie c = new Cookie("IDProvider",providerName);
			c.setMaxAge(2592000); // expires after 30 days (in seconds)
			response.addCookie(c);
			
			response.sendRedirect("/Home?r=" + new Random().nextInt(9999));
		} catch (Exception e) {
			session.invalidate();
			response.sendRedirect("/");
		}
	}
}
