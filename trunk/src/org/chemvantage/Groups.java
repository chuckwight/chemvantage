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
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class Groups extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private int queryLimit = 10; // maximum number of user-search results returned
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet is used by PZone admins to create groups, instructors to set deadlines, and student to join.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
			
			long groupId = 0;
			try {
				groupId = Long.parseLong(request.getParameter("GroupId"));
			} catch (Exception e2) {}
			Group group = groupId>0?ofy.find(Group.class,groupId):null;
			
			// Authorized users only beyond this point:
			if(!(user.isAdministrator() || user.isInstructor() || user.isTeachingAssistant())) response.sendRedirect("/");
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			if (userRequest.equals("GroupScoresCSV")) {
				csvGroupScores(user,group,request,response);
				return;
			}
			
			out.println(Home.getHeader(user));
			if (group == null) out.println(groupsForm(user,request));
			else if (user.isAdministrator() || group.instructorId.equals(user.id) || group.tAIds.contains(user.id)) {
				if (userRequest.equals("ManageGroup")) out.println(manageGroupForm(user,group,request));
				else if (userRequest.equals("AssignHomeworkQuestions")) out.println(assignHWQuestionsForm(user,group,request));
				else if (userRequest.equals("AssignQuizQuestions")) out.println(assignQuizQuestionsForm(user,group,request));
				else if (userRequest.equals("GroupMembers")) out.println(showGroupMembers(user,group,request));
				else if (userRequest.equals("GroupScores")) out.println(showGroupScores(user,group,request));
				else if (userRequest.equals("GroupPracticeExams")) out.println(showGroupPracticeExams(user,group,request));
				else if (userRequest.equals("RescueOptions")) out.println(showRescueOptions(user,group,request));
				else out.println(groupsForm(user,request));
			}
			out.println(Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
				
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			long groupId = 0;
			try {
				groupId = Long.parseLong(request.getParameter("GroupId"));
			} catch (Exception e2) {}
			Group group = groupId>0?ofy.find(Group.class,groupId):null;
			
			// Authorized users only beyond this point:
			if (!(user.isAdministrator() || user.isInstructor() || user.isTeachingAssistant())) response.sendRedirect("/Home");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			if (userRequest.equals("JoinGroup")) {
				user.changeGroups(groupId);
				out.println(Home.getHeader(user) + groupsForm(user,request) + Home.footer);
				return;
			}
			
			// Additional user restrictions: no TAs beyond this point
			if (!(user.isAdministrator() || (group==null && user.isInstructor()) || group.instructorId.equals(user.id))) {
				out.println(Home.getHeader(user) + "<span style='color:red'>You have read-only access in this area</span>" + groupsForm(user,request) + Home.footer);
				return;
			}
			
			String message = "";
			out.println(Home.getHeader(user));			
			if (userRequest.equals("CreateGroup")) {
				createGroup(user,request);
				out.println(groupsForm(user,request));
			} else if (userRequest.equals("UpdateGroup")) {
				modifyGroup(user,request);
				out.println(groupsForm(user,request));
			} else if (userRequest.equals("DeleteGroup")) {
				deleteGroup(group);
				out.println(groupsForm(user,request));
			} else if (userRequest.equals("SetTimeZone")) {
				setTimeZone(user,group,request);
				out.println(groupsForm(user,request));
			} else if (userRequest.equals("UpdateAssignment")) {
				updateAssignment(user,group,request);
				out.println(manageGroupForm(user,group,request));
			} else if (userRequest.equals("Copy Assignments")) {
				copyAssignments(user,group,request);
				out.println(manageGroupForm(user,group,request));
			} else if (userRequest.equals("UpdateDeadlines")) {
				updateDeadlines(user,group,request);
				out.println(manageGroupForm(user,group,request));
			} else if (userRequest.equals("RescueService")) {
				setRescueOptions(user,group,request);
				out.println(showRescueOptions(user,group,request));
			} else {
				User thisUser = ofy.find(User.class,request.getParameter("UserId"));
				if (userRequest.equals("InviteUser")) {
					message = inviteUser(user,group,request,thisUser);
					response.sendRedirect("Groups?UserRequest=GroupMembers"
							+ "&SearchString=" + request.getParameter("SearchString") 
							+ "&Message=" + message + "&GroupId=" + group.id);
					return;
				} else if (userRequest.equals("AssignTA")) {
					if (!thisUser.isTeachingAssistant()) {
						thisUser.roles+=4;
						ofy.put(thisUser);
					}
					group.tAIds.add(thisUser.id);
					ofy.put(group);
					message = thisUser.getBothNames() + " was assigned as a TA for this group.";
					response.sendRedirect("Groups?UserRequest=GroupMembers&UserId=" + thisUser.id 
							+ "&Message=" + message + "&GroupId=" + group.id);
					return;
				} else if (userRequest.equals("DropTA")) {
					group.tAIds.remove(thisUser.id); ofy.put(group);
					Query<Group> allGroups = ofy.query(Group.class);
					boolean isTA = false;
					for (Group g : allGroups) if (g.isTA(thisUser.id)) isTA = true;
					if (!isTA && thisUser.isTeachingAssistant()) {
						thisUser.roles-=4;
						ofy.put(thisUser);
					}
					message = thisUser.getBothNames() + " was dropped as a TA from this group.";
					response.sendRedirect("Groups?UserRequest=GroupMembers&UserId=" + thisUser.id 
							+ "&Message=" + message + "&GroupId=" + group.id);
					return;
				} else if (userRequest.equals("DropUser")) {
					group.memberIds.remove(thisUser.id); ofy.put(group);
					thisUser.myGroupId = 0; ofy.put(thisUser);
					message = thisUser.getBothNames() + " was dropped from membership in this group.";
					response.sendRedirect("Groups?UserRequest=GroupMembers&UserId=" + thisUser.id
							+ "&Message=" + message + "&GroupId=" + group.id);
					return;
				}
			}
			out.println(Home.footer);
		} catch (Exception e) {
			response.getWriter().println(e.toString());
		}
	}
	
	String groupsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Join A Class or Group</h3>");
		try {
			Group myGroup = user.myGroupId>0?ofy.find(Group.class,user.myGroupId):null;
			if (myGroup == null && user.myGroupId > 0) { // bulletproofing in case user's group disappears unexpectedly
				user.changeGroups(0);
				ofy.put(user);				
			}
			
			buf.append("As an instructor or teaching assistant, you have the ability to freely join or move between ChemVantage groups "
					+ "that you own or to which you have been assigned.  This is useful for viewing assignment deadlines and your own scores "
					+ "as if you were a student in your group.");
			buf.append("<FORM METHOD=POST ACTION=Groups>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=JoinGroup>");

			Query<Group> allGroups = ofy.query(Group.class);
			buf.append("<SELECT NAME=GroupId onChange=submit()><OPTION VALUE=0>Default group (none)</OPTION>\n");
			for (Group g : allGroups) {
				if ((g.domain==null && user.domain==null) || (g.domain != null && g.domain.equals(user.domain)))
					buf.append("<OPTION VALUE=" + g.id + (g.id==user.myGroupId?" SELECTED>":">") + g.description + " (" + g.getInstructorBothNames() + ")</OPTION>\n");
			}
			buf.append("</SELECT></FORM>\n");

			
			buf.append("<h3>Manage Your Groups</h3>\n");
			buf.append("As an instructor, you have the ability to create, edit and delete groups. "
					+ "These are usually groups of students taking a single class.  Students are responsible "
					+ "for joining the appropriate group, but you can manage the enrollments of the group. "
					+ "You may assign quizzes and homework for your group members by setting deadlines. "
					+ "You may create and manage multiple groups of students, but you can be a member of only "
					+ "one group at a time for each subject. Use the form above to switch groups at any time.<p>\n");

			buf.append("Use the links at the far right to set deadlines, view group scores and manage "
					+ "enrollments for your group.<p>\n");
			buf.append("<TABLE>\n<TR><TD><b>Subject</b></TD><TD><b>Instructor</b></TD>"
					+ "<TD><b>Description</b></TD><TD COLSPAN=2 ALIGN=CENTER><b>Actions</b></TD><TD><b>View Scores</b></TD>"
					+ "<TD><b>Deadlines</b></TD><TD><b>Enrollments</b></TD><TD ALIGN=CENTER><b>Time Zone</b></TD></TR>\n");

			for (Group g : allGroups) {
				if ((user.isAdministrator() && (user.domain==null || user.domain.equals(g.domain))) || user.id.equals(g.instructorId) || g.tAIds.contains(user.id))
					buf.append("<TR>"
							+ "<TD>" + subject.title + "</TD>"
							+ "<TD>" + g.getInstructorBothNames() + "</TD>"
							+ "<FORM METHOD=POST ACTION=Groups>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateGroup>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + g.id + ">"
							+ "<TD><INPUT NAME=Description VALUE='" + g.description + "'></TD>"
							+ "<TD><INPUT TYPE=SUBMIT VALUE=Update></TD></FORM>"
							+ "<FORM METHOD=POST ACTION=Groups><TD>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=DeleteGroup>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + g.id + ">"
							+ "<INPUT TYPE=SUBMIT VALUE=Delete onClick=\"return confirm('Permanently delete this group. Are you sure?')\">"
							+ "</TD></FORM>"
							+ "<TD><a href=Groups?UserRequest=GroupScores&AssignmentType=Quiz&GroupId=" + g.id + ">Quiz</a>"
							+ "<br><a href=Groups?UserRequest=GroupScores&AssignmentType=Homework&GroupId=" + g.id + ">Homework</a>"
							+ "<br><a href=Groups?UserRequest=GroupPracticeExams&GroupId=" + g.id + ">Exams</a></TD>"
							+ "<TD ALIGN=CENTER><a href=Groups?UserRequest=ManageGroup&GroupId=" + g.id + ">Edit</a></TD>"
							+ "<TD ALIGN=CENTER><a href=Groups?UserRequest=GroupMembers&GroupId=" + g.id + ">Add/Drop</a></TD>"
							+ "<FORM METHOD=POST ACTION=Groups><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SetTimeZone>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + g.id + "'><TD>" 
							+ timeZoneSelectBox(g.getTimeZone().getID()) 
							+ "</TD></FORM>"
							+ "</TR>\n");
			}
			buf.append("</TABLE>");

			if (user.isAdministrator() || user.isInstructor()) {
				buf.append("<h3>Create a New Group</h3><FORM METHOD=POST ACTION=Groups>"
						+ "To create a new group, choose from the existing subjects and provide a complete description "
						+ "that will help students to identify the proper group to join. For example: <b>"
						+ "U. of Utah CHEM 1210-002 MWF 9:00-9:50 AM</b><p>\n"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=CreateGroup>\n"
						+ "<TABLE><TR><TD><b>Instructor</b></TD><TD><b>Description</b></TD><TD>&nbsp;</TD></TR>"
						+ "<TR><TD>" + user.getBothNames() + "</TD>"
						+ "<TD><INPUT NAME=Description></TD>"
						+ "<TD><INPUT TYPE=SUBMIT VALUE='Create This New Group'></TD>"
						+ "</TR>\n</FORM>\n");
				buf.append("</TABLE>\n");
			}
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String timeZoneSelectBox(String myTimeZone) {
		StringBuffer buf = new StringBuffer();
		try {
			if (myTimeZone == null) myTimeZone = TimeZone.getDefault().getID();
			buf.append("<SELECT NAME=TimeZone onChange=submit()>");
			String[] TZIDs = TimeZone.getAvailableIDs();
			for (int i=0;i<TZIDs.length;i++) {
				buf.append("<OPTION" 
						+ (TZIDs[i].equals(myTimeZone)?" SELECTED>":">") 
						+ TZIDs[i] + "</OPTION>");
			}
			buf.append("</SELECT>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	private void createGroup(User user,HttpServletRequest request) {
		try {
			Group g = new Group(user.id,CharHider.quot2html(request.getParameter("Description")));
			g.domain = user.domain;
			ofy.put(g);
		} catch (Exception e) {
		}
	}

	private void deleteGroup(Group g) {
		try {
			Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",g.id);
			for (Assignment a:assignments) ofy.delete(a);
			ofy.delete(g);
		}catch (Exception e) {
		}
	}
	
	private void modifyGroup(User user,HttpServletRequest request) {
		try {
			Group g = ofy.get(Group.class,Long.parseLong(request.getParameter("GroupId")));
			g.description = CharHider.quot2html(request.getParameter("Description"));
			ofy.put(g);
		} catch (Exception e) {
		}		
	}

	private void setTimeZone(User user,Group group,HttpServletRequest request) {
		try {
			TimeZone oldTZ = group.getTimeZone();
			TimeZone newTZ = TimeZone.getTimeZone(request.getParameter("TimeZone"));
			Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",group.id);
			for (Assignment a : assignments) {  // adjust the deadline to correspond to midnight on the new TimeZone
				long deadline = a.deadline.getTime();
				a.deadline = new Date(deadline + oldTZ.getOffset(deadline) - newTZ.getOffset(deadline));
				ofy.put(a);
			}
			group.timeZone = request.getParameter("TimeZone");
			ofy.put(group);
			group.deleteScores();
			QueueFactory.getDefaultQueue().add(withUrl("/CalculateScores").param("GroupId",Long.toString(group.id)));
		} catch (Exception e) {
		}
	}
	
	String manageGroupForm(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h3>Manage Group: " + group.description + " (" + group.getInstructorBothNames() + ")</h3>");
			DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
			df.setTimeZone(group.getTimeZone());
			String today = df.format(new Date());
			
			buf.append("The ChemVantage Rescue Service is currently " 
					+ (group.sendRescueMessages?"activated.":"turned off.")
					+ " <a href=Groups?UserRequest=RescueOptions&GroupId=" + group.id + ">Show Rescue Service Options</a><p>");
			
			buf.append("<b>Assignment Deadlines</b><br>"
					+ "To assign a quiz or homework for this group, enter a deadline for the assignment and click Update. "
					+ "All deadlines expire just before midnight (23:59:59) local time on the date indicated. "
					+ "Be sure to set the correct time zone on the Groups page.<p>"
					+ "To omit or delete an assignment, leave its deadline blank. "
					+ "<p>You may change the deadline date at any time; scores will be recalculated automatically."
					+ "<p>When you first create an assignment, all questions in the database are used by default. "
					+ "However, you may designate a subset of quiz questions by clicking the "
					+ "'Select' link and/or assign a subset of homework questions using the 'Assign' link below. "
					+ " <p>");

			buf.append("<TABLE>\n<TR><TH>Title</TH><TH>Quiz Deadline</TH><TH COLSPAN=2> Questions</TH>"
					+ "<TH>&nbsp;&nbsp;&nbsp;&nbsp;</TH><TH>HW Deadline</TH><TH COLSPAN=2>Exercises</TH><TH>&nbsp;</TH></TR>\n");

			// allow copying of assignments from another group:
			List<Group> groups = ofy.query(Group.class).list();
			if (groups.size() > 1) {
				buf.append("<div id=copy>");
				buf.append("You may copy all assignments, deadlines and selected questions from another group "
						+ "by selecting it below.<br><FONT COLOR=RED>Warning: this will permanently delete any "
						+ "current assignments for this group shown below.</FONT>"
						+ "<FORM ACTION=Groups METHOD=POST>");
				buf.append("<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>");
				buf.append("<SELECT NAME=CopyGroupId><OPTION VALUE=0>Select a group to copy from (optional)</OPTION>");
				for (Group g : groups) {
					if (g.id==group.id) continue; 
					buf.append("<OPTION VALUE=" + g.id + ">" + g.description + " (" + User.getBothNames(g.instructorId) + ")</OPTION>");
				}
				buf.append("</SELECT><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Copy Assignments'></FORM>");
				buf.append("</div><p>");
			}
			
			// get a list of topics in the default order and rearrange them to start with the current group topics:
			List<Topic> topics = ofy.query(Topic.class).order("orderBy").list();
			for (Long topicId : group.topicIds) {
				for (Topic t : topics) if (t.id==topicId) {
					topics.add(group.topicIds.indexOf(topicId),topics.remove(topics.indexOf(t)));
					break;
				}
			}
			for (Topic t : topics) {
				long i = group.getAssignmentId("Quiz",t.id);
				Assignment q = i>0?ofy.find(Assignment.class,i):null;
				long j = group.getAssignmentId("Homework",t.id);
				Assignment h = j>0?ofy.find(Assignment.class,j):null;
				buf.append("<FORM NAME=A" + t.id + " METHOD=POST ACTION=Groups>"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateDeadlines>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + t.id + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>\n");
				buf.append("<TR><TD>" + t.title + "</TD><TD><INPUT SIZE=15 NAME=QuizDeadline ");
				buf.append((q==null?"onFocus=\"A" + t.id + ".QuizDeadline.value='" + today + "'\"":"VALUE='" + df.format(q.deadline) + "'")
						+ "></TD>\n"
						+ "<TD ALIGN=CENTER>" + (q==null?0+"</TD><TD></TD>":q.questionKeys.size() + "</TD>"
						+ "<TD ALIGN=CENTER><A href=Groups?UserRequest=AssignQuizQuestions&GroupId=" + group.id + "&TopicId=" + t.id + ">Select</A></TD>")
						+ "<TD></TD>\n");
				buf.append("<TD><INPUT SIZE=15 NAME=HWDeadline "); 
				buf.append((h==null?"onFocus=\"A" + t.id + ".HWDeadline.value='" + today + "'\"":"VALUE='" + df.format(h.deadline) + "'")
						+ "></TD>\n"
						+ "<TD ALIGN=CENTER>" + (h==null?0+"</TD><TD></TD>":h.questionKeys.size() + "</TD>"
						+ "<TD ALIGN=CENTER><A href=Groups?UserRequest=AssignHomeworkQuestions&GroupId=" + group.id + "&TopicId=" + t.id + ">Assign</A></TD>")
						+ "<TD><INPUT TYPE=SUBMIT VALUE='Update'></TD>"
						+ "</TR>\n</FORM>\n");
			}
			buf.append("</TABLE>\n");
		}
		catch (Exception e) {
			buf.append("<p>" + e.toString());
		}

		return buf.toString();
	}

	String assignQuizQuestionsForm(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Select Quiz Questions</h3>");
		try {
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic topic = ofy.get(Topic.class,topicId);
			long qi = group.getAssignmentId("Quiz",topic.id);
			Assignment assignment = qi>0?ofy.get(Assignment.class,qi):null;
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			df.setTimeZone(group.getTimeZone());
			buf.append("<b>Subject: " + subject.title + "<br>"
					+ "Topic: " + topic.title + "<br>"
					+ "Group: " + group.description + "<br>"
					+ "Deadline: " + df.format(assignment.deadline) + "</b><br>");
			buf.append("<a href='Groups'>Return to Groups Page</a><p>");

			buf.append("Each quiz consists of 10 questions selected at random from the items below. You may select "
					+ "the items that will be used for this group by checking the boxes in the left column. Students are provided "
					+ "answers to the items that they answer incorrectly. Therefore, the total number of questions should be "
					+ "larger than 10, but not much larger than 50.  Experience shows that 30 items is about right in most cases. "
					+ "Students may submit quizzes after the deadline, but the scores will no be included in their group scores.<p>");
			
			Query<Question> questions = ofy.query(Question.class).filter("assignmentType","Quiz").filter("topicId",topicId).filter("isActive",true);
			if (assignment.questionKeys.size()==questions.count()) { // allow instructor to copy assignments from another group:
				List<Assignment> assignments = ofy.query(Assignment.class).filter("assignmentType","Quiz").filter("topicId",topic.id).list();
				if (assignments.size() > 1) {
					buf.append("You may use the box below to copy homework problem selections from another group. "
							+ "The choices include groups where problems have been specifically selected to match content in a particular "
							+ "textbook.  Once selected, you may return to this form and customize the question selections as desired.");
					buf.append("<FORM METHOD=POST ACTION=Groups>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateAssignment>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Quiz>");
					buf.append("<SELECT NAME=AssignmentId><OPTION VALUE=0>Select a group (optional)</OPTION>");
					for (Assignment a:assignments) {
						try {
							if (a.groupId==group.id) continue; 
							Group g = ofy.get(Group.class,a.groupId);
							buf.append("<OPTION VALUE='" + a.id + "'>" + g.description + " (" + User.getBothNames(g.instructorId) + ")</OPTION>");
						} catch (Exception e2) {
							ofy.delete(a);
						}
					}
					buf.append("</SELECT>");
					buf.append("<INPUT TYPE=SUBMIT VALUE='Copy Selections From This Group'></FORM><p>");
				}
			} else buf.append("To copy assigned questions from another group, first assign all questions below.<p>");
			
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			buf.append("<FORM NAME=Questions METHOD=POST ACTION=Groups>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Quiz>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions) {
				i++;
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(assignment.questionKeys.contains(new Key<Question>(Question.class,q.id))?" CHECKED>":">");
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

	String assignHWQuestionsForm(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Select Homework Questions</h3>");
		try {
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic topic = ofy.get(Topic.class,topicId);
			long hi = group.getAssignmentId("Homework",topic.id);
			Assignment assignment = ofy.get(Assignment.class,hi);
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			df.setTimeZone(group.getTimeZone());
			buf.append("<b>Subject: " + subject.title + "<br>"
					+ "Topic: " + topic.title + "<br>"
					+ "Group: " + group.description + "<br>"
					+ "Deadline: " + df.format(assignment.deadline) + "</b><br>");
			buf.append("<a href='Groups'>Return to Groups Page</a><p>");

			buf.append("Select the questions to be assigned to students in this group, then click the button "
					+ "at the bottom of the page. Each question is worth 1 point, so the maximum possible score on the "
					+ "assignment is equal to the number of questions selected. Students may work unassigned problems and/or submit "
					+ "post-deadline answers; however, these are not included in their group scores.<p>");

			Query<Question> questions = ofy.query(Question.class).filter("assignmentType","Homework").filter("topicId",topicId).filter("isActive",true);
			if (assignment.questionKeys.size()==questions.count()) { // allow instructor to copy assignments from another group:
				List<Assignment> assignments = ofy.query(Assignment.class).filter("assignmentType","Homework").filter("topicId",topic.id).list();
				if (assignments.size() > 1) {
					buf.append("You may use the box below to copy homework problem selections from another group. "
							+ "The choices include groups where problems have been specifically selected to match content in a particular "
							+ "textbook.  Once selected, you may return to this form and customize the question selections as desired.");
					buf.append("<FORM METHOD=POST ACTION=Groups>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateAssignment>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Homework>");
					buf.append("<SELECT NAME=AssignmentId><OPTION VALUE=0>Select a group (optional)</OPTION>");
					for (Assignment a:assignments) {
						try {
							if (a.groupId==group.id) continue; 
							Group g = ofy.get(Group.class,a.groupId);
							buf.append("<OPTION VALUE='" + a.id + "'>" + g.description + " (" + User.getBothNames(g.instructorId) + ")</OPTION>");
						} catch (Exception e2) {
							ofy.delete(a);
						}
					}
					buf.append("</SELECT>");
					buf.append("<INPUT TYPE=SUBMIT VALUE='Copy Selections From This Group'></FORM><p>");
				}
			} else buf.append("To copy assigned questions from another group, first assign all questions below.<p>");
			
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=Groups>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topic.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=Homework>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : questions) {
				i++;
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(assignment.questionKeys.contains(new Key<Question>(Question.class,q.id))?" CHECKED>":">");
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

	void updateAssignment(User user,Group group,HttpServletRequest request) {
		try {
			group.setGroupTopicIds();
			String assignmentType = request.getParameter("AssignmentType");
			long i = group.getAssignmentId(assignmentType,Long.parseLong(request.getParameter("TopicId")));
			Assignment assignment = i>0?ofy.get(Assignment.class,i):null;
			
			String[] questionIds = request.getParameterValues("QuestionId");
			String copyAssignmentId = request.getParameter("AssignmentId");
			assignment.questionKeys.clear();
			if (!(copyAssignmentId==null)) {  // copy assigned questions form another group
				try {
					Assignment template = ofy.get(Assignment.class,Long.parseLong(request.getParameter("AssignmentId")));
					for (Key<Question> key: template.questionKeys) assignment.questionKeys.add(key);
				} catch (Exception e2) {}
			}
			else if (questionIds.length==0) {
				ofy.delete(assignment);
			}
			else {
				for (String id : questionIds) assignment.questionKeys.add(new Key<Question>(Question.class,Long.parseLong(id)));
			}
			ofy.put(assignment);
		} catch (Exception e) {System.out.println(e.toString());}
	}		

	String updateDeadlines(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try { 
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
			df.setTimeZone(group.getTimeZone());
			Calendar deadline = Calendar.getInstance(group.getTimeZone());
			try { // update the quiz deadline for this topic
				long i = group.getAssignmentId("Quiz",topicId);
				Assignment a = i>0?ofy.get(Assignment.class,i):null;
				String d = request.getParameter("QuizDeadline");
				if (a != null && d.length()==0) ofy.delete(a);
				else if (d.length()>0) {
					if (a==null) a = new Assignment(group.id,topicId,"Quiz",new Date());
					deadline.setTime(df.parse(d));
					deadline.add(Calendar.DATE,1);		// add 1 day and subtract 1 second
					deadline.add(Calendar.SECOND,-1);	// to set deadline 1 second before midnight on the date indicated
					if (a.deadline.compareTo(deadline.getTime())!=0) {  // deadline was changed
						a.deadline = deadline.getTime();
						ofy.put(a);
						group.deleteScores(a);
						QueueFactory.getDefaultQueue().add(withUrl("/CalculateScores").param("AssignmentId",Long.toString(a.id)));
					}
				}
			} catch (Exception e2) {}
			try { // update the homework deadline for this topic
				long i = group.getAssignmentId("Homework",topicId);
				Assignment a = i>0?ofy.get(Assignment.class,i):null;
				String d = request.getParameter("HWDeadline");
				if (a != null && d.length()==0) ofy.delete(a);
				else if (d.length()>0) {
					if (a==null) a = new Assignment(group.id,topicId,"Homework",new Date());
					deadline.setTime(df.parse(d));
					deadline.add(Calendar.DATE,1);		// add 1 day and subtract 1 second
					deadline.add(Calendar.SECOND,-1);	// to set deadline 1 second before midnight on the date indicated
					if (a.deadline.compareTo(deadline.getTime())!=0) {  // deadline was changed
						a.deadline = deadline.getTime();
						ofy.put(a);
						group.deleteScores(a);
						QueueFactory.getDefaultQueue().add(withUrl("/CalculateScores").param("AssignmentId",Long.toString(a.id)));
					}
				}
			} catch (Exception e2) {}
			group.setGroupTopicIds();
			group.setNextDeadline();
			ofy.put(group);
		}
		catch (Exception e) {
			buf.append("<FONT COLOR=RED>" + e.getMessage() + "</FONT>");
		}
		return buf.toString();
	}

	void copyAssignments(User user, Group group, HttpServletRequest request) {
		try {
			// delete all current assignments for the group
			List<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",group.id).list();
			group.quizAssignmentIds.clear();
			group.hwAssignmentIds.clear();
			ofy.delete(assignments);
			
			// get the properties of the group to be copied from
			long copyGroupId = Long.parseLong(request.getParameter("CopyGroupId"));
			Group copyGroup = ofy.get(Group.class,copyGroupId);
			TimeZone cgTZ = copyGroup.getTimeZone();
			TimeZone myTZ = group.getTimeZone();			
			// get a list of assignments from the group to be copied
			List<Assignment> copyAssignments = ofy.query(Assignment.class).filter("groupId",copyGroupId).list();
			
			for (Assignment ca : copyAssignments) {
				long utcDeadlineMillis = ca.deadline.getTime();
				Date deadline = new Date(utcDeadlineMillis + cgTZ.getOffset(utcDeadlineMillis) - myTZ.getOffset(utcDeadlineMillis));
				Assignment newAssignment = new Assignment(group.id,ca.topicId,ca.assignmentType,deadline);
				newAssignment.questionKeys = ca.questionKeys;
				ofy.put(newAssignment).getId();	
			}
			group.setGroupTopicIds();
			ofy.put(group);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
	public String showGroupPracticeExams(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>PracticeExam Results</h2>");
			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(group.getTimeZone());
			Date now = new Date();
			buf.append(df.format(now) + "<br>");
			buf.append("Group: " + group.getInstructorBothNames() + " - " + group.description + "<p>");
			buf.append("The table below shows a summary of results for all practice exams taken, disaggregated by topic:<UL>"
					+ "<li>Green dot = 85-100%" 
					+ "<li>Yellow dot = 50-84%"
					+ "<li>Red dot = 0-50%"
					+ "<li>No dot = 0 possible points on this topic</UL>"
					+ "Students have the ability to delete all of their practice exam scores at any time.");
			
			buf.append("<p><b>Topics for this Group</b><br>");
			
			List<Long> topicIds = group.getGroupTopicIds();  // list of topics in order of quiz/homework due dates
			Map<Long,Topic> topics = ofy.get(Topic.class,topicIds);
			
			// Make a list of assignment titles
			buf.append("<OL>");
			for (Long t : topicIds) {
				buf.append("<LI>" + topics.get(t).title);
			}
			buf.append("</OL>");
		
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR ALIGN=LEFT><TH>#</TH><TH>Name</TH>");
			for (int i=1;i<=topics.size();i++) { // add a header for each assigned quiz or homework
				buf.append("<TD>" + i + "</TD>");
			}
			buf.append("</TR>"); // end of header row for scores table
			List<User> groupMembers = new ArrayList<User>(ofy.get(User.class,group.memberIds).values());
			Collections.sort(groupMembers);
			int i = 0;
			for (User u : groupMembers) {
				if (u.myGroupId != group.id) {  // user has been removed from this group; skip this entry
					group.memberIds.remove(u.id);
					ofy.put(group);
					continue;
				}
				i++;
				buf.append("<TR><TD>" + i + ".</TD><TD><A href=mailto:" + u.email + ">" + u.getFullName() + "</A></TD>");
				for (Long t : topicIds) {
					buf.append("<TD>" + getPracticeExamScore(u,topics.get(t)) + "</TD>");
				}
				buf.append("</TR>");
			}
			buf.append("</TABLE>");
			
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	public String showGroupScores(User user,Group group,HttpServletRequest request) {
		long LIMIT_MILLIS = 1000 * 25; // response time limit in milliseconds
		StringBuffer buf = new StringBuffer();
		try {
			long startTime = System.currentTimeMillis();
			String assignmentType = request.getParameter("AssignmentType");
			buf.append("<h2>" + assignmentType + " Scores</h2>");
			
			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(group.getTimeZone());
			Date now = new Date();
			buf.append(df.format(now) + "<br>");
			buf.append("Group: " + group.getInstructorBothNames() + " - " + group.description);
			
			// link to save as comma separated values (CSV) file:
			buf.append("<br><a href=Groups?UserRequest=GroupScoresCSV&AssignmentType=" + assignmentType + "&GroupId=" + group.id + ">"
					+ "Save these scores as a comma separated variables (.csv) file</a>");

			// Make a table of assignments for this group
			buf.append("<p><b>" + assignmentType + " Assignments for this Group</b><br>");
		
			List<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",group.id).filter("assignmentType",assignmentType).order("deadline").list();
			int counter = 1;
			df = DateFormat.getDateInstance(DateFormat.SHORT);
			df.setTimeZone(group.getTimeZone());
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>#</TH><TH ALIGN=LEFT>Topic</TH><TH ALIGN=LEFT>Deadline</TH></TR>");
			for (Assignment a : assignments) {
				buf.append("<TR><TD><b>" + (assignmentType.equals("Quiz")?"Q":"HW") + counter + "</b></TD>"
						+ "<TD>" + ofy.get(Topic.class,a.topicId).title + "</TD>"
						+ "<TD>" + df.format(a.deadline) + "</TD></TR>");
				counter++;
			}
			buf.append("</TABLE>");
			
			// Make a table of user scores for the assignments (1 row per student; 1 column per assignment)
			List<User> groupMembers = new ArrayList<User>(ofy.get(User.class,group.memberIds).values());
			Collections.sort(groupMembers);
			Set<Key<Score>> keys = new HashSet<Key<Score>>();
			for (User u : groupMembers) for (Assignment a : assignments) keys.add(new Key<Score>(new Key<User>(User.class,u.id),Score.class,a.id));
			Map<Key<Score>,Score> scoresMap = ofy.get(keys);
			buf.append("<p><b>Scores for this Group (" + group.memberIds.size() + " users)</b><br>");
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR ALIGN=LEFT><TH>#</TH><TH>Name</TH>");
			for (int i=1;i<=assignments.size();i++) { // add a header for each assigned quiz or homework
				buf.append("<TH>" + (assignmentType.equals("Quiz")?"Q":"HW") + i + "</TH>");
			}
			buf.append("<TH>Total</TH></TR>"); // end of header row for scores table
			int i = 0;
			int[] sumScores = new int[assignments.size()];
			int[] nScores = new int[assignments.size()];
			boolean partialTable = false;
			for (User u : groupMembers) {
				if (u.myGroupId != group.id) {  // user has been removed from this group; skip this entry
					group.memberIds.remove(u.id);
					ofy.put(group);
					continue;
				}
				i++;
				buf.append("<TR><TD>" + i + ".</TD><TD><A href=mailto:" + u.email + ">" + u.getFullName() + "</A></TD>");
				int j = 0;
				int studentTotalScore = 0;
				for (Assignment a : assignments) {
					Key<Score> k = new Key<Score>(new Key<User>(User.class,u.id),Score.class,a.id);
					Score s = scoresMap.get(k);
					if (s==null) s = group.getScore(u.id, a);
					/*if (s==null) {
						s = Score.getInstance(u.id,a);
						ofy.put(s);
					}*/
					sumScores[j] += s.score;
					studentTotalScore += s.score;
					if (s.numberOfAttempts>0) nScores[j]++;
					j++;
					buf.append("<TD ALIGN=CENTER>" + s.getDotScore(a.deadline,group.rescueThresholdScore) + "</TD>");
				}
				buf.append("<TD ALIGN=CENTER>" + studentTotalScore + "</TD></TR>");
				if (System.currentTimeMillis()-startTime > LIMIT_MILLIS) {
					partialTable = true;
					break; 
				}
			}
			if (partialTable) {  // print a warning message
				buf.append("<TR><TD COLSPAN=" + (assignments.size() + 2) + ">"
						+ "Due to a timeout limitation of the system, this is only a partial list of scores.<br>" 
						+ "Please refresh this page to resume calculating the scores.</TD></TR>");
			} else {
				// print a row of average scores for the assignments
				buf.append("<TR><TD COLSPAN=2>Average Score</TD>");
				for (int j=0;j<assignments.size();j++) {
					try {
						if (nScores[j] > 0) buf.append("<TD ALIGN=CENTER>" + Math.round(10.0*sumScores[j]/nScores[j])/10.0  + "</TD>");
						else buf.append("<TD>&nbsp;</TD>");
					} catch (Exception e) {
						buf.append("<TD>" + e.toString()+ "</TD>");
					}
				}
				buf.append("<TD></TD></TR>");
			}
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	void csvGroupScores(User user,Group group,HttpServletRequest request,HttpServletResponse response) {
		long LIMIT_MILLIS = 1000 * 25; // response time limit in milliseconds
		PrintWriter out = null;
		try {
			long startTime = System.currentTimeMillis();
			response.setContentType("text/csv");
			out = response.getWriter();
			String assignmentType = request.getParameter("AssignmentType");
			List<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",group.id).filter("assignmentType",assignmentType).order("deadline").list();
			List<User> groupMembers = new ArrayList<User>(ofy.get(User.class,group.memberIds).values());
			Collections.sort(groupMembers);
			Set<Key<Score>> keys = new HashSet<Key<Score>>();
			for (User u : groupMembers) for (Assignment a : assignments) keys.add(new Key<Score>(new Key<User>(User.class,u.id),Score.class,a.id));
			Map<Key<Score>,Score> scoresMap = ofy.get(keys);
			StringBuffer buf = new StringBuffer();
			
			// header row of csv file:
			buf.append("Name,Email");
			for (int i=1;i<=assignments.size();i++) buf.append((assignmentType.equals("Quiz")?",Quiz ":",Homework ") + i);
			out.println(buf.toString());
			
			for (User u : groupMembers) {
				buf = new StringBuffer();
				if (u.myGroupId != group.id) {  // user has been removed from this group; skip this entry
					group.memberIds.remove(u.id);
					ofy.put(group);
					continue;
				}
				buf.append("\"" + u.getFullName() + "\"," + u.email);
				for (Assignment a : assignments) {
					Key<Score> k = new Key<Score>(new Key<User>(User.class,u.id),Score.class,a.id);
					Score s = scoresMap.get(k);
					if (s==null) {
						s = Score.getInstance(u.id,a);
						ofy.put(s);
					}
					buf.append("," + s.getScore());
				}
				out.println(buf.toString());
				if (System.currentTimeMillis()-startTime > LIMIT_MILLIS) throw new Exception();
			}
		} catch (Exception e) {
			out.println("An error occurred, most likely due to a system timeout. "
					+ "Please refresh this page to continue exporting the group scores to a CSV file");
		}
	}
	
	String showRescueOptions(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Group Rescue Service Options</h2>");
		buf.append("Group: " + group.description + "<br>");
		buf.append("Instructor: " + group.getInstructorBothNames() + "<p>");
		
		buf.append("ChemVantage offers the optional service of notifying students and teaching assistants in your "
				+ "group when individual students miss an assignment deadline or score poorly on an assignment. Use "
				+ "the form elements on this page to configure this service to meet the needs of your group or class.<p>");
		try {
			buf.append("<FORM ACTION=Groups METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=RescueService>"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
					+ "<INPUT TYPE=HIDDEN NAME=SendRescueMessages VALUE=" + Boolean.toString(!group.sendRescueMessages) + ">"
					+ "The ChemVantage Rescue Service is currently " + (group.sendRescueMessages?"activated.":"turned off.")
					+ " <INPUT TYPE=SUBMIT VALUE='Turn Rescue Service " + (group.sendRescueMessages?"Off":"On") + "'>"
					+ "</FORM>");
			if (group.sendRescueMessages) {
				buf.append("<FORM ACTION=Groups METHOD=POST><h3>Rescue Service Options</h3>");
				buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=RescueService>"
						+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
						+ "<INPUT TYPE=HIDDEN NAME=SendRescueMessages VALUE=true>");
				buf.append("Each student in this group will be notified if he or she misses the deadline for "
						+ "an assignment<br>or has a score of "
						+ "<INPUT TYPE=TEXT SIZE=4 NAME=RescueThresholdScore VALUE=" + group.rescueThresholdScore + "> or below.<p>");
				buf.append("Email subject line: <INPUT TYPE=TEXT SIZE=40 NAME=DefaultRescueSubject VALUE='" + group.defaultRescueSubject + "'><br>");
				buf.append("Email message text:<br><TEXTAREA NAME=DefaultRescueMessage ROWS=15 COLS=80 WRAP=SOFT>" + group.defaultRescueMessage + "</TEXTAREA><br>");
				buf.append("Select one or more of the following contacts to be copied in the email:<br>");
				buf.append("<INPUT TYPE=CHECKBOX NAME=RescueCcIds VALUE=" + group.instructorId + (group.rescueCcIds.contains(group.instructorId)?" CHECKED>":">") 
						+ User.getBothNames(group.instructorId) + " (" + User.getEmail(group.instructorId) + ")<br>");
				for (String id : group.tAIds)
					buf.append("<INPUT TYPE=CHECKBOX NAME=RescueCcIds VALUE=" + id + (group.rescueCcIds.contains(id)?" CHECKED>":">") 
							+ User.getBothNames(id) + " (" + User.getEmail(id) + ")<br>");
					buf.append("<INPUT TYPE=SUBMIT VALUE='Update Rescue Service Options'></FORM>");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	void setRescueOptions(User user,Group group,HttpServletRequest request) {
		if (request.getParameter("SendRescueMessages") != null)	group.sendRescueMessages = Boolean.parseBoolean(request.getParameter("SendRescueMessages"));
		try {
			group.rescueThresholdScore = Integer.parseInt(request.getParameter("RescueThresholdScore"));
			group.defaultRescueSubject = request.getParameter("DefaultRescueSubject");
			group.defaultRescueMessage = request.getParameter("DefaultRescueMessage");
			String[] rescueCcIds = request.getParameterValues("RescueCcIds");
			group.rescueCcIds.clear();
			if (rescueCcIds!=null && rescueCcIds.length>0) for (String id : rescueCcIds) group.rescueCcIds.add(id);
		} catch (Exception e) {}
		if (group.defaultRescueSubject == null || group.defaultRescueSubject.length()==0) {
			 group.defaultRescueSubject = "ChemVantage Rescue Service Message";
		}
		if (group.defaultRescueMessage == null || group.defaultRescueMessage.length()==0) {
			group.defaultRescueMessage = "Oops! You may have missed a ChemVantage assignment deadline.\n\n"
				+ "You are receiving this message because you either failed to submit an assignment at "
				+ "ChemVantage.org before the deadline, or your score was low enough to trigger a concern.\n\n"
				+ "You may be able to earn some makeup credit by completing the assignment after the due date. "
				+ "You should see a red dot beside the score on this assignment on your ChemVantage Scores page. "
				+ "When you complete the assignment satisfactorily after the deadline, the red dot will disappear.";
		}
		ofy.put(group);
	}
	
	String showGroupMembers(User user,Group group,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Manage Group Enrollments</h2>");
		try {
			List<User> groupMembers = new ArrayList<User>(ofy.get(User.class,group.memberIds).values());
			Collections.sort(groupMembers);
			
  			List<User> groupTAs = new ArrayList<User>();
			for (String id : group.tAIds) {
				groupTAs.add(ofy.get(User.class,id));
			}
 			Collections.sort(groupTAs);
			
			buf.append("<b>Group: </b>" + group.getInstructorBothNames() + " - " + group.description + "<p>");

			String searchString = request.getParameter("SearchString");
			if (searchString==null) searchString = "";
			searchString = searchString.toLowerCase().trim();
			int i = searchString.indexOf('*');
			searchString = (i>=0?searchString.substring(0,i+1):searchString);

			buf.append("<h4>User Search</h4>");
			buf.append("<FORM ACTION=Groups METHOD=GET>");
			buf.append("You may search for users in order to:<ul>"
					+ "<li>Assign a user to be a teaching assistant for this group"
					+ "<li>Invite any user to join this group</ul>" 
					+ "Enter the user's exact email address or any portion of the <i>lastName, firstName</i>.<br>"
					+ "Use * to search for all users. The search will return a maximum of " + this.queryLimit + " results.<br>\n");
			buf.append("<INPUT NAME=SearchString VALUE='" + searchString + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=GroupMembers>"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
					+ "<INPUT TYPE=SUBMIT VALUE='Search for Users'></FORM>\n");

			if (searchString.length() > 0) {   //present the results of the search
				searchString.replace("*","");	
				List<User> searchResults = new ArrayList<User>();
				User foundUser = ofy.find(User.class,searchString);
				if (foundUser != null) searchResults.add(foundUser);
				if (searchResults.isEmpty()) searchResults = ofy.query(User.class).filter("email",searchString).list();
				if (searchResults.isEmpty()) searchResults = ofy.query(User.class).filter("lowercaseName >=",searchString).filter("lowercaseName <",(searchString+'\ufffd')).limit(this.queryLimit).list();

				buf.append("<h4>Search Results</h4>");
				if (searchResults.isEmpty()) {
					buf.append("No matching users were found. Try using just the last name, or a shorter portion of the name.");		
				} else {
					buf.append("<TABLE BORDER=1 CELLSPACING=0>"
							+ "<TR><TD><b>Name</b></TD><TD><b>Email</b></TD><TD COLSPAN=2><b>Add To Group</b></TD></TR>");
					for (User u : searchResults) {
						buf.append("<TR><TD>" + u.getFullName() + "</TD>");
						String email = u.email;
						String id = u.id; // this should be identical to email except for Google account holders
						buf.append("<TD><a href=mailto:" + email + ">" + email + "</a></TD>");
						if (u.email.equals(searchString)) {
							buf.append("<TD><FONT COLOR=#FF0000>" + request.getParameter("Message") + "</FONT></TD>");
						}
						else {
							if (group.isMember(id)) {
								buf.append("<TD ALIGN=CENTER>member</TD>");
							} else if (group.id.equals(u.myGroupId)) {
								buf.append("<TD ALIGN=CENTER>invited</TD>");
							} else {
								buf.append("<FORM ACTION=Groups METHOD=POST><TD>"
										+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=InviteUser>"
										+ "<INPUT TYPE=HIDDEN NAME=SearchString VALUE='" + CharHider.quot2literal(searchString) + "'>"
										+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + id + "'>"
										+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
										+ "<INPUT TYPE=SUBMIT VALUE='Invite to Join'>"
										+ "</TD></FORM>");
							}
							if (group.isTA(id)) {
								buf.append("<TD ALIGN=CENTER>TA</TD>");
							} else {
								buf.append("<FORM ACTION=Groups METHOD=POST><TD>"
										+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=AssignTA>"
										+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + id + "'>"
										+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
										+ "<INPUT TYPE=SUBMIT VALUE='Assign as TA'></TD></FORM>");
							}
						}
						buf.append("</TR>");
					}
					buf.append("</TABLE>");
				}
			}
			buf.append("<h4>Current Group Members</h4>");
			boolean headerWritten = false;
			for (User u : groupTAs) {
				if (!headerWritten) {
					buf.append("Teaching Assistants<br>");
					buf.append("<TABLE BORDER=1 CELLSPACING=0>"
							+ "<TR><TD><b>Name</b></TD><TD><b>Email</b></TD><TD><b>Remove From Group</b></TD></TR>");
					headerWritten = true;
				}
				buf.append("<TR><TD>" + u.getFullName() + "</TD>");
				String email = u.email;
				buf.append("<TD><A href=mailto:" + email + ">" + email + "</a></TD>"
						+ "<FORM ACTION=Groups METHOD=POST>"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='DropTA'>"
						+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
						+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
						+ "<TD><INPUT TYPE=SUBMIT VALUE='Drop this TA'></TD></FORM></TR>");
			}
			if (headerWritten) buf.append("</TABLE><br>");

			int nStudents = groupMembers.size();
			if (nStudents > 0) { // make a table of current student group members
				buf.append("Students (" + nStudents + ")<br>");
				buf.append("<TABLE BORDER=1 CELLSPACING=0>"
						+ "<TR><TD><b>Name</b></TD><TD><b>Email</b></TD><TD><b>Remove From Group</b></TD></TR>");
				for (User u : groupMembers) {
					buf.append("<TR><TD>" + u.getFullName() + "</TD>");
					String email = u.email;
					buf.append("<TD><A href=mailto:" + email + ">" + email + "</a></TD>"
							+ "<FORM ACTION=Groups METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='DropUser'>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE='" + group.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=UserId VALUE='" + u.id + "'>"
							+ "<TD><INPUT TYPE=SUBMIT VALUE='Drop this user'></TD></FORM></TR>");
				}
				buf.append("</TABLE>");
			}
			else buf.append("No students have joined this group.");

		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	String inviteUser(User user,Group group,HttpServletRequest request,User invitee) {
		StringBuffer buf = new StringBuffer();
		try {
			invitee.myGroupId = group.id;
			ofy.put(invitee);
			buf.append("<h3>Success</h3>");
			buf.append(invitee.getBothNames() + " has been invited to join this group.<br>");
			buf.append("<a href=Groups?UserRequest=GroupMembers&GroupId=" + group.id);
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	public String getPracticeExamScore(User user,Topic topic) {
		Query<PracticeExamTransaction> transactions = ofy.query(PracticeExamTransaction.class).filter("userId",user.id);
		int score = 0;
		int possibleScore = 0;
		for (PracticeExamTransaction pt : transactions) {
			for (int i=0;i<pt.topicIds.length;i++) {
				if (pt.topicIds[i]==topic.id) {
					score += pt.scores[i];
					possibleScore += pt.possibleScores[i];
				}
			}
		}
		if (possibleScore == 0) return "&nbsp";
		double pct = 100. * score / possibleScore;
		if (pct >= 85) return "<img src=images/green_dot.gif alt=green>";
		if (pct >= 50) return "<img src=images/yellow_dot.gif alt=yellow>";
		return "<img src=images/red_dot.gif alt=red>";
	}

}		
