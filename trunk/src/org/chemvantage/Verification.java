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
			
			if (userRequest.equals("Save")) {
				String firstName = request.getParameter("FirstName");
				String lastName = request.getParameter("LastName");
				String email = request.getParameter("Email");
				if (firstName != null) user.setFirstName(firstName);
				if (lastName != null) user.setLastName(lastName);
				if (email != null) user.setEmail(email);
				user.lastLogin = new Date();
				ofy.put(user);
				out.println(Home.getHeader(user) + personalInfoForm(user,false) + Home.footer);
			} else if (userRequest.equals("Verify My Email Address")) {
				user.lastLogin = new Date();
				ofy.put(user);
				if (user.email.substring(user.email.indexOf("@")).toLowerCase().equals("@gmail.com")) {
					response.sendRedirect(UserServiceFactory.getUserService().createLoginURL("/Verification"));
				} else out.println(Home.getHeader(user) + personalInfoForm(user,verificationEmailSent(user,request)) + Home.footer);
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
					+ "For details, see our <a href=/w3c/privacy.html>Privacy Policy</a>.<br>"
					+ "In order for ChemVantage to function properly as a learning resource, "
					+ "we need to associate your name and email address with your account. "
					+ "This is important for protecting <i>you</i> by making it difficult "
					+ "for someone else to impersonate you or tamper with your account.<p>");

			buf.append("<FORM NAME=Info ACTION=Verification METHOD=POST>");
			buf.append("<TABLE>");
			if (user.requiresUpdates()) buf.append("<TR><TD></TD><TD><FONT COLOR=RED SIZE=-2>(all fields are required)</FONT></TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>First Name:</TD><TD>" + (user.firstName.isEmpty()?"<INPUT NAME=FirstName VALUE='" + CharHider.quot2html(user.firstName) + "'>":user.firstName) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Last Name:</TD><TD>" + (user.lastName.isEmpty()?"<INPUT NAME=LastName VALUE='" + CharHider.quot2html(user.lastName) + "'>":user.lastName) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Email address:</TD><TD>" + (emailRequired?"<INPUT NAME=Email>":user.email));
			if (!emailRequired && !user.verifiedEmail){
				buf.append(" (unverified) ");
				if (verificationEmailSent) buf.append("<br><FONT COLOR=RED>A verification email has been sent to your address.</FONT><br>");
				else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Verify My Email Address'><br>");
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
			if (nameRequired || emailRequired) {
				buf.append("<TR><TD COLSPAN=2><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save'></TD></TR></TABLE>");
			} else {  // all information is current
				buf.append("</TABLE><h3>Any Corrections Needed?</h3>"
						+ "If your name and/or email shown above is not correct, please send a message to "
						+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> giving detailed "
						+ "instructions for any changes that are necessary.<p>" 
						+ "<a href=/Home>Go to the ChemVantage Home Page now</a><p>");
			}
			buf.append("</FORM");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
}