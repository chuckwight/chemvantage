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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	static final Map<String, String> openIdProviders;
    static final Map<String, String> openIdLogos;
    static {
		openIdProviders = new HashMap<String, String>();
    	openIdLogos = new HashMap<String, String>();
    	
        openIdProviders.put("Google", "google.com"); openIdLogos.put("Google", "/images/openid/google+.jpg");
        openIdProviders.put("Yahoo", "yahoo.com"); openIdLogos.put("Yahoo", "/images/openid/yahoo.jpg");
        openIdProviders.put("AOL", "aol.com"); openIdLogos.put("AOL", "/images/openid/aol.jpg");
    }
    
	
	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Login.header + logoutPage(request,response) + Login.footer);
	}
	
	String logoutPage(HttpServletRequest request,HttpServletResponse response) {
		StringBuffer buf = new StringBuffer("<h2>You have successfully signed out of ChemVantage</h2>");
		try {
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) user = Nonce.getUser(request.getParameter("Nonce"));
			else user = User.getInstance(session);
			
			session.invalidate();  // now the user is logged out of ChemVantage

			if (user==null && !request.isSecure()) {
				buf.append("If you were signed out unexpectedly, the most likely reason is because you are using a plain-text (http) URL to "
						+ "access ChemVantage inside a frame that uses a secure (https) URL. The best way to fix this is to make certain that your "
						+ "learning management system is configured to launch Chemvantage using the URL https://" + request.getServerName() + "/lti<p>");
			} else if (user==null) {
				buf.append("If you were signed out unexpectedly, it is almost certainly because your browser "
						+ "did not persist your session. ChemVantage uses the session ID value to keep you logged in from one page "
						+ "to the next. The most common session failure occurs when ChemVantage is displayed inside a frame (e.g., "
						+ "when using a learning management system). Here are a few ways to solve the problem:<ul>"
						+ "<li>If you disabled cookies in your browser, you must enable them for the domain " + request.getServerName()
						+ "<li>In your browser settings you may be able to tell the browser to trust the domain " + request.getServerName()
						+ "<li>Ask your instructor to configure the assignment in the LMS to present ChemVantage pages in a new window"
						+ "<li>If you have a choice of browsers, avoid Internet Explorer and use Safari (better), Firefox (better) or Chrome (best for maintaining sessions)"
						+ "<li>Ensure that both the LMS and ChemVantage URLs are configured to use secure (https) encryption"
						+ "<li>When you click on a ChemVantage link, open the page in a new window (e.g., in Safari, use Command-click to open the page)</ul><p>");
			} else if ("Google".equals(user.authDomain)) {
				buf.append("If you are at a public computer, you must do 2 more things to protect your online identity:<ol>"
						+ "<li>Visit your identity provider's site below to sign out there."
						+ "<li>Shut down this browser completely to destroy any temporary cookies."
						+ "</ol>");
				buf.append("<center><a href='http://google.com'>"
						+ "<img src='/images/google.png' border=0 alt='google.com'><br/>" 
						+ "google.com</a></center>");
			} else buf.append("If you are at a public computer, please shut down this browser completely to protect your online identity.<p>");

			if (!"BLTI".equals(user.authDomain)) buf.append("<p><a href=/ target=_top>Return to the ChemVantage sign in page</a>.");

		} catch (Exception e) {
			request.getSession().invalidate();
		}
		return buf.toString();
	}
}