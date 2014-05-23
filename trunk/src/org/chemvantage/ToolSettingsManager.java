/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2014 ChemVantage LLC
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;

public class ToolSettingsManager extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();

	public String getServletInfo() {
		return "This servlet reads and writes settings to the Tool Service embedded in LTI Tool Consumers (learning management systems).";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			StringBuffer buf = new StringBuffer("<h2>LTI Tool Settings Manager</h2>");
			String oauth_consumer_key = request.getParameter("oath_consumer_key");
			if (oauth_consumer_key == null) { // print a form to get the consumer key
				buf.append("Enter the consumer key value for any tool consumer that supports LTI version 2.0 or higher:<br>"
						+ "<form method=GET>Consumer key: <input type=text name=oauth_consumer_key><input type=submit name=UserRequest value='Get Tool Settings'></form>");
			} else {
				buf.append(getToolSettings(oauth_consumer_key));
			}
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
		} catch (Exception e) {}
	}
	
	String getToolSettings(String key) {
		StringBuffer buf = new StringBuffer("Tool settings for oauth_consumer_key " + key + "(JSON format):<p>");
		try {
			BLTIConsumer tc = ofy.get(BLTIConsumer.class,key);
			buf.append(new LTIMessage("GET","","",tc.getToolProxyURL(),key,tc.secret).send());
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
}
