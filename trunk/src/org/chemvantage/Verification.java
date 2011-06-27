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
import java.util.Date;
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

import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;


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

			if (userRequest.equals("Save My Email")) {
				if (!user.email.equals(request.getParameter("Email"))) user.verifiedEmail = false;
				user.setEmail(request.getParameter("Email"));
				user.lastLogin = new Date();
				ofy.put(user);
				if (user.email.substring(user.email.indexOf("@")).toLowerCase().equals("@gmail.com")) {
					response.sendRedirect(UserServiceFactory.getUserService().createLoginURL("/Verification"));
				} else out.println(Home.getHeader(user) + personalInfoForm(user,verificationEmailSent(user,request)) + Home.footer);
			} else if (userRequest.equals("Save My Name")) {
				user.setFirstName(request.getParameter("FirstName"));
				user.setLastName(request.getParameter("LastName"));
				ofy.put(user);
				out.println(Home.getHeader(user) + personalInfoForm(user,false) + Home.footer);
			} else if (userRequest.equals("Verify My Email Address")) {
				user.lastLogin = new Date();
				ofy.put(user);
				if (user.email.substring(user.email.indexOf("@")).toLowerCase().equals("@gmail.com")) {
					response.sendRedirect(UserServiceFactory.getUserService().createLoginURL("Verification"));
				} else out.println(Home.getHeader(user) + personalInfoForm(user,verificationEmailSent(user,request)) + Home.footer);
			} else if (userRequest.equals("Verify With Google")) {
				user.lastLogin = new Date();
				ofy.put(user);
				response.sendRedirect(UserServiceFactory.getUserService().createLoginURL("Verification"));
			} else if (userRequest.equals("Remind Me Later")) {
				user.lastLogin = new Date();
				ofy.put(user);
				response.sendRedirect("Home");
			} else {
				out.println(Home.getHeader(user) + personalInfoForm(user,false) + Home.footer);
			}
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
			msg.addRecipient(Message.RecipientType.CC,new InternetAddress("admin@chemvantage.org", "ChemVantage"));
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
	
	String personalInfoForm(User user,boolean verificationEmailSent) {
		StringBuffer buf = new StringBuffer();
		boolean nameRequired = user.firstName.isEmpty() || user.lastName.isEmpty();
		boolean emailRequired = user.email.isEmpty();
		try {
			buf.append("<h2>Your Contact Information</h2>"
					+ "ChemVantage protects your personal information. "
					+ "For details, see our <a href=About#terms>Privacy Policy</a>.<br>"
					+ "In order for ChemVantage to function properly as a learning resource, "
					+ "we need to associate your name and email address with your account. "
					+ "This is important for protecting <i>you</i> by making it difficult "
					+ "for someone else to impersonate you or tamper with your account.");

			buf.append("<FORM NAME=Info ACTION=Verification METHOD=POST>");

			//  Enter or verify the user's name
			buf.append("<h3>Your Name" + (nameRequired?"":": " + user.getFullName()) + "</h3>"
					+ "When you join a ChemVantage group, your name is visible to your instructor for the purpose "
					+ "of maintaining group enrollments and for reporting ChemVantage scores on assignments. "
					+ "If you are an instructor, your name is visible to all users for the purpose of identifying "
					+ "your ChemVantage groups.<p>");
			if (nameRequired) {
				buf.append("<FONT COLOR=RED>Please enter your first (given) and last (family) names:</FONT><br>"
						+ "First Name: <INPUT NAME=FirstName VALUE='" + CharHider.quot2html(user.firstName) + "'><br>"
						+ "Last Name: <INPUT NAME=LastName VALUE='" + CharHider.quot2html(user.lastName) + "'><br>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save My Name'><br>");
			}

			// Enter or verify the user's email address
			buf.append("<h3>Your Email Address" + (emailRequired?"":": " + user.email + (user.verifiedEmail?" (verified)":" <FONT COLOR=RED>(unverified)</FONT>")) + "</h3>");
			if (verificationEmailSent) {
				buf.append("<FONT COLOR=RED>An email message was sent to " + user.email
						+ " with a coded link to verify your email address with ChemVantage.</FONT><p>");
			}
			else if (!emailRequired && !user.verifiedEmail) {
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Verify My Email Address'><p>");
			}
			buf.append("ChemVantage uses your email address in the following ways:<UL>"
					+ "<LI>As a unique identifier to distinguish you from other users with similar names"
					+ "<LI>As a means of communicating with you when necessary to answer your questions or "
					+ "to remind you (at your option) of assignment deadlines."
					+ "<LI>Your email address is visible to your instructor for sending you messages.</UL>");
			if (emailRequired) {
				buf.append("<FONT COLOR=RED>Please enter your preferred email address: </FONT>"
						+ "<INPUT NAME=Email>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save My Email'><p>");
				buf.append("or <a href=" + UserServiceFactory.getUserService().createLoginURL("/Verification") 
						+ ">use my Google/GMail account to authenticate to ChemVantage</a><p>");
			}

			// View the user's SMS message device, if registered (see the Scores servlet)
			if (user.smsMessageDevice!=null && !user.smsMessageDevice.isEmpty()) {
				try {
					Long.parseLong(user.smsMessageDevice.substring(0,10));
					buf.append("<h3>Your SMS/Text Address: " + user.smsMessageDevice + "</h3>"
							+ "You have registered your SMS device to receive reminders of ChemVantage assignment deadlines.<p>");
				} catch (Exception e2) {
					buf.append("<h3>Your SMS/Text Address: " + user.smsMessageDevice.substring(5) + "</h3>"
							+ "You have registered your SMS device address but have not completed the "
							+ "confirmation process.<p>");
				}
				buf.append("You can manage your SMS notification options on the group <a href=Scores>Scores</a> page.<br>");	
			}

			// Final wrap-up
			if (!nameRequired && !emailRequired && user.verifiedEmail) {  // all information is current
				buf.append("<h3>Any Corrections Needed?</h3>"
						+ "If your name and/or email shown above is not correct, please send a message to "
						+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> giving detailed "
						+ "instructions for any changes that are necessary. Please include your userId "
						+ "below with the message.<p>");
			} else {                                        // something is still incomplete
				buf.append("<h3>Not Now</h3>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Remind Me Later'><p>"
						+ "If you need assistance completing the registration of your name or email address, "
						+ "please send a message to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> "
						+ "with your request. Be sure to include your userId below with the message.<p>");
			}
			buf.append("UserId: " + user.id);
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
}