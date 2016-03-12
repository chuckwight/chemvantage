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

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.googlecode.objectify.cmd.Query;

public class DomainAdmin extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private int queryLimit = 20;
	
	public String getServletInfo() {
		return "This servlet is used by Google Apps administrators to manage user properties and roles.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		try {
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) user = Nonce.getUser(request.getParameter("Nonce"));
			else user = User.getInstance(session);
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/Logout");
				return;
			}
			session.setAttribute("UserId", user.id);
			String nonce = null;
			if (session.isNew()) nonce = Nonce.createInstance(user);
			
			String domainName = user.domain;
			if (domainName==null && user.isChemVantageAdmin()) domainName = request.getParameter("Domain");
			if (domainName==null) response.sendRedirect("/Admin"); // ChemVantage administrator
			
			// Check to ensure that user is either domain admin or ChemVAntage admin
			Domain d = ofy().load().type(Domain.class).filter("domainName", domainName).first().now();
			if (d==null) {
				out.println("The ChemVantage domain " + domainName + " does not exist.");
				return;
			}
			List<String> domainAdmins = d.getDomainAdmins();
			if (!((domainAdmins != null && domainAdmins.contains(user.id)) || user.isChemVantageAdmin())) {
				response.sendRedirect("/Home");
			}
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("Edit User") || userRequest.equals("Check ID")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				out.println(Home.getHeader(user) + editUserForm(user,request,usr,nonce) + Home.footer);
			}
			else {
				String searchString = request.getParameter("SearchString");
				String cursor = request.getParameter("Cursor");
				out.println(Home.getHeader(user) + mainAdminForm(d,user,searchString,cursor,nonce) + Home.footer);
			}
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) user = Nonce.getUser(request.getParameter("Nonce"));
			else user = User.getInstance(session);
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/Logout");
				return;
			}
			session.setAttribute("UserId", user.id);
			String nonce = null;
			if (session.isNew()) nonce = Nonce.createInstance(user);
			
			String domainName = user.domain;
			if (domainName==null && user.isChemVantageAdmin()) domainName = request.getParameter("Domain");
			if (domainName==null) response.sendRedirect("/Admin"); // ChemVantage administrator
			
			// Check to ensure that user is either domain admin or ChemVantage admin
			Domain d = ofy().load().type(Domain.class).filter("domainName", domainName).first().now();
			List<String> domainAdmins = d.getDomainAdmins();
			if (!((domainAdmins != null && domainAdmins.contains(user.id)) || user.isChemVantageAdmin())) {
				response.sendRedirect("/Home");
			}
			
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String searchString = null;
			String cursor = null;
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			if (userRequest.equals("Update User")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe(); // user record to modify
				updateUser(usr,request);
				if (usr.id.equals(user.id)) user = usr; // admin modifying own record; update now to reflect new status
			} else if (userRequest.equals("Delete User")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				ofy().delete().entity(usr);
			} else if (userRequest.equals("Login As This User")) {
				request.getSession(true).setAttribute("UserId",request.getParameter("UserId"));
				response.sendRedirect("/Home");
				return;
			} else if (userRequest.equals("Confirm Merge")) {
				User usr = ofy().load().type(User.class).id(request.getParameter("UserId")).safe();
				User mergeUser = ofy().load().type(User.class).id(request.getParameter("MergeUserId")).safe();
				mergeAccounts(usr,mergeUser);
			} else if (userRequest.equals("Assign Administrator")) {
				try {
					User usr = ofy().load().type(User.class).id(request.getParameter("AdminId")).now();
					usr.setIsAdministrator(true);
					usr.domain = d.domainName;
					ofy().save().entity(usr);
					d.addAdmin(usr.id);
					ofy().save().entity(d);
				} catch (Exception e) {
					out.println("Sorry, this user could not be assigned to administer the domain.");
				}
			} else if (userRequest.equals("Revoke Administrator")) {
				try {
					User usr = ofy().load().type(User.class).id(request.getParameter("AdminId")).now();
					usr.setIsAdministrator(false);
					usr.domain = d.domainName;
					ofy().save().entity(usr);
					d.removeAdmin(usr.id);
					ofy().save().entity(d);	
					response.sendRedirect("/admin?Domain=" + domainName);
					return;
				} catch (Exception e) {
					out.println("Sorry, the requested operation failed unexpectedly.");
				}
			}
			out.println(Home.getHeader(user) + mainAdminForm(d,user,searchString,cursor,nonce) + Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	String mainAdminForm(Domain d,User user,String searchString,String cursor,String nonce) {
		StringBuffer buf = new StringBuffer("\n\n<h2>ChemVantage Domain Administration</h2>");
		try {
			List<String> domainAdmins = d.getDomainAdmins();
			buf.append("<table>");
			buf.append("<tr><td>Domain name: </td><td>" + d.domainName + "</td></tr>");
			if (domainAdmins != null) for (String id : domainAdmins) {
				buf.append("<tr><td>Domain administrator: </td>");
				buf.append("<td>" + User.getBothNames(id) + "&nbsp;&lt;" + User.getEmail(id) + "&gt;</td><td> <form method=post><input type=hidden name=AdminId value='" + id + "'><input type=submit name=UserRequest value='Revoke Administrator'></form></span></td></tr>");
			}
			buf.append("</table>");
			buf.append("Assign a new administrator for this domain:<br>"
					+ "<form method=post>"
					+ "UserId: <input type=text name=AdminId>"
					+ "<input type=submit name=UserRequest value='Assign Administrator'>"
					+ "<input type=hidden name=Domain value='" + d.domainName + "'>"
					+ "</form>");
			
			// Start user search section for editing user properties
			Query<User> results = null;
			if (searchString != null) {
				searchString = searchString.toLowerCase().trim();
				int i = searchString.indexOf('*');
				if (i == 0) searchString = "";
				else if (i > 0) searchString = searchString.substring(0,i);
				results = ofy().load().type(User.class).filter("email >=",searchString).filter("email <",(searchString+'\ufffd')).filter("domain",d.domainName).limit(this.queryLimit);
				if (cursor!=null) results.startAt(Cursor.fromWebSafeString(cursor));
			}
			
			buf.append("\n<h3>Manage User Accounts</h3>");
			buf.append("\n<FORM METHOD=GET>"
					+ "There are " + d.getActiveUsers() + " active user accounts for this domain.<br>"
					+ "As a domain administrator, your main responsibility is to <a href=# onClick=document.getElementById('manage').style.display='inLine'>manage user accounts</a>.<br>"
					+ "To search for a user, enter the first few letters of the user's email address. Leave blank to browse all ChemVantage users in this domain.<br>");

			buf.append("\n<INPUT NAME=SearchString VALUE='" + (searchString==null?"":searchString) + "'>"
					+ "\n<INPUT TYPE=SUBMIT VALUE='Search for users'>"
					+ (nonce==null?"":"<INPUT TYPE=HIDDEN Name=Nonce VALUE=" + nonce + ">")
					+ "<INPUT TYPE=HIDDEN NAME=Domain VALUE='" + d.domainName + "'></FORM>");

			if(results != null) {
				QueryResultIterator<User> iterator = results.iterator();
				int nResults = results.count();
				buf.append("<FONT SIZE=-1>Showing " + (nResults==this.queryLimit?"first ":"") + nResults + " results. "
						+ (nResults>4?"You can narrow this search by entering more of the user's email address":"") + "</FONT><br>");
				buf.append("\n<TABLE CELLSPACING=5><TR><TD><b>Last Name</b></TD><TD><b>First Name</b></TD><TD><b>Email</b></TD>"
						+ "<TD><b>Role</b></TD><TD><b>UserId</b></TD><TD><b>Last Login</b></TD><TD><b>Action</b></TD></TR>");
				while (iterator.hasNext()) {
					User u = iterator.next();
					u.clean();
					buf.append("\n<FORM METHOD=GET><INPUT TYPE=HIDDEN NAME=Domain VALUE=" + d.domainName + ">"
							+ "<TR style=color:" + (u.alias==null?"black":"grey") + "><TD>" + u.getLastName() + "</TD>"
							+ "<TD>" + u.getFirstName() + "</TD>"
							+ "<TD>" + u.getEmail() + "</TD>"
							+ "<TD>" + u.getPrincipalRole() + "</TD>" 
							+ "<TD>" + u.id + "</TD>"
							+ "<TD>" + u.lastLogin + "</TD>"
							+ "<TD><INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Edit User'></TD></TR></FORM>");
				}
				buf.append("\n</TABLE>");
				if (nResults==this.queryLimit) buf.append("<a href=/admin?Domain=" + d.domainName + "&SearchString=" + searchString + "&Cursor=" + iterator.getCursor().toWebSafeString() + ">show more users</a>"); 
			} else if (searchString != null) buf.append("\nSorry, the search returned no results.");
/*
			buf.append("<div id='manage' style='display:none'>"
					+ "<h3>Basic and Premium Accounts</h3>"
					+ "Any individual user may browse ChemVantage without charge using a free basic account simply by navigating to the site.<br>"
					+ "In order to join a ChemVantage group (usually a chemistry class taught by one of your instructors), a user must upgrade to a premium account. "
					+ "During the free trial period this happens automatically.<p>"
					+ "There are 2 ways to upgrade to a premium account after the free trial period:<ol>"
					+ "<li>The domain (e.g., school or college) may purchase premium account seats ($2.00/ea in quantities of 50 or more) that are allocated to users when they first join a group. If you want to purchase premium accounts on behalf of your students, you simply have to ensure that a sufficient number of seats are purchased in advance."
					+ "<li>If no seats are available, the user will be asked to purchase an individual premium account upgrade ($4.99) when joining a group for the first time. If you want students to purchase their own premium accounts, you don't have to do anything; it's automatic."
					+ "</ol>");
*/
			buf.append("<div id='manage' style='display:none'><h3>Instructor and Admin Accounts</h3>"
					+ "As the domain administrator, you have the ability to grant instructor or administrator privileges to users in your domain.<br/> "
					+ "Find the user's account using the search box above and edit the user's profile to grant the appropriate rights.<ul>" 
					+ "<li>Instructors can create and manage ChemVantage groups (classes)"
					+ "<li>Administrators can grant and revoke user privileges"
					+ "</ul>"
					+ "</div>");
					
			buf.append("<h3>Questions or Comments</h3>"
					+ "See the <a href=/help.html>Help Page</a> for useful tips and tricks, or send us a message using the <a href=/Feedback>Feedback Page</a>, or contact us directly at <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<br> "
					+ "For emergencies, call us at 1-801-810-4401 (domain administrators only, please)");

		} catch (Exception e) {
			buf.append("<p>" + e.toString());
		}
		return buf.toString();
	}

	String editUserForm(User user,HttpServletRequest request,User usr,String nonce) {
		StringBuffer buf = new StringBuffer("\n\n<h3>Edit User Properties</h3>");
		try {
			buf.append("\nUsing this form, you may edit edit any user-specific fields "
					+ "for this user, login as the user, or delete the user account permanently.<p>");
			String domainName = user.domain==null?request.getParameter("Domain"):user.domain;
			int roles = usr.roles;
			buf.append("\n<TABLE><FORM NAME=UserForm METHOD=POST ACTION=admin>"
					+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
					+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=Domain VALUE='" + domainName + "'>"
					+ "\n<TR><TD ALIGN=RIGHT>UserID: </TD><TD>" + usr.id + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>AuthDomain: </TD><TD>" + usr.authDomain + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Account type: </TD><TD>" + (usr.hasPremiumAccount()?"premium":"basic") + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Email: </TD><TD><INPUT NAME=Email VALUE='" + usr.getEmail() + "'>" + (usr.verifiedEmail?" (verified)":" (unverified)") + "</TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>Last Name: </TD><TD><INPUT NAME=LastName VALUE='" 
					+ CharHider.quot2html(usr.getLastName()) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT>First Name: </TD><TD><INPUT NAME=FirstName VALUE='" 
					+ CharHider.quot2html(usr.getFirstName()) + "'></TD></TR>"
					+ "\n<TR><TD ALIGN=RIGHT VALIGN=TOP>Roles: </TD><TD>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=8" + (roles%16/8==1?" CHECKED":"") + ">Instructor<br>"
					+ "<INPUT TYPE=CHECKBOX NAME=Roles VALUE=16" + (roles%32/16==1?" CHECKED":"") + ">Administrator"
					+ "</TD></TR>");
			if (usr.alias != null) buf.append("<TR><TD ALIGN=RIGHT>Alias: </TD><TD>" + usr.alias + "</TD></TR>");
			
			if (usr.hasPremiumAccount() || eligibleToJoin(usr,domainName)) buf.append("<TR><TD ALIGN=RIGHT>Group: </TD><TD>" + groupSelectBox(usr.myGroupId,usr.domain) + "</TD></TR>");
			else buf.append("<TR><TD ALIGN=RIGHT>Group: </TD><TD>(none)</TD></TR>");
			
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
					buf.append("<FORM METHOD=POST>"
							+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='Confirm Merge'>"
							+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=Domain VALUE='" + domainName + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=MergeUserId VALUE='" + mergeUser.id + "'>"
							+ "<TABLE><TR><TD ALIGN=RIGHT>UserId: </TD><TD>" + mergeUser.id + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Name: </TD><TD>" + mergeUser.getFullName() + "</TD></TR>"
							+ "<TR><TD ALIGN=RIGHT>Email: </TD><TD>" + mergeUser.getEmail() + "</TD></TR>"
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
				buf.append("<FORM METHOD=GET>Enter the UserId of the account to be deleted: "
						+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
						+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + usr.id + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=Domain VALUE='" + domainName + "'>"
						+ "<INPUT TYPE=TEXT NAME=MergeUserId>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Check ID'></FORM>");
			}
		}
		catch (Exception e) {
			buf.append("<br>" + e.toString());
		}
		return buf.toString();
	}

	boolean eligibleToJoin(User user,String domainName) {
		// This method checks to see if the user is eligible to join a new group
		//if (user.hasPremiumAccount() || user.domain==null) return true;
		if (user.hasPremiumAccount()) return true;
		Domain domain = ofy().load().type(Domain.class).filter("domainName", domainName).first().now();
		if (domain == null) return false;
		if (domain.seatsAvailable>0) return true;
		return false;
	}
	
	String groupSelectBox(long myGroupId,String domain) {
		StringBuffer buf = new StringBuffer();
		try {
			Query<Group> groups = ofy().load().type(Group.class).filter("domain",domain);
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
			if (!usr.getEmail().equals(request.getParameter("Email"))) usr.verifiedEmail = false;
			usr.setEmail(request.getParameter("Email"));
			usr.setFirstName(request.getParameter("FirstName"));
			usr.setLastName(request.getParameter("LastName"));
			usr.setLowerCaseName();
			if (usr.roles>roles && roles==0) usr.setPremium(false);
			usr.roles = roles;
			if (usr.roles>7) usr.setPremium(true);
			
			Domain domain = ofy().load().type(Domain.class).filter("domainName",usr.domain).first().now();
			if (usr.roles>15) domain.addAdmin(usr.id);
			else domain.removeAdmin(usr.id);
			ofy().save().entity(domain);
			
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
	
	protected static void mergeAccounts(User toUser,User fromUser) {
		// find all transactions for fromUser and credit to toUser:
		if (toUser.getFirstName().isEmpty()) toUser.setFirstName(fromUser.getFirstName());
		if (toUser.getLastName().isEmpty()) toUser.setLastName(fromUser.getLastName());
		if (toUser.getEmail().isEmpty() && fromUser.verifiedEmail) toUser.setEmail(fromUser.getEmail());
		if (toUser.myGroupId==0) {
			toUser.myGroupId = fromUser.myGroupId;
			toUser.notifyDeadlines = toUser.myGroupId>0?(toUser.notifyDeadlines || fromUser.notifyDeadlines):false;
			toUser.smsMessageDevice = toUser.myGroupId>0?fromUser.smsMessageDevice:null;
		}
		toUser.roles = fromUser.roles>toUser.roles?fromUser.roles:toUser.roles;
		toUser.setLowerCaseName();
		
		ofy().save().entity(toUser);
		
		List<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",fromUser.id).list();
		for (HWTransaction h:hwTransactions) h.userId = toUser.id;
		ofy().save().entities(hwTransactions);
		
		List<PracticeExamTransaction> peTransactions = ofy().load().type(PracticeExamTransaction.class).filter("userId",fromUser.id).list();
		for (PracticeExamTransaction p:peTransactions) p.userId = toUser.id;
		ofy().save().entities(peTransactions);
		
		List<QuizTransaction> qTransactions = ofy().load().type(QuizTransaction.class).filter("userId",fromUser.id).list();
		for (QuizTransaction q:qTransactions) q.userId = toUser.id;
		ofy().save().entities(qTransactions);
		
		List<VideoTransaction> vTransactions = ofy().load().type(VideoTransaction.class).filter("userId",fromUser.id).list();
		for (VideoTransaction v:vTransactions) v.userId = toUser.id;
		ofy().save().entities(vTransactions);
		
		List<Group> myGroups = ofy().load().type(Group.class).filter("instructorId",fromUser.id).list();
		for (Group g:myGroups) g.instructorId=toUser.id;
		ofy().save().entities(myGroups);
		
		fromUser.setAlias(toUser.id);  // diverts future logins to the new UserId
		fromUser.myGroupId=0;          // removes old userId from the group to avoid duplicate gradebook entries
		ofy().save().entity(fromUser);
	}
}

