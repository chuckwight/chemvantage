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
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.QueryResults;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

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
			if (ofy().load().type(User.class).id(userId).now()==null) User.createUserServiceUser(userService.getCurrentUser());

			HttpSession session = request.getSession();
			session.setAttribute("UserId",userId);

			User user = User.getInstance(session); 
			if (user.authDomain==null || !user.authDomain.equals("Google)")) {
				user.authDomain = "Google";
				ofy().save().entity(user);
			}

			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			out.println(Home.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Home.footer);
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String searchString = request.getParameter("SearchString");
			String cursor = request.getParameter("Cursor");
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			if (userRequest.equals("Announce")) {
				Home.announcement = request.getParameter("Announcement");
			} else if (userRequest.equals("Search for Consumer")) {
				searchString = request.getParameter("oauth_consumer_key");
			} else if (userRequest.equals("Generate New Shared Secret")) {
				createBLTIConsumer(request);
			} else if (userRequest.equals("Delete BLTI Consumer")) {
				deleteBLTIConsumer(request);
			} else if (userRequest.equals("Login As This User")) {
				request.getSession(true).setAttribute("UserId",request.getParameter("UserId"));
				response.sendRedirect("/Home");
				return;
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
					+ " site to prevent logins except by site administrators<br>"
					+ "<INPUT TYPE=SUBMIT VALUE='Post this message now'></FORM>");
			
			// Start user search section for editing user properties
			if ("(show all)".equals(searchString)) searchString = "";
			Query<User> results = null;
			if ("Search for users".equals(userRequest)) {
				searchString = searchString.toLowerCase().trim();
				int i = searchString.indexOf('*');
				if (i == 0) searchString = "";
				else if (i > 0) searchString = searchString.substring(0,i);
				results = searchString.isEmpty()?ofy().load().type(User.class).order("email").limit(this.queryLimit):ofy().load().type(User.class).filter("email >=",searchString).filter("email <",(searchString+'\ufffd')).limit(this.queryLimit);
			}
			
			buf.append("\n<h3>User Search</h3>");
			int nUsers = ofy().load().type(User.class).count();
			buf.append("\n<FORM NAME=UsrSearch METHOD=GET>To search for a user, enter a portion of the user's email address.<br/>");

			buf.append("\n<INPUT NAME=SearchString VALUE='" + ("Search for users".equals(userRequest) && searchString!=null?CharHider.quot2html(searchString):"(show all)") + "' onFocus=UsrSearch.SearchString.value=''>"
					+ "\n<INPUT TYPE=SUBMIT NAME='UserRequest' VALUE='Search for users'></FORM>");

			if(results != null) {
				QueryResults<User> iterator = cursor==null?results.iterator():results.startAt(Cursor.fromUrlSafe(cursor)).iterator();
				int nResults = results.count();
				buf.append("<FONT SIZE=-1>Showing " + nResults + " users matching the search criteria. "
						+ (nResults==this.queryLimit?"You can narrow this search by entering more of the user's email address.":"") + "</FONT><br>");
				buf.append("\n<TABLE CELLSPACING=5><TR><TD><b>Email</b></TD><TD><b>Last Name</b></TD><TD><b>First Name</b></TD>"
						+ "<TD><b>Role</b></TD><TD><b>UserId</b></TD><TD><b>Last Login</b></TD><TD><b>Action</b></TD></TR>");
				while (iterator.hasNext()) {
					User u = iterator.next();
					buf.append("\n<FORM METHOD=GET>"
							+ "<TR style=color:" + (u.alias==null?"black":"grey") + ">"
							+ "<TD>" + u.id + "</TD>"
							+ "<TD>" + u.getPrincipalRole() + "</TD>" 
							+ "<TD>" + u.lastLogin + "</TD>"
							+ "<TD><INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Edit User'></TD></TR></FORM>");
				}
				buf.append("\n</TABLE>");
				if (nResults==this.queryLimit) buf.append("<a href=/Admin?UserRequest=Search+for+users&SearchString=" + searchString + "&Cursor=" + iterator.getCursorAfter().toUrlSafe() + "><FONT SIZE=-1>show more users</FONT></a>"); 
			} else buf.append("<FONT SIZE=-1>There are currently " + nUsers + " active ChemVantage accounts.</FONT><p>");
			
			// This section provides information about domains
			buf.append("<h3>Most Active ChemVantage Domains</h3>");
			List<Domain> domains = ofy().load().type(Domain.class).order("-dailyLoginsAvg").limit(10).list();
			if (domains.size()>0) {
				buf.append("<table><tr><td>Domain Name</td><td>Last Login</td><td>Users</td><td style='text-align:center'>Administrator</td><td>Avg Daily Logins</td></tr>");
				for (Domain d : domains) {
					nUsers = ofy().load().type(User.class).filter("domain",d.domainName).count();
					if (d.activeUsers!=nUsers) {
						d.activeUsers = nUsers;
						ofy().save().entity(d);
					}
					buf.append("<tr><td>" + d.domainName + "</a></td>"
							+ "<td>" + d.lastLogin.toString() + "</td>"
							+ "<td style='text-align:center'>" + d.activeUsers + "</td>"
							+ "<td style='text-align:center'>");
					try {
						List<String> domainAdmins = d.getDomainAdmins();
						if (domainAdmins.isEmpty()) buf.append("(not assigned)");
						for (String uId : domainAdmins) buf.append(uId + (domainAdmins.size()>1?"<br>":""));
					} catch (Exception e) {
						buf.append ("(not assigned)");
					}
					buf.append("</td><td style='text-align:center'>" + d.getDailyLoginsAvg() + "</td></tr>");
				}
				buf.append("</table>");
			} else buf.append("No domains are currently active.");

			buf.append("<h3>Basic LTI Consumer</h3>");
			int nConsumers = ofy().load().type(BLTIConsumer.class).count();
			String defKey = "Search for Consumer".equals(userRequest) && (searchString!=null&&!searchString.isEmpty())?searchString:"(show all)";
			buf.append("<FORM NAME=ConsKey ACTION=Admin METHOD=POST>Use this form below to search for, create or delete specific LTI consumers.<br>"
					+ "Consumer Key: <INPUT TYPE=TEXT NAME=oauth_consumer_key VALUE='" + defKey + "' onFocus=ConsKey.oauth_consumer_key.value=''>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Search for Consumer'> "
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Generate New Shared Secret'> "
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete LTI Consumer'>"
					+ "</FORM>");

			if ("Search for Consumer".equals(userRequest)) {
				Key<BLTIConsumer> keyFirst = Key.create(BLTIConsumer.class,(searchString.isEmpty()?"\u0000":searchString));
				Key<BLTIConsumer> keyLast = Key.create(BLTIConsumer.class,(searchString.isEmpty()?"\ufffd":searchString+"\ufffd"));					
				Query<BLTIConsumer> consumerResults = ofy().load().type(BLTIConsumer.class).filterKey(">=",keyFirst).filterKey("<",keyLast).limit(this.queryLimit);
				QueryResults<BLTIConsumer> consumers = cursor==null?consumerResults.iterator():consumerResults.startAt(Cursor.fromUrlSafe(cursor)).iterator();
				
				int nResults = consumerResults.count();
				if (nResults==0) buf.append("<FONT SIZE=-1>No LTI consumers matched the search criteria.</FONT><p>");
				else buf.append("<FONT SIZE=-1>Showing " + nResults + " LTI consumers matching the search criteria.</FONT><p>");
				
				buf.append("<TABLE><TR><TH>Consumer Key</TH><TH>Secret</TH></TR>");
				while (consumers.hasNext()) {
					BLTIConsumer cons = consumers.next();
					buf.append("<TR><TD>" + cons.oauth_consumer_key + "</TD>");
					buf.append("<TD><INPUT TYPE=BUTTON VALUE='Reveal secret' "
							+ "onClick=javascript:getElementById('" + cons.oauth_consumer_key + "').style.display='';this.style.display='none'>"
							+ "<div id='"+ cons.oauth_consumer_key + "' style='display: none'>" + cons.secret + "</div></TD></TR>");
				}
				buf.append("</TABLE>");
				if (nResults==this.queryLimit) buf.append("<FONT SIZE=-1><a href='/Admin?UserRequest=Search for Consumer&SearchString=(show all)&Cursor=" + consumers.getCursorAfter().toUrlSafe() + "'><FONT SIZE=-1>show more consumers</FONT></a><p>");
			} else {
				buf.append("<FONT SIZE=-1>There are currently " + nConsumers + " registered LTI consumers.</FONT><p>");
			}
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}


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
	
	void createBLTIConsumer(HttpServletRequest request) {
		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		// prevent over-writing an existing BLTIConsumer
		BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).now(); 
		if (c==null) {
			c = new BLTIConsumer(oauth_consumer_key); 
			ofy().save().entity(c);
		}
	}
		
	void deleteBLTIConsumer(HttpServletRequest request) {
		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).now(); 
		if (c!=null) ofy().delete().entity(c);
	}
	
}

