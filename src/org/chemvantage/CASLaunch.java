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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;

public class CASLaunch extends HttpServlet {

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	private static final long serialVersionUID = 137L;
	protected static final Map<String,String> casProviders = new HashMap<String,String>();
	protected static final Map<String,String> casLogos = new HashMap<String,String>();
	static {
        casProviders.put("Utah.edu","https://go.utah.edu/cas");
        casLogos.put("Utah.edu", "/images/openid/utah.jpg");
	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
			String ticket = request.getParameter("ticket");
			if (ticket==null || ticket.isEmpty()) throw new Exception();

			String parameters = "service=" + URLEncoder.encode("https://" + request.getServerName() + "/cas","UTF-8") + "&ticket=" + URLEncoder.encode(ticket,"UTF-8");
			boolean validated = false;
			BufferedReader in = null;
			String authDomain = null;
			for (String p : casProviders.keySet()) {       // cycle through the list of trusted CAS identity providers to validate the CAS authentication ticket
				URL u = new URL(casProviders.get(p) + "/validate"); 
				HttpURLConnection uc = (HttpURLConnection) u.openConnection();
				uc.setDoOutput(true);
				uc.setRequestMethod("GET");
				uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
				OutputStreamWriter outStream = new OutputStreamWriter(uc.getOutputStream());
				outStream.write(parameters);
				outStream.close();

				in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				validated = in.readLine().equals("yes");
				if (validated) {
					authDomain = p;
					break;
				}
			}
			
			String userId = null;
			if (validated) userId = in.readLine().toLowerCase();
			in.close();
			
			if (userId==null || userId.isEmpty()) {
				response.sendRedirect(request.getServerName());
				return;
			}
			// After this point, the CAS launch was validated successfully. 

			// Provision a new user account if necessary, and store the userId in the user's session
			User user = ofy.find(User.class,userId);
			String email = userId + "@" + authDomain.toLowerCase();
			if (user==null) user = User.createCASUser(userId,email,authDomain.toLowerCase().trim());
			
			request.getSession(true).setAttribute("UserId",userId);
			// try to set a Cookie with the user's ID provider:
			Cookie c = new Cookie("IDProvider",authDomain);
			c.setMaxAge(2592000); // expires after 30 days (in seconds)
			response.addCookie(c);
		
			// Redirect the user's browser to the ChemVantage Home page
			response.sendRedirect("http://" + request.getServerName() + "/Home");
		} catch (Exception e) {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
		    out.println("Sorry, the CAS authentication request could not be completed.");
		}
	}

	@Override
	public void destroy() {

	}

}