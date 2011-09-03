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
import java.text.DateFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class Activate extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet allows the user to upgrade to a premium level account.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This method receives a coded URL to activate a demo premium user account
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			String userId = request.getParameter("UserId");
			String code = request.getParameter("Code");
			if (userId.isEmpty() || code.isEmpty()) throw new Exception();
			User user = ofy.get(User.class,userId);
			out.println(Login.header + activated(user,code) + Login.footer);
		} catch (Exception e) {	
			out.println(Login.header + "<h3>Activation Failed</h3>" + Login.footer);
		}
	}
	
	String activated(User user,String code) {
		try {
			DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
			boolean validCode = (Integer.parseInt(code) == new Key<User>(User.class,user.id).hashCode());
			if (!validCode) return "<h3>Activation Failed</h3>Sorry, the URL is invalid.<br>"
			+ "Please unsure that you copied the entire URL, including any portion that may "
			+ "have wrapped to a new line in your email message.";

			DemoPremiumAccount demo = ofy.find(DemoPremiumAccount.class,user.id);
			if (demo != null) return "<h3>Activation Failed</h3>Sorry, this link has already been used.";

			if (user.hasPremiumAccount()) return "<h3>Activation Failed</h3>Sorry, the link cannot be used because the user already has a premium account.";

			// everything looks OK; activate the demo premium account:
			DemoPremiumAccount upgrade = new DemoPremiumAccount(user.id);
			ofy.put(upgrade);
			user.setPremium(true);
			ofy.put(user);

			return "<h3>Thank you</h3>Your ChemVantage account has been upgraded to premium status.<p>"
			+ "To get email or SMS/text message notifications of assignment deadlines, "
			+ "please set your preferences on the ChemVantage 'View My Scores' page.<p>"
			+ "Your free trial will expire on " + df.format(upgrade.endDate) + "<p>"
			+ "Please send any questions or concerns to admin@chemvantage.org";
		} catch (Exception e) {
			return "<h3>Activation Failed</h3>Sorry, the demo premium activation failed due to an unexpected error." + e.toString();
		}
	}	
}
