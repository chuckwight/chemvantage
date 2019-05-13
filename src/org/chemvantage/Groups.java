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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

public class Groups extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	public String getServletInfo() {
		return "This servlet is used by ChemVantage admins to create groups, instructors to set deadlines, and student to join.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			User user = null;
			if (session.isNew()) user = Nonce.getUser(request.getParameter("Nonce"));
			else user = User.getInstance(session);
			
			session.setAttribute("UserId", user.id);
			String nonce = null;
			if (session.isNew()) nonce = Nonce.createInstance(user);
			
			// Authorized users only beyond this point:
			if(!(user.isAdministrator() || user.isInstructor())) response.sendRedirect("/");
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
						
			if (userRequest.contentEquals("AssignQuizQuestions")) out.println(selectQuestionsForm(user,request,nonce));
			else if (userRequest.contentEquals("AssignHomeworkQuestions")) out.println(selectQuestionsForm(user,request,nonce));
			else if (userRequest.equals("AssignExamQuestions")) out.println(selectExamQuestionsForm(user,request,nonce));
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			User user = null;
			String nonce = null;
			if (session.isNew()) {
				user = Nonce.getUser(request.getParameter("Nonce"));
				nonce = Nonce.createInstance(user);
			}
			else user = User.getInstance(session);
			if (user==null) {
				response.sendRedirect("/Logout");
				return;
			}
			session.setAttribute("UserId", user.id);
			
			long groupId = 0;
			try {
				groupId = Long.parseLong(request.getParameter("GroupId"));
			} catch (Exception e2) {}
			Group group = groupId>0?ofy().load().type(Group.class).id(groupId).now():null;
				
			// Authorized users only beyond this point:
			if (!(user.isAdministrator() || user.isInstructor() && group.domain.contentEquals(user.domain))) response.sendRedirect("/Logout");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			if (userRequest.equals("UpdateAssignment")) { // records question item selections in assignment entity
				updateAssignment(user,group,request);
				Assignment assignment = ofy().load().type(Assignment.class).id(Long.parseLong(request.getParameter("AssignmentId"))).safe();
				response.sendRedirect("/" + assignment.assignmentType + "?AssignmentId=" + assignment.id
						+ (nonce==null?"":"&nonce=" + nonce));
				return;
			}
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	String selectQuestionsForm(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).now();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			String assignmentType = assignment.assignmentType;
			Topic topic = ofy().load().type(Topic.class).id(assignment.topicId).safe();
			
			buf.append("<h3>Select " + assignmentType + " Questions</h3>");
			buf.append("<b>Subject: " + subject.title + "<br>"
					+ "Topic: " + topic.title + "<br>"
					+ "Group: " + group.description + "</b><p>");
					
			// Option 1: allow instructor to copy assigned questions list from another group
			// first make a list of this instructor's groups having an assignment with this topic
			try {
				List<Assignment> allAssignments = ofy().load().type(Assignment.class).filter("assignmentType",assignmentType).filter("topicId",topic.id).list();
				List<Assignment> eligibleForCopy = new ArrayList<Assignment>();
				
				for (Assignment a : allAssignments) {
					try {
						if (a.id == assignment.id) continue;  // don't copy from the same group
						if (a.domain.equals(assignment.domain)) eligibleForCopy.add(a);
					} catch (Exception e) {}
				}
				// If any such groups exist, create a form for copying
				if (eligibleForCopy.size() > 0) {
					buf.append("<h4>Option 1: Copy question selections from another group</h4>"
							+ "Select the class from which to copy the question selections:<br>");
					buf.append("<FORM METHOD=POST ACTION=Groups>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateAssignment>"
							+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + assignment.id + "'>");
					buf.append("<SELECT NAME=CopyAssignmentId><OPTION VALUE=0>Select a group (optional)</OPTION>");
					for (Assignment a : eligibleForCopy) {
						try {
							Group g = ofy().load().type(Group.class).id(a.groupId).safe();
							buf.append("<OPTION VALUE='" + a.id + "'>" + g.description + "</OPTION>");
						} catch (Exception e2) {
							ofy().delete().entity(a);
						}
					}
					buf.append("</SELECT>");
					buf.append("<INPUT TYPE=SUBMIT VALUE='Copy Selections'></FORM><p>");
					buf.append("<h4>Option 2: Select questions manually</h4>");
				}
			} catch (Exception e) {}
			
