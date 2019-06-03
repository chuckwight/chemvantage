/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2014 ChemVantage LLC
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
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class PracticeExam extends HttpServlet {
	// parameters that determine the properties of the exam program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for exam overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printExam()
	int timeLimit = 60;                  // minutes; set to zero for no time limit to complete the exam
	int waitForNewDownload = 0;          // minutes; set to zero for unlimited rate of exam downloads
	boolean enforceDeadlines = true;     // true means that exam score is not recorded after the deadline
	boolean allowMultipleTries = true;   // false allows only one attempt at each exam; true is recommended
	boolean scrambleQuestions = true;    // false presents the same questions each attempt; true is recommended
	boolean allowWorkAhead = false;      // true makes every exam available; false makes available after the deadline for the previous exam expires
	boolean showMissedQuestions = true;  // true reveals questions that were answered incorrectly as part of the grading process
	boolean useSectionDeadlines = false; // true uses default deadlines for all sections of the course
	int numberOfSections = 1;            // 
	boolean trackAnswers= false;         // true keeps track of missed questions for students
	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	static Map<Key<Question>,Question> examQuestions = new HashMap<Key<Question>,Question>();

	public String getServletInfo() {
		return "This servlet presents and scores an exam for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		HttpSession session = request.getSession();
		User user = null;
		if (session.isNew()) {
			user = Nonce.getUser(request.getParameter("Nonce"));
			session.setAttribute("UserId", user.id);
		} else user = User.getInstance(session);
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String nonce = session.isNew()?Nonce.createInstance(user):null;
		out.println(Home.header + printExam(user,request,nonce) + Home.footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		HttpSession session = request.getSession();
		User user = null;
		if (session.isNew()) {
			user = Nonce.getUser(request.getParameter("Nonce"));
			session.setAttribute("UserId", user.id);
		} else user = User.getInstance(session);
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.header + printScore(user,request) + Home.footer);
	}

	String designExam(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>Practice " + subject.title + " Exam</h2>");
			buf.append("<div id=topicsForm>");

			buf.append("Please select <b>at least 3 topics</b> below to be covered on this practice exam.<p>");
			buf.append("<FORM NAME=TopicForm METHOD=GET>");
			buf.append(nonce!=null?"<input type=hidden name=Nonce value='" + nonce + "'":"");
			buf.append("\n<TABLE>");
			List<Topic> topics = ofy().load().type(Topic.class).list();
			int i = 0;
			for (Topic t : topics) {
				if ("Hide".equals(t.orderBy)) continue;
				buf.append(i%3==0?"<TR><TD>":"<TD>");
				buf.append("<label><INPUT TYPE=CHECKBOX NAME=TopicId VALUE='" + t.id + "' "
						+ "onClick=\"javascript: var checked=0; "
						+ "for(i=0;i<document.TopicForm.TopicId.length;i++) if(document.TopicForm.TopicId[i].checked) checked++;"
						+ "document.TopicForm.begin.disabled=(checked<3);"
						+ "if(document.TopicForm.begin.disabled) document.TopicForm.begin.value='Select at least 3 topics';"
						+ "else document.TopicForm.begin.value='Begin the exam';\">" 
						+ t.title + "</label><br>\n");
				buf.append(i%3==2?"</TD></TR>\n":"</TD>");
				i++;
			}
			buf.append("</TABLE><p>");
			buf.append("You will have " + timeLimit + " minutes to submit this exam for scoring.<br>");
			buf.append("<INPUT TYPE=SUBMIT NAME=begin DISABLED=true VALUE='Select at least 3 topics'>");
			buf.append("</FORM></div>");
		} catch (Exception e) {
			buf.append("designExam: " + e.toString());
		}
		return buf.toString();
	}
