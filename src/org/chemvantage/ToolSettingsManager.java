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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

public class ToolSettingsManager extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet reads and writes settings to the Tool Service embedded in LTI Tool Consumers (learning management systems).";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		try {
			HttpSession session = request.getSession();
			User user = User.getInstance(session);
			if (user==null) {
				response.sendRedirect("/Logout");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			StringBuffer buf = new StringBuffer("<h2>LTI Tool Settings Manager</h2>");
			String oauth_consumer_key = request.getParameter("oauth_consumer_key");
			if (oauth_consumer_key == null) { // print a form to get the consumer key
				buf.append("Enter the consumer key value for any tool consumer that supports LTI version 2.0 or higher:<br>"
						+ "<form method=GET>Consumer key: <input type=text name=oauth_consumer_key><input type=submit name=UserRequest value='Get Tool Settings'></form>");
			} else {
				buf.append(getToolSettings(oauth_consumer_key));
			}
			out.println(Home.getHeader(user) + buf.toString() + Home.footer);
		} catch (Exception e) {}
	}
	
	String getToolSettings(String key) {
		StringBuffer buf = new StringBuffer("Tool settings for oauth_consumer_key " + key + " (JSON format):<p>");
		String response = "";
		try {
			BLTIConsumer tc = ofy().load().type(BLTIConsumer.class).id(key).safe();
			response = new LTIMessage("GET","application/vnd.ims.lti.v2.ToolSettings+json",tc.getToolSettingsURL(),tc).send();
			JSONObject toolSettings = JSONObject.fromObject(response);
			buf.append(toolSettings.toString(3));
		} catch (Exception e) {
			buf.append(e.toString() + "<p>");
			buf.append(response);
		}
		return buf.toString();
	}
}

