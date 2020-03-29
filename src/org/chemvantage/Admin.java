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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.QueryResults;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Admin")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class Admin extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private int queryLimit = 20;
	Subject subject = Subject.getSubject();

	public String getServletInfo() {
		return "This servlet is used by ChemVantage admins to manage user properties and roles.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			User user = new User(userId);
			user.setIsChemVantageAdmin(true);
			user.setToken(0);
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			out.println(Home.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Home.footer);
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("Token")); 
			user.setToken();

			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			if (userRequest.equals("Announce")) {
				Home.announcement = request.getParameter("Announcement");
			} 
			out.println(Home.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	String mainAdminForm(User user,String userRequest,String searchString,String cursor) {
		StringBuffer buf = new StringBuffer("\n\n<h2>Administration</h2>");
		try {
			buf.append("<h3>Announcements</h3>");
			buf.append("The following message will be posted in red font at the top of each page for authenticated users: ");
			
			buf.append("<FORM ACTION=Admin METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=Announce>"
					+ "<INPUT TYPE=TEXT SIZE=80 NAME=Announcement VALUE='" + Home.announcement + "'><BR>"
					+ "<INPUT TYPE=SUBMIT VALUE='Post this message now'></FORM>");

			buf.append("<h3>User Feedback</h3>");
			Query<UserReport> reports = ofy().load().type(UserReport.class).order("-submitted");
			if (reports.count()==0) buf.append("There are no new user reports at this time.");
			else {
				for (UserReport r : reports) {
					buf.append(r.view(user) + "<hr>");  // returns report only for ChemVantage admin
				}
			}

			buf.append("<h3>Recent Activity (past 30 days)</h3>");
			Date lastMonth = new Date(new Date().getTime()-2592000000L);
			List<BLTIConsumer> cons = ofy().load().type(BLTIConsumer.class).filter("lastLogin >",lastMonth).list();
			for (BLTIConsumer c : cons) {
				buf.append(c.oauth_consumer_key + " (created " + c.created + ")<br>");
			}
			List<Deployment> deps = ofy().load().type(Deployment.class).filter("lastLogin >",lastMonth).list();
			for (Deployment d : deps) {
				buf.append(d.platform_deployment_id + " (created " + d.created + ")<br>");
			}
			buf.append("<br>Total number of Response entities: " + ofy().load().type(Response.class).filter("submitted >",lastMonth).count());
			
			buf.append("<h3>Basic LTI Consumer Search</h3>");
			int nConsumers = ofy().load().type(BLTIConsumer.class).count();
			
			if (searchString==null || searchString.isEmpty()) searchString = "(show all)";  // default search
			else if (searchString.endsWith("*")) searchString = searchString.substring(0,searchString.length()-1);
			
			buf.append("<FORM NAME=ConsKey ACTION=/Admin METHOD=GET>Use this form below to search for specific LTI consumers.<br>"
					+ "Consumer Key: <INPUT TYPE=TEXT NAME=SearchString VALUE='" + searchString + "' onFocus=ConsKey.oauth_consumer_key.value=''>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Search for Consumer'> "
					+ "<INPUT TYPE=HIDDEN NAME=Token VALUE=" + user.token + ">"
					+ "</FORM>");
			
			if ("Search for Consumer".equals(userRequest)) {
				// BLTIConsumers use the oauth_consumer_key as the id value, so we can use a filterKey search
				// To do this, calculate the values of the lowest possible Key and the highest possible Key for the search
				// Note that \u0000 is the lowest value UTF-8 character, and \ufffd is the highest
				Key<BLTIConsumer> keyFirst = Key.create(BLTIConsumer.class,("(show all)".equals(searchString)?"\u0000":searchString));
				Key<BLTIConsumer> keyLast = Key.create(BLTIConsumer.class,("(show all)".equals(searchString)?"\ufffd":searchString+"\ufffd"));					
				Query<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class).filterKey(">=",keyFirst).filterKey("<",keyLast).limit(this.queryLimit);				
				if (cursor != null) consumers = consumers.startAt(Cursor.fromUrlSafe(cursor));

				// Determine the number of results to see if we need a cursor to continue the search later
				int nResults = consumers.count();				
				if (nResults==0) buf.append("<FONT SIZE=-1>No LTI consumers matched the search criteria.</FONT><p>");
				else {
					buf.append("<FONT SIZE=-1>Showing " + nResults + " LTI consumers matching the search criteria.</FONT><p>");

					buf.append("<TABLE><TR><TH>Consumer Key</TH><TH>Secret</TH></TR>");
					
					QueryResults<BLTIConsumer> iter = consumers.iterator();
					while (iter.hasNext()) {
					//for (BLTIConsumer c : consumers) {
						BLTIConsumer c = iter.next();
						buf.append("<TR><TD>" + c.oauth_consumer_key + "</TD>");
						buf.append("<TD><INPUT TYPE=BUTTON VALUE='Reveal secret' "
								+ "onClick=javascript:getElementById('" + c.oauth_consumer_key + "').style.display='';this.style.display='none'>"
								+ "<div id='"+ c.oauth_consumer_key + "' style='display: none'>" + c.secret + "</div></TD></TR>");
					}
					buf.append("</TABLE>");
					if (nResults==this.queryLimit) buf.append("<FONT SIZE=-1><a href='/Admin?UserRequest=Search for Consumer&SearchString=" + searchString + "&Cursor=" + iter.getCursorAfter().toUrlSafe() + "'><FONT SIZE=-1>show more consumers</FONT></a><p>");
				}
			} else { // no search is being conducted, so just list the current total number of consumers
				buf.append("<FONT SIZE=-1>There are currently " + nConsumers + " registered LTI consumers.</FONT><p>");
			}
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}

/*
	String groupSelectBox(long myGroupId) {
		StringBuffer buf = new StringBuffer();
		try {
			Query<Group> groups = ofy().load().type(Group.class);
			buf.append("\n<SELECT NAME=GroupId><OPTION VALUE=0>(none)</OPTION>");
			for (Group g : groups) {
				buf.append("\n<OPTION VALUE=" + g.id + (g.id==myGroupId?" SELECTED>":">") 
						+ g.description + "</OPTION>");
			}
			buf.append("</SELECT>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	
	String BLTIConsumerForm() {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h3>Basic LTI Consumers</h3>");
			buf.append("The following is a list of organizations that are permitted to make Basic LTI "
					+ "connections to ChemVantage, usually from within a learning management system. "
					+ "In order to authorize a new LMS to make BLTI launch requests, ChemVantage must provide "
					+ "the LMS administrator with an oauth_consumer_key (a string similar to a domain name like "
					+ "'webct.utah.edu' or 'webct,business.utah.edu') and a shared secret (random string).");
			Query<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class);
			if (consumers.count() == 0) buf.append("(no BLTI consumers have been authorized yet)<p>");
			for (BLTIConsumer c : consumers) {
				buf.append(c.oauth_consumer_key);
				buf.append(" <INPUT TYPE=BUTTON VALUE='Reveal secret' "
				+ "onClick=javascript:getElementById('" + c.oauth_consumer_key + "').style.display='';this.style.display='none'>"
				+ "<div id='"+ c.oauth_consumer_key + "' style='display: none'>" + c.secret + "</div><br>");
			}
			buf.append("<b>Create/Delete BLTI Consumer</b><br><FORM ACTION=Admin METHOD=POST>"
					+ "BLTI oath_consumer_key: <INPUT TYPE=TEXT NAME=oath_consumer_key>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Generate New BLTI Secret'>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete BLTI Consumer'>"
					+ "</FORM>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
*/	
}

