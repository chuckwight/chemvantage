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
		if (m==null) m = new EmailMessage("subject line","message body text",false);  // in case no messages exist yet
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest==null) userRequest = "";
		
		String msg = "";
		String subjectLine = request.getParameter("SubjectLine");
		if (subjectLine==null) subjectLine = m.subjectLine;
		String text = request.getParameter("Text");
		if (text == null) text = m.text;
		boolean isActive = Boolean.parseBoolean(request.getParameter("IsActive"));
		
		List<Key<EmailMessage>> msgKeys = new ArrayList<Key<EmailMessage>>();
		Key<EmailMessage> mKey = Key.create(m);
		List<Contact> contacts = new ArrayList<Contact>();
		
		switch (userRequest) {
		case "Save New Message":
			m = new EmailMessage(subjectLine,text,isActive);
			ofy().save().entity(m).now();
			msg = "The message was saved.";
			break;
		case "Update This Message":
			m.subjectLine = subjectLine;
			m.text = text;
			m.isActive = isActive;
			ofy().save().entity(m).now();
			msg = "The message was updated OK.";
			break;
		case "Delete This Message":
			msgKeys = ofy().load().type(EmailMessage.class).order("created").keys().list();
			if (m.lastRecipientCreated.getTime()==0L) {  // message has never been sent
				ofy().delete().entity(m).now();
				msg = "The message was deleted OK. The text remains in case you want to save it again.";
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
				boolean testOnly = true;
				String firstName = request.getParameter("FirstName");
				String lastName = request.getParameter("LastName");
				String email = request.getParameter("Email");
				contacts.add(new Contact(firstName,lastName,email));
				int count = send10Messages(m,testOnly,contacts);
				msg = count + " test message was sent OK.";
			} catch (Exception e) {
				msg = "Send failed. " + e.toString() + " " + e.getMessage();
			}
			break;
		case "Send 10 Messages":
			try {
				boolean testOnly = false;
				contacts = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).limit(10).list();
				send10Messages(m,testOnly,contacts);
			} catch (Exception e) {}
			return;
		case "Send 50 Messages":
			int nMessages = 50;
			try {
				int nAvailable = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).count();
				int nSending = nAvailable > nMessages? nMessages:nAvailable;
				int nTasks = nSending/10 + (nSending%10==0?0:1);
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
			break;
		case "Send 100 Messages":  // this task is fully automated by cron.yaml
			if (request.getServerName().contains("dev-vantage")) break;  // don't run this every day from the dev server
			int nMessagesToSend = 100;
			List<EmailMessage> messages = ofy().load().type(EmailMessage.class).order("created").list();
			for (EmailMessage message : messages) {
				if (!message.isActive) continue;
				try {
					int nAvailable = ofy().load().type(Contact.class).filter("created >",message.lastRecipientCreated).count();
					int nSending = nAvailable > nMessagesToSend? nMessagesToSend:nAvailable;
					int nTasks = nSending/10 + (nSending%10==0?0:1);
					int i = 0;
					for (i=0; i<nTasks;i++) {
						long delayMillis = i * 60000L;
						QueueFactory.getDefaultQueue().add(withUrl("/messages").param("UserRequest","Send 10 Messages").param("MessageId",String.valueOf(m.id)).countdownMillis(delayMillis));
					}
					nAvailable -= nSending;
					if (nAvailable == 0) {  // deactivate the message if it has been sent to all eligible recipients
						message.isActive = false;
						ofy().save().entity(message);
						continue;
					}
					
					nMessagesToSend -= nSending;
					if (nMessagesToSend == 0) break;  // end the loop through messages if all 100 messages have been sent
				} catch (Exception e) {
				}
			}
			break;
		default:
		}
		
		buf.append(editMessage(subjectLine,text,m.id,m.isActive));
		if (!msg.isEmpty()) buf.append("<br/>" + msg + "<br/>");
		if (m!=null) buf.append(sendMessage(m));
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Subject.getHeader(user) + buf.toString() + Subject.footer);
	}

	String editMessage(String subjectLine, String text, long messageId, boolean isActive) {
		return "<div style='width: 350px;border-style:solid;border-width: 1px;padding-left:25px;'>" + subjectLine + "</div>"
			+ "<div style='width: 750px;height: 300px;border-style: solid;border-width: 1px;padding: 25px;'>"
			+ salutationText(null) + (text.isEmpty()?"A preview of the message will be shown here.":text) + unsubscribeText(null) + "</div>"
			+ "<h4>Edit the message here:</h4>"
			+ "<form method=post action=/messages>"
			+ "<input type=text size=40 name=SubjectLine placeholder='Email subject line (plain text)' value='" + subjectLine + "'>"
			+ "<label><input type=checkbox name=IsActive value=true " + (isActive?"CHECKED":"") + " /> This message is active</label><br/>"
			+ "<textarea name=Text rows=10 cols=100>" + text + "</textarea><br/>"
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
		int nAvailable = ofy().load().type(Contact.class).filter("unsubscribed",false).filter("created >",m.lastRecipientCreated).count();
		boolean unsent = m.lastRecipientCreated == null || m.lastRecipientCreated.getTime() == 0L;
		return "<h4>Send This Message</h4>"
			+ "You have " + nContacts + " contacts in the database, " + nUnsubscribed + " of whom have unsubscribed from your messages.<br/>"
			+ "This message " 
			+ (unsent?"has not yet been sent to any contacts.":"can be sent to as many as " + nAvailable + " more contacts in batches of up to 50 messages.") 
			+ "<br/>"
			+ "<form method=post action=/messages>"
			+ "<input type=hidden name=MessageId value=" + m.id + " />"
			+ "<input type=submit name=UserRequest value='Send 50 Messages' />&nbsp;"
			+ "<input type=submit name=UserRequest value='Send 1 Test Message' /> to "
			+ "<input type=text size=7 name=FirstName value=Chuck /> "
			+ "<input type=text size=7 name=LastName value=Wight /> "
			+ "<input type=text name=Email value='chuck.wight@gmail.com' />"
			+ "</form>";		
	}
	
	int send10Messages(EmailMessage m,boolean testOnly,List<Contact> contacts) throws Exception {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		int count = 0;
		for (Contact c : contacts) {
			try {
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage LLC"));
				msg.addRecipient(Message.RecipientType.TO, new InternetAddress(c.email, c.getFullName()));
				msg.setSubject(m.subjectLine);
				msg.setContent(salutationText(c) + m.text + unsubscribeText(c) ,"text/html");
				Transport.send(msg);
			} catch (Exception e) {
			}
			if (!testOnly) m.lastRecipientCreated = c.created;
			count++;
			Thread.sleep(1000);  // slows execution to 1 message per second
		}
		ofy().save().entity(m).now();
		return count;
	}
	
	String unsubscribeText(Contact c) {
		return "<span style='font-size: small;'><a href=https://www.chemvantage.org/unsubscribe?k=" 
				+ (c==null?"":Key.create(c).toLegacyUrlSafe()) 
				+ ">Unsubscribe</a></span>";
	}
	
	String salutationText(Contact c) {
		if (c==null) c = new Contact("Edwin","Strangeglove","strange@example.com");
		return c.lastName==null || c.lastName.isEmpty()? "" : ("Dr. " + c.lastName + ", ");
	}
}
