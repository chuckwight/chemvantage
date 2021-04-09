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
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			user.setToken();
			
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
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			if (!user.isChemVantageAdmin()) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "Announce": 
				subject.announcement = request.getParameter("Announcement");
				ofy().save().entity(subject);
				break;
			case "Update Account":
				BLTIConsumer tc = ofy().load().type(BLTIConsumer.class).id(request.getParameter("ConsumerKey")).safe();
				tc.status = request.getParameter("Status");
				tc.org_type = request.getParameter("OrgType");
				switch (tc.org_type) {
				case "nonprofit": 
					tc.expires = null; 
					break;
				default:
					Date now = new Date();
					Date in10Days = new Date(now.getTime() + 864000000L);
					if (tc.expires==null || tc.expires.after(in10Days)) tc.expires = in10Days;
				}
				ofy().save().entity(tc).now();
				break;
			case "Submit Review":
				Deployment d = ofy().load().type(Deployment.class).id(request.getParameter("platform_deployment_id")).safe();
				switch(request.getParameter("action")) {
				case "Approve":
					d.status = "approved";  //"pending";
					ofy().save().entity(d);
					LTIRegistration.sendApprovalEmail(d);
					break;
				case "Block":
					d.status = "blocked";
					ofy().save().entity(d);
				case "Delete":
					ofy().delete().entity(d);
				}
				break;
			}
			out.println(Home.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Home.footer);
		} catch (Exception e) {
			response.getWriter().println("Unexpected error: " + e.toString() + e.getMessage());
		}
	}

	String mainAdminForm(User user,String userRequest,String searchString,String cursor) {
		StringBuffer buf = new StringBuffer("\n\n<h2>Administration</h2>");
		try {
			buf.append("<h3>Announcements</h3>");
			buf.append("The following message will be posted in red font at the top of each main page: ");
			
			buf.append("<FORM ACTION=Admin METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=Announce>"
					+ "<INPUT TYPE=TEXT SIZE=80 NAME=Announcement VALUE='" + subject.announcement + "'><BR>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=SUBMIT VALUE='Post this message now'></FORM>");

			buf.append("<h3>User Feedback</h3>");
			Query<UserReport> reports = ofy().load().type(UserReport.class).order("-submitted");
			if (reports.count()==0) buf.append("There are no new user reports at this time.");
			else {
				for (UserReport r : reports) {
					buf.append(r.view(user) + "<hr>");  // returns report only for ChemVantage admin
				}
			}

			buf.append("<h3>Contributed Questions</h3>");
			int nPending = ofy().load().type(ProposedQuestion.class).count();
			if (nPending == 0) buf.append("No questions are pending editorial review.");
			else buf.append("<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a>");
			
			buf.append("<h3>Recent Activity (past 30 days)</h3>");
			
			Date lastMonth = new Date(new Date().getTime()-2592000000L);
			buf.append("Active Basic LTI Consumer accounts: " + ofy().load().type(BLTIConsumer.class).filter("lastLogin >",lastMonth).count());
			if ("showBLTI".equals(userRequest)) {
				buf.append("<ul>");
				List<BLTIConsumer> recentTCs = ofy().load().type(BLTIConsumer.class).filter("lastLogin >",lastMonth).list();
				for (BLTIConsumer c : recentTCs) {
					int resp = ofy().load().type(Response.class).filter("userId >",c.oauth_consumer_key).filter("userId <",c.oauth_consumer_key+"~").count();
					buf.append("<li>" + c.oauth_consumer_key + " generated "+ resp + " responses. Contact: " + c.contact_name + " (" + c.email + ")</li>");
				}
				buf.append("</ul>");
			} else buf.append(" <a href=/Admin?UserRequest=showBLTI>show details</a><br/>");
			
			buf.append("Active LTI Advantage deployments: " + ofy().load().type(Deployment.class).filter("lastLogin >",lastMonth).count() + "<br>");
			buf.append("Total number of Response entities: " + ofy().load().type(Response.class).filter("submitted >",lastMonth).count());
			
			buf.append("<h3>Accounts Needing Review and Approval</h3>");
			List<Deployment> review = ofy().load().type(Deployment.class).filter("status", "review").list();
			for (Deployment d : review) {
				buf.append("<form method=post><input type=hidden name=UserRequest value='Submit Review'/><input type=hidden name=sig value='" + user.getTokenSignature() + "'/>"
						+ "<input type=hidden name=platform_deployment_id value='" + d.platform_deployment_id + "'/>"
						+ d.platform_deployment_id + " (" + d.lms_type + ")<br/>"
						+ "by " + d.contact_name + " (" + d.email + ") at " + d.organization + " (" + d.org_url + ")<br/>"
						+ "<input type=submit name=action value='Approve'/>&nbsp;<input type=submit name=action value='Delete'/></form><br/>");
			}
			
			buf.append("<h3>New and Expiring Accounts</h3>");			
			Date now = new Date();
			Date twoMonthsAgo = new Date(now.getTime()-5184000000L);
			Date twoMonthsFromNow = new Date(now.getTime()+5184000000L);
			List<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class).limit(10).filter("status",null).list();
			consumers.addAll(ofy().load().type(BLTIConsumer.class).filter("expires >",twoMonthsAgo).filter("expires <",twoMonthsFromNow).list());
			
			if (consumers.size()==0) buf.append("none");
			for (BLTIConsumer nc : consumers) {	
				buf.append("<form method=post>"
						+ "<input type=hidden name=ConsumerKey value='" + nc.oauth_consumer_key + "'>"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">");
				if (nc.status==null) buf.append("<input type=checkbox name=Status value=approved checked>Approve ");
				else buf.append("<select name=Status><option value=''>unknown</option>"
						+ "<option value=approved" + ("approved".equals(nc.status)?" selected>":">") + "approved</option>"
						+ "<option value=invoiced" + ("invoiced".equals(nc.status)?" selected>":">") + "invoiced</option>"
						+ "<option value=expired" + (nc.expires != null && nc.expires.before(now)?" selected>":">") + "expired</option></select> ");
				buf.append("Key: " + nc.oauth_consumer_key + " Contact: " + nc.contact_name + " (" + nc.email + ") "
						+ "Org: " + nc.organization + " (<a href='" + nc.org_url + "' target=_blank>" + nc.org_url + "</a>)<br>");
				buf.append("Org Type: <select name=OrgType><option value=''>unknown</option>"
						+ "<option value=nonprofit" + ("nonprofit".equals(nc.org_type)?" selected>":">") + "nonprofit</option>"
						+ "<option value=forprofit" + ("forprofit".equals(nc.org_type)?" selected>":">") + "forprofit</option>"
						+ "<option value=personal" + ("personal".equals(nc.org_type)?" selected>":">") + "personal</option></select> ");
				buf.append("LMSDomain: " + nc.domain + " Created: " + nc.created+ " Expires: " + (nc.expires==null?"never":nc.expires) + "<br>"
						+ "<input type=submit name=UserRequest value='Update Account'></form><br>");
			}

			buf.append("<h3>Basic LTI Consumer Search</h3>");
			int nConsumers = ofy().load().type(BLTIConsumer.class).count();
			
			if (searchString==null || searchString.isEmpty()) searchString = "(show all)";  // default search
			else if (searchString.endsWith("*")) searchString = searchString.substring(0,searchString.length()-1);
			
			buf.append("<FORM NAME=ConsKey ACTION=/Admin METHOD=GET>Use this form below to search for specific LTI consumers.<br>"
					+ "Consumer Key: <INPUT TYPE=TEXT NAME=SearchString VALUE='" + searchString + "' onFocus=ConsKey.oauth_consumer_key.value=''>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Search for Consumer'> "
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "</FORM>");
			
			if ("Search for Consumer".equals(userRequest)) {
				// BLTIConsumers use the oauth_consumer_key as the id value, so we can use a filterKey search
				// To do this, calculate the values of the lowest possible Key and the highest possible Key for the search
				// Note that \u0000 is the lowest value UTF-8 character, and \ufffd is the highest
				Key<BLTIConsumer> keyFirst = Key.create(BLTIConsumer.class,("(show all)".equals(searchString)?"\u0000":searchString));
				Key<BLTIConsumer> keyLast = Key.create(BLTIConsumer.class,("(show all)".equals(searchString)?"\ufffd":searchString+"\ufffd"));					
				Query<BLTIConsumer> qConsumers = ofy().load().type(BLTIConsumer.class).filterKey(">=",keyFirst).filterKey("<",keyLast).limit(this.queryLimit);				
				if (cursor != null) qConsumers = qConsumers.startAt(Cursor.fromUrlSafe(cursor));

				// Determine the number of results to see if we need a cursor to continue the search later
				int nResults = qConsumers.count();				
				if (nResults==0) buf.append("<FONT SIZE=-1>No LTI consumers matched the search criteria.</FONT><p>");
				else {
					buf.append("<FONT SIZE=-1>Showing " + nResults + " LTI consumers matching the search criteria.</FONT><p>");

					buf.append("<TABLE><TR><TH>Consumer Key</TH><TH>Secret</TH></TR>");
					
					QueryResults<BLTIConsumer> iter = qConsumers.iterator();
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

