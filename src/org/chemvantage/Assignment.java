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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;

@Entity
public class Assignment {
	@Id 	Long id;
	//@Index 	long groupId;
	@Index	String domain;
	@Index	String assignmentType;
	@Index	long topicId;
	@Index	String resourceLinkId;
			String lis_outcome_service_url;
			String lti_ags_lineitem_url;
			String lti_nrps_context_membership_url;
			List<Long> topicIds; // used for practice exams which have multiple topicIds
			List<String> resourceLinkIds = new ArrayList<String>();  // deprecated
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

	Assignment() {}

	Assignment(String platformDeploymentId,String resourceLinkId,String lisOutcomeServiceUrl) {  // specific to Quiz and Homework assignments with a single topicId
		this.domain = platformDeploymentId;
		this.resourceLinkId = resourceLinkId;
		this.lis_outcome_service_url = lisOutcomeServiceUrl;
	}

	Assignment(String assignmentType, long topicId, List<Long> topicIds, String platform_deployment_id) {
		this.assignmentType = assignmentType;
		this.topicId = topicId;
		this.topicIds = topicIds;
		this.domain = platform_deployment_id;
		this.questionKeys = ofy().load().type(Question.class).filter("assignmentType",this.assignmentType).filter("topicId",this.topicId).keys().list();
	}
	
	String selectQuestionsForm(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			Topic topic = ofy().load().type(Topic.class).id(this.topicId).safe();
			
			buf.append("<h3>Select " + assignmentType + " Questions</h3>");
			buf.append("<b>Subject: " + Subject.getSubject().title + "<br>"
					+ "Topic: " + topic.title + "</b><p>");
					
			// Allow instructor to pick individual question items from all active questions:
			if (assignmentType.contentEquals("Quiz")) {
				buf.append("Each quiz consists of 10 questions selected at random from the items below. You may select "
						+ "the items that will be used for this group by checking the boxes in the left column. Students are provided "
						+ "answers to the items that they answer incorrectly. Therefore, the total number of questions should be "
						+ "larger than 10, but not much larger than 50.  Experience shows that 30 items is about right in most cases.<p>"
						+ "If you don't see a question you want to include, you may "
						+ "<a href=/Contribute?Token=" + user.token + ">contribute a new question item</a> to the database.<p>");
			} else if (assignmentType.contentEquals("Homework")) {
				buf.append("Select the homework questions to be assigned to students in this group, then click the "
						+ "'Use Selected Items' button. Each question is worth 1 point, so the maximum possible score on the "
						+ "assignment is equal to the number of questions selected. Students may work unassigned problems; "
						+ "however, these are not included in the scores reported to the class LMS.<p>"
						+ "If you don't see a question you want to include, you may "
						+ "<a href=/Contribute?Token=" + user.token + ">contribute a new question item</a> to the database.<p>");
			}
			
			Query<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topic.id).filter("isActive",true);
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=/" + this.assignmentType + ">"
					+ "<INPUT TYPE=HIDDEN NAME=Token VALUE=" + user.token + ">"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + this.id + "'>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions) {
				i++;
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(this.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String selectExamQuestionsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Select Practice Exam Questions</h3>");
		try {
			Map<Long,Topic>topics = ofy().load().type(Topic.class).ids(this.topicIds);
			buf.append("<b>Subject: " + Subject.getSubject().title + "</b><br/>"
					+ "Topics:<OL>");
			for (Topic t:topics.values()) buf.append("<LI>" + t.title + "</LI>");
			buf.append("</OL>");
			
			buf.append("Each practice exam consists of items selected at random from the items below:<ul>"
					+ "<li>10 quiz questions worth 2 points each</li>"
					+ "<li> 5 homework questions worth 10 points each</li>"
					+ "<li> 2 more challenging homework questions worth 15 points each</li></ul>"
					+ "for a total of 100 points. Each exam must be completed within 60 minutes to be scored.<p>"
					+ "Select the items to be included in exams assigned to your class.<p>");
			
			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_10pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_15pt = new ArrayList<Key<Question>>();
			
			for (long tid : topicIds) {  // Sort and collect the question keys
				questionKeys_02pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",2).keys().list());
				questionKeys_10pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",10).keys().list());
				questionKeys_15pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",15).keys().list());
			}
			
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			buf.append("<FORM NAME=Questions METHOD=POST ACTION=PracticeExam>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=Token VALUE=" + user.token + ">"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + this.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=PracticeExam>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			Question q = null;
			int i = 0;
			
			// 2-point questions:
			buf.append("<TR><TD COLSPAN=2><U>2-point Questions: (select at least 10)</U></TD></TR>");
			i=0;
			for (Key<Question> k : questionKeys_02pt) {
				i++;
				try {
					q = ofy().load().key(k).safe();
				} catch (Exception e) {
					continue;
				}
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(this.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			// 10-point questions:
			buf.append("<TR><TD COLSPAN=2><U>10-point Questions: (select at least 5)</U></TD></TR>");
			i=0;
			for (Key<Question> k : questionKeys_10pt) {
				i++;
				try {
					q = ofy().load().key(k).safe();
				} catch (Exception e) {
					continue;
				}
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(this.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			// 15-point questions:
			buf.append("<TR><TD COLSPAN=2><U>15-point Questions: (select at least 2)</U></TD></TR>");
			i=0;
			for (Key<Question> k : questionKeys_15pt) {
				i++;
				try {
					q = ofy().load().key(k).safe();
				} catch (Exception e) {
					continue;
				}
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(this.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}

			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM>");
		} catch (Exception e) {
			buf.append("Sorry, the assignment could not be found. " + e.getMessage());
		}
		return buf.toString();
	}

	void updateQuestions(HttpServletRequest request) {
		try {
			String[] questionIds = request.getParameterValues("QuestionId");
			this.questionKeys.clear();
			if (questionIds != null) for (String id : questionIds) this.questionKeys.add(Key.create(Question.class,Long.parseLong(id)));
			ofy().save().entity(this).now();	
		} catch (Exception e) {}
	}

}