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
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
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
			User user = User.getInstance(session);  // follows the alias chain to the end user
			if (user.authDomain == null || !user.authDomain.equals("Google")) {
				user.authDomain = "Google";
				ofy().save().entity(user).now();
			}
			try {  // two-factor authentication
/*
				if (user.needs2FactorLogin) {
					int code = new Random().nextInt(1000000);
					Message msg = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
					msg.setFrom(new InternetAddress("admin@chemvantage.org","ChemVantage"));
					msg.setSubject("ChemVantage Login");
					msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.smsMessageDevice));
					msg.setText("Your verification code is " + code);
					Transport.send(msg);
				}
			} catch (Exception e2) {}
*/
				if (userService.isUserAdmin()) {
					User chemvantageAdmin = ofy().load().type(User.class).id("110561916370930969984").now();
					Properties props = new Properties();
					Session localSession = Session.getDefaultInstance(props, null);
					Message msg = new MimeMessage(localSession);
					msg.setFrom(new InternetAddress("admin@chemvantage.org","ChemVantage"));
					msg.setSubject("ChemVantage Admin Login");
					msg.setRecipient(Message.RecipientType.TO,new InternetAddress(chemvantageAdmin.smsMessageDevice));
					msg.setText(user.getEmail() + " signed in just now.");
					Transport.send(msg);
				}
			} catch (Exception e2) {}
			response.sendRedirect("/Home?r=" + new Random().nextInt(9999));
		} catch (Exception e) {
			session.invalidate();
			response.sendRedirect("/");
		}
	}
}
