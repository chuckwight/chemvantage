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
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;

public class CASLaunch extends HttpServlet {

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
		doPost(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
			String ticket = request.getParameter("ticket");
			if (ticket==null || ticket.isEmpty()) return;

			String parameters = "service=" + URLEncoder.encode("http://" + request.getServerName() + "/cas","UTF-8") + "&ticket=" + URLEncoder.encode(ticket,"UTF-8");
			URL u = new URL("https://ulogin.utah.edu/cas/validate");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("GET");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			OutputStreamWriter outStream = new OutputStreamWriter(uc.getOutputStream());
			outStream.write(parameters);
			outStream.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			boolean validated = in.readLine().equals("yes");
			
			String userId = null;
			if (validated) userId = in.readLine();
			in.close();
			
			if (userId==null || userId.isEmpty()) {
				response.sendRedirect(request.getServerName());
				return;
			}
			// After this point, the CAS launch was validated successfully. 

			// Provision a new user account if necessary, and store the userId in the user's session
			User user = ofy.find(User.class,userId);
			if (user==null) user = new User(userId);
			user.authDomain = "https://ulogin.utah.edu/cas";
			if (!user.requiresUpdates()) user.lastLogin = new Date();
			ofy.put(user);

			request.getSession(true).setAttribute("UserId",userId);

			// Redirect the user's browser to the ChemVantage Home page
			response.sendRedirect("/Home");
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