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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/PracticeExam")
public class PracticeExam extends HttpServlet {
	// parameters that determine the properties of the exam program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for exam overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printExam()
	//int timeAllowed = 3600;              // seconds; set to zero for no time limit to complete the exam
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
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
				case "AssignExamQuestions":
					if (!user.isInstructor()) throw new Exception();
					Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
					out.println(Home.header("Select ChemVantage Practice Exam Topics") + a.selectExamQuestionsForm(user) + Home.footer);
					break;
				case "ReviewExamScores":
					out.println(Home.header("Review ChemVantage Practice Exam Scores") + reviewExamScores(user) + Home.footer);
					break;
				case "ReviewExam":
					long practiceExamTransactionId = Long.parseLong(request.getParameter("PracticeExamTransactionId"));
					out.println(Home.header("Review ChemVantage Practice Exam") + reviewExam(user,practiceExamTransactionId) + Home.footer);
					break;
				default: out.println(Home.header("ChemVantage Practice Exam") + printExam(user,request) + Home.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();


			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Assignment a = null;
			switch(userRequest) {
				case "UpdateAssignment":
					a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
					a.updateQuestions(request);
					out.println(Home.header("ChemVantage Practice Exam") + printExam(user,request) + Home.footer);
					break;
				case "Submit Revised Exam Score":
					if (submitRevisedExamScore(user,request)) out.println(Home.header("Review ChemVantage Practice Exam Scores") + reviewExamScores(user) + Home.footer);
					else out.println("Sorry, an unexpected error occurred. Please go BACK and try again.");
					break;
				case "Set Allowed Time":
					a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
					try {
						double minutes = Double.parseDouble(request.getParameter("TimeAllowed"));
						a.timeAllowed = minutes<1.0?60:(int)(minutes*60);
					} catch (Exception e) {
						a.timeAllowed = 3600;
					}
					ofy().save().entity(a).now();
					out.println(Home.header("ChemVantage Practice Exam") + printExam(user,request) + Home.footer);					
					break;
				default: out.println(Home.header("ChemVantage Practice Exam Results") + printScore(user,request) + Home.footer);
			}
		} catch (Exception e) {
			//response.getWriter().println(e.toString() + " " + e.getMessage());
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	String designExam(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		//String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		try {
			buf.append("<h2>Practice " + subject.title + " Exam</h2>");
			buf.append("<div id=topicsForm>");

			buf.append("Please select <b>at least 3 topics</b> below to be covered on this practice exam.<p>");
			buf.append("<FORM NAME=TopicForm METHOD=GET>");
			buf.append("<input type=hidden name=sig value=" + user.getTokenSignature() + ">");
			
			buf.append("<div style='display:table'>");
			List<Topic> topics = ofy().load().type(Topic.class).list();
			int i = 0;
			for (Topic t : topics) {
				if ("Hide".equals(t.orderBy)) continue;
				buf.append((i%3==0?"<div style='display:table-row'>":"") + "<div style='display:table-cell'>");
				buf.append("<label><INPUT TYPE=CHECKBOX NAME=TopicId VALUE='" + t.id + "' "
						+ "onClick=\"javascript: var checked=0; "
						+ "for(i=0;i<document.TopicForm.TopicId.length;i++) if(document.TopicForm.TopicId[i].checked) checked++;"
						+ "document.TopicForm.begin.disabled=(checked<3);"
						+ "if(document.TopicForm.begin.disabled) document.TopicForm.begin.value='Select at least 3 topics';"
						+ "else document.TopicForm.begin.value='Begin the exam';\">" 
						+ t.title + "</label><br>\n");
				buf.append(i%3==2?"</div></div>":"</div>");
				i++;
			}
			buf.append("</div><p>");
			
			buf.append("You will have 60 minutes to submit this exam for scoring.<br>");
			buf.append("<INPUT TYPE=SUBMIT NAME=begin DISABLED=true VALUE='Select at least 3 topics'>");
			buf.append("</FORM></div>");
		} catch (Exception e) {
			buf.append("designExam: " + e.toString());
		}
		return buf.toString();
	}