/*
	String instructorPage(HttpServletRequest request,long assignmentId,String nonce) {
		// this page is displayed by default when the instructor accesses this assigned exam
		// to view the exam itself, include ShowPracticeExam=true as one of the GET parameters
		StringBuffer buf = new StringBuffer();
		try {
			Assignment assignment = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group group = ofy().load().type(Group.class).id(assignment.groupId).safe();

			DateFormat dfShort = DateFormat.getDateInstance(DateFormat.SHORT);
			DateFormat dfLong = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			dfShort.setTimeZone(group.getTimeZone());
			dfLong.setTimeZone(group.getTimeZone());
			boolean noDeadline = assignment.getDeadline().getTime()==0L;
			
			buf.append("\n<h2>" + subject.title + " Practice Exam</h2>");
			buf.append("<FONT SIZE=-1>This is the instructor page; students will <a href=/PracticeExam?AssignmentId=" + assignment.id + "&ShowPracticeExam=true&Nonce=" + nonce + ">go directly to the practice exam</a>.</FONT><p>");
			
			buf.append("<b>Topics covered:</b><OL>");
			for (long topicId : assignment.topicIds) buf.append("<LI>" + ofy().load().type(Topic.class).id(topicId).safe().title + "</LI>");
			buf.append("</OL><p>");

			buf.append("<FORM ACTION='/Groups' METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=Nonce VALUE=" + nonce + ">"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=PracticeExam>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE=" + assignment.id + ">"
					+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + group.id + ">"
					+ "<b>Practice Exam Deadline:</b> " + (noDeadline?"<FONT COLOR=RED>none</FONT>":dfLong.format(assignment.getDeadline()))
					+ " <a href=# onClick=document.getElementById('deadlineForm').style.display='inLine'><FONT SIZE=-2>change this</FONT></a><p>");
			buf.append("<div id='deadlineForm' style='display:none'>"
					+ "Enter a date below and select your local time zone.<br/>"
					+ "After the deadline ChemVantage will not report scores on this practice exam to the LMS,<br/>"
					+ "but students may still use the assignment link to practice taking exams.<br/>"
					+ "<INPUT TYPE=TEXT SIZE=15 NAME=PracticeExamDeadline VALUE='" + (noDeadline?"none":dfShort.format(assignment.getDeadline())) 
					+ "' onFocus=if(PracticeExamDeadline.value=='none'){PracticeExamDeadline.value='" + dfShort.format(new Date()) + "';document.getElementById('esBox').checked=true}>"
					+ "at 11:59:59 PM in the " + Groups.timeZoneSelectBox(group.timeZone,false) + " time zone.<br/>"
					+ "<label><INPUT TYPE=CHECKBOX ID=esBox NAME=EmailScores VALUE=true" + (assignment.emailScoresToInstructor?" CHECKED>":">") + " Email scores to me after the deadline.</lable><br>"
					+ "<label><INPUT TYPE=CHECKBOX NAME=UpdateLMSScores VALUE=true> Update scores in the LMS grade book.</label><br>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Set Deadline'></FORM></div><p>");
		
			buf.append("<b>Customize This Exam</b> <a id=slink href=/Groups?UserRequest=AssignExamQuestions&GroupId=" + group.id + "&AssignmentId=" + assignment.id + "&Nonce=" + nonce + "><FONT SIZE=-2>select questions</FONT></a><p>");
				
			buf.append("<b>Practice Exam Scores</b> <a href=# onClick=document.getElementById('details').style.display='inLine'><FONT SIZE=-2>show details</FONT></a><br/>"
					+ "<div id='details' style='display:none'>The following is a list of maximum pre-deadline scores on this exam. In most cases, these scores have been reported to the grade book "
					+ "in the class learning management system. However, the LMS may have a policy that is different from ChemVantage (e.g., record first score only), so it "
					+ "is possible that these scores may be different from those in the LMS grade book. The number of exam attempts is shown in parentheses for your reference. "
					+ "A red dot indicates a score that is low enough to be a concern. If a student completes this assignment satisfactorily after the deadline, the red dot will "
					+ "disappear, but the score will remain unchanged. If you change the deadline, all scores will be recalculated to reflect the revised deadline. (Try it!)</div><p>");
			
			if (group.validatedMemberCount()==0) return buf.toString();
			Map<String,User> members = ofy().load().type(User.class).ids(group.memberIds);
			
			// prepare a complete set of Score keys for this assignment and load all existing keys into the scoresMap
			Set<Key<Score>> keys = new HashSet<Key<Score>>();
			for (String id:group.memberIds) keys.add(Key.create(Key.create(User.class,id),Score.class,assignment.id));
			Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
			int i = 0;
			Score s = null;
			
			buf.append("Instructors and Teaching Assistants<br>"
					+ "<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
			for (String id:group.memberIds) {
				try {
					User u = members.get(id);
					if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) {
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
						s = scoresMap.get(k);
						if (s==null) {
							s = Score.getInstance(u.id,assignment);
							ofy().save().entity(s).now();
						}
						i++;
						buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getScore() + "</TD></TR>");
						members.remove(id);
					}
				} catch (Exception e2) {}
			}
			buf.append("</TABLE><p>");

			// display the table of student scores, filling in where it may be incomplete (this is rare, but possible due to add/drop)
			i=0;
			Date now = new Date();
			buf.append("Students<p>");
			if (members.size()>0) {
				buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TD></TD><TD>Name</TD><TD>Email</TD><TD>Score</TD></TR>");
				for (String id:group.memberIds) {
					try {
						User u = members.get(id);
						if (u.isInstructor() || u.isAdministrator() || group.isTA(u.id)) continue;
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,assignment.id);
						s = scoresMap.get(k);
						if (s==null) {
							s = Score.getInstance(u.id,assignment);
							ofy().save().entity(s).now();
						}
						i++;
						buf.append("<TR><TD>" + i + "</TD><TD>" + u.getFullName() + "</TD><TD>" + u.getEmail() + "</TD><TD ALIGN=CENTER>" + s.getDotScore(noDeadline?now:assignment.getDeadline(),group.rescueThresholdScore) + "</TD></TR>");
					} catch (Exception e2) {}
				} 
				buf.append("</TABLE>");
			} else buf.append("No students are registered in this group.");

		} catch (Exception e) {
			return buf.toString() + e.getMessage();
		}
		return buf.toString();
	}
*/	
	String printExam(User user,HttpServletRequest request,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			// Get requested topic ids for this exam
			List<Long> topicIds = new ArrayList<Long>();
			long assignmentId = 0;
			Assignment a = null;
			try {  // this branch works if the practice exam is assigned
				assignmentId=Long.parseLong(request.getParameter("AssignmentId"));
				a = ofy().load().type(Assignment.class).id(assignmentId).safe();
				topicIds = a.topicIds;
			} catch (Exception e) {  // otherwise this is a student-designed exam
				String[] topicStringIds = request.getParameterValues("TopicId");
				if (topicStringIds != null) {
					for (int i=0;i<topicStringIds.length;i++) topicIds.add(Long.parseLong(topicStringIds[i]));
				}
			}

			// Check to see if this user has any pending exams:
			Date now = new Date();
			Date oneHourAgo = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			String lis_result_sourcedid = request.getParameter("lis_result_sourcedid");
			if (lis_result_sourcedid==null) lis_result_sourcedid = request.getParameter("custom_lis_result_sourcedid");
			
			List<PracticeExamTransaction> qpt = ofy().load().type(PracticeExamTransaction.class).filter("userId",user.id).filter("graded",null).filter("downloaded >",oneHourAgo).list();
			PracticeExamTransaction pt = null;  // placeholder for recovery of one of the pending exam transactions
			
			if (qpt.size()>0) {  // there is at least one pending practice exam
				if (a == null) {  // entered by manual topic selection (no assignment)
					pt = qpt.get(0);  // gets the first pending practice exam transaction in the list
					topicIds = pt.topicIds;
					buf.append("<script language=javascript>"
							+ "onload=alert('You are resuming a previously downloaded exam.')"
							+ "</script>");
				} else {  // the request is for an exam corresponding to an assignment
					for (PracticeExamTransaction t : qpt) {
						if (t.topicIds.equals(a.topicIds)) {
							pt = t;  // found the correct pending exam for this assignment
							break;
						}
					}  // if the for-loop expires without finding a corresponding transaction, pt remains null and a new exam is created below
				}
			}
			else if (topicIds.size() < 3) return designExam(user,request,nonce);  // redirect to get a valid set of 3+ topic keys
			
			
			if (pt == null) {  // this is a valid request for a new exam with at least 3 topicIds; create a new transaction
				pt = new PracticeExamTransaction(topicIds,user.id,now,null,new int[topicIds.size()],new int[topicIds.size()],lis_result_sourcedid,request.getRemoteAddr());
				ofy().save().entity(pt).now();	
			}
			
			// past this point we will present a practice exam to the student
			
			List <Key<Question>> questionKeys_02pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",2).filter("topicId in",topicIds).keys().list();
			List <Key<Question>> questionKeys_10pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",10).filter("topicId in",topicIds).keys().list();
			List <Key<Question>> questionKeys_15pt = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("pointValue",15).filter("topicId in",topicIds).keys().list();
			
			List<Key<Question>> remove = new ArrayList<Key<Question>>();
			if (a != null) {  // eliminate any questionKeys not listed in the assignment
				for (Key<Question> k : questionKeys_02pt) if (!a.questionKeys.contains(k)) remove.add(k);
				questionKeys_02pt.removeAll(remove); remove.clear();
				for (Key<Question> k : questionKeys_10pt) if (!a.questionKeys.contains(k)) remove.add(k);
				questionKeys_10pt.removeAll(remove); remove.clear();
				for (Key<Question> k : questionKeys_15pt) if (!a.questionKeys.contains(k)) remove.add(k);
				questionKeys_15pt.removeAll(remove); remove.clear();
			}

			buf.append("\n<h2>" + subject.title + " Exam</h2>");

			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			
			Date downloaded = pt.downloaded;
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - downloaded.getTime())/1000);

			buf.append("Topics covered on this exam:<OL>");
			for (long topicId : topicIds) {
				buf.append("<LI>" + ofy().load().type(Topic.class).id(topicId).safe().title + "</LI>");
			}
			buf.append("</OL>");

			if (user.isInstructor()) buf.append("<table style='border: 1px solid'><tr><td>"
					+ "Instructor: you may <a href=/Groups?UserRequest=AssignExamQuestions&AssignmentId=" + a.id + "&Nonce=" + nonce + ">"
					+ "customize this practice exam</a> by selecting/deselecting the available question items."
					+ "</td></tr></table><p>");
			
			buf.append("This exam must be submitted for grading within " + timeLimit + " minutes of when it is first downloaded.");

			Random rand = new Random();  // create random number generator to select exam questions
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			rand.setSeed(pt.id);  // random number generator seeded with PracticeExamTransaction id value

			buf.append(ajaxExamJavaScript());  // this code allows users to use the Google SOAP search spell checking function
			
			buf.append("\n<FORM METHOD=POST ACTION=PracticeExam "
					+ "onSubmit=\"return confirm('Submit this exam for grading now. Are you sure?')\">");

			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Practice Exam'><p>");

			// Include a nonce reference as a hedge in case the session is not maintained by the user's browser
			buf.append(nonce!=null?"\n<input type=hidden name=Nonce value='" + nonce + "'>":"");
			if (a!=null) buf.append("\n<input type=hidden name=AssignmentId value='" + a.id + "'>");
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			int[] possibleScores = new int[topicIds.size()];

			// Two-point questions
			buf.append("<U>2 point questions:</U>");
			buf.append("<OL>\n");
			int nQuestions = 10;
			int i = 0;
			while (i<nQuestions && questionKeys_02pt.size()>0) {
				Key<Question> k = questionKeys_02pt.remove(rand.nextInt(questionKeys_02pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy().load().key(k).safe();
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicIds.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
			buf.append("</OL>");

			// 10-point questions
			buf.append("<U>10 point questions:</U>");
			buf.append("<OL>\n");
			nQuestions = 5;
			i=0;
			while (i<nQuestions && questionKeys_10pt.size()>0) {
				Key<Question> k = questionKeys_10pt.remove(rand.nextInt(questionKeys_10pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy().load().key(k).safe();
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicIds.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
						buf.append("</OL>");

			// 15-point questions
			buf.append("<U>15 point questions:</U>");
			buf.append("<OL>\n");
			nQuestions = 2;
			i = 0;
			while (i<nQuestions && questionKeys_15pt.size()>0) {
				Key<Question> k = questionKeys_15pt.remove(rand.nextInt(questionKeys_15pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy().load().key(k).safe();
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicIds.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
			buf.append("</OL>");

			pt.possibleScores = possibleScores;
			ofy().save().entity(pt);

			buf.append("\n<input type=hidden name='ExamId' value=" + pt.id + ">");
			buf.append("\n<input type=hidden name='UserRequest' value='GradeExam'>");
			buf.append("<div id='timer1' style='color: red'></div><div id=ctrl1 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Practice Exam'>");
			buf.append("\n</form>");

			// this code for displaying/hiding timers and a 30-seconds-remaining alert box
			buf.append(timerScripts(secondsRemaining)); 
			
		} catch (Exception e) {
			buf.append("printExam: " + e.toString());
		}
		return buf.toString();
	}

	String timerScripts(int secondsRemaining) {
		return "<SCRIPT language='JavaScript'>"
				+ "function toggleTimers() {"
				+ "  var timer0 = document.getElementById('timer0');"
				+ "  var timer1 = document.getElementById('timer1');"
				+ "  var ctrl0 = document.getElementById('ctrl0');"
				+ "  var ctrl1 = document.getElementById('ctrl1');"
				+ "  if (timer0.style.display=='') {" 
				+ "    timer0.style.display='none';timer1.style.display='none';"
				+ "    ctrl0.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
				+ "    ctrl1.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';"
				+ "  } else {"
				+ "    timer0.style.display='';timer1.style.display='';"
				+ "    ctrl0.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
				+ "    ctrl1.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';"
				+ "  }"
				+ "}"
				+ "var seconds;var minutes;var oddSeconds;"
				+ "var endTime = new Date().getTime() + " + secondsRemaining + "*1000;"
				+ "function countdown() {"
				+ "var now = new Date().getTime();"
				+ "seconds=Math.round((endTime-now)/1000);"
				+ "minutes = seconds<0?Math.ceil(seconds/60):Math.floor(seconds/60);"
				+ "oddSeconds = seconds%60;"
				+ "for(i=0;i<2;i++)"
				+ "document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
				+ "if (seconds==30) alert('30 seconds remaining');"
				+ "setTimeout('countdown()',1000);"
				+ "}"
				+ "countdown();"
				+ "</SCRIPT>"; 
	}
	
	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>Practice Exam Results</h2>");
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			buf.append(df.format(now) + "<br>");
			
			long examId = Long.parseLong(request.getParameter("ExamId"));
			PracticeExamTransaction pt = ofy().load().type(PracticeExamTransaction.class).id(examId).safe();
			if (pt.graded != null) return "Sorry, this exam has been graded already.";
			if (now.getTime() - pt.downloaded.getTime() > 60000*(this.timeLimit+1)) return "Sorry, the grading period for this exam has expired.";

			// if everything is still OK, score the exam:
			List<Long> topicIds = pt.topicIds;			
			List<String> topicTitles = new ArrayList<String>();
			for (int i=0;i<topicIds.size();i++) topicTitles.add(ofy().load().type(Topic.class).id(topicIds.get(i)).safe().title);
			
			// create a buffer to hold the correct solutions to missed questions:
			StringBuffer missedQuestions = new StringBuffer();
			missedQuestions.append("The following questions were answered incorrectly. There may be additional questions (not shown) that were left unanswered.");			
			missedQuestions.append("<OL>");

			int[] studentScores = new int[topicIds.size()];
			int wrongAnswers = 0;

			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			// begin the main scoring loop:
			for (Key<Question> k : questionKeys) {

				String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
				if (studentAnswer != null) for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
				else studentAnswer = new String[] {""};
				if (studentAnswer[0].length() > 0) { // an answer was submitted
					Question q = examQuestions.get(k);
					if (q==null) {
						try {
							q = ofy().load().key(k).safe();
							examQuestions.put(k,q);
						} catch (Exception e) {
							continue;
						}
					}
					q.setParameters((int)(pt.id - q.id));
					int score = studentAnswer[0].length()==0?0:q.isCorrect(studentAnswer[0])?q.pointValue:0;
					if (score > 0) studentScores[topicIds.indexOf(q.topicId)] += score;
					if (studentAnswer[0].length() > 0) ofy().save().entity(new Response("PracticeExam",q.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));
					if (score == 0) {
						// include question in list of incorrectly answered questions
						wrongAnswers++;
						missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer[0],false) + "</LI>\n");
					}
				}
			}
			missedQuestions.append("</OL>\n");
			pt.scores = studentScores;
			pt.graded = now;
			ofy().save().entity(pt);
			
			Assignment a = null;  // find the Assignment object for this Practice Exam, if it exists
			try {
				a = ofy().load().type(Assignment.class).id(Long.parseLong(request.getParameter("AssignmentId"))).safe();
				Queue queue = QueueFactory.getDefaultQueue();  // used for computing Score objects offline by Task queue
				Score s = Score.getInstance(user.id,a);
				ofy().save().entity(s).now();
				if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",a.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
			} catch (Exception e) {}

			int score = 0;
			int possibleScore = 0;
			for (int i=0;i<topicIds.size();i++) {
				score += studentScores[i];
				possibleScore += pt.possibleScores[i];
			}
			buf.append("<b>Your score on this exam is " + score + " out of a possible " + possibleScore + " points.</b><p>");
			if (score > 0 && score == possibleScore) buf.append ("<b>Congratulations on a perfect score!</b>");
			else {
				if (wrongAnswers > 0) {
					buf.append("<TABLE><TR><TD><b>Topic</b></TD><TD><b>Score</b></TD>"
							+ "<TD><b>Possible</b></TD><TD><b>Percent</b></TD><TD></TD></TR>");
					for (int i=0;i<topicIds.size();i++) {
						int pct = (pt.possibleScores[i]>0?pt.scores[i]*100/pt.possibleScores[i]:0);
						String color = (pct>84?"#00FF00":(pct<50?"#FF0000":"#FFFF00"));
						buf.append("<TR>"
								+ "<TD>" + topicTitles.get(i) + "</TD>"
								+ "<TD ALIGN=RIGHT>" + pt.scores[i] + "</TD>"
								+ "<TD ALIGN=RIGHT>" + pt.possibleScores[i] + "</TD>"
								+ "<TD ALIGN=RIGHT>" + pct + "%</TD>"
								+ "<TD><div style='background-color:" + color + ";width:" + pct 
								+ "px;'/>&nbsp;</TD></TR>");
					}
					buf.append("</TABLE><p>");
					buf.append(missedQuestions); // list of missed questions with correct answers
				}
				else buf.append("Some questions were left blank.");
			}
			// embed ajax code to provide feedback
			buf.append(ajaxScoreJavaScript(user.verifiedEmail));
		}
		catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	String ajaxExamJavaScript() {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSpellCheck(id) {\n"
		+ "  var xmlhttp;\n"
		+ "  var answer = document.getElementById(id).value.trim();\n"
		+ "  if (answer.length==0) {\n"
		+ "    document.getElementById('status').innerHTML='Nothing to check';\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "  var correctedAnswer;\n"
		+ "  var status=document.getElementById('status'+id);\n"
		+ "  var answerField=document.getElementById(id);\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      correctedAnswer = xmlhttp.responseText.trim();\n"
		+ "      if (correctedAnswer=='Spell checker is offline, sorry') {\n"
		+ "      status.innerHTML=correctedAnswer; return false;\n"
		+ "    }\n"
		+ "    answerField.value=correctedAnswer;\n"
		+ "    if (correctedAnswer==answer) status.innerHTML='Spelling is OK';\n"
		+ "    else status.innerHTML='Did you mean this instead?';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','SpellingChecker?UserRequest=SpellCheck&Answer='+answer,true);\n"
		+ "  xmlhttp.send(null);\n"  
		+ "  return false;\n"
		+ "}\n"
		+ "function GetXmlHttpObject() {\n"
		+ "  if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari\n"
		+ "    return new XMLHttpRequest();\n"
		+ "  }\n"
		+ "  if (window.ActiveXObject) { // code for IE6, IE5\n"
		+ "    return new ActiveXObject('Microsoft.XMLHTTP');\n"
		+ "  }\n"
		+ "  return null;\n"
		+ "}\n"
		+ "</SCRIPT>";			
	}

	String ajaxScoreJavaScript(boolean verifiedEmail) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,note) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (url.length==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('feedback' + id).innerHTML="
		+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  url += '&QuestionId=' + id + '&Notes=' + note;\n"
		+ "  xmlhttp.open('GET',url,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function ajaxStars(nStars) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (nStars==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    var msg;\n"
		+ "    switch (nStars) {\n"
		+ "      case '1': msg='1 star - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to tell us why:'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=1><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - How we can serve you better?</a>'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=2><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=Feedback>Click here</a> '"
		+ "                + 'to provide additional feedback.';"
		+ "                break;\n"
		+ "      case '4': msg='4 stars - Thank you';"
		+ "                break;\n"
		+ "      case '5': msg='5 stars - Thank you!';"
		+ "                break;\n"
		+ "      default: msg='You clicked ' + nStars + ' stars.';\n"
		+ "    }\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('vote').innerHTML=msg;\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','Feedback?UserRequest=AjaxRating&NStars='+nStars,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function GetXmlHttpObject() {\n"
		+ "  if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari\n"
		+ "    return new XMLHttpRequest();\n"
		+ "  }\n"
		+ "  if (window.ActiveXObject) { // code for IE6, IE5\n"
		+ "    return new ActiveXObject('Microsoft.XMLHTTP');\n"
		+ "  }\n"
		+ "  return null;\n"
		+ "}\n"
		+ "</SCRIPT>";
	}
}
