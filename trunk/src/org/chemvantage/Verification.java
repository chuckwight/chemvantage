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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;


public class Verification extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet verifies a user-supplied email address.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {

		// This method receives a coded URL to verify a user's email address
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		try {
			String userId = request.getParameter("UserId");
			String code = request.getParameter("Code");
			if (userId.isEmpty() || code.isEmpty()) throw new Exception();
			User user = ofy.get(User.class,userId);
			user.verifiedEmail = (Integer.parseInt(code) == new Key<User>(User.class,userId).hashCode());
			ofy.put(user);
			out.println(Login.header + verifiedEmail(user.verifiedEmail) + Login.footer);
		} catch (Exception e) {	
			doPost(request,response);  // view current information if logged in; else go to Login page
		}	
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}

			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("Save My Information")) {
				String firstName = request.getParameter("FirstName");
				String lastName = request.getParameter("LastName");
				String email = request.getParameter("Email");
				if (firstName != null && !firstName.isEmpty()) user.setFirstName(firstName);
				if (lastName != null && !lastName.isEmpty()) user.setLastName(lastName);
				if (email != null && !email.isEmpty()) {
					user.setEmail(email);
					user.verifiedEmail = false;
				}
				user.lastLogin = new Date();
				ofy.put(user);
				boolean requiresVerification = !user.verifiedEmail && !user.email.isEmpty();
				out.println(Home.getHeader(user) + personalInfoForm(user,requiresVerification?verificationEmailSent(user,request):false,request) + Home.footer);
			} else if (userRequest.equals("Verify My Email Address")) {
				try {
					user.verifiedEmail = user.email.equals(UserServiceFactory.getUserService().getCurrentUser().getEmail());
				} catch(Exception e){
				}
				user.lastLogin = new Date();
				ofy.put(user);
				out.println(Home.getHeader(user) + (user.verifiedEmail?personalInfoForm(user,false,request):personalInfoForm(user,verificationEmailSent(user,request),request)) + Home.footer);
			} else if (userRequest.equals("JoinGroup")) {
				try {
					long newGroupId = Long.parseLong(request.getParameter("GroupId"));
					user.changeGroups(newGroupId);
				} catch (Exception e2) {
				}
				out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
			} else if (userRequest.equals("Get Authorization Code")) {

				if (mergeAuthCodeSent(user,request)) out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
				else out.println("Sorry, the authorization code could not be sent. Please try again later.");
			} else if (userRequest.equals("Merge This Account With Mine")) {
				try {
					String fromUserId = request.getParameter("FromAccount");
					String toUserId = request.getParameter("ToAccount");
					int code = Integer.parseInt(request.getParameter("Code"));
					if (code==Math.abs((new Key<User>(User.class,fromUserId).toString() + new Key<User>(User.class,toUserId)).toString().hashCode())) {
						User fromUser = ofy.get(User.class,fromUserId);
						User toUser = ofy.get(User.class,toUserId);
						Admin.mergeAccounts(toUser,fromUser);
						out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
					} else out.println("The authorization code was not valid.");
				} catch (Exception e) {
					out.println("The authorization code was not valid.");
				}
			} else out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
		} catch (Exception e) {
		}
	}

	private boolean verificationEmailSent(User user,HttpServletRequest request) {
		if (user.verifiedEmail) return false;  // no verification email is necessary
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = "<h3>ChemVantage Email Verification</h3>"
			+ "To confirm that this is the email address associated with your ChemVantage account, "
			+ "please click the link below or copy/paste it into your web browser address bar.<p>"
			+ request.getRequestURL().toString() + "?UserId=" + user.id + "&Code="
			+ new Key<User>(User.class,user.id).hashCode() + "  <p>"
			+ "If you did not request this verification, please do not click the link.<br>" 
			+ "If you need assistance, please reply to admin@chemvantage.org.<p>"
			+ "Thank you.";

		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(user.email, user.getBothNames()));
			msg.setSubject("ChemVantage Email Verification");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	String verifiedEmail(boolean verified) {
		StringBuffer buf = new StringBuffer();
		if (verified) buf.append("<h3>Email Verification Complete</h3>" 
				+ "Thank you for verifying your email address with ChemVantage.");
		else buf.append("<h3>Verification Failed</h3>Sorry, this action was unsuccessful. "
				+ "Please be sure that the complete URL was included in the request by copying " 
				+ "and pasting it into the URL address bar in your browser. If you need assistance, "
				+ "send email to admin@chemvantage.org");
		return buf.toString();
	}

	String personalInfoForm(User user,boolean verificationEmailSent,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		boolean nameRequired = user.firstName.isEmpty() || user.lastName.isEmpty();
		boolean emailRequired = user.email.isEmpty();
		try {
			UserService userService = UserServiceFactory.getUserService();
			if (userService.isUserAdmin()) throw new Exception(); // don't set email for admins logged in as users
			String em = "";
			if (emailRequired) em = userService.getCurrentUser().getEmail();
			if (!(em==null || em.isEmpty())) {
				user.setEmail(em);
				user.verifiedEmail = true;
				ofy.put(user);
				emailRequired = false;
			}
		} catch (Exception e) {}
		try {
			buf.append("<h2>Your ChemVantage Account Information</h2>"
					+ "ChemVantage protects your personal information. For details, see our <a href=/w3c/privacy.html>Privacy Policy</a>.<br>"
					+ "In order for ChemVantage to function properly as a learning resource, we need to associate your name and email address with your account.<br>"
					+ "This is important for protecting <i>you</i> by making it difficult for someone else to impersonate you or tamper with your account.<p>");

			buf.append("<FORM NAME=Info ACTION=Verification METHOD=POST>");
			buf.append("<TABLE>");
			buf.append("<TR><TD ALIGN=RIGHT>First Name:</TD><TD>" + (user.firstName.isEmpty()?"<span style=color:red>*</span><INPUT NAME=FirstName SIZE=50>":user.firstName) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Last Name:</TD><TD>" + (user.lastName.isEmpty()?"<span style=color:red>*</span><INPUT NAME=LastName SIZE=50>":user.lastName) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT VALIGN=TOP>Email:</TD><TD>" + (emailRequired?"<span style=color:red>*</span><INPUT NAME=Email SIZE=50>":user.email));
			if (!emailRequired && !user.verifiedEmail){
				buf.append(" (unverified) ");
				if (verificationEmailSent) buf.append("<br><FONT COLOR=RED>A verification email has been sent to your address.</FONT><br>");
				else if (!nameRequired) buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Verify My Email Address'><br>");
			}
			buf.append("</TD></TR>");
			if (user.smsMessageDevice!=null && !user.smsMessageDevice.isEmpty()) {
				String smsAddress = "";
				try {
					Long.parseLong(user.smsMessageDevice.substring(0,10));
					smsAddress = user.smsMessageDevice;
				} catch (Exception e2) {
					smsAddress = user.smsMessageDevice.substring(5) + " (unverified)";
				}
				buf.append("<TR><TD ALIGN=RIGHT>SMS address:</TD><TD>" + smsAddress + "</TD></TR>");	
			}
			if (user.myGroupId>0L) { // user already belongs to a group
				Group myGroup = ofy.find(Group.class,user.myGroupId);
				buf.append("<TR><TD ALIGN=RIGHT>Group:</TD><TD>" + myGroup.description + " (" + myGroup.getInstructorBothNames() + ")</TD></TR>");
			}

			if (nameRequired || emailRequired) {
				buf.append("<TR><TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save My Information'></TD>"
						+ "<TD>" + (user.requiresUpdates()?"<span style=color:red>* <span style=font-size:smaller>required field</span></span>":"") + "</TD></TR></TABLE></FORM>");
			} else {  // all information is current
				buf.append("</FORM>");
				if (user.myGroupId==0L) { // give the user an opportunity to join a group
					buf.append("<TR><TD ALIGN=RIGHT VALIGN=TOP>Group:</TD><TD>");
					buf.append("<FORM NAME=JoinGroup METHOD=POST ACTION=Verification>");
					buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=JoinGroup>");

					Query<Group> allGroups = ofy.query(Group.class);
					buf.append("<SELECT NAME=GroupId "
							+ "onChange=\"if(confirm('Are you sure that you  want to join this group? "
							+ "This action can only be reversed by an instructor.'))document.JoinGroup.submit();"
							+ "else document.JoinGroup.GroupId[0].selected=true;\">"
							+ "<OPTION VALUE=0>Default group (none)</OPTION>\n");
					for (Group g : allGroups) {
						try {
							buf.append("<OPTION VALUE=" + g.id + ">" + g.description + " (" + g.getInstructorBothNames() + ")</OPTION>\n");
						} catch (Exception e2) {
							continue;
						}
					}
					buf.append("</SELECT></TD></TR></FORM>"
							+ "<TR><TD COLSPAN=2><span id=instructions style='color:red'><br>If you are enrolled in a chemistry class or other group using ChemVantage, "
							+ "please select it here.<br>You will have access to group assignments, and your instructor will have access to your ChemVantage "
							+ "scores.<br>If you are not enrolled in a class, don't change anything! Leave the group selected as 'Default group (none)'</span></TD></TR>");
				}
				buf.append("</TABLE>\n");
				
				buf.append("<h3>Any Corrections Needed?</h3>"
						+ "If your name and/or email shown above is not correct, please send a message to "
						+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><br>giving detailed "
						+ "instructions for any changes that are needed.<p>"); 
				buf.append("<style type='text/css'>a.nav, a.nav:link, a.nav:visited {display:block; width:250px; height:35px; "
						+ "background:red; border:1px solid #000; margin-top:2px; text-align:center; text-decoration:none; "
						+ "font-family:verdana, arial, sans-serif; font-size:15px; color:white; line-height:35px; overflow:hidden;}"
						+ "a.nav:hover {color:#fff; background:#800;}</style>");
				buf.append("<a class='nav' href='/Home'>Go To The Home Page Now</a>");
			}
			
			buf.append("<p><a href=# style='font-size:smaller' onClick=\"javascript: document.getElementById('multi').style.display=''\">I can't find my account</a>"
					+ "<div id='multi' style='display:none'>"
					+ "<h3>Do You Have Multiple ChemVantage Accounts?</h3>"
					+ "This is fairly common because there are multiple ways of creating ChemVantage accounts.<br>"
					+ "If you think you have more than one account and you don't see options below for merging<br>"
					+ "them below, first check each account to make sure that the email address has been verified<br>"
					+ "by ChemVantage. If that doesn't work, send a detailed account merge request to "
					+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<p>");
			if (user.verifiedEmail && !user.requiresUpdates()) {
				// This section finds accounts with duplicate verified email addresses for immediate account merging
				List<User> duplicateEmails = ofy.query(User.class).filter("email",user.email).list();
				buf.append("<TABLE>");
				for (User u : duplicateEmails) {
					if (u.id.equals(user.id) || !u.verifiedEmail || u.alias!=null) continue;
					int code = Math.abs((new Key<User>(User.class,u.id).toString() + new Key<User>(User.class,user.id).toString()).hashCode());
					int i = duplicateEmails.indexOf(u);
					String consumerKey = u.authDomain.equals("BLTI")?u.id.substring(0, u.id.indexOf(":")):u.authDomain;
					buf.append("<TR><TD>" + u.getFullName() + " (" + u.email + ") - authorization domain=" + consumerKey + "</TD>"
							+ "<TD><FORM NAME=DuplicateEmail" + i + " ACTION=Verification METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=FromAccount VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=ToAccount VALUE='" + user.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=Code VALUE='" + code + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Merge This Account With Mine' "
							+ "onClick=\"javascript: document.DuplicateEmail" + i + ".UserRequest.style='display:none';return confirm('Are you sure? This action cannot be undone.')\">"
							+ "</FORM></TD></TR>");
				}
				// This section finds duplicate names, sends a code to the user's email address and accepts it to initiate an account merge.
				List<User> duplicateNames = ofy.query(User.class).filter("lowercaseName",user.lowercaseName).list();
				for (User u : duplicateNames) {
					if (u.id.equals(user.id) || duplicateEmails.contains(u) || !u.verifiedEmail || u.alias!=null) continue;
					int i = duplicateNames.indexOf(u);
					String consumerKey = u.authDomain.equals("BLTI")?u.id.substring(0, u.id.indexOf(":")):u.authDomain;
					buf.append("<TR><TD>" + u.getFullName() + " (" + u.email + ") - authorization domain=" + consumerKey + "</TD>"
							+ "<TD><FORM NAME=DuplicateName" + i + " ACTION=Verification METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=FromAccount VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=ToAccount VALUE='" + user.id + "'>");
					if ("Get Authorization Code".equals(request.getParameter("UserRequest")) && u.id.equals(request.getParameter("FromAccount"))) {
						buf.append("</TD></TR><TR><TD COLSPAN=2>"
								+ "<span style=color:red>An email has been sent to " + u.email + " with an authorization code. Please enter it here: </span>"
								+ "<INPUT TYPE=TEXT NAME=Code><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Merge This Account With Mine'>");
					} else {
						buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Get Authorization Code'>");
					}
					buf.append("</FORM></TD></TR>");
				}
				buf.append("</TABLE>");
			}
			buf.append("</div>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	boolean mergeAuthCodeSent(User user,HttpServletRequest request) {
		if (!user.verifiedEmail) return false;
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			User fromAccountUser = ofy.get(User.class,request.getParameter("FromAccount"));
			int code = Math.abs((new Key<User>(User.class,fromAccountUser.id).toString() + new Key<User>(User.class,user.id)).toString().hashCode());
			String msgBody = "<h3>ChemVantage Account Merge Request</h3>"
				+ "To complete the requested account merge request, please copy/paste the following "
				+ "authorization number into the request form:<p>"
				+ code + "<p>"
				+ "If you did not request this authorization code, please ignore this message.<br>" 
				+ "For assistance, please reply to admin@chemvantage.org.<p>"
				+ "Thank you.";
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(fromAccountUser.email, fromAccountUser.getBothNames()));
			msg.setSubject("ChemVantage Authorization Number");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}