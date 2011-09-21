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
import java.util.ArrayList;
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
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public class Notification extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "ChemVantage servlet sends reminders to premium users on the morning of each assignment deadline.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		try {
			Date now = new Date();
			Date startWindow = new Date(now.getTime() + 43200000); // 12 hr from now in millis
			Date endWindow = new Date(startWindow.getTime() + 21600000);   // 6 hours later
			List<Assignment> assignments = ofy.query(Assignment.class).filter("deadline >",startWindow).filter("deadline <",endWindow).list();
			List<Long> groupIds = new ArrayList<Long>();
			for (Assignment a : assignments) {
				if (groupIds.contains(a.groupId)) continue;  // This ensures that only 1 email is sent to each student
				else groupIds.add(a.groupId);                // if there are multiple assignments due tonight.
				Queue queue = QueueFactory.getDefaultQueue();
				queue.add(withUrl("/Notification").param("AssignmentId",Long.toString(a.id)));
			}
		} catch (Exception e) {
		}
	}

	@Override
	protected void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {  // task to notify users for one particular assignment
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			sendEmailNotifications(assignmentId);
		} catch (Exception e) { 
		}
	}
	
	protected void sendEmailNotifications(long assignmentId) {
		try {
			Objectify ofy = ObjectifyService.begin();
			Assignment a = ofy.get(Assignment.class,assignmentId);
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			Group group = ofy.get(Group.class,a.groupId);
			for (String userId : group.memberIds) {
				try {
					User user = ofy.get(User.class,userId);
					if (!user.notifyDeadlines) continue;      // user does not want notifications
					if (user.myGroupId != group.id) continue; // user has not yet joined this group 
					Message msg = new MimeMessage(session);
					msg.setFrom(new InternetAddress("admin@chemvantage.org","ChemVantage"));
					msg.setSubject("ChemVantage");
					if (smsRegistered(user)) {
						msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.smsMessageDevice));
						msg.setText("Reminder: 1 or more assignments due by midnight tonight.");
					}
					else if (user.verifiedEmail) {
						msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.email));
						msg.setText("This is a friendly reminder from ChemVantage that you have one " 
								+ "or more assignments due by midnight tonight. "
								+ "If you do not wish to receive these reminders, you can change "
								+ "your notification options on the ChemVantage Scores page.");
					}
					Transport.send(msg);
				} catch (Exception e) {
					continue; // unexpected error; continue with the next user
				}
			}
		} catch (Exception e) {
		}
	}

	private boolean smsRegistered(User user) {
		try {
			Long.parseLong(user.smsMessageDevice.substring(0,10));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
