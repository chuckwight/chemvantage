/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2022 ChemVantage LLC
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
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

@WebServlet("/messages")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class ManageMessages extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	public ManageMessages() {
        super();
        // TODO Auto-generated constructor stub
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request,response);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		UserService userService = UserServiceFactory.getUserService();
		String userId = userService.getCurrentUser().getUserId();
		User user = new User("https://"+request.getServerName(), userId);
		user.setIsChemVantageAdmin(true);
		user.setToken();
		
		StringBuffer buf = new StringBuffer("<h2>Manage Messages</h2>");
		
		String subjectLine = request.getParameter("SubjectLine");
		String text = request.getParameter("Text");
		long messageId = 0L;
		boolean allowUpdate = false;
		
		EmailMessage m = null;
		if (text==null) {
			m = ofy().load().type(EmailMessage.class).order("-created").first().now();
			text = m.text;
			subjectLine = m.subjectLine;
			messageId = m.id;
		} else {
			try {
				allowUpdate = true;
				messageId = Long.parseLong(request.getParameter("MessageId"));
				m = ofy().load().type(EmailMessage.class).id(messageId).safe();
			} catch (Exception e) {}
		}
		
		boolean allowDelete = m != null && m.lastRecipientCreated.equals(new Date(0L));
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		String msg = "";
		
		switch (userRequest) {
		case "Save New Message":
			m = new EmailMessage(subjectLine,text);
			ofy().save().entity(m).now();
			msg = "The message was saved.";
			break;
		case "Update This Message":
			if (m != null) {
				m.subjectLine = subjectLine;
				m.text = text;
				ofy().save().entity(m).now();
				msg = "The message was updated OK.";
			}
			break;
		case "Delete This Message":
			if (m != null && m.lastRecipientCreated.after(new Date(0L))) {
				ofy().delete().entity(m).now();
				msg = "The message was deleted OK.";
			}
		break;
		case "Send 50 Messages":
			try {
				m = ofy().load().type(EmailMessage.class).id(messageId).safe();
				int count = send50Messages(m);
				msg = count + " messages were sent OK.";
			} catch (Exception e) {
				msg = "Send failed. " + e.toString() + " " + e.getMessage();
			}
		default:
		}
		
		buf.append(createNewMessage(subjectLine,text,allowUpdate,allowDelete,messageId));
		if (!msg.isEmpty()) buf.append("<br/>" + msg + "<br/>");
		if (m!=null) buf.append(sendMessage(m));
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Subject.getHeader(user) + buf.toString() + Subject.footer);
	}

	String createNewMessage(String subjectLine, String text, boolean allowUpdate, boolean allowDelete, long messageId) {
		return "<h4>Create New Message</h4>"
			+ "<div style='width: 250px;border-style:solid;border-width: 1px;padding-left:25px;'>" + subjectLine + "</div>"
			+ "<div style='width: 600px;height: 300px;border-style: solid;border-width: 1px;padding: 25px;'>"
			+ (text.isEmpty()?"A preview of the message will be shown here.":text) + "</div>"
			+ "<br/>Edit HTML here:<br/>"
			+ "<form method=post action=/messages>"
			+ "<input type=text size=40 name=SubjectLine placeholder='Email subject line (plain text)' value='" + subjectLine + "'><br/>"
			+ "<textarea name=Text rows=10 cols=80>"
			+ text
			+ "</textarea><br/>"
			+ "<input type=hidden name=MessageId value=" + messageId + ">"
			+ "<input type=submit name=UserRequest value='Preview Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Save New Message'>&nbsp;"
			+ (allowUpdate?"<input type=submit name=UserRequest value='Update This Message'>&nbsp;":"")
			+ (allowDelete?"<input type=submit name=UserRequest value='Delete This Message'>&nbsp;":"")
			+ "</form>";
	}
	
	String sendMessage(EmailMessage m) {
		int nRecipients = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created <=",m.lastRecipientCreated).count();
		int nAvailable = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).count();
		return "<h4>Send This Message</h4>"
			+ "This message has been sent to " + nRecipients + " recipients, and can be sent to as many as " 
			+ nAvailable + " more in batches of up to 50 per day.<br/>"
			+ "<form method=post action=/messages>"
			+ "<input type=hidden name=MessageId value=" + m.id + ">"
			+ "<input type=submit name=UserRequest value='Send 50 Messages'>"
			+ "</form>";		
	}
	
	int send50Messages(EmailMessage m) throws Exception {
		List<Contact> contacts = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).limit(50).list();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		int count = 0;
		for (Contact c : contacts) {
			try {
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage LLC"));
				msg.addRecipient(Message.RecipientType.TO, new InternetAddress(c.email, c.getFullName()));
				msg.setSubject(m.subjectLine);
				msg.setContent(m.text,"text/html");
				Transport.send(msg);
				count++;
			} catch (Exception e) {
			}
			m.lastRecipientCreated = c.created;
		}
		ofy().save().entity(m).now();
		return count;
	}
}
