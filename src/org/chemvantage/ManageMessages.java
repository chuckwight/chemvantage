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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;

@WebServlet("/messages")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class ManageMessages extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request,response);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		User user = null;
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			user.setToken();
		} catch (Exception e) {
			user = new User();
		}
		
		StringBuffer buf = new StringBuffer("<h2>Manage Messages</h2>");
		
		EmailMessage m = null;
		try {  // load the current or requested message
			m = ofy().load().type(EmailMessage.class).id(Long.parseLong(request.getParameter("MessageId"))).safe();
		} catch (Exception e) {   // load the newest message (first load)
			m = ofy().load().type(EmailMessage.class).order("-created").first().now();
		}
		if (m==null) m = new EmailMessage("subject line","message body text");  // in case no messages exist yet
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		String msg = "";
		String subjectLine = request.getParameter("SubjectLine");
		if (subjectLine==null) subjectLine = m.subjectLine;
		String text = request.getParameter("Text");
		if (text == null) text = m.text;
		
		List<Key<EmailMessage>> msgKeys = new ArrayList<Key<EmailMessage>>();
		Key<EmailMessage> mKey = Key.create(m);
		
		switch (userRequest) {
		case "Save New Message":
			m = new EmailMessage(subjectLine,text);
			ofy().save().entity(m).now();
			msg = "The message was saved.";
			break;
		case "Update This Message":
			m.subjectLine = subjectLine;
			m.text = text;
			ofy().save().entity(m).now();
			msg = "The message was updated OK.";
			break;
		case "Delete This Message":
			if (m.lastRecipientCreated.getTime()==0L) {  // message has never been sent
				ofy().delete().entity(m).now();
				msg = "The message was deleted OK.";
			} else msg = "This message cannot be deleted because it has been sent already.";
			break;
		case "Previous Message":
			msgKeys = ofy().load().type(EmailMessage.class).order("created").keys().list();			
			for (int i=0;i<msgKeys.size();i++) {
				if (msgKeys.get(i).equals(mKey)) {
					if (i==0) msg = "This is the oldest message.";  // don't change m
					else m = ofy().load().key(msgKeys.get(i-1)).now();  // assign m to the following message
					break;
				}
			}
			subjectLine = m.subjectLine;
			text = m.text;
			break;
		case "Next Message":
			msgKeys = ofy().load().type(EmailMessage.class).order("created").keys().list();
			for (int i=0;i<msgKeys.size();i++) {
				if (msgKeys.get(i).equals(mKey)) {  // found the right spot in the list
					if (i==msgKeys.size()-1) msg = "This is the newest message.";  // don't change m
					else m = ofy().load().key(msgKeys.get(i+1)).now();  // assign m to the following message
					break;
				}
			}
			subjectLine = m.subjectLine;
			text = m.text;
			break;
		case "Send 1 Test Message":
			try {
				int count = send10Messages(m,true);
				msg = count + " test message was sent OK.";
			} catch (Exception e) {
				msg = "Send failed. " + e.toString() + " " + e.getMessage();
			}
			break;
		case "Send 10 Messages":
			try {
				send10Messages(m,false);
			} catch (Exception e) {}
			return;
		case "Send 50 Messages":
			int nMessages = 50;
			try {
				int nAvailable = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).count();
				int nSending = nAvailable > nMessages? nMessages:nAvailable;
				int nTasks = nAvailable/10 + nAvailable%10==0?0:1;
				int i = 0;
				msg = "Sending " + nSending + " messages in " + nTasks + " tasks of 10 messages each at 1 task/minute. ";
				for (i=0; i<nTasks;i++) {
					long delayMillis = i * 60000L;
					QueueFactory.getDefaultQueue().add(withUrl("/messages").param("UserRequest","Send 10 Messages").param("MessageId",String.valueOf(m.id)).countdownMillis(delayMillis));
				}
				msg += i + " tasks were launched OK.";
			} catch (Exception e) {
				msg += "Send failed. " + e.toString() + " " + e.getMessage();
			}
		default:
		}
		
		buf.append(editMessage(subjectLine,text,m.id));
		if (!msg.isEmpty()) buf.append("<br/>" + msg + "<br/>");
		if (m!=null) buf.append(sendMessage(m));
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Subject.getHeader(user) + buf.toString() + Subject.footer);
	}

	String editMessage(String subjectLine, String text, long messageId) {
		return "<div style='width: 350px;border-style:solid;border-width: 1px;padding-left:25px;'>" + subjectLine + "</div>"
			+ "<div style='width: 700px;height: 300px;border-style: solid;border-width: 1px;padding: 25px;'>"
			+ (text.isEmpty()?"A preview of the message will be shown here.":text + unsubscribeText(null)) + "</div>"
			+ "<br/>Edit HTML here:<br/>"
			+ "<form method=post action=/messages>"
			+ "<input type=text size=40 name=SubjectLine placeholder='Email subject line (plain text)' value='" + subjectLine + "'><br/>"
			+ "<textarea name=Text rows=10 cols=95>" + text + "</textarea><br/>"
			+ "<input type=hidden name=MessageId value=" + messageId + ">"
			+ "<input type=submit name=UserRequest value='Preview Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Save New Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Update This Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Delete This Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Previous Message'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Next Message'>&nbsp;"
			+ "</form>";
	}
	
	String sendMessage(EmailMessage m) {
		int nContacts = ofy().load().type(Contact.class).count();
		int nUnsubscribed = ofy().load().type(Contact.class).filter("unsubscribed",true).count();
		int nRecipients = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created <=",m.lastRecipientCreated).count();
		int nAvailable = nContacts - nUnsubscribed - nRecipients;
		return "<h4>Send This Message</h4>"
			+ "You have " + nContacts + " contacts in the database, " + nUnsubscribed + " of whom have unsubscribed from your messages.<br/>"
			+ "This message has been sent to " + nRecipients + " contacts, and can be sent to as many as " 
			+ nAvailable + " more in batches of up to 50 per day.<br/>"
			+ "<form method=post action=/messages>"
			+ "<input type=hidden name=MessageId value=" + m.id + ">"
			+ "<input type=submit name=UserRequest value='Send 50 Messages'>&nbsp;"
			+ "<input type=submit name=UserRequest value='Send 1 Test Message'>"
			+ "</form>";		
	}
	
	int send10Messages(EmailMessage m, boolean testOnly) throws Exception {
		List<Contact> contacts;
		if (testOnly) {
			contacts = new ArrayList<Contact>();
			contacts.add(new Contact("Chuck","Wight","admin@chemvantage.org"));
		}
		else contacts = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).limit(10).list();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		int count = 0;
		for (Contact c : contacts) {
			try {
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage LLC"));
				msg.addRecipient(Message.RecipientType.TO, new InternetAddress(c.email, c.getFullName()));
				msg.setSubject(m.subjectLine);
				msg.setContent(m.text + unsubscribeText(c) ,"text/html");
				Transport.send(msg);
			} catch (Exception e) {
			}
			if (!testOnly) m.lastRecipientCreated = c.created;
			count++;
		}
		ofy().save().entity(m).now();
		return count;
	}
	
	String unsubscribeText(Contact c) {
		return "<span style='font-size: small;'><a href=https://www.chemvantage.org/unsubscribe?k=" 
				+ (c==null?"":Key.create(c).toLegacyUrlSafe()) 
				+ ">Unsubscribe</a></span>";
	}
}