			// Option 2: allow instructor to pick individual question items from all active questions:
			if (assignmentType.contentEquals("Quiz")) {
				buf.append("Each quiz consists of 10 questions selected at random from the items below. You may select "
						+ "the items that will be used for this group by checking the boxes in the left column. Students are provided "
						+ "answers to the items that they answer incorrectly. Therefore, the total number of questions should be "
						+ "larger than 10, but not much larger than 50.  Experience shows that 30 items is about right in most cases.<p>"
						+ "If you don't see a question you want to include, you may "
						+ "<a href=/Contribute>contribute a new question item</a> to the database.<p>");
			} else if (assignmentType.contentEquals("Homework")) {
				buf.append("Select the homework questions to be assigned to students in this group, then click the "
						+ "'Use Selected Items' button. Each question is worth 1 point, so the maximum possible score on the "
						+ "assignment is equal to the number of questions selected. Students may work unassigned problems; "
						+ "however, these are not included in the scores reported to the class LMS.<p>"
						+ "If you don't see a question you want to include, you may "
						+ "<a href=/Contribute>contribute a new question item</a> to the database.<p>");
			}
			
			Query<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topic.id).filter("isActive",true);
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=Groups>"
					+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + assignment.id + "'>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions) {
				i++;
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(assignment.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
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

	String selectExamQuestionsForm(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer("<h3>Select Practice Exam Questions</h3>");
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();
			Map<Long,Topic>topics = ofy().load().type(Topic.class).ids(assignment.topicIds);
			buf.append("<b>Subject: " + subject.title + "</b><br/>"
					+ "Group: " + group.description + "<br/>"
			//		+ "Deadline: " + df.format(assignment.getDeadline()) + "<br/>"
					+ "Topics:<OL>");
			for (Topic t:topics.values()) buf.append("<LI>" + t.title + "</LI>");
			buf.append("</OL>");
			
			buf.append("Each practice exam consists of items selected at random from the items below:<ul>"
					+ "<li>10 quiz questions worth 2 points each</li>"
					+ "<li> 5 homework questions worth 10 points each</li>"
					+ "<li> 2 more challenging homework questions worth 15 points each</li></ul>"
					+ "for a total of 100 points. Each exam must be completed within 60 minutes to be scored.<p>"
					+ "Select the items to be included in exams assigned to your class.<p>");
			
			List <Key<Question>> questionKeys_02pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",2).filter("topicId in",assignment.topicIds).keys().list();
			List <Key<Question>> questionKeys_10pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",10).filter("topicId in",assignment.topicIds).keys().list();
			List <Key<Question>> questionKeys_15pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",15).filter("topicId in",assignment.topicIds).keys().list();
				
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			buf.append("<FORM NAME=Questions METHOD=POST ACTION=Groups>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ (nonce==null?"":"<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">")
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + assignment.id + "'>"
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
				buf.append(assignment.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
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
				buf.append(assignment.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
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
				buf.append(assignment.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
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

	void updateAssignment(User user,Group group,HttpServletRequest request) {
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			
			// Option 1: copy questionKeys from another assignment
			if (request.getParameter("CopyAssignmentId")!=null) {
				try { // copy questionKeys from another assignment
					long copyAssignmentId = Long.parseLong(request.getParameter("CopyAssignmentId"));
					Assignment copyAssignment = ofy().load().type(Assignment.class).id(copyAssignmentId).safe();
					assignment.questionKeys.clear();
					assignment.questionKeys.addAll(copyAssignment.questionKeys);
					ofy().save().entity(assignment).now();
					return;	
				} catch (Exception e) {
					return;
				}
			} else { // Option 2: select questions manually from form
				try {
					String[] questionIds = request.getParameterValues("QuestionId");
					assignment.questionKeys.clear();
					if (questionIds != null) for (String id : questionIds) assignment.questionKeys.add(Key.create(Question.class,Long.parseLong(id)));
					ofy().save().entity(assignment).now();	
					return;
				} catch (Exception e) { // set assignment questionKeys based on checkbox form submission
				}
			}
		} catch (Exception e) {}
	}
}
