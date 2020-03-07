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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class UserReport implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	Date submitted;
			String userId;
			int stars;
			long questionId;
			String comments = "";
	
	UserReport() {}
	
	UserReport(String userId,long questionId,String comments) {
		this.userId = userId;
		this.questionId = questionId;
		this.comments = comments;
		this.submitted = new Date();
	}
	
	public UserReport(String userId,int stars,String comments) {
		this.userId = userId;
		this.stars = stars;
		this.comments = comments;
		this.submitted = new Date();
	}
	
	public String view(User user) {
		StringBuffer buf = new StringBuffer();
		// User must be identified to see the report
		if (user==null) return null;
		// User must be author of the report or the ChemVantage administrator
		if (!(user.id.equals(this.userId) || user.isChemVantageAdmin())) return null;  

		try {
			buf.append("On " + submitted + " a user said:<br>");
			
			if (stars>0) buf.append(" (" + stars + " stars)<br>");
			buf.append("<FONT COLOR=RED>" + comments + "</FONT><br>");
			try {
				Question q = ofy().load().type(Question.class).id(this.questionId).safe();
				q.setParameters(-1); // -1 randomizes the question
				Topic topic = ofy().load().type(Topic.class).id(q.topicId).now();
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
				if (this.userId!=null) {
					List<Response> responses = ofy().load().type(Response.class).filter("userId",userId).filter("questionId",questionId).list();
					if (responses.size() > 0) {
						buf.append("<table><tr><td>Date/Time (UTC)</td><td>Student Response</td><td>Correct Response</td><td>Score</td></tr>");
						for (Response r : responses) buf.append("<tr><td>" + r.submitted.toString() + "</td><td align=center>" + r.studentResponse 
								+ "</td><td align=center>" + r.correctAnswer + "</td><td align=center>" + r.score + "</td></tr>");
						buf.append("</table>");
					}
				}
				if (user.isEditor()) buf.append("<a href=Edit?UserRequest=Edit&QuestionId=" + this.questionId + "&TopicId=" + topic.id + "&AssignmentType=" + q.assignmentType + ">Edit Question</a> ");
			} catch (Exception e2) {}
			if (user.isChemVantageAdmin()) // Create a form for deleting the report
				buf.append("<FORM METHOD=POST ACTION=Feedback>"
					+ "<INPUT TYPE=HIDDEN NAME=ReportId VALUE=" + this.id + ">"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Report'>"
					+ "<INPUT TYPE=HIDDEN NAME=Token VALUE='" + user.token + "'>"
					+ "</FORM><p>");
		} catch (Exception e) {
			buf.append("<br>" + e.toString());
		}
		return buf.toString();
	}
/*
	public String view() {
		StringBuffer buf = new StringBuffer();
		User user = null;
		try {
			user = ofy().load().type(User.class).id(this.userId).safe();
		} catch (Exception e2) {				
		}
		buf.append("On " + submitted + " " + "a user said:<br>\n");
		if (stars>0) buf.append("(" + stars + " stars)<br>\n");
		buf.append("<FONT COLOR=RED>" + comments + "</FONT><p>\n\n");
		
		if (this.questionId>0) {
			try {
				Question q = ofy().load().type(Question.class).id(this.questionId).safe();
				q.setParameters(userId!=null?userId.hashCode():-1);
				Topic topic = ofy().load().type(Topic.class).id(q.topicId).now();
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
				if (user!=null) {
					buf.append("<table><tr><td>Date/Time (UTC)</td><td>Student Response</td><td>Correct Response</td><td>Score</td></tr>");
					List<Response> responses = ofy().load().type(Response.class).filter("userId",userId).filter("questionId",questionId).list();
					for (Response r : responses) buf.append("<tr><td>" + r.submitted.toString() + "</td><td align=center>" + r.studentResponse 
							+ "</td><td align=center>" + r.correctAnswer + "</td><td align=center>" + r.score + "</td></tr>");
					buf.append("</table>");
				}
			} catch (Exception e) {
				buf.append("<br>" + e.getMessage());
			}
		}
		return buf.toString();
	}
	*/
}