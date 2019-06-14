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
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
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
	
			if (user.use2FactorAuth && session.getAttribute("Code")==null) {
				int code = new Random().nextInt(900000) + 100000;
				if (TwoFactorAuth.sentSMSCode(user, code)) {
					session.setAttribute("ProposedCode", code);
					out.println(Home.getHeader(user) + TwoFactorAuth.verificationForm("/Admin", false) + Home.footer);
					return;
				}
			} else {
				session.setAttribute("Code",1); // failed text message; let the user in this time
			}
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("Edit User") || userRequest.equals("Check ID")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				out.println(Home.getHeader(user) + editUserForm(user,request,usr) + Home.footer);
			}
			else {
				String searchString = request.getParameter("SearchString");
				String cursor = request.getParameter("Cursor");
				out.println(Home.getHeader(user) + mainAdminForm(user,userRequest,searchString,cursor) + Home.footer);
			}
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
				Login.lockedDown = Boolean.parseBoolean(request.getParameter("LockedDown"));
			} else if (userRequest.equals("Update User")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe(); // user record to modify
				updateUser(usr,request);
				searchString = usr.getEmail();
				if (usr.id.equals(user.id)) user = usr; // admin modifying own record; update now to reflect new status
			} else if (userRequest.equals("Delete User")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				searchString = usr.getEmail();
				ofy().delete().entity(usr);
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
			} else if (userRequest.equals("Confirm Merge")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				User mergeUser = ofy().load().type(User.class).id(request.getParameter("MergeUserId")).safe();
				mergeAccounts(usr,mergeUser);
				searchString = usr.getEmail();
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
					+ "<INPUT TYPE=RADIO NAME=LockedDown VALUE=false" + (Login.lockedDown?"":" CHECKED") + ">unlock&nbsp;"
					+ "<INPUT TYPE=RADIO NAME=LockedDown VALUE=true" + (Login.lockedDown?" CHECKED":"") + ">lock"
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
				QueryResultIterator<User> iterator = cursor==null?results.iterator():results.startAt(Cursor.fromWebSafeString(cursor)).iterator();
				int nResults = results.count();
				buf.append("<FONT SIZE=-1>Showing " + nResults + " users matching the search criteria. "
						+ (nResults==this.queryLimit?"You can narrow this search by entering more of the user's email address.":"") + "</FONT><br>");
				buf.append("\n<TABLE CELLSPACING=5><TR><TD><b>Email</b></TD><TD><b>Last Name</b></TD><TD><b>First Name</b></TD>"
						+ "<TD><b>Role</b></TD><TD><b>UserId</b></TD><TD><b>Last Login</b></TD><TD><b>Action</b></TD></TR>");
				while (iterator.hasNext()) {
					User u = iterator.next();
					u.clean();
					buf.append("\n<FORM METHOD=GET>"
							+ "<TR style=color:" + (u.alias==null?"black":"grey") + ">"
							+ "<TD>" + u.getEmail() + "</TD>"
							+ "<TD>" + u.getLastName() + "</TD>"
							+ "<TD>" + u.getFirstName() + "</TD>"
							+ "<TD>" + u.getPrincipalRole() + "</TD>" 
							+ "<TD>" + u.id + "</TD>"
							+ "<TD>" + u.lastLogin + "</TD>"
							+ "<TD><INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Edit User'></TD></TR></FORM>");
				}
				buf.append("\n</TABLE>");
				if (nResults==this.queryLimit) buf.append("<a href=/Admin?UserRequest=Search+for+users&SearchString=" + searchString + "&Cursor=" + iterator.getCursor().toWebSafeString() + "><FONT SIZE=-1>show more users</FONT></a>"); 
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
				QueryResultIterator<BLTIConsumer> consumers = cursor==null?consumerResults.iterator():consumerResults.startAt(Cursor.fromWebSafeString(cursor)).iterator();
				
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
				if (nResults==this.queryLimit) buf.append("<FONT SIZE=-1><a href='/Admin?UserRequest=Search for Consumer&SearchString=(show all)&Cursor=" + consumers.getCursor().toWebSafeString() + "'><FONT SIZE=-1>show more consumers</FONT></a><p>");
			} else {
				buf.append("<FONT SIZE=-1>There are currently " + nConsumers + " registered LTI consumers.</FONT><p>");
			}
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}

	String editUserForm(User user,HttpServletRequest request,User usr) {
		StringBuffer buf = new StringBuffer("\n\n<h3>Edit User Properties</h3>");
		try {
			buf.append("\nUsing this form, you may edit edit any user-specific fields "
					+ "for this user, login as the user, or delete the user account permanently.<p>");

			int roles = usr.roles;
			buf.append("\n<TABLE><FORM NAME=UserForm METHOD=POST ACTION=Admin>"
					+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
					+ "\n<TR><TD ALIGN=RIGHT>UserID: </TD><TD>" + usr.id + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>AuthDomain: </TD><TD>" + usr.authDomain + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Domain: </TD><TD><INPUT NAME=Domain VALUE='" + (usr.domain==null?"":usr.domain) + "'>" + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Email: </TD><TD><INPUT NAME=Email VALUE='" + usr.getEmail() + "'>" + (usr.verifiedEmail?" (verified)":" (unverified)") + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>LastName: </TD><TD><INPUT NAME=LastName VALUE='" 
					+ CharHider.quot2html(usr.getLastName()) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>FirstName: </TD><TD><INPUT NAME=FirstName VALUE='" 
					+ CharHider.quot2html(usr.getFirstName()) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT VALIGN=TOP>Roles: </TD><TD>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=1" + (roles%2/1==1?" CHECKED":"") + ">Contributor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=2" + (roles%4/2==1?" CHECKED":"") + ">Editor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=8" + (roles%16/8==1?" CHECKED":"") + ">Instructor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=16" + (roles%32/16==1?" CHECKED":"") + ">Administrator"
					+ "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Acct Type: </TD><TD>"
					+ "<INPUT TYPE=RADIO NAME=Premium VALUE='false'" + (!usr.premium?" CHECKED":"") + ">Basic "
					+ "<INPUT TYPE=RADIO NAME=Premium VALUE='true'" + (usr.premium?" CHECKED":"") + ">Premium "
					+ "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Alias: </TD><TD><INPUT TYPE=TEXT NAME=Alias VALUE='" + (usr.alias==null?"":usr.alias) + "'></TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Group: </TD><TD>" + groupSelectBox(usr.myGroupId) + "</TD></TR>");
			buf.append("\n<TR><TD ALIGN=RIGHT>Last Login: </TD>"
					+ "<TD>" + usr.lastLogin + "</TD></TR>"
					+ "\n</TABLE>");

			buf.append("\n<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update User'>"
					+ "\n <INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete User' "
					+ "onClick=\"return confirm('Delete this user. Are you sure?')\">"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Login As This User'>"
					+ "\n</FORM>");

			// Use this section for merging two user accounts into a single account and deleting the extra account
			buf.append("<h3>Merge User Accounts</h3>");
			buf.append("Use this form to merge all user records from two separate accounts into a single account. This will "
					+ "transfer all of the scores to the account indicated above and delete the unwwanted account below:<br>");	
			
			String mergeUserId = request.getParameter("MergeUserId");
			if (mergeUserId!=null) {
				try {
					User mergeUser = ofy().load().type(User.class).id(request.getParameter("MergeUserId")).safe();
					if (usr.id.equals(mergeUser.id)) mergeUser = null; // prevents merging identical accounts
					Group g = mergeUser.myGroupId>0?ofy().load().type(Group.class).id(mergeUser.myGroupId).now():null;
					buf.append("<FORM METHOD=POST ACTION=Admin>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='Confirm Merge'>"
							+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=MergeUserId VALUE='" + mergeUser.id + "'>"
							+ "<TABLE><TR><TD ALIGN=RIGHT>UserId: </TD><TD>" + mergeUser.id + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Name: </TD><TD>" + mergeUser.getFullName() + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Email: </TD><TD>" + mergeUser.getEmail() + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Role: </TD><TD>" + mergeUser.getPrincipalRole() + "</TD></TR>"
					        + "<TR><TD ALIGN=RIGHT>Group: </TD><TD>" + (g==null?"(none)":g.description + "(anonymous)") + "</TD></TR>"
					        + "</TABLE>Transfer records and delete this account: "
					        + "<INPUT TYPE=SUBMIT VALUE='Confirm Merge'>"
					        + "</FORM>");
				} catch (Exception e2) {
					buf.append("No separate user record was found for <b>" + mergeUserId + "</b>.<br>");
					mergeUserId = null;
				}
			}
			if (mergeUserId==null) { // either no mergeUserId was specified or the value was invalid
				buf.append("<br>The account to be retained with all records has a UserId: <b>" + usr.id + "</b>");
				buf.append("<FORM METHOD=GET ACTION=Admin>Enter the UserId of the account to be deleted: "
						+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
						+ "<INPUT TYPE=TEXT NAME=MergeUserId>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Check ID'></FORM>");
			}
		}
		catch (Exception e) {
			buf.append("<br>" + e.toString());
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
						+ g.description + " (" + g.getInstructorBothNames() + ")</OPTION>");
			}
			buf.append("</SELECT>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	void updateUser(User usr,HttpServletRequest request) {
		try {
			int roles = 0;
			String userRoles[] = request.getParameterValues("Roles");
			if (userRoles != null) { // calculate the value of roles based on the various roles assigned to this user
				for (int i = 0; i < userRoles.length; i++) {
					roles += Integer.parseInt(userRoles[i]);
				}
			}
			usr.setDomain(request.getParameter("Domain"));
			if (!usr.getEmail().equals(request.getParameter("Email"))) usr.verifiedEmail = false;
			usr.setEmail(request.getParameter("Email"));
			usr.setFirstName(request.getParameter("FirstName"));
			usr.setLastName(request.getParameter("LastName"));
			usr.setLowerCaseName();
			usr.roles = roles;
			usr.setPremium(Boolean.parseBoolean(request.getParameter("Premium")));
			if (request.getParameter("Alias").isEmpty()) usr.alias = null;
			else usr.alias = request.getParameter("Alias");
			try {
				long newId = Long.parseLong(request.getParameter("GroupId"));  // get groupId for new group
				usr.changeGroups(newId);
			} catch (Exception e) {
			}
			ofy().save().entity(usr);
		}
		catch (Exception e) {
		}
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
	
	protected static void mergeAccounts(User toUser,User fromUser) {
		// find all transactions for fromUser and credit to toUser:
		if (toUser.getFirstName().isEmpty()) toUser.setFirstName(fromUser.getFirstName());
		if (toUser.getLastName().isEmpty()) toUser.setLastName(fromUser.getLastName());
		if (toUser.getEmail().isEmpty() && fromUser.verifiedEmail) toUser.setEmail(fromUser.getEmail());
		if (toUser.myGroupId<=0 && fromUser.myGroupId>=0) {
			toUser.changeGroups(fromUser.myGroupId);
			fromUser.changeGroups(0L);
			toUser.notifyDeadlines = toUser.myGroupId>0?(toUser.notifyDeadlines || fromUser.notifyDeadlines):false;
			toUser.smsMessageDevice = toUser.smsMessageDevice==null?fromUser.smsMessageDevice:toUser.smsMessageDevice;
		}
		if (toUser.domain == null && fromUser.domain != null) {
			toUser.domain = fromUser.domain;
			fromUser.domain = null;
		}
		toUser.roles = fromUser.roles>toUser.roles?fromUser.roles:toUser.roles;
		toUser.setPremium(fromUser.hasPremiumAccount() || toUser.hasPremiumAccount());
		toUser.setLowerCaseName();
		fromUser.setAlias(toUser.id);  // diverts future logins to the new UserId
		
		ofy().save().entity(toUser);
		ofy().save().entity(fromUser);
		
		List<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",fromUser.id).list();
		for (HWTransaction h:hwTransactions) h.userId = toUser.id;
		ofy().save().entities(hwTransactions);
		
		List<PracticeExamTransaction> peTransactions = ofy().load().type(PracticeExamTransaction.class).filter("userId",fromUser.id).list();
		for (PracticeExamTransaction p:peTransactions) p.userId = toUser.id;
		ofy().save().entities(peTransactions);
		
		List<QuizTransaction> qTransactions = ofy().load().type(QuizTransaction.class).filter("userId",fromUser.id).list();
		for (QuizTransaction q:qTransactions) q.userId = toUser.id;
		ofy().save().entities(qTransactions);
		
		List<Group> myGroups = ofy().load().type(Group.class).filter("instructorId",fromUser.id).list();
		for (Group g:myGroups) g.instructorId=toUser.id;
		ofy().save().entities(myGroups);
		
	}
	
	protected static void autoMergeAccounts(String email) {
		// this method attempts to find all active user accounts having the same 
		// email address and merge them into a smaller number of accounts by aliasing.
		// The general strategy is:
		//   1. Leave CAS accounts alone because it's easy to access them directly from the home page
		//   2. Don't merge any accounts that have aliases or unverified or empty email addresses.
		//   3. Any two accounts not associated with a domain can be merged into a single account either direction.
		//   4. If the user has a domain account (LTI or Google Apps), any non-domain accounts should be merged into it
		//   5. If the address of any account matches a registered domain, add the user to that domain.

		try {
			List<User> userAccounts = ofy().load().type(User.class).filter("email",email).list();
			for (User u : userAccounts) {
				if ((u.alias != null && !u.alias.isEmpty()) || u.getEmail().isEmpty() || !u.verifiedEmail || "CAS".equals(u.authDomain)) {
					userAccounts.remove(u);
					continue;
				}
				try { // if this domain exists as a registered ChemVantage domain, assign the user to it
					Domain d = ofy().load().type(Domain.class).filter("domainName",u.authDomain).first().safe();
					u.domain = d.domainName;
					ofy().save().entity(u);
				} catch (Exception e) {
				}
			}
			if (userAccounts.size()<2) return;
			User fromUser = null;
			User toUser = null;
			for (User u : userAccounts) {			
				if (fromUser==null && u.domain==null) fromUser = u;
				else if (toUser==null) toUser = u;
				if (fromUser!=null && toUser!=null) {
					Admin.mergeAccounts(toUser, fromUser);
					Admin.autoMergeAccounts(toUser.getEmail());  // proceed recursively until merge process fails
					return;
				}
			}
		} catch (Exception e) {
		}
	}
}

