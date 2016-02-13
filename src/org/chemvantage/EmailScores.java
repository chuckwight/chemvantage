/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2016 ChemVantage LLC
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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

public class EmailScores extends HttpServlet {
	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "ChemVantage servlet sends assignment scores to instructors after the deadline upon request.";
	}
	
	@Override
	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {  // this method is called by the default Task queue once every 3 hours
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			emailScoresToInstructor(assignmentId);
		} catch (Exception e) {		
		}
	}
	
	public void emailScoresToInstructor(long assignmentId) {
		try {
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			if (!assignment.emailScoresToInstructor) return; // if the emailScores service is not activated, quit now

			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			
			User instructor = ofy().load().type(User.class).id(group.instructorId).safe();
			if (instructor.alias != null) { // follow the alias chain to the end
				List<String> userIds = new ArrayList<String>();
				userIds.add(instructor.id);
				userIds.add(0,instructor.alias);
				instructor = User.getInstance(userIds);
			}

			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setRecipient(Message.RecipientType.TO,new InternetAddress(instructor.getEmail(),instructor.getBothNames()));
			msg.setSubject("ChemVantage Assignment Scores");

			switch (assignment.assignmentType) {
			case ("Quiz"):
				msg.setText(quizScores(group,assignment)); break;
			case ("Homework"):
				msg.setText(homeworkScores(group,assignment)); break;
			case ("PracticeExam"):
				msg.setText(practiceExamScores(group,assignment)); break;
			}
			Transport.send(msg);
		}catch (Exception e) {
		}
	}

	String quizScores(Group group,Assignment assignment) {
		StringBuffer buf = new StringBuffer();
		Topic t = ofy().load().type(Topic.class).id(assignment.topicId).safe();
		buf.append("<b>ChemVantage Quiz Scores - " + t.title + "</b><p>"
				+ "The following is a list of maximum pre-deadline scores on this quiz. If you registered your class using a learning management system (LMS), then in most cases "
				+ "these scores have already been reported to the LMS grade book. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
				+ "is possible that these scores may be different. The average number of quiz attempts is shown in parentheses for your reference. "
				+ "A red dot indicates a score that is low enough to be a concern. You may click the assignment link in the LMS or go to the ChemVantage instructor page to change the deadline "
				+ "at any time. If you change the deadline, all scores will be recalculated to reflect the revised deadline.<p>");
		
		if (group.memberIds.size()==0) return buf.toString();
		Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
		// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
		Set<Key<Score>> keys = new HashSet<Key<Score>>();
		for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
		Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
		int i = 0;
		Score s = null;
		buf.append("Group: " + group.description + "<p>");
		buf.append("Instructors and Teaching Assistants<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) {
				Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
				s = scoresMap.get(k);
				if (s==null) {
					s = Score.getInstance(u.id,assignment);
					ofy().save().entity(s).now();
				}
				i++;
				buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
			}
		}
		buf.append("</TABLE><p>");

		// display the table of student scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
		i=0;
		buf.append("Students<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) continue;
			Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
			s = scoresMap.get(k);
			if (s==null) {
				s = Score.getInstance(u.id,assignment);
				ofy().save().entity(s).now();
			}
			i++;
			buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
		}
		buf.append("</TABLE>");
		return buf.toString();
	}
	
	String homeworkScores(Group group,Assignment assignment) {
		StringBuffer buf = new StringBuffer();
		Topic t = ofy().load().type(Topic.class).id(assignment.topicId).safe();
		buf.append("<b>ChemVantage Homework Scores - " + t.title + "</b><p>"
				+ "The following is a list of best pre-deadline scores on this assignment. In most cases, these scores have been reported to the grade book "
				+ "in the class learning management system. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
				+ "is possible that these scores may be different from those in the LMS grade book. "
				+ "A red dot indicates a score that is low enough to be a concern. If you change the deadline, all scores will be recalculated to reflect the revised deadline.<p>");
		
		if (group.memberIds.size()==0) return buf.toString();
		Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
		
		// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
		Set<Key<Score>> keys = new HashSet<Key<Score>>();
		for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
		Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
		int i = 0;
		Score s = null;
		buf.append("Group: " + group.description + "<p>");
		buf.append("Instructors and Teaching Assistants<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) {
				Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
				s = scoresMap.get(k);
				if (s==null) {
					s = Score.getInstance(u.id,assignment);
					ofy().save().entity(s).now();
				}
				i++;
				buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
			}
		}
		buf.append("</TABLE><p>");

		// display the table of student scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
		i=0;
		buf.append("Students<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) continue;
			Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
			s = scoresMap.get(k);
			if (s==null) {
				s = Score.getInstance(u.id,assignment);
				ofy().save().entity(s).now();
			}
			i++;
			buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
		}
		buf.append("</TABLE>");
		return buf.toString();
	}
	
	String practiceExamScores(Group group,Assignment assignment) {
		StringBuffer buf = new StringBuffer();
		buf.append("<b>Practice Exam Scores</b><p>");
		buf.append("Topics covered on this exam:<ol>");
		Map<Long,Topic> topics = ofy().load().type(Topic.class).ids(assignment.topicIds);
		for (Topic t:topics.values()) {
			buf.append("<li>" + t.title);
		}
		buf.append("</ol>");
		buf.append("The following is a list of maximum pre-deadline scores on this exam. In most cases, these scores have been reported to the grade book "
				+ "in the class learning management system. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
				+ "is possible that these scores may be different from those in the LMS grade book. The number of exam attempts is shown in parentheses for your reference. "
				+ "A red dot indicates a score that is low enough to be a concern. If you change the deadline, all scores will be recalculated to reflect the revised deadline.<p>");
		
		if (group.memberIds.size()==0) return buf.toString();
		Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
		
		// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
		Set<Key<Score>> keys = new HashSet<Key<Score>>();
		for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
		Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
		int i = 0;
		Score s = null;
		buf.append("Group: " + group.description + "<p>");
		buf.append("Instructors and Teaching Assistants<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) {
				Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
				s = scoresMap.get(k);
				if (s==null) {
					s = Score.getInstance(u.id,assignment);
					ofy().save().entity(s).now();
				}
				i++;
				buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
			}
		}
		buf.append("</TABLE><p>");

		// display the table of student scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
		i=0;
		buf.append("Students<br>"
				+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
		for (String id:group.memberIds) {
			User u = members.get(id);
			if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) continue;
			Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
			s = scoresMap.get(k);
			if (s==null) {
				s = Score.getInstance(u.id,assignment);
				ofy().save().entity(s).now();
			}
			i++;
			buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(assignment.deadline,group.rescueThresholdScore) + "</TD></TR>");
		}
		buf.append("</TABLE>");
	
		return buf.toString();
	}
}
