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
import static com.googlecode.objectify.ObjectifyService.ofy;

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
import com.googlecode.objectify.Key;

public class Rescue extends HttpServlet {
	private static final long serialVersionUID = 137L;
	//Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "ChemVantage servlet sends helpful messages to students who miss assignments or score badly.";
	}
	
	@Override
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {  // this method is called by cron once every 3 hours
		try {
			Date now = new Date();
			Date startWindow = new Date(now.getTime() - 14400000); // 4 hours ago in millis
			Date endWindow = new Date(now.getTime() - 3600000);    // 1 hour ago in millis
			List<Assignment> assignments = ofy().load().type(Assignment.class).filter("deadline >",startWindow).filter("deadline <",endWindow).list();
			for (Assignment a : assignments) {
				Group group = ofy().load().type(Group.class).id(a.groupId).now();
				if (group==null) continue;
				Queue queue = QueueFactory.getDefaultQueue();
				if (group.sendRescueMessages) queue.add(withUrl("/Rescue").param("AssignmentId",Long.toString(a.id)));
				if (a.emailScoresToInstructor) queue.add(withUrl("/EmailScores").param("AssignmentId", Long.toString(a.id)));
			}
		} catch (Exception e) {
		}
	}
	
	@Override
	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {  // this method is called by the default Task queue once every 3 hours
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			sendRescueMessages(assignmentId);
		} catch (Exception e) {		
		}
	}
	
	public void sendRescueMessages(long assignmentId) {
		// This method searches for students who missed group assignment deadlines and sends them email messages.
		try {
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			if (!group.sendRescueMessages) return; // if the rescue service is not activated, quit now
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			Date now = new Date();
			Date twelveHoursAgo = new Date(now.getTime()-43200000);  // Date object 12 hours in the past
			for (String userId : group.memberIds) {
				Score score = ofy().load().key(Key.create(Key.create(User.class,userId),Score.class,assignment.id)).safe();
				if (score == null) {
					score = Score.getInstance(userId,assignment);
					ofy().save().entity(score);
				}
				
				if (100.0*(double)score.score/(double)score.maxPossibleScore > group.rescueThresholdScore) continue;  // this user is OK; go to next group member
				
				// send a rescue message to this user
				User user = ofy().load().type(User.class).id(userId).safe();
				if (user.alias != null) { // follow the alias chain to the end
					List<String> userIds = new ArrayList<String>();
					userIds.add(user.id);
					userIds.add(0,user.alias);
					user = User.getInstance(userIds);
				}
				if (user.isInstructor() || user.isTeachingAssistant()) continue;
				
				RescueMessage rm = ofy().load().type(RescueMessage.class).id(userId).now();  // check for a recent RescueMessages to this user
				if (rm==null) ofy().save().entity(new RescueMessage(userId));  // create a temporary record of this message being sent
				else if (rm.sent.before(twelveHoursAgo)) {  // this is an old record. Send a new message and update the record
					rm.sent = now;
					ofy().save().entity(rm);
				} else continue; // a new record was found; skip this user to avoid sending duplicate messages
				
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
				msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.getEmail(),user.getBothNames()));
				msg.setSubject(group.defaultRescueSubject);
				String messageText = group.defaultRescueMessage;
				for (String id : group.rescueCcIds) {
					String email = User.getEmail(id);
					String name = User.getBothNames(id);
					msg.addRecipient(Message.RecipientType.CC,new InternetAddress(email,name));
				}
				msg.setContent(messageText,"text/html");
				Transport.send(msg);
				}
		}catch (Exception e) {
		}
	}
}
