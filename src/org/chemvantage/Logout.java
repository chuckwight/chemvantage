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

import com.google.appengine.api.users.UserServiceFactory;

public class Logout extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	static final Map<String, String> openIdProviders;
    static final Map<String, String> openIdLogos;
    static final Map<String, String> casProviders;
    static final Map<String, String> casLogos;
	static {
		openIdProviders = new HashMap<String, String>();
    	openIdLogos = new HashMap<String, String>();
    	casProviders = new HashMap<String, String>();
    	casLogos = new HashMap<String, String>();
    	
        openIdProviders.put("Google", "google.com"); openIdLogos.put("Google", "/images/openid/google.jpg");
        openIdProviders.put("Yahoo", "yahoo.com"); openIdLogos.put("Yahoo", "/images/openid/yahoo.jpg");
        openIdProviders.put("AOL", "aol.com"); openIdLogos.put("AOL", "/images/openid/aol.jpg");
        openIdProviders.put("MyOpenID", "myopenid.com"); openIdLogos.put("MyOpenID", "/images/openid/myopenid.jpg");
        casProviders.put("Utah.edu", "https://ulogin.utah.edu/cas/"); casLogos.put("Utah.edu", "/images/openid/utah.jpg");
	}
    
	
	public String getServletInfo() {
		return "Servlet for confirming logout from ChemVantage.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		request.getSession().invalidate();
		User user = User.getInstance(request.getSession());
		if (user != null && request.getParameter("try")==null) {  // prevents infinite loop of login failures
			response.sendRedirect(UserServiceFactory.getUserService().createLogoutURL("/Logout?try=again",user.authDomain));
		}
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Login.header + logoutPage(request) + Login.footer);
	}
	
	String logoutPage(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		User user = User.getInstance(request.getSession());
		if (user==null) buf.append("<h2>You have successfully signed out of ChemVantage</h2>");
		else {
			buf.append("<h2>ChemVantage Logout Failed!</h2>" + user.getBothNames() + "<br>" + user.email + "<br>" + user.authDomain + "<p>");
		}
		
		buf.append("If you are at a public computer, you must do 2 more things to protect your online identity:<ol>"
				+ "<li>Visit your identity provider's site to sign out there. (ChemVantage cannot do this for you.)"
				+ "<li>Shut down this browser completely (don't just close the page) to destroy temporary cookies."
				+ "</OL>");
	
		buf.append("<CENTER><TABLE CELLSPACING=20><TR>");
		Cookie[] cookies = request.getCookies();
		String providerName = null;
		for (Cookie c : cookies) if ("IDProvider".equals(c.getName())) providerName = c.getValue();
		
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
				providerUrl = casProviders.get(providerName);
				providerLogo = casLogos.get(providerName);
				buf.append("<TD style='text-align:center'><a href='" + providerUrl + "'>"
						+ "<img src='" + providerLogo + "' border=0 alt='" + providerUrl + "'><br/>" 
						+ providerUrl + "</a></TD>");			
			}
		} else { // present links to all public OpenID providers
			for (String p : openIdProviders.keySet()) {
				String providerUrl = openIdProviders.get(p);
				buf.append("<TD style='text-align:center'><a href='http://" + providerUrl + "'>"
						+ "<img src='" + openIdLogos.get(providerName) + "' border=0 alt='" + providerUrl + "'><br/>" 
						+ providerUrl + "</a></TD>");
			}
		}
		buf.append("</TR></TABLE></CENTER><p>");

		buf.append("<a href=/>Return to the ChemVantage sign in page</a>.");

		return buf.toString();
	}
}