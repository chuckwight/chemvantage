package org.chemvantage;

import java.util.Date;

import javax.persistence.Id;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public class UserReport {
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
	
	public String adminView() {
		StringBuffer buf = new StringBuffer();
		Objectify ofy = ObjectifyService.begin();
		try {
			User user = userId==null?null:ofy.get(User.class,this.userId);
			buf.append("\n<FORM METHOD=POST ACTION=Feedback>"
					+ "On " + submitted 
					+ (user==null?" (anonymous) ":" <a href=mailto:" + user.email + ">" + user.getBothNames() + "</a> ")
					+ (user.verifiedEmail?"":"<FONT SIZE=-1>(unverified)</FONT> ") + "said:<br>");
			//for (int i=0;i<5;i++) buf.append((i<stars?"<img alt='star' src=images/star2.gif>":"<img alt='' src=images/star1.gif>"));
			if (stars>0) buf.append(" (" + stars + " stars)<br>");
			buf.append("<FONT COLOR=RED>" + comments + "</FONT><br>");
			try {
				Question q = ofy.get(Question.class,this.questionId);
				q.setParameters();
				Topic topic = ofy.find(Topic.class,q.topicId);
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
				buf.append("<a href=Edit?UserRequest=Review&QuestionId=" + this.questionId + ">Review Question</a>");
			} catch (Exception e2) {}
			buf.append("<INPUT TYPE=HIDDEN NAME=ReportId VALUE=" + this.id + ">"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Report'>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Reply'>"
					+ "</FORM>");

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
				q.setParameters();
				Topic topic = ofy.find(Topic.class,q.topicId);
				buf.append("Topic: " + topic.title + " (" + q.assignmentType + " question)<br>");
				buf.append(q.printAll());
			} catch (Exception e2) {}
		} catch (Exception e) {
			buf.append("<br>" + e.getMessage());
		}
		return buf.toString();
	}
}