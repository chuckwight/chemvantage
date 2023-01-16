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
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Admin")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class Admin extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet is used by ChemVantage admins to manage user properties and roles.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			//user.setToken();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			out.println(Subject.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Subject.footer);
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			//user.setToken();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "Announce": 
				Subject.setAnnouncement(request.getParameter("Announcement"));
				break;
			case "Submit Review":
				Deployment d = ofy().load().type(Deployment.class).id(request.getParameter("platform_deployment_id")).safe();
				switch(request.getParameter("action")) {
				case "Approve":
					d.status = "approved";
					ofy().save().entity(d);
					break;
				case "Block":
					d.status = "blocked";
					ofy().save().entity(d);
					break;
				case "Delete":
					ofy().delete().entity(d);
				}
				break;
			}
			out.println(Subject.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Subject.footer);
		} catch (Exception e) {
			response.getWriter().println("Unexpected error: " + e.toString() + e.getMessage());
		}
	}

	String mainAdminForm(User user,String userRequest,String searchString,String cursor) {
		StringBuffer buf = new StringBuffer("\n\n<h2>Administration</h2>");
		try {
			// Announcements
			buf.append("<h3>Announcements</h3>");
			buf.append("The following message will be posted in red font at the top of each main page: ");
			
			buf.append("<FORM ACTION=Admin METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=Announce>"
					+ "<INPUT TYPE=TEXT SIZE=80 NAME=Announcement VALUE='" + Subject.getAnnouncement() + "'><BR>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=SUBMIT VALUE='Post this message now'></FORM>");

			// User Feedback
			Query<UserReport> reports = ofy().load().type(UserReport.class).order("-submitted");
			if (reports.count() > 0)  {
				buf.append("<h3>User Feedback</h3>");
				for (UserReport r : reports) {
					buf.append(r.view(user) + "<hr>");  // returns report only for ChemVantage admin
				}
			}

			// Item Bank Requests
			List<Contact> contacts = ofy().load().type(Contact.class).filter("vetted",false).list();
			if (contacts.size() > 0) {
				buf.append("<h3>Requests for Access to the Item Bank</h3>");
				buf.append("<ul>");
				for (Contact c : contacts) {
					buf.append("<li>" + c.getFullName() + " (" + c.getEmail() + ") at " + c.institution
							+ "<form method=post action=/items>"
							+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
							+ "<input type=hidden name=Email value='" + c.getEmail() + "' />"
							+ "<input type=submit name=UserRequest value=Approve /> "
							+ "<input type=submit name=UserRequest value=Deny />"
							+ "</form>"
							+ "</li>");
				}
				buf.append("</ul>");
			}
			
			// Contributed Questions
			int nPending = ofy().load().type(ProposedQuestion.class).count();
			if (nPending > 0) buf.append("<h3>Contributed Questions</h3>"
					+ "<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a>");
			
			// Recent Activity
			buf.append("<h3>Recent Activity (past 30 days)</h3>");			
			if ("ShowGroupEnrollments".equals(userRequest)) buf.append(Group.enrollmentReport());
			else {
				Date lastMonth = new Date(new Date().getTime()-2592000000L);			
				buf.append("Active LTI Advantage deployments: " + ofy().load().type(Deployment.class).filter("lastLogin >",lastMonth).count() 
					+ " <a href=/Admin?UserRequest=ShowGroupEnrollments>show details</a><br/>");			
				buf.append("Total number of Response entities: " + ofy().load().type(Response.class).filter("submitted >",lastMonth).count());
			}
			
			// New Accounts
			List<Deployment> review = ofy().load().type(Deployment.class).filter("status", "pending").list();
			if (review.size() > 0) buf.append("<h3>Accounts Needing Review and Approval</h3>");
			for (Deployment d : review) {
				buf.append("<form method=post><input type=hidden name=UserRequest value='Submit Review'/><input type=hidden name=sig value='" + user.getTokenSignature() + "'/>"
						+ "<input type=hidden name=platform_deployment_id value='" + d.platform_deployment_id + "'/>"
						+ d.platform_deployment_id + " (" + d.lms_type + ")<br/>"
						+ "by " + d.contact_name + " (" + d.email + ") at " + d.organization + " (" + d.org_url + ").<br/>");
				int nAssignments = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).count();
				buf.append("Assignments: " + nAssignments + ".<br/>"
						+ "<input type=submit name=action value='Approve'/>&nbsp;"
						+ "<input type=submit name=action value='Block'/>&nbsp;"
						+ "<input type=submit name=action value='Delete'/></form><br/>");
			}
			
			buf.append("<h3>Signature Code for 1 month Anonymous Access: " + Long.toHexString(User.encode(new Date(new Date().getTime() + 2678400000L).getTime())) + "</h3>");	
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}
}

