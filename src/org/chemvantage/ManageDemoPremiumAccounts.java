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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

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

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class ManageDemoPremiumAccounts extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet allows sends invitations to get a free demo level account.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = "";
		
		if (userRequest.equals("PurgeDemoPremiumAccounts")) {
			purgeDemoPremiumAccounts();
			return;
		}
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.getHeader(user) + freeUpgradeDemoForm(user,request) + Home.footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		if ("Send Invitations".equals(request.getParameter("UserRequest"))) {
			Queue queue = QueueFactory.getDefaultQueue();
			queue.add(withUrl("/ManageDemoPremiumAccounts").param("GroupId",request.getParameter("GroupId")).param("Message", request.getParameter("Message")).param("Subject", request.getParameter("Subject")));
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.println(Home.getHeader(User.getInstance(request.getSession(true))) + "Task sent to default task queue for execution." + Home.footer);
			return;
		}
		sendInvitations(request);
	}

	void purgeDemoPremiumAccounts() {
		Date now = new Date();
		List<DemoPremiumAccount> accounts = ofy.query(DemoPremiumAccount.class).filter("isActive", true).list();
		for (DemoPremiumAccount a : accounts) {
			try {
				if (a.endDate.after(now)) continue;  // still in trial period
				User u = ofy.find(User.class,a.userId);
				PayPalIPN p = ofy.query(PayPalIPN.class).filter("userId", u.id).get();
				if (p == null) u.setPremium(false);  // no purchase record; revoke the premium account
				a.setIsActive(false); // deactivate (but don't delete) the demoPremiumAccount record
			} catch (Exception e) {
				a.setIsActive(false); // deactivate (but don't delete) the demoPremiumAccount record
			}
		}
	}
	
	String freeUpgradeDemoForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		String defaultMessage = "FREE TRIAL - CHEMVANTAGE PREMIUM ACCOUNT UPGRADE <p>\n\n"
			+ "You're invited to try a ChemVantage premium account for 2 weeks at no charge. <br>\n"
			+ "Click the link below (or paste the complete URL into your browser) to activate this offer. <p>\n\n"
			+ "You get: <br>\n"
			+ " - reminders (email or SMS) on the morning of each assignment deadline <br>\n"
			+ " - a cool spell checker for fill-in-the-blank quiz questions <br>\n"
			+ " - a 15 minute countdown timer for quizzes <p>\n\n";
		
		buf.append("<h2>Offer Demo Premium Accounts</h2>");
		buf.append("This form allows the ChemVantage administrator to send email invitations to every "
			+ "student in a group to activate a free 2-week trial premium account.<p>");
		Query<Group> groups = ofy.query(Group.class);
		if (groups.count()==0) {
			buf.append("There are no ChemVantage groups.");
			return buf.toString();
		}
		buf.append("<FORM ACTION=ManageDemoPremiumAccounts METHOD=POST>"
				+ "Select a group: <SELECT NAME=GroupId>");
		for (Group g : groups) buf.append("<OPTION VALUE=" + g.id + ">" + g.description + " (" + User.getBothNames(g.instructorId) + ")</OPTION>");
		buf.append("</SELECT><br>");
		
		buf.append("<INPUT NAME=Subject SIZE=50 VALUE='Free Trial Premium Account Upgrade'><br>");
		buf.append("<TEXTAREA NAME=Message ROWS=20 COLS=80 WRAP=SOFT>");
		buf.append(defaultMessage);
		buf.append("</TEXTAREA><br><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send Invitations'>");
		buf.append("</FORM>");
		return buf.toString();
	}

	String sendInvitations(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		String baseMessage = request.getParameter("Message");
		String url = request.getRequestURL().toString();
		url = url.substring(0,url.lastIndexOf("/"));
		try {
			long groupId = Long.parseLong(request.getParameter("GroupId"));
			Group group = ofy.get(Group.class,groupId);
			for (String userId : group.memberIds) {
				try {
					User u = ofy.get(User.class,userId);
					if (u.email.isEmpty() || u.hasPremiumAccount()) continue;
					String code = Integer.toString(new Key<User>(User.class,u.id).hashCode());
					Message msg = new MimeMessage(session);
					msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
					msg.addRecipient(Message.RecipientType.TO,new InternetAddress(u.email,u.getBothNames()));
					msg.setSubject(request.getParameter("Subject"));
					msg.setContent(baseMessage.concat(url).concat("/Activate?UserId=" + u.id + "&Code=" + code),"text/html");
					Transport.send(msg);
				} catch (Exception e) {
					continue;
				}
			}
		} catch (Exception e) {
			return "The operation failed. " + e.toString();
		}
		return buf.toString();
	}
}
