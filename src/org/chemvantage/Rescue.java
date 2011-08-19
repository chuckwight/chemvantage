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

public class Rescue extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

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
			List<Assignment> assignments = ofy.query(Assignment.class).filter("deadline >",startWindow).filter("deadline <",endWindow).list();
			for (Assignment a : assignments) {
				Group group = ofy.find(Group.class,a.groupId);
				if (group==null || !group.sendRescueMessages) continue;
				Queue queue = QueueFactory.getDefaultQueue();
				queue.add(withUrl("/Rescue").param("AssignmentId",Long.toString(a.id)));
			}
		} catch (Exception e) {
		}
	}
	
	@Override
	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {  // this method is called by cron once every 3 hours
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			sendRescueMessages(assignmentId);
		} catch (Exception e) {		
		}
	}
	
	public void sendRescueMessages(long assignmentId) {
		// This method searches for students who missed group assignment deadlines and sends them email messages.
		try {
			Assignment assignment = ofy.get(Assignment.class,assignmentId);
			Group group = ofy.get(Group.class,assignment.groupId);
			if (!group.sendRescueMessages) return; // if the rescue service is not activated, quit now
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);

			for (String userId : group.memberIds) {
				Score score = ofy.find(new Key<Score>(new Key<User>(User.class,userId),Score.class,assignment.id));
				if (score == null) {
					score = Score.getInstance(userId,assignment);
					ofy.put(score);
				}
				
				if (score.score > group.rescueThresholdScore) continue;  // this user is OK; go to next group member

				// send a rescue message to this user
				User user = ofy.get(User.class,userId);
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
				msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.email,user.getBothNames()));
				msg.setSubject(group.defaultRescueSubject);
				String messageText = group.defaultRescueMessage;
				for (String id : group.rescueCcIds) {
					String email = User.getEmail(id);
					String name = User.getBothNames(id);
					msg.addRecipient(Message.RecipientType.CC,new InternetAddress(email,name));
				}
				msg.setText(messageText);
				Transport.send(msg);
				}
		}catch (Exception e) {
		}
	}
}
