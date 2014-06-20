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

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public class UserReport implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
    String userId;
	int stars;
	long questionId;
	String comments = "";
	Date submitted;

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
	
	public String adminView(User adminUser) {
		StringBuffer buf = new StringBuffer();
		Objectify ofy = ObjectifyService.begin();
		try {
			User user = null;
			try {
				user = ofy.get(User.class,this.userId);
			} catch (Exception e) {}
			
			// this statement permits viewing the userReport only if the viewer is the ChemVantage administrator
			// or the domain admin of a report from the domain or the report author (user)
			boolean showReport = false;
			if (adminUser.isAdministrator() && (adminUser.domain==null)) showReport = true;  // ChemVantage administrator
			else if (adminUser.isAdministrator() && adminUser.domain.equals(user.domain)) showReport = true;  // domain administrator
			else if (adminUser.id.equals(user.id)) showReport = true;			
			if (!showReport) return "";
			
			buf.append("\n<FORM METHOD=POST ACTION=Feedback>"
					+ "On " + submitted 
					+ (user==null?" (anonymous) ":" <a href=mailto:" + user.email + ">" + user.getBothNames() + "</a> " + (user.verifiedEmail?"":"<FONT SIZE=-1>(unverified)</FONT> "))
					+ "said:<br>");
			
			if (stars>0) buf.append(" (" + stars + " stars)<br>");
			buf.append("<FONT COLOR=RED>" + comments + "</FONT><br>");
			try {
				Question q = ofy.get(Question.class,this.questionId);
				q.setParameters(userId!=null?userId.hashCode():-1); // -1 randomizes the question
				Topic topic = ofy.find(Topic.class,q.topicId);
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
				if (user!=null) {
					List<Response> responses = ofy.query(Response.class).filter("userId",userId).filter("questionId",questionId).list();
					if (responses.size() > 0) {
						buf.append("<table><tr><td>Date/Time (UTC)</td><td>Student Response</td><td>Correct Response</td><td>Score</td></tr>");
						for (Response r : responses) buf.append("<tr><td>" + r.submitted.toString() + "</td><td align=center>" + r.studentResponse 
								+ "</td><td align=center>" + r.correctAnswer + "</td><td align=center>" + r.score + "</td></tr>");
						buf.append("</table>");
					}
				}
				buf.append("<a href=Edit?UserRequest=Edit&QuestionId=" + this.questionId + "&TopicId=" + topic.id + "&AssignmentType=" + q.assignmentType + ">Edit Question</a> ");
			} catch (Exception e2) {}
			buf.append("<INPUT TYPE=HIDDEN NAME=ReportId VALUE=" + this.id + ">"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Report'>"
					+ (user != null?"<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Reply'>":"")
					+ "</FORM><p>");

		} catch (Exception e) {
			buf.append("<br>" + e.getMessage());
		}
		return buf.toString();
	}

	public String replyView() {
		StringBuffer buf = new StringBuffer();
		Objectify ofy = ObjectifyService.begin();
		try {
			User user = userId==null?null:ofy.get(User.class,this.userId);
			buf.append("On " + submitted + " " + user.getBothNames() + " said:<br>\n");
			if (stars>0) buf.append("(" + stars + " stars)<br>\n");
			buf.append("<FONT COLOR=RED>" + comments + "</FONT><p>\n\n");
			try {
				Question q = ofy.get(Question.class,this.questionId);
				q.setParameters(userId!=null?userId.hashCode():-1);
				Topic topic = ofy.find(Topic.class,q.topicId);
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
				if (user!=null) {
					buf.append("<table><tr><td>Date/Time (UTC)</td><td>Student Response</td><td>Correct Response</td><td>Score</td></tr>");
					List<Response> responses = ofy.query(Response.class).filter("userId",userId).filter("questionId",questionId).list();
					for (Response r : responses) buf.append("<tr><td>" + r.submitted.toString() + "</td><td align=center>" + r.studentResponse 
							+ "</td><td align=center>" + r.correctAnswer + "</td><td align=center>" + r.score + "</td></tr>");
					buf.append("</table>");
				}
			} catch (Exception e2) {}
		} catch (Exception e) {
			buf.append("<br>" + e.getMessage());
		}
		return buf.toString();
	}
}