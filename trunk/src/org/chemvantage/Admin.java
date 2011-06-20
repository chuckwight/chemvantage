/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class Admin extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private int queryLimit = 20;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet is used by PZone admins to manage user properties and roles.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			// begin standard user authentication section
			UserService userService = UserServiceFactory.getUserService();
			User user = ofy.get(User.class,userService.getCurrentUser().getNickname());
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId", user.id);
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("Edit User") || userRequest.equals("Check ID")) {
				User usr = ofy.get(User.class,request.getParameter("UserId"));
				out.println(Home.getHeader(user) + editUserForm(user,request,usr) + Home.footer);
			}
			else {
				String searchString = request.getParameter("SearchString");
				String cursor = request.getParameter("Cursor");
				out.println(Home.getHeader(user) + mainAdminForm(user,searchString,cursor) + Home.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/");
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			// begin standard user authentication section
			UserService userService = UserServiceFactory.getUserService();
			User user = ofy.get(User.class,userService.getCurrentUser().getNickname());
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId", user.id);
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String searchString = null;
			String cursor = null;
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			if (userRequest.equals("Announce")) {
				Home.announcement = request.getParameter("Announcement");
				Login.lockedDown = Boolean.parseBoolean(request.getParameter("LockedDown"));
			} else if (userRequest.equals("Update User")) {
				User usr = ofy.get(User.class,request.getParameter("UserId")); // user record to modify
				updateUser(usr,request);
				searchString = usr.getFullName();
				ofy.put(usr);
				if (usr.id.equals(user.id)) user = usr; // admin modifying own record; update now to reflect new status
			} else if (userRequest.equals("Delete User")) {
				User usr = ofy.get(User.class,request.getParameter("UserId"));
				searchString = usr.getFullName();
				ofy.delete(usr);
			} else if (userRequest.equals("Generate New BLTI Secret")) {
				createBLTIConsumer(request);
			} else if (userRequest.equals("Delete BLTI Consumer")) {
				deleteBLTIConsumer(request);
			} else if (userRequest.equals("Login As This User")) {
				session.setAttribute("UserId",request.getParameter("UserId"));
				response.sendRedirect("/Home");
				return;
			} else if (userRequest.equals("Confirm Merge")) {
				User usr = ofy.get(User.class,request.getParameter("UserId"));
				User mergeUser = ofy.get(User.class,request.getParameter("MergeUserId"));
				mergeAccounts(usr,mergeUser);
				ofy.delete(mergeUser);
				searchString = usr.getFullName();
			}
			out.println(Home.getHeader(user) + mainAdminForm(user,searchString,cursor) + Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	String mainAdminForm(User user,String searchString,String cursor) {
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
			Query<User> results = null;
			if (searchString != null) {
				searchString = searchString.toLowerCase().trim();
				int i = searchString.indexOf('*');
				if (i == 0) searchString = "";
				else if (i > 0) searchString = searchString.substring(0,i);
				results = ofy.query(User.class).filter("lowercaseName >=",searchString).filter("lowercaseName <",(searchString+'\ufffd')).limit(this.queryLimit);
				if (cursor!=null) results.startCursor(Cursor.fromWebSafeString(cursor));
			}
			
			buf.append("\n<h3>User Search</h3>");
			buf.append("\n<FORM METHOD=GET>"
					+ "To search for a user, enter a portion of the user's <i>lastname, firstname</i>. Wildcards (*) are OK.<br>");

			buf.append("\n<INPUT NAME=SearchString VALUE='" + (searchString==null?"":CharHider.quot2html(searchString)) + "'>"
					+ "\n<INPUT TYPE=SUBMIT VALUE='Search for users'></FORM>");

			if(results != null) {
				QueryResultIterator<User> iterator = results.iterator();
				int nResults = results.count();
				buf.append("<FONT SIZE=-1>Showing " + (nResults==this.queryLimit?"first ":"") + nResults + " results. "
						+ (nResults>4?"You can narrow this search by entering more of the user's <i>lastname, firstname</i>":"") + "</FONT><br>");
				buf.append("\n<TABLE CELLSPACING=5><TR><TD><b>Last Name</b></TD><TD><b>First Name</b></TD><TD><b>Email</b></TD>"
						+ "<TD><b>Role</b></TD><TD><b>Acct Type</b></TD><TD><b>Last Login</b></TD><TD><b>Action</b></TD></TR>");
				while (iterator.hasNext()) {
					User u = iterator.next();
					u.clean(); // ensures that all User fields are valid for this user
					buf.append("\n<FORM METHOD=GET>"
							+ "<TR><TD>" + u.lastName + "</TD>"
							+ "<TD>" + u.firstName + "</TD>"
							+ "<TD>" + u.email + "</TD>"
							+ "<TD>" + u.getPrincipalRole() + "</TD>" 
							+ "<TD>" + (u.hasPremiumAccount()?"Premium":"Basic") + "</TD>"
							+ "<TD>" + u.lastLogin + "</TD>"
							+ "<TD><INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Edit User'></TD></TR></FORM>");
				}
				buf.append("\n</TABLE>");
				if (nResults==this.queryLimit) buf.append("<a href=/Admin?SearchString=" + searchString + "&Cursor=" + iterator.getCursor().toWebSafeString() + ">show more users</a>"); 
			} else if (searchString != null) buf.append("\nSorry, the search returned no results.");
				// write a section here to give the total number of users  
			buf.append("<h3>Basic LTI Consumers</h3>");
			buf.append("The following is a list of organizations that are permitted to make Basic LTI "
					+ "connections to ChemVantage, usually from within a learning management system. "
					+ "In order to authorize a new LMS to make BLTI launch requests, ChemVantage must provide "
					+ "the LMS administrator with<UL>"
					+ "<LI>a Basic LTI launch URL (http://chem-vantage.appspot.com/BLTILaunch/)"
					+ "<LI>an oauth_consumer_key (an identifying string e.g., 'webct.business.utah.edu')"
					+ "<LI>a shared secret (random string or hex number).</UL>");
			Query<BLTIConsumer> consumers = ofy.query(BLTIConsumer.class);
			if (consumers.count() == 0) buf.append("(no BLTI consumers have been authorized yet)<p>");
			else buf.append("<TABLE><TR><TH>Consumer Key</TH><TH>Secret</TH></TR>");
			for (BLTIConsumer c : consumers) {
				buf.append("<TR><TD>" + c.oauth_consumer_key + "</TD>");
				buf.append("<TD><INPUT TYPE=BUTTON VALUE='Reveal secret' "
				+ "onClick=javascript:getElementById('" + c.oauth_consumer_key + "').style.display='';this.style.display='none'>"
				+ "<div id='"+ c.oauth_consumer_key + "' style='display: none'>" + c.secret + "</div></TD></TR>");
			}
			if (consumers.count() > 0) buf.append("</TABLE>");
			buf.append("<FORM ACTION=Admin METHOD=POST>"
					+ "Consumer Key: <INPUT TYPE=TEXT NAME=oauth_consumer_key>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Generate New BLTI Secret'>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete BLTI Consumer'>"
					+ "</FORM>");
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
			boolean premiumAccount = usr.hasPremiumAccount();
			buf.append("\n<TABLE><FORM NAME=UserForm METHOD=POST ACTION=Admin>"
					+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
					+ "\n<TR><TD ALIGN=RIGHT>UserID: </TD><TD>" + usr.id + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Email: </TD><TD><INPUT NAME=Email VALUE='" + usr.email + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>LastName: </TD><TD><INPUT NAME=LastName VALUE='" 
					+ CharHider.quot2html(usr.lastName) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>FirstName: </TD><TD><INPUT NAME=FirstName VALUE='" 
					+ CharHider.quot2html(usr.firstName) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT VALIGN=TOP>Roles: </TD><TD>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=1" + (roles%2/1==1?" CHECKED":"") + ">Contributor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=2" + (roles%4/2==1?" CHECKED":"") + ">Editor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=8" + (roles%16/8==1?" CHECKED":"") + ">Instructor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=16" + (roles%32/16==1?" CHECKED":"") + ">Administrator"
					+ "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Acct Type: </TD><TD>"
					+ "<INPUT TYPE=RADIO NAME=Premium VALUE='false'" + (!premiumAccount?" CHECKED":"") + ">Basic "
					+ "<INPUT TYPE=RADIO NAME=Premium VALUE='true'" + (premiumAccount?" CHECKED":"") + ">Premium</TD></TR>");
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
					User mergeUser = ofy.get(User.class,request.getParameter("MergeUserId"));
					if (usr.id.equals(mergeUser.id)) mergeUser = null; // prevents merging identical accounts
					Group g = mergeUser.myGroupId>0?ofy.find(Group.class,mergeUser.myGroupId):null;
					buf.append("<FORM METHOD=POST ACTION=Admin>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='Confirm Merge'>"
							+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=MergeUserId VALUE='" + mergeUser.id + "'>"
							+ "<TABLE><TR><TD ALIGN=RIGHT>UserId: </TD><TD>" + mergeUser.id + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Name: </TD><TD>" + mergeUser.getFullName() + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Email: </TD><TD>" + mergeUser.email + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Role: </TD><TD>" + mergeUser.getPrincipalRole() + "</TD></TR>"
					        + "<TR><TD ALIGN=RIGHT>Group: </TD><TD>" + (g==null?"(none)":g.description + "(" + User.getBothNames(g.instructorId) + ")") + "</TD></TR>"
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
			Query<Group> groups = ofy.query(Group.class);
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
			usr.setEmail(request.getParameter("Email"));
			usr.setFirstName(request.getParameter("FirstName"));
			usr.setLastName(request.getParameter("LastName"));
			usr.setLowerCaseName();
			usr.roles = roles;
			usr.setPremium(Boolean.parseBoolean(request.getParameter("Premium")));
			try {
				long newId = Long.parseLong(request.getParameter("GroupId"));  // get groupId for new group
				usr.changeGroups(newId);
			} catch (Exception e) {
			}
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
			Query<BLTIConsumer> consumers = ofy.query(BLTIConsumer.class);
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
		BLTIConsumer c = ofy.find(BLTIConsumer.class,oauth_consumer_key); 
		if (c==null) {
			c = new BLTIConsumer(oauth_consumer_key); 
			ofy.put(c);
		}
	}
	
	void deleteBLTIConsumer(HttpServletRequest request) {
		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		BLTIConsumer c = ofy.find(BLTIConsumer.class,oauth_consumer_key); 
		if (c!=null) ofy.delete(c);
	}
	
	void mergeAccounts(User toUser,User fromUser) {
		// find all transactions for fromUser and credit to toUser:
		Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",fromUser.id);
		for (HWTransaction h:hwTransactions) {
			h.userId = toUser.id;
			ofy.put(h);
		}
		Query<PracticeExamTransaction> peTransactions = ofy.query(PracticeExamTransaction.class).filter("userId",fromUser.id);
		for (PracticeExamTransaction p:peTransactions) {
			p.userId = toUser.id;
			ofy.put(p);
		}
		Query<QuizTransaction> qTransactions = ofy.query(QuizTransaction.class).filter("userId",fromUser.id);
		for (QuizTransaction q:qTransactions) {
			q.userId = toUser.id;
			ofy.put(q);
		}
		Query<VideoTransaction> vTransactions = ofy.query(VideoTransaction.class).filter("userId",fromUser.id);
		for (VideoTransaction v:vTransactions) {
			v.userId = toUser.id;
			ofy.put(v);
		}
		Query<Group> myGroups = ofy.query(Group.class).filter("instructorId",fromUser.id);
		for (Group g:myGroups) {
			g.instructorId=toUser.id;
			ofy.put(g);
		}
		QuizScore.removeAll(toUser.id);  // clears QuizScore objects to be recalculated when read
		ofy.delete(fromUser);
	}
}

