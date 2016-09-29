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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

public class UserServiceLaunch extends HttpServlet {

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
			String userId = userService.getCurrentUser().getUserId(); // throws exception if user is not authenticated
			if (ofy().load().type(User.class).id(userId).now()==null) User.createUserServiceUser(userService.getCurrentUser());
			session.setAttribute("UserId", userId);
			User user = User.getInstance(session,false);  // follows the alias chain to the end user; no 2-factor auth required
			if (user.authDomain == null || !user.authDomain.equals("Google")) {
				user.authDomain = "Google";
				ofy().save().entity(user).now();
			}
		
			if (user.use2FactorAuth && session.getAttribute("Code")==null) {  // send 2-factor authentication form
				int code = new Random().nextInt(900000) + 100000;
				if (TwoFactorAuth.sentSMSCode(user, code)) {
					session.setAttribute("ProposedCode", code);
					response.setContentType("text/html");
					PrintWriter out = response.getWriter();
					out.println(Login.header + TwoFactorAuth.verificationForm("/Home") + Login.footer);
					return;
				} else { // text message failed to send; allow user temporary access anyway
					session.setAttribute("Code", 1);
				}
			}
			response.sendRedirect("/Home?r=" + new Random().nextInt(9999));
		} catch (Exception e) {
			session.invalidate();
			response.sendRedirect("/");
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		HttpSession session = request.getSession();
		try {
			int proposedCode = (int)session.getAttribute("ProposedCode");
			int code = Integer.parseInt(request.getParameter("Code"));
			if (proposedCode != code) throw new Exception();
			session.setAttribute("Code", code);
			String returnURL = request.getParameter("ReturnURL");
			if (returnURL==null || returnURL.isEmpty()) returnURL = "/Home";
			response.sendRedirect(returnURL);		
		} catch (Exception e) {
			response.sendRedirect("/");
		}
		
	}
}
