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
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
			String userId = "admin";
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			switch (userRequest) {
			case "OpenStaxReport":
				out.println(Subject.header() + Group.openStaxReport() + Subject.footer);
				break;
			case "OpenStaxCSVReport":
				response.setContentType("text/csv");
				out.println(Group.openStaxCSVReport());
				break;
			default: 
				out.println(Subject.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Subject.footer);
			}
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			String userId = "admin";
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			
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
			case "OpenStaxReport":  // for monthly cron job
				String project_id = System.getProperty("com.google.appengine.application.id");
				if (project_id.equals("chem-vantage-hrd")) {
					String msg = "<h3>Your Quarterly OpenStax Ally Partner Report Is Ready</h3>"
							+ "<ol><li>Download the CSV file <a href=https://www.chemvantage.org/Admin?UserRequest=OpenStaxCSVReport>here</a></li>"
							+ "<li>Import the CSV file to cell B3 of the Excel template file at Drive -> ChemVantage LLC-> OpenStax Partnership -> Quarterly Reports</li>"
							+ "<li>Review the file, including any carry-forward amounts from previous quarter (up to $500.)</li>"
							+ "<li>Save and upload the file to the <a href=https://openstax-community.force.com/partnerportal>OpenStax Partner Portal</a></li></ol>";
					LTIMessage.sendEmailToAdmin("OpenStax Quarterly Report", msg);
				}
				return;
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
			case "Create Vouchers":
				try {
					int n = Integer.parseInt(request.getParameter("NVouchers"));
					String org = request.getParameter("Organization");
					if (org == null) throw new Exception();
					for (int i=0; i<n; i++) new Voucher(org);
				} catch (Exception e) {}
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
			List<Contact> contacts = ofy().load().type(Contact.class).filter("role","applicant").list();
			if (contacts.size() > 0) {
				buf.append("<h3>Requests for Access to the Item Bank</h3>");
				buf.append("<ul>");
				for (Contact c : contacts) {
					buf.append("<li>" + c.getFullName() + " (" + c.getEmail() + ") at " + c.institution
							+ "<form method=post action=/itembank>"
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
			
			// OpenStax
			buf.append("<h3>Quarterly OpenStax Ally Partner Report</h3>"
					+ "<a href=/Admin?UserRequest=OpenStaxReport>Preview</a> or <a href=/Admin?UserRequest=OpenStaxCSVReport>Download CSV File</a>");
			
			// Create subscription vouchers
			buf.append("<h3>1-Year Subscription Vouchers</h3>");
			
			if ("Create Vouchers".equals(userRequest)) {
				buf.append("Unclaimed Vouchers:<br/>");
				DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
				List<Voucher> vouchers = ofy().load().type(Voucher.class).filter("activated =",null).order("-purchased").list();
				if (vouchers.size()>0) {
					buf.append("<table><tr><th>Org</th><th>Purchased</th><th>Paid</th><th>Months</th><th>Code</th></th>");
					for (Voucher v : vouchers) buf.append("<tr>"
							+ "<td style='text-align:center'>" + v.org + "</td>"
							+ "<td style='text-align:center'>" + df.format(v.purchased) + "</td>"
							+ "<td style='text-align:center'>$" + v.paid + "</td>"
							+ "<td style='text-align:center'>" + v.months + "</td>"
							+ "<td style='text-align:center'>" + v.code + "</td>"
							+ "</tr>");
					buf.append("</table>");
				}
			}
			
			buf.append("<form method=post>");
			buf.append("<input type=hidden name=UserRequest value='Create Vouchers' />");
			buf.append("Create <input type=text size=3 name=NVouchers value=0 /> new vouchers for <input type=text name=Organization placeholder=organization /> <input type=submit value='Show Codes' />");
			buf.append("</form>");
			// Signature Code
			buf.append("<h3>Signature Code for 1 month Anonymous Access: " + Long.toHexString(User.encode(new Date(new Date().getTime() + 2678400000L).getTime())) + "</h3>");	
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}
}