	String printExam(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		try {
			// Get requested topic ids for this exam
			List<Long> topicIds = new ArrayList<Long>();
			long assignmentId = 0;
			Assignment a = null;
			try {  // this branch works if the practice exam is assigned
				//assignmentId=Long.parseLong(request.getParameter("AssignmentId"));
				assignmentId=user.getAssignmentId();
				a = ofy().load().type(Assignment.class).id(assignmentId).safe();
				topicIds = a.topicIds;
			} catch (Exception e) {  // otherwise this is a student-designed exam
				String[] topicStringIds = request.getParameterValues("TopicId");
				if (topicStringIds != null) {
					for (int i=0;i<topicStringIds.length;i++) topicIds.add(Long.parseLong(topicStringIds[i]));
				}
			}

			// Check to see if the timeAllowed has been modified by the instructor:
			int timeAllowed = 3600;  // default value in seconds
			if (a != null && a.timeAllowed!=null) {
				timeAllowed = a.timeAllowed;  // instructor option, e.g. for student disability accommodations
				user = User.getUser(user.getTokenSignature(),timeAllowed/60);
			}
			
			// Check to see if this user has any pending exams:
			Date now = new Date();
			Date oneHourAgo = new Date(now.getTime()-timeAllowed*1000);  // timeAllowed seconds ago 
			
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
						if (t.assignmentId==a.id || t.topicIds.equals(a.topicIds)) {
							pt = t;  // found the correct pending exam for this assignment
							break;
						}
					}  // if the for-loop expires without finding a corresponding transaction, pt remains null and a new exam is created below
				}
			}
			else if (topicIds.size() < 3) return designExam(user,request);  // redirect to get a valid set of 3+ topic keys
			
			
			if (pt == null) {  // this is a valid request for a new exam with at least 3 topicIds; create a new transaction
				pt = new PracticeExamTransaction(topicIds,user.id,now,null,new int[topicIds.size()],new int[topicIds.size()],user.getLisResultSourcedid());
				pt.assignmentId = assignmentId;
				ofy().save().entity(pt).now();	
			}
			
			// past this point we will present a practice exam to the student. 
			
			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_10pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_15pt = new ArrayList<Key<Question>>();
			
			for (long tid : topicIds) {  //First collect the question keys
				questionKeys_02pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",2).keys().list());
				questionKeys_10pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",10).keys().list());
				questionKeys_15pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",tid).filter("pointValue",15).keys().list());
			}

			List<Key<Question>> remove = new ArrayList<Key<Question>>();
			if (pt.questionKeys==null || pt.questionKeys.isEmpty()) {  // create a new set of questions
				if (a != null) {  // eliminate any questionKeys not listed in the assignment
					for (Key<Question> k : questionKeys_02pt) if (!a.questionKeys.contains(k)) remove.add(k);
					questionKeys_02pt.removeAll(remove); remove.clear();
					for (Key<Question> k : questionKeys_10pt) if (!a.questionKeys.contains(k)) remove.add(k);
					questionKeys_10pt.removeAll(remove); remove.clear();
					for (Key<Question> k : questionKeys_15pt) if (!a.questionKeys.contains(k)) remove.add(k);
					questionKeys_15pt.removeAll(remove); remove.clear();
				}
			} else {  // eliminate all but the prior selected questions for this transaction
				for (Key<Question> k : questionKeys_02pt) if (!pt.questionKeys.contains(k)) remove.add(k);
				questionKeys_02pt.removeAll(remove); remove.clear();
				for (Key<Question> k : questionKeys_10pt) if (!pt.questionKeys.contains(k)) remove.add(k);
				questionKeys_10pt.removeAll(remove); remove.clear();
				for (Key<Question> k : questionKeys_15pt) if (!pt.questionKeys.contains(k)) remove.add(k);
				questionKeys_15pt.removeAll(remove); remove.clear();
			}
			
			buf.append("<script>function showWorkBox(qid){}</script>");  // prevents javascript error from Question.print()
			
			buf.append("<h2>General Chemistry Exam</h2>");
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			
			Date downloaded = pt.downloaded;
			
			int secondsRemaining = (int) (timeAllowed - (now.getTime() - downloaded.getTime())/1000);

			buf.append("Topics covered on this exam:<OL>");
			for (long topicId : topicIds) {
				buf.append("<LI>" + ofy().load().type(Topic.class).id(topicId).safe().title + "</LI>");
			}
			buf.append("</OL>");

			if (user.isInstructor()) buf.append("<mark>"
					+ "Instructor: you may <a href=/PracticeExam?UserRequest=AssignExamQuestions&sig=" + user.getTokenSignature() + ">"
					+ "customize this practice exam</a>. You may also <a href=/PracticeExam?UserRequest=ReviewExamScores&sig=" + user.getTokenSignature() 
					+ ">review the scores</a> on this assignment."
					+ "</mark><p>");
			
			buf.append("This exam must be submitted for grading within " + timeAllowed/60 + " minutes of when it is first downloaded.");

			Random rand = new Random();  // create random number generator to select exam questions
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			rand.setSeed(pt.id);  // random number generator seeded with PracticeExamTransaction id value

			//buf.append(ajaxExamJavaScript());  // this code allows users to use the Google SOAP search spell checking function
			
			buf.append("\n<FORM METHOD=POST ACTION=PracticeExam "
					+ "onSubmit=\"return confirm('Submit this exam for grading now. Are you sure?')\">");

			buf.append("<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Practice Exam'><p>");

			// Include a nonce reference as a hedge in case the session is not maintained by the user's browser
			//buf.append(cvsToken!=null?"\n<input type=hidden name=CvsToken value='" + cvsToken + "'>":"");
			buf.append("<input type=hidden name=sig value='" + user.getTokenSignature() + "'>");
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
				pt.questionKeys.add(k);
			}
			buf.append("</OL>");

			if (pt.questionShowWork==null) pt.questionShowWork = new HashMap<Key<Question>,String>();  // initialize this, if necessary
			
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
				if (assignmentId>0) buf.append("<SCRIPT>"
						+ "document.getElementById('showWork" + q.id + "').style.display='';"
						+ "</SCRIPT>");
				pt.questionKeys.add(k);
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
				if (assignmentId>0) buf.append("<SCRIPT>"
						+ "document.getElementById('showWork" + q.id + "').style.display='';"
						+ "</SCRIPT>");
				pt.questionKeys.add(k);
			}
			buf.append("</OL>");

			pt.possibleScores = possibleScores;
			ofy().save().entity(pt).now();

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
			
			Assignment a = null;
			
			if (user.isAnonymous()) buf.append("<h3><font color=red>Anonymous User</font></h3>");
			else a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();  // find the Assignment object for this Practice Exam, if it exists
			
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			buf.append(df.format(now) + "<br>");
			
			long examId = Long.parseLong(request.getParameter("ExamId"));
			PracticeExamTransaction pt = ofy().load().type(PracticeExamTransaction.class).id(examId).safe();
			if (pt.graded != null) return "Sorry, this exam has been graded already.";
			
			int timeAllowed = 3600;
			try {
				timeAllowed = a.timeAllowed;
			} catch (Exception e) {} // catches eException if exam timeAllowed has not been customized
			
			if (now.getTime() - pt.downloaded.getTime() > 1000*(timeAllowed+60)) return "Sorry, the grading period for this exam has expired.";  // 60-second grace period

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
			  
			List<Response> responses = new ArrayList<Response>();
			// begin the main scoring loop:
			for (Key<Question> k : questionKeys) {
				Question q=null;
				String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
				if (studentAnswer != null) for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
				else studentAnswer = new String[] {""};
				if (studentAnswer[0].length() > 0) { // an answer was submitted
					q = examQuestions.get(k);
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
					if (studentAnswer[0].length() > 0) {
						Response r = new Response("PracticeExam",q.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now);
						r.transactionId = pt.id;
						responses.add(r);
					}
					if (score == 0) {
						// include question in list of incorrectly answered questions
						wrongAnswers++;
						missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer[0],false) + "</LI>\n");
					}
				}
				if (q!=null && q.pointValue > 2) pt.questionShowWork.put(k, request.getParameter("ShowWork" + k.getId()));
			}
			missedQuestions.append("</OL>\n");
			pt.scores = studentScores;
			pt.graded = now;
			ofy().save().entity(pt).now();
			ofy().save().entities(responses);
			
			try {
				Score s = Score.getInstance(user.id,a);
				ofy().save().entity(s).now();
				if (a.lti_ags_lineitem_url != null) { // LTI v1.3
					LTIMessage.postUserScore(s);
				} else if (a.lis_outcome_service_url != null) { // LTI v1.1 put report into the Task Queue
					QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",a.id.toString()).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  
				}
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
			buf.append(ajaxScoreJavaScript(user.getTokenSignature()));
		}
		catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}


	String ajaxScoreJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,note,email) {\n"
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
		+ "  url += '&QuestionId=' + id + '&Token=" + signature + "&Notes=' + note + '&Email=' + email;\n"
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
	
	String reviewExamScores(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "<h2>Access Denied</h2>You must be an instructor to view this page.";

			long assignmentId = user.getAssignmentId();

			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
			if (a == null) return "Sorry, we did not find an assignment associated with this practice exam.";

			List<Topic> topics = new ArrayList<Topic>(ofy().load().type(Topic.class).ids(a.topicIds).values());

			buf.append("<h2>Practice Exam Assignment Results</h2>"
					+ "Assignment ID: " + assignmentId + "<br>"
					+ "Created: " + a.created + "<br>"
					+ "Topics covered:<ol>");
			for (Topic t : topics) buf.append("<li>" + t.title + "</li>");
			buf.append("</ol>");

			// Get all of the PracticeExamTransactions associated with this assignment:
			List<PracticeExamTransaction> pets = ofy().load().type(PracticeExamTransaction.class).filter("assignmentId",assignmentId).list();			
			List<PracticeExamTransaction> abandoned = new ArrayList<PracticeExamTransaction>();
			for (PracticeExamTransaction pet : pets) if (pet.graded==null) abandoned.add(pet);
			pets.removeAll(abandoned);
			
			if (pets.size()==0) {
				buf.append("There are no submissions of this practice exam assignment yet.<p>");
				return buf.toString();
			}

			int i=0;
			buf.append("<table>");
			buf.append("<tr><th>Exam</th><th>Downloaded</th><th>Elapsed Time</th>");
			for (int j=1;j<=topics.size();j++) buf.append("<th>Topic " + j + "</th>");
			buf.append("<th>Total Score</th><th>Reviewed</th><th></th></tr>");
			for (PracticeExamTransaction pet : pets) {
				i++;
				buf.append("<tr style='text-align: center'>");
				buf.append("<td>" + i + "</td><td>" + pet.downloaded + "</td><td>" + ((pet.graded.getTime()-pet.downloaded.getTime())/60000) + " min.</td>");

				int score = 0;
				int possibleScore = 0;
				for (int j=0;j<a.topicIds.size();j++) {
					score += pet.scores[j];
					possibleScore += pet.possibleScores[j];
					if (pet.possibleScores[j] == 0) buf.append("<td>0%</td>");
					else buf.append("<td>" + String.valueOf(100*pet.scores[j]/pet.possibleScores[j]) + "%" + "</td>");
				}
				if (pet.graded==null) buf.append("<td> - </td><td> - </td><td></td>");
				else {
					buf.append("<td>" + String.valueOf(100*score/possibleScore) + "%</td><td>" 
							+ (pet.graded==null?" - ":(pet.reviewed==null?"no":pet.reviewed)) + "</td><td>"
							+ "<a href=PracticeExam?UserRequest=ReviewExam&PracticeExamTransactionId=" + pet.id 
							+ "&sig=" + user.getTokenSignature() + ">Review</a></td>");
				}
				buf.append("</tr>");
			}
			buf.append("</table><p>");
			
			buf.append("In addition, " + abandoned.size() + " exams were downloaded but not submitted for grading.<p>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String reviewExam(User user, long practiceExamTransactionId) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "<h2>Access Denied</h2>You must be an instructor to view this page.";

			PracticeExamTransaction pet = ofy().load().type(PracticeExamTransaction.class).id(practiceExamTransactionId).safe();
			if (pet.assignmentId != user.getAssignmentId()) return "<h2>Access Denied</h2>Go back and relaunch this assignment from your LMS.";
			
			// Get the question keys from the PracticeExamTransaction and sort them into 3 lists by point value
			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_10pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_15pt = new ArrayList<Key<Question>>();
			
			for (Key<Question> k : pet.questionKeys) {
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy().load().key(k).safe();
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				switch (q.pointValue) {
					case (2): questionKeys_02pt.add(k); break;
					case (10): questionKeys_10pt.add(k); break;
					case (15): questionKeys_15pt.add(k); break;
				}
			}
			
			// create a HashMap of all the questionIds and student's responses for all items submitted
			List<Response> responses = ofy().load().type(Response.class).filter("transactionId",pet.id).list();
			Map<Long,String> studentAnswers = new HashMap<Long,String>();
			for (Response r : responses) studentAnswers.put(r.questionId,r.studentResponse);
			
			buf.append("<h2>General Chemistry Exam</h2>");
		
			
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			List<Topic> topics = new ArrayList<Topic>(ofy().load().type(Topic.class).ids(a.topicIds).values());
			buf.append("Topics covered:<ol>");
			for (Topic t : topics) buf.append("<li>" + t.title + "</li>");
			buf.append("</ol>");

			buf.append("<form action=/PracticeExam method=post>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + ">"
					+ "<input type=hidden name=PracticeExamTransactionId value=" + String.valueOf(practiceExamTransactionId) + ">");
			
			buf.append("Please review the student responses to the exam questions below. Use the sliders on the right to award "
					+ "partial credit or otherwise edit the scores as appropriate. When you are finished, click the button to "
					+ "<input type=submit name=UserRequest value='Submit Revised Exam Score'> to your LMS grade book.");
			
			int i=0; // counter for the question items
			
			// Two-point questions
			buf.append("<h3>2 point questions:</h3>");
			buf.append("<table>");
			for(Key<Question> k : questionKeys_02pt) {
				i++;
				Question q = examQuestions.get(k);
				q.setParameters((int)(pet.id - q.id));
				buf.append("<tr style='vertical-align:middle'><td><b>" + i + ". </b>" 
						+ q.printAllToStudents(studentAnswers.get(q.id),false) + "</td>");

				// Try to get the question score from the PracticeExamTransaction. If null, recompute it from the student's response
				int score = pet.questionScores.get(k)==null?(q.isCorrect(studentAnswers.get(q.id))?q.pointValue:0):pet.questionScores.get(k);
				
				buf.append("<td style='text-align:center'><span id='score" + q.id + "'>" + score + "</span> pts<br>"
						+ "<input type=range name=Range" + q.id + " value=" + score + " min=0 max=" + q.pointValue 
						+ " onchange=document.getElementById('score" + q.id + "').innerHTML=this.value;></td>");
				buf.append("</tr>");
			}
			buf.append("</table>");

			i=0;
			
			// Ten-point questions
			buf.append("<h3>10 point questions:</h3>");
			buf.append("<table>");
			for(Key<Question> k : questionKeys_10pt) {
				i++;
				Question q = examQuestions.get(k);
				q.setParameters((int)(pet.id - q.id));
				buf.append("<tr style='vertical-align:middle'><td><b>" + i + ". </b>" 
						+ q.printAllToStudents(studentAnswers.get(q.id),true,pet.questionShowWork.get(k)) + "</td>");

				// Try to get the question score from the PracticeExamTransaction. If null, recompute it from the student's response
				int score = pet.questionScores.get(k)==null?(q.isCorrect(studentAnswers.get(q.id))?q.pointValue:0):pet.questionScores.get(k);
				
				buf.append("<td style='text-align:center'><span id='score" + q.id + "'>" + score + "</span> pts<br>"
						+ "<input type=range name=Range" + q.id + " value=" + score + " min=0 max=" + q.pointValue 
						+ " onchange=document.getElementById('score" + q.id + "').innerHTML=this.value;></td>");
				buf.append("</tr>");
			}
			buf.append("</table>");

			i=0;
			
			// Fifteen-point questions
			buf.append("<h3>15 point questions:</h3>");
			buf.append("<table>");
			for(Key<Question> k : questionKeys_15pt) {
				i++;
				Question q = examQuestions.get(k);
				q.setParameters((int)(pet.id - q.id));
				buf.append("<tr style='vertical-align:middle'><td><b>" + i + ". </b>" 
						+ q.printAllToStudents(studentAnswers.get(q.id),true,pet.questionShowWork.get(k)) + "</td>");

				// Try to get the question score from the PracticeExamTransaction. If null, recompute it from the student's response
				int score = pet.questionScores.get(k)==null?(q.isCorrect(studentAnswers.get(q.id))?q.pointValue:0):pet.questionScores.get(k);
				
				buf.append("<td style='text-align:center'><span id='score" + q.id + "'>" + score + "</span> pts<br>"
						+ "<input type=range name=Range" + q.id + " value=" + score + " min=0 max=" + q.pointValue 
						+ " onchange=document.getElementById('score" + q.id + "').innerHTML=this.value;></td>");
				buf.append("</tr>");
			}
			buf.append("</table>");
			
			buf.append("<h3>When You Have Finished Reviewing This Exam</h3>"
					+ "Please click the button to <input type=submit name=UserRequest value='Submit Revised Exam Score'> to your LMS grade book.");

			buf.append("</form>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	boolean submitRevisedExamScore(User user,HttpServletRequest request) throws Exception {
		try {
			// First do some validation to make sure that the user is the instructor for this assignment and the transaction is for this assignment:
			if (!user.isInstructor()) throw new Exception("You must be the instructor for this course.");
			long practiceExamTransactionId = Long.parseLong(request.getParameter("PracticeExamTransactionId"));
			PracticeExamTransaction pet = ofy().load().type(PracticeExamTransaction.class).id(practiceExamTransactionId).safe();
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			if (!pet.assignmentId.equals(a.id)) throw new Exception("Mismatched assignment ID values");
			
			// reset the transaction scores arrays
			pet.scores = new int[pet.topicIds.size()];
			pet.possibleScores = new int[pet.topicIds.size()];
			
			// Iterate through all of the questions for this exam, getting scores from the range inputs on the review form and compiling the scores
			for (Key<Question> k : pet.questionKeys) {
				Question q = examQuestions.get(k);
				if (q==null) try {
					q = ofy().load().key(k).safe();
					examQuestions.put(k,q);
				} catch (Exception e) {}  // might fail if the question is deleted from the database (too bad, sorry)
				int score = Integer.parseInt(request.getParameter("Range" + k.getId()));
				pet.questionScores.put(k, score);
				pet.scores[pet.topicIds.indexOf(q.topicId)] += score;
				pet.possibleScores[pet.topicIds.indexOf(q.topicId)] += q.pointValue; 
			}
			
			// Record the timestamp for the exam review and save the revised transaction entity
			pet.reviewed = new Date();
			ofy().save().entity(pet).now();
			
			// Create/store a new Score entity and submit it to the LMS grade book
			try {
				Score s = Score.getInstance(pet.userId,a);
				ofy().save().entity(s).now();
				if (a.lti_ags_lineitem_url != null) { // LTI v1.3
					LTIMessage.postUserScore(s);
				} else if (a.lis_outcome_service_url != null) { // LTI v1.1 put report into the Task Queue
					QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",a.id.toString()).param("UserId",URLEncoder.encode(pet.userId,"UTF-8")));  
				}
			} catch (Exception e) {}

		} catch (Exception e) {
			throw e;
		}
		return true;
	}
}
