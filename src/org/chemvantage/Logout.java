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
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

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
		request.getSession().invalidate();
		UserService userService = UserServiceFactory.getUserService();
		if (userService.isUserLoggedIn()) response.sendRedirect(userService.createLogoutURL("/Logout"));
		else {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.println(Login.header + logoutPage(request) + Login.footer);
		}
	}
	
	String logoutPage(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>You have successfully signed out of ChemVantage</h2>");
		
		Cookie[] cookies = request.getCookies();
		String providerName = "";
		for (Cookie c : cookies) if ("IDProvider".equals(c.getName())) providerName = c.getValue();

		if ("BLTI".equals(providerName)) {
			buf.append("If you are at a public computer, please shut down this browser completely to protect your online identity.<p>"
					+ "To connect to ChemVantage again, click one of the assignment links inside your class learning management system.");
		} else {
			buf.append("If you are at a public computer, you must do 2 more things to protect your online identity:<ol>"
					+ "<li>Visit your identity provider's site below to sign out there."
					+ "<li>Shut down this browser completely to destroy any temporary cookies."
					+ "</OL>");

			buf.append("<CENTER><TABLE CELLSPACING=20><TR>");

			if (providerName != null && !providerName.isEmpty()) {  // ID provider is known from cookie
				String providerUrl=null;
				String providerLogo=null;
				if (Login.openIdProviders.keySet().contains(providerName)) {
					providerUrl = openIdProviders.get(providerName);
					providerLogo = openIdLogos.get(providerName);
					buf.append("<TD style='text-align:center'><a href='http://" + providerUrl + "'>"
							+ "<img src='" + providerLogo + "' border=0 alt='" + providerUrl + "'><br/>" 
							+ providerUrl + "</a></TD>");
				} else if (CASLaunch.casProviders.keySet().contains(providerName)) {
					providerUrl = CASLaunch.casProviders.get(providerName) + "/logout";
					providerLogo = CASLaunch.casLogos.get(providerName);
					buf.append("<TD style='text-align:center'><a href='" + providerUrl + "'>"
							+ "<img src='" + providerLogo + "' border=0 alt='" + providerUrl + "'><br/>" 
							+ "CAS Sign Out</a></TD>");			
				} else buf.append("<TD style='text-align:center'><a href='http://google.com'>"
							+ "<img src='/images/openid/google+.jpg' border=0 alt='google.com'><br/>" 
							+ "google.com</a></TD>");
			} else { // present links to all public OpenID providers
				for (String p : openIdProviders.keySet()) {
					String providerUrl = openIdProviders.get(p);
					buf.append("<TD style='text-align:center'><a href='http://" + providerUrl + "'>"
							+ "<img src='" + openIdLogos.get(p) + "' border=0 alt='" + providerUrl + "'><br/>" 
							+ providerUrl + "</a></TD>");
				}
			}
			buf.append("</TR></TABLE></CENTER><p>");		
			buf.append("<a href=/>Return to the ChemVantage sign in page</a>.");
		}
		return buf.toString();
	}
}