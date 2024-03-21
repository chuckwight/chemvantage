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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/* 
 * Access to this servlet is restricted to ChemVantage admin users and the project service account
 * by specifying login: admin in a url handler of the project app.yaml file
 */
@WebServlet("/Admin")
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
				if (Subject.projectId.equals("chem-vantage-hrd")) {
					String msg = "<b>Your Quarterly OpenStax Ally Partner Report Is Ready</b>"
							+ "<ol><li>Download the CSV file <a href=https://www.chemvantage.org/Admin?UserRequest=OpenStaxCSVReport>here</a></li>"
							+ "<li>Import the CSV file to cell B3 of the Excel template file at Drive -> ChemVantage LLC-> OpenStax Partnership -> Quarterly Reports</li>"
							+ "<li>Review the file, including any carry-forward amounts from previous quarter (up to $500.)</li>"
							+ "<li>Save and upload the file to the <a href=https://openstax-community.force.com/partnerportal>OpenStax Partner Portal</a></li></ol>";
					Utilities.sendEmail("ChemVantage","admin@chemvantage.org","OpenStax Quarterly Report", msg);
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
					int price = Integer.parseInt(request.getParameter("Price"));
					if (org == null) throw new Exception();
					List<Voucher> newVouchers = new ArrayList<Voucher>();
					for (int i=0; i<n; i++) newVouchers.add(new Voucher(org,price));
					ofy().save().entities(newVouchers).now();
				} catch (Exception e) {}
				break;
			}
			out.println(Subject.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Subject.footer);
		} catch (Exception e) {
			response.getWriter().println("Unexpected error: " + e.toString() + e.getMessage());
		}
	}

	private String mainAdminForm(User user,String userRequest,String searchString,String cursor) {
		StringBuffer buf = new StringBuffer("<h1>Administration</h1>");
		try {
			// Announcements
			buf.append("<h2>Announcements</h2>");
			buf.append("The following message will be posted in red font at the top of each main page: ");
			
			buf.append("<FORM ACTION=Admin METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=Announce>"
					+ "<INPUT TYPE=TEXT SIZE=80 NAME=Announcement VALUE='" + Subject.getAnnouncement() + "'><BR>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=SUBMIT VALUE='Post this message now'></FORM>");

			// User Feedback
			int nUserReports = ofy().load().type(UserReport.class).count();
			if (nUserReports > 0)  {
				buf.append("<h2>User Feedback</h2>"
						+ "You have " + nUserReports + " new user feedback reports.");
			}

			// Item Bank Requests
			List<Contact> contacts = ofy().load().type(Contact.class).filter("role","applicant").list();
			if (contacts.size() > 0) {
				buf.append("<h2>Requests for Access to the Item Bank</h2>");
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
			if (nPending > 0) buf.append("<h2>Contributed Questions</h2>"
					+ "<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a>");
			
			// Recent Activity
			buf.append("<h2>Recent Activity (past 30 days)</h2>");			
			if ("ShowGroupEnrollments".equals(userRequest)) buf.append(Group.enrollmentReport());
			else {
				Date oneMonthAgo = new Date(new Date().getTime()-2592000000L);			
				buf.append("Active LTI Advantage deployments: " + ofy().load().type(Deployment.class).filter("lastLogin >",oneMonthAgo).count() 
					+ " <a href=/Admin?UserRequest=ShowGroupEnrollments>show details</a><br/>");			
				int hwt = ofy().load().type(HWTransaction.class).filter("graded >",oneMonthAgo).count();
				int qzt = ofy().load().type(QuizTransaction.class).filter("graded >",oneMonthAgo).count();
				int stt = ofy().load().type(STTransaction.class).filter("graded >",oneMonthAgo).count();
				int pet = ofy().load().type(PracticeExamTransaction.class).filter("graded >",oneMonthAgo).count();
				int plt = ofy().load().type(PlacementExamTransaction.class).filter("graded >",oneMonthAgo).count();
				int vdt = ofy().load().type(VideoTransaction.class).filter("graded >",oneMonthAgo).count();
				int pot = ofy().load().type(PollTransaction.class).filter("completed >",oneMonthAgo).count();
				buf.append("Total number of transactions: " + (hwt+qzt+stt+pet+plt+vdt+pot) + " "
						+ "<a href=# onclick=document.getElementById('transdetail').style.display=''>show details</a><br/>"
						+ "<div id=transdetail style='display:none;'>"
						+ "Homework: " +  hwt + "<br/>"
						+ "Quiz: " + qzt + "<br/>"
						+ "SmartText: " + stt + "<br/>"
						+ "Practice Exam: " + pet + "<br/>"
						+ "Placement Exam: " + plt + "<br/>"
						+ "Video: " + vdt + "<br/>"
						+ "Poll: " + pot
						+ "</div>");
			}
			
			// New Accounts
			List<Deployment> review = ofy().load().type(Deployment.class).filter("status", "pending").list();
			if (review.size() > 0) buf.append("<h2>Accounts Needing Review and Approval</h2>");
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
			buf.append("<h2>Quarterly OpenStax Ally Partner Report</h2>"
					+ "<a href=/Admin?UserRequest=OpenStaxReport>Preview</a> or <a href=/Admin?UserRequest=OpenStaxCSVReport>Download CSV File</a>");
			
			// Create subscription vouchers
			buf.append("<h2>1-Year Subscription Vouchers</h2>");
			List<Voucher> vouchers = ofy().load().type(Voucher.class).filter("activated =",null).order("-purchased").list();
			
			if ("Create Vouchers".equals(userRequest)) {
				Date fiveSecondsAgo = new Date(new Date().getTime()-5000L);
				buf.append("<b>New Voucher Codes - these will only be displayed once</b>:<br/>");
				DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
				buf.append("<table><tr><th>Organization</th><th>Purchased</th><th>Paid</th><th>Months</th><th>Code</th></th>");
				for (Voucher v : vouchers) {
					if (v.purchased.before(fiveSecondsAgo)) break;
					buf.append("<tr>"
							+ "<td style='text-align:center'>" + v.org + "</td>"
							+ "<td style='text-align:center'>" + df.format(v.purchased) + "</td>"
							+ "<td style='text-align:center'>$" + v.paid + "</td>"
							+ "<td style='text-align:center'>" + v.months + "</td>"
							+ "<td style='text-align:center'>" + v.code + "</td>"
							+ "</tr>");
				}
				buf.append("</table><br/>");
			} else {
				buf.append("<b>There are " + vouchers.size() + " unclaimed vouchers.</b><br/>");
				if (vouchers.size()>0) {
					Map<String,Integer> voucherCounts = new HashMap<String,Integer>();
					for (Voucher v : vouchers) { // increment count for this org
						voucherCounts.put(v.org, voucherCounts.containsKey(v.org)?voucherCounts.get(v.org)+1:1);
					}
					buf.append("<table><tr><th>Organization</th><th>Vouchers</th></th>");
					for (Entry<String,Integer> e : voucherCounts.entrySet()) {
						buf.append("<tr><td>" + e.getKey() + "</td><td style='text-align:center'>" + e.getValue() + "</td></tr>");
					}
				}
				buf.append("</table><br/>");
			}

			buf.append("<form method=post>");
			buf.append("<input type=hidden name=UserRequest value='Create Vouchers' />");
			buf.append("Create <input type=text size=3 name=NVouchers value=0 /> new vouchers for "
					+ "<input type=text name=Organization placeholder=organization /> at "
					+ "$<input type=text size=3 name=Price value=16> each. "
					+ "<input type=submit value='Show Codes' />");
			buf.append("</form>");
			// Signature Code
			buf.append("<h2>Signature Code for 1 month Anonymous Access: " + Long.toHexString(User.encode(new Date(new Date().getTime() + 2678400000L).getTime())) + "</h2>");	
			buf.append("<h2>Signature Code for 1 year Anonymous Access: " + Long.toHexString(User.encode(new Date(new Date().getTime() + 31536000000L).getTime())) + "</h2>");	
		
		} catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}
}

