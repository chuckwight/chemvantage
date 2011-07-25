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

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;

public class Rescue extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "PZone servlet presents user's detailed scores in the Practice Zone site.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon every 6 hours.
		sendRescueMessages();
		sendDeadlineReminders();
	}
	
	public void sendRescueMessages() {
		// This method searches for students who missed group assignment deadlines and sends them email messages.
		Objectify ofy = ObjectifyService.begin();
		Date now = new Date();
		Date then = new Date(now.getTime()-21600000); // 6 hours ago
		List<Assignment> assignments = ofy.query(Assignment.class).filter("deadline >",then).filter("deadline <",now).list();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		for (Assignment a : assignments) {
			try {
				Group group = ofy.find(Group.class,a.groupId);
				if (group == null) {
					ofy.delete(a);
					continue;
				}
				if (!group.sendRescueMessages) continue; // if the rescue service is not activated, go to next assignment
				if (a.assignmentType.equals("Quiz")) {
					Query<QuizTransaction> quizTransactions = ofy.query(QuizTransaction.class).filter("userId in",group.memberIds).filter("topicId",a.topicId).filter("downloaded <",a.deadline);
					for (QuizTransaction qt : quizTransactions) 
						if (qt.score >= qt.possibleScore*group.thresholdScorePct/100 && group.memberIds.contains(qt.userId)) 
							group.memberIds.remove(qt.userId);  // leave only ids of group members with low or missing scores
				} else if (a.assignmentType.equals("Homework")) {
					int possibleScore = a.questionKeys.size();
					List<Long> assignedQuestionIds = new ArrayList<Long>();
					for (Key<Question> k : a.questionKeys) assignedQuestionIds.add(k.getId());
					// for each group member add a list of assigned questions to the matrix:
					List<List<Long>> questionIdMatrix = new ArrayList<List<Long>>();
					for (int i=0;i<group.memberIds.size();i++) questionIdMatrix.add(assignedQuestionIds);
					// for each homework problem answered correctly, remove the corresponding questionId from the matrix:
					Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId in",group.memberIds).filter("topicId",a.topicId).filter("questionId in",assignedQuestionIds).filter("score >",0);
					for (HWTransaction hwt : hwTransactions)
						questionIdMatrix.get(group.memberIds.indexOf(hwt.userId)).remove(hwt.questionId);
					for (String userId : group.memberIds)
						if (questionIdMatrix.get(group.memberIds.indexOf(userId)).size() <= possibleScore*group.thresholdScorePct/100) 
							group.memberIds.remove(userId); // leave only ids of group members high numbers of missing homework scores
				} else continue; // if the assignment is neither quiz nor homework, go to the next assignment

				if (group.memberIds.size() == 0) continue; // don't send messages for this group; everyone is OK

				for (String userId : group.memberIds) {
					try {
						User user = ofy.get(User.class,userId);
						Message msg = new MimeMessage(session);
						msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
						msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.email,user.getBothNames()));
						msg.setSubject(group.defaultRescueSubject);
						String messageText = group.defaultRescueMessage;
						for (String id : group.rescueCcIds) {
							String email = User.getEmail(id);
							String name = User.getBothNames(id);
							messageText += name + " (" + email + ")\n";
							msg.addRecipient(Message.RecipientType.CC,new InternetAddress(email,name));
						}
						msg.setText(messageText);
						Transport.send(msg);
					} catch (Exception e) {
						continue;
					}
				}
			}catch (Exception e2) {
				continue;
			}
		}
	}


	public void sendDeadlineReminders() {
		// This method searches for assignments due at midnight tonight and sends reminders to users who elect notification
		Objectify ofy = ObjectifyService.begin();
		Date now = new Date();
		Date startWindow = new Date(now.getTime() + 43200000); // 12 hr from now in millis
		Date endWindow = new Date(startWindow.getTime() + 21600000);   // 6 hours later
		List<Assignment> assignments = ofy.query(Assignment.class).filter("deadline >",startWindow).filter("deadline <",endWindow).list();
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		for (Assignment a : assignments) {
			Group group = ofy.find(Group.class,a.groupId);
			if (group == null) {
				ofy.delete(a);
				continue;
			}
			for (String userId : group.memberIds) {
				try {
					Message msg = new MimeMessage(session);
					msg.setFrom(new InternetAddress("admin@chemvantage.org","ChemVantage"));
					msg.setSubject("ChemVantage");
					User user = ofy.find(User.class,userId);
					if (user == null) group.memberIds.remove(userId); // user account was deleted
					if (user.myGroupId != group.id) continue; // user has not yet joined this group 
					if (user.notifyDeadlines && smsRegistered(user)) {
						msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.smsMessageDevice));
						msg.setText("Reminder: " + a.assignmentType + " at chemvantage.org due by midnight tonight.");
					}
					else if (user.notifyDeadlines) {
						msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.email));
						msg.setText("This is a friendly reminder from ChemVantage that you have a " 
								+ a.assignmentType + " due by midnight tonight. <p>"
								+ "If you do not wish to receive these reminders, you can change "
								+ "your notification options on the Scores page at "
								+ "<a href=http://www.chemvantage.org>www.chemvantage.org</a>");
					}
					Transport.send(msg);
				} catch (Exception e) {
					continue;
				}
			}
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
