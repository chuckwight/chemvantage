/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2021 ChemVantage LLC
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet("/PlacementExam")
public class PlacementExam extends HttpServlet {
	
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet presents and scores a General Chemistry placement exam for the user.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Assignment a = null;
			if (!user.isAnonymous()) a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			
			switch (userRequest) {
				case "AssignExamQuestions":
					if (!user.isInstructor()) throw new Exception();
					out.println(Subject.header("Select ChemVantage Placement Exam Topics") + selectExamQuestionsForm(user) + Subject.footer);
					break;
				case "ReviewExamScores":
					out.println(Subject.header("Review ChemVantage Placement Exam Scores") + reviewExamScores(user,a) + Subject.footer);
					break;
				case "Download CSV File":
					response.setContentType("text/csv");
					out.println(generateCSVFile(user));
					break;
				case "Download Detailed CSV File":
					response.setContentType("text/csv");
					out.println(generateDetailedCSVFile(user));
					break;
				case "ReviewExam":
					long placementExamTransactionId = Long.parseLong(request.getParameter("PlacementExamTransactionId"));
					String studentUserId = request.getParameter("UserId");
					out.println(Subject.header("Review ChemVantage Placement Exam") + reviewExam(user,placementExamTransactionId,studentUserId) + Subject.footer);
					break;
				case "SubmissionReview":
					User forUser = new User(user.platformId,request.getParameter("ForUserId"));
					out.println(Subject.header("ChemVantage Placement Exam") + submissionReview(user,forUser) + Subject.footer);
					break;
				case "AnalyzeQuestions":
					out.println(Subject.header("Review ChemVantage Placement Exam Scores") + analyzeQuestions(user,a) + Subject.footer);
					break;	
				default: 
					out.println(Subject.header("ChemVantage Placement Exam") + printExam(user,a,request) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			Assignment a = null;
			if (!user.isAnonymous()) a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch(userRequest) {
				case "UpdateAssignment":
					if (user.isInstructor()) {
						a.updateQuestions(request);
						out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
					}
					break;
				case "Submit Revised Exam Score":
					if (submitRevisedExamScore(user,request)) out.println(Subject.header("Review ChemVantage Placement Exam Scores") + reviewExamScores(user,a) + Subject.footer);
					else out.println("Sorry, an unexpected error occurred. Please go BACK and try again.");
					break;
				case "Set Allowed Time":
					if (user.isInstructor()) {
						try {
							double minutes = Double.parseDouble(request.getParameter("TimeAllowed"));
							if (minutes > 300.) minutes = 300.;
							a.timeAllowed = minutes<1.0?60:(int)(minutes*60);
						} catch (Exception e) {
							a.timeAllowed = 3600;
						}
						ofy().save().entity(a).now();
						out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
					}
					break;
				case "Set Allowed Attempts":
					if (user.isInstructor()) {
						try {
							a.attemptsAllowed = Integer.parseInt(request.getParameter("AttemptsAllowed"));
							if (a.attemptsAllowed<1) a.attemptsAllowed = null;
						} catch (Exception e) {
							a.attemptsAllowed = null;
						}
						ofy().save().entity(a).now();
						out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
					}
					break;
				case "PrintExam":
					out.println(Subject.header("ChemVantage Placement Exam") + printExam(user,a,request) + Subject.footer);
					break;
				case "DeleteSubmission":
					if (user.isInstructor()) {
						try {
							Long tid = Long.parseLong(request.getParameter("tid"));
							ofy().delete().key(Key.create(PlacementExamTransaction.class,tid)).now();
						} catch (Exception e) {}
						out.println(Subject.header("ChemVantage Instructor Page") + reviewExamScores(user,a) + Subject.footer);
					}
					break;
				case "Set Password":
					if (user.isInstructor()) {
						a.password = request.getParameter("ExamPassword");
						if (a.password != null) a.password = a.password.trim();
						ofy().save().entity(a).now();
						out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
					}
					break;
				default: out.println(Subject.header("ChemVantage Placement Exam Results") + printScore(user,request) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	static String instructorPage(User user,Assignment a) {
		StringBuffer buf = new StringBuffer();		
		try {
			if (a.conceptIds.isEmpty()) {  // legacy assignment with topicIds
				List<Topic> topics = new ArrayList<Topic>(ofy().load().type(Topic.class).ids(a.topicIds).values());
				for (Topic t : topics) a.conceptIds.add(t.conceptIds.get(0));
				ofy().save().entity(a).now();
			}

			Deployment d = ofy().load().type(Deployment.class).id(a.domain).now();
			boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
				
			buf.append("<h2>General Chemistry Placement Exam - Instructor Page</h2>");
			
			buf.append("Many chemistry departments are now using placement exams as an advising tool for students entering General Chemistry "
					+ "in order to maximize the probability of student success and to lower the D-F-W rate for the course. "
					+ "This placement exam assesses the student's<ul>"
					+ "<li>skills and knowledge of essential concepts in chemistry</li>"
					+ "<li>skills and knowledge of essential concepts in mathematics</li>"
					+ "<li>ability to interpret and solve word problems</li>"
					+ "</ul>");
			
			buf.append("Students have " + (a.timeAllowed==null?"60":a.timeAllowed/60) + " minutes to complete the exam.<br/>");
			
			if (a.attemptsAllowed==null)
				buf.append("We recommend that students be allowed multiple attempts to complete the exam. Most of the question items are "
					+ "parameterized, so it is extremely unlikely that any two placement exams will be the same.<br/><br/>");
			else buf.append("Students may attempt this placement exam only " + a.attemptsAllowed + (a.attemptsAllowed==1?" time.":" times.") + "<br/>"); 
				
			if (supportsMembership) buf.append("You can use the link below to review the scores on each section as well as the student responses to each question.<br/>");
			
			buf.append("<br/>");
			
			if (d.price > 0) {		
				buf.append("There are two ways to pay for placement exams:<ol>"
						+ "<li>You can <a href='/checkout2.jsp?sig=" + user.getTokenSignature() + "' target=_blank >purchase ChemVantage student licenses</a> "
						+ "for this LTI account in quantities of 50 or more for as little as $2.00 USD per license. Every unique student who "
						+ "downloads a placement exam will use one license, which is valid for a period of 10 months.</li>"
						+ "<li>When there are no licenses remaining in your account, each student will be charged $" + d.price + ".00 USD per month for an individual license.</li></ol>"
						+ "You can use the settings in your LMS to restrict the number of placement exam retakes, if desired.<br/><br/>");

				buf.append("<b>Your account has " + d.nLicensesRemaining + " licenses remaining.</b><br/><br/>"
						+ "You have connected to ChemVantage as an instructor or administrator; therefore, you have unlimited free access to this tool (no license required).<br/><br/>");
			}
			
			buf.append("From here, you may<UL>"
					+ "<LI><a href='/PlacementExam?UserRequest=AssignExamQuestions&sig=" + user.getTokenSignature() + "'>Customize this exam</a> "
							+ "to set the time allowed, attempts allowed, an optional password and select the available question items.</LI>"
					+ (supportsMembership?"<LI><a href='/PlacementExam?UserRequest=ReviewExamScores&sig=" + user.getTokenSignature() + "'>Review the exam results</a> and (optionally) assign partial credit for answers</LI>":"")
					+ "</UL>");
			
			if (a.password != null && !a.password.isEmpty()) {
				buf.append("<h4>The password for this exam is: " + a.password + "</h4>");
			}
			
			buf.append("<a style='text-decoration: none' href='/PlacementExam?sig=" + user.getTokenSignature() + "'>"
					+ "<button style='display: block; width: 500px; border: 1 px; background-color: #00FFFF; color: black; padding: 14px 28px; font-size: 18px; text-align: center; cursor: pointer;'>"
					+ "Show This Assignment (recommended)</button></a>");
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + (e.getMessage()==null?e.toString():e.getMessage()));
		}
		return buf.toString();
	}
	
	static String passwordPrompt(User user,String msg) {
		StringBuffer buf = new StringBuffer();
		buf.append(Subject.banner);
		buf.append("<h3>Enter the password for this assignment</h3>");
		if (msg==null) msg="";
		buf.append("<div id='msgSpan' style='color:#EE0000'>" + msg + "</div><br/>");
		buf.append("Your instructor should provide you with the password.</br>"
				+ "<form method=post action=/PlacementExam >"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature()  + "' />"
				+ "<input type=hidden name=UserRequest value=PrintExam />"
				+ "Password: <input type=password size=30 name='ExamPassword' /> "
				+ "<input id=start type=submit value='Begin the exam' disabled />"
				+ "</form><br/><br/>");	

  		buf.append("<script>"
 				+ "function enableSubmission() {"
  				+ "  document.getElementById('msgSpan').innerHTML = '';"
				+ "  document.getElementById('start').disabled=false;"
				+ "}"
				+ "if (document.getElementById('msgSpan').innerHTML === '') enableSubmission();"
				+ "else setTimeout(enableSubmission,5000);"
				+ "</script>");
		return buf.toString();
	}
	
	static String printExam(User user,Assignment a,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug:");
		try {
			// Check to see if a password is required to start the exam
			if (a == null || a.password == null || a.password.isEmpty() || a.password.equals(request.getParameter("ExamPassword")));  // continue
			else {
				String msg = (request.getParameter("ExamPassword")==null?"":"The password was not correct. Please wait...");
				return passwordPrompt(user,msg);
			}			
			debug.append("1a");
			
			if (a==null) {  // start dummy assignment for anonymous user
				a = new Assignment();
				a.id = 0L;
				a.assignmentType = "PlacementExam";
				List<Concept> concepts = ofy().load().type(Concept.class).list();
				for (Concept c : concepts) {
					switch (c.title) {
					case "Essential Math":
					case "Essential Chemistry":
					case "Word Problems":
						a.conceptIds.add(c.id);
					}
				}
			} else if (a.conceptIds.isEmpty()) {  // legacy assignment with topicIds
				List<Topic> topics = new ArrayList<Topic>(ofy().load().type(Topic.class).ids(a.topicIds).values());
				for (Topic t : topics) a.conceptIds.add(t.conceptIds.get(0));
				ofy().save().entity(a).now();
			}

			// Check to see if the timeAllowed has been modified by the instructor:
			int timeAllowed = 3600;  // default value in seconds
			if (a.timeAllowed!=null) {
				timeAllowed = a.timeAllowed;  // instructor option, e.g. for student disability accommodations
				user = User.getUser(user.getTokenSignature(),timeAllowed/60+30);
			}
			debug.append("1b");
			
			// Now we will retrieve or create a PlacementExamTransaction entity:
			PlacementExamTransaction pt = null;
			
			// Check to see if this user has any pending exams:
			Date now = new Date();
			Date startTime = new Date(now.getTime()-timeAllowed*1000);  // about 1 hour ago depending on timeAllowed ago 
			List<PlacementExamTransaction> pets = ofy().load().type(PlacementExamTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).order("downloaded").list();
			int nAttempts = pets.size();
			
			// get the most recent transaction and determine whether this is resuming a recent exam
			pt = nAttempts==0?null:pets.get(nAttempts-1);
			boolean resumingExam = false;
			if (pt != null && pt.graded==null && pt.downloaded.after(startTime)) resumingExam = true;
			else { // this is a new exam
				pt =  new PlacementExamTransaction(user,a);		  
				if (a.attemptsAllowed != null && nAttempts >= a.attemptsAllowed) {
					buf.append(Subject.banner);
					buf.append("<h2>Sorry, you are only allowed " + a.attemptsAllowed + " attempt" + (a.attemptsAllowed==1?"":"s") + " on this assignment.</h2>");
					
					DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
					buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Placement Exam Score (percent)</th></tr>");
					for (PlacementExamTransaction pet : pets) {
						int score = 0;
						int possibleScore = 0;
						for (int i=0;i<a.conceptIds.size();i++) {
							score += pet.scores[i];
							possibleScore += pet.possibleScores[i];
						}
						int pct = (possibleScore>0?score*100/possibleScore:0);

						buf.append("<tr><td>" + pet.id + "</td><td>" + df.format(pet.downloaded) + "</td><td align=center>" + (pet.graded==null?"-":pct + "%") +  "</td></tr>");
					}
					buf.append("</table><br>Missing scores indicate assignments that were downloaded but not submitted for scoring.<br/><br/>");
					return buf.toString();
				}	
				ofy().save().entity(pt).now();	// need to save this to get the pt.id for setting Question parameters
			}
			debug.append("2");
			
			// past this point we will present a placement exam to the student. 
			// first retrieve all the questions using the assignment.questionKeys List:
			Map<Key<Question>,Question> questions = new HashMap<Key<Question>,Question>();
			if (resumingExam && !pt.questionKeys.isEmpty()) {
				debug.append("a" + pt.questionKeys.size());
				questions = ofy().load().keys(pt.questionKeys);
			}
			else {
				debug.append("b" + a.questionKeys.size());
				questions = ofy().load().keys(a.questionKeys);  // this method tolerates keys for questions that have been deleted
			}
			if (questions.isEmpty()) throw new Exception("Error: No questions were found for this assignment.");
			debug.append("3");
			
			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_04pt = new ArrayList<Key<Question>>();
			for (Key<Question> k : new ArrayList<Key<Question>>(questions.keySet())) {
				Question q = questions.get(k);
				if (isInvalid(q)) continue;
				switch (q.pointValue) {
				case 2: questionKeys_02pt.add(k); break;
				case 4: questionKeys_04pt.add(k); break;
				}
			}
			
			// Reduce the size of questionKeys Lists to the number of questions needed
			Random rand = new Random();  // create random number generator to select exam questions
			rand.setSeed(pt.id);  // random number generator seeded with PracticeExamTransaction id value
			while (questionKeys_02pt.size()>30) questionKeys_02pt.remove(rand.nextInt(questionKeys_02pt.size()));
			while (questionKeys_04pt.size()>10) questionKeys_04pt.remove(rand.nextInt(questionKeys_04pt.size()));

			debug.append("4");
			
			// Check to make sure that some questions exist:
			if (questions.size()==0) return "<h2>General Chemistry Placement Exam</h2>"
					+ "Thanks for visiting. We are in the process of developing and validating the question items for this exam.<br/><br/>";
			
			buf.append("<script>function showWorkBox(qid){}</script>");  // prevents javascript error from Question.print()
			
			if (user.isAnonymous()) buf.append(Subject.banner);
			
			buf.append("<h2>General Chemistry Placement Exam</h2>");
			if (user.isAnonymous()) buf.append("Anonymous User<br/>");
			
			if (a.attemptsAllowed!=null) buf.append("You are allowed " + a.attemptsAllowed + (a.attemptsAllowed==1?" attempt":" attempts") + " on this exam. This is attempt #" + (nAttempts + (resumingExam?0:1)) + ".<br/>");
			
			buf.append("This exam must be submitted for grading within " + timeAllowed/60 + " minutes of when it is first downloaded.<br/>");
			if (resumingExam) buf.append("You are resuming a placement exam originally downloaded at " + pt.downloaded + "<br/>");
			
			buf.append("\n<FORM NAME=PlacementExamForm METHOD=POST ACTION=/PlacementExam "
					+ "onSubmit=\"return confirm('Submit this placement exam for grading now. Are you sure?')\">");

			buf.append("<div id='timer0' style='color: #EE0000'></div><div id=ctrl0 style='font-size:50%;color:#EE0000;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Placement Exam'><p>");

			buf.append("<input type=hidden name=sig value='" + user.getTokenSignature() + "'>");
			if (a!=null) buf.append("\n<input type=hidden name=AssignmentId value='" + a.id + "'>");
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			int[] possibleScores = new int[a.conceptIds.size()];
			debug.append("6");
			
			// 2-point questions
			buf.append("<U>2 point questions:</U>");
			buf.append("<OL>\n");
			int nQuestions = 30;
			int i = 0;
			while (i<nQuestions && questionKeys_02pt.size()>0) {
				try {
					Key<Question> k = questionKeys_02pt.remove(resumingExam?0:rand.nextInt(questionKeys_02pt.size()));
					Question q = questions.get(k);
					i++;
					possibleScores[a.conceptIds.indexOf(q.conceptId)] += q.pointValue;
					q.setParameters((int)(pt.id ^ q.id));
					buf.append("\n<li>" + q.print() + "<br></li>\n");
					if (!resumingExam) pt.questionKeys.add(k);
				} catch (Exception e) {}
			}
			buf.append("</OL>");

			// 4-point questions
			buf.append("<U>4 point questions:</U>");
			buf.append("<OL>\n");
			nQuestions = 10;
			i=0;
			while (i<nQuestions && questionKeys_04pt.size()>0) {
				try {
					Key<Question> k = questionKeys_04pt.remove(resumingExam?0:rand.nextInt(questionKeys_04pt.size()));
					Question q = questions.get(k);
					i++;
					possibleScores[a.conceptIds.indexOf(q.conceptId)] += q.pointValue;
					q.setParameters((int)(pt.id ^ q.id));
					buf.append("\n<li>" + q.print() + "<br></li>\n");
					if (!resumingExam) pt.questionKeys.add(k);
				} catch (Exception e) {}
			}
			buf.append("</OL>");

			debug.append("7");
			pt.possibleScores = possibleScores;
			ofy().save().entity(pt).now();

			buf.append("\n<input type=hidden name='ExamId' value=" + pt.id + ">");
			buf.append("\n<input type=hidden name='UserRequest' value='GradeExam'>");
			buf.append("<div id='timer1' style='color: #EE0000'></div><div id=ctrl1 style='font-size:50%;color:#EE0000;'><a href=javascript:toggleTimers()>hide timers</a><p></div>");
			buf.append("\n<input type=submit value='Grade This Placement Exam'>");
			buf.append("\n</form>");
			
			long endMillis = pt.downloaded.getTime() + timeAllowed*1000L;
			// this code for displaying/hiding timers and a 30-seconds-remaining alert box
			buf.append(timerScripts(endMillis)); 
		} catch (Exception e) {
			buf.append("printExam: " + e.toString() + " " + debug.toString());
		}
		return buf.toString();
	}

	static boolean isInvalid(Question q) {
		return q==null || q.conceptId==0 || !(q.pointValue==2 || q.pointValue==4);
	}

	static String timerScripts(long endMillis) {
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
				+ "var endTime = " + endMillis + ";"
				+ "function countdown() {"
				+ "var now = new Date().getTime();"
				+ "seconds=Math.round((endTime-now)/1000);"
				+ "minutes = seconds<0?Math.ceil(seconds/60):Math.floor(seconds/60);"
				+ "oddSeconds = seconds%60;"
				+ "for(i=0;i<2;i++) document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
				+ "if (seconds==30) alert('30 seconds remaining');"
				+ "if (seconds < 0) document.PlacementExamForm.submit();"
				+ "else setTimeout('countdown()',1000);"
				+ "}"
				+ "countdown();"
				+ "</SCRIPT>"; 
	}
	
	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		try {
			buf.append("<h2>Placement Exam Results</h2>");
			if (user.isAnonymous()) buf.append("Anonymous User<br/>");
			
			Assignment a = null;			
			try {
				a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			} catch (Exception e) {  // anonymous user
				a = new Assignment();
				a.id = 0L;
				a.assignmentType = "PlacementExam";
				List<Concept> concepts = ofy().load().type(Concept.class).list();
				for (Concept c : concepts) {
					switch (c.title) {
					case "Essential Math":
					case "Essential Chemistry":
					case "Word Problems":
						a.conceptIds.add(c.id);
					}
				}
			}
			
			Date now = new Date();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			buf.append(df.format(now) + "<br/><br/>");
			
			long examId = Long.parseLong(request.getParameter("ExamId"));
			PlacementExamTransaction pt = ofy().load().type(PlacementExamTransaction.class).id(examId).safe();
			if (pt.graded != null) return "Sorry, this exam has been graded already.";
			
			int timeAllowed = 3600;
			try {
				timeAllowed = a.timeAllowed;
			} catch (Exception e) {} // catches eException if exam timeAllowed has not been customized
			
			if (now.getTime() - pt.downloaded.getTime() > 1000*(timeAllowed+300)) return "Sorry, the grading period for this exam has expired.";  // 5-minute grace period

			// if everything is still OK, score the exam:
			List<String> conceptTitles = new ArrayList<String>();
			for (int i=0;i<a.conceptIds.size();i++) conceptTitles.add(ofy().load().type(Concept.class).id(a.conceptIds.get(i)).safe().title);
			
			// create a buffer to hold the correct solutions to missed questions:
			StringBuffer missedQuestions = new StringBuffer();
			missedQuestions.append("The following questions were answered incorrectly. There may be additional questions (not shown) that were left unanswered.");			
			missedQuestions.append("<OL>");

			int[] studentScores = new int[a.conceptIds.size()];
			int wrongAnswers = 0;

			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			// Load all of the relevant questions
			Map<Key<Question>,Question> questions = ofy().load().keys(questionKeys);
			
			List<Response> responses = new ArrayList<Response>();
			
			// begin the main scoring loop:
			for (Key<Question> k : questionKeys) {
				Question q=null;
				String studentAnswer = orderResponses(request.getParameterValues(Long.toString(k.getId())));
				if (!studentAnswer.isEmpty()) { // an answer was submitted
					q = questions.get(k);				
					try {
						if ("NUMERIC".equals(q.type)) {  // this section converts ionic charge 2+ to +2 and 3- to -3
							int length = studentAnswer.length();  // length of the trimmed String
							int charge = Integer.parseInt(studentAnswer.substring(0,length-1));  // magnitude of the charge
							char last = studentAnswer.charAt(length-1); // sign of the charge
							if (last=='+' || last=='-') studentAnswer = last + String.valueOf(charge);  // move the sign to the front of the String
						}
					} catch (Exception e) {}

					q.setParameters((int)(pt.id ^ q.id));
					
					int score = studentAnswer.length()==0?0:q.isCorrect(studentAnswer)?q.pointValue:0;
					if (score==0 && q.agreesToRequiredPrecision(studentAnswer)) score = q.pointValue - 1;  // partial credit for wrong sig figs
					if (score > 0) studentScores[a.conceptIds.indexOf(q.conceptId)] += score;
					if (studentAnswer.length() > 0) {
						Response r = new Response("PlacementExam",a.id,q.id,studentAnswer,q.getCorrectAnswer(),score,q.pointValue,Subject.hashId(user.getId()),now);
						r.transactionId = pt.id;
						responses.add(r);
					}
					if (score < q.pointValue) {
						// include question in list of incorrectly answered questions
						wrongAnswers++;
						missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer,true) + "</LI>\n");
					}
				}
				if (q!=null && q.pointValue > 2) pt.questionShowWork.put(k, request.getParameter("ShowWork" + k.getId()));
			}
			missedQuestions.append("</OL>\n");
			pt.scores = studentScores;
			pt.graded = now;
			ofy().save().entity(pt).now();
			ofy().save().entities(responses);
			
			if (!user.isAnonymous()) {
				try {
					Score s = Score.getInstance(user.getId(),a);
					ofy().save().entity(s).now();
					if (a.lti_ags_lineitem_url != null) { // LTI v1.3
						LTIMessage.postUserScore(s,user.getId());
					}
				} catch (Exception e) {}
			}
			
			int score = 0;
			int possibleScore = 0;
			for (int i=0;i<a.conceptIds.size();i++) {
				score += studentScores[i];
				possibleScore += pt.possibleScores[i];
			}
			buf.append("<b>Your score on this placement exam is " + score + " out of a possible " + possibleScore + " points.</b><p>");
			if (score > 0 && score == possibleScore) buf.append ("<h2>Congratulations on a perfect score!</h2>");
			else {
				buf.append("<TABLE><TR><TD><b>Topic</b></TD><TD><b>Score</b></TD>"
						+ "<TD><b>Possible</b></TD><TD><b>Percent</b></TD><TD></TD></TR>");
				int pct = 0;
				for (int i=0;i<a.conceptIds.size();i++) {
					if (pt.possibleScores[i]>0) pct = (int)Math.round(pt.scores[i]*100./pt.possibleScores[i]);
					else pct = 0;
					String color = (pct>84?"#00FF00":(pct<50?"#FF0000":"#FFFF00"));
					buf.append("<TR>"
							+ "<TD>" + conceptTitles.get(i) + "</TD>"
							+ "<TD ALIGN=RIGHT>" + pt.scores[i] + "</TD>"
							+ "<TD ALIGN=RIGHT>" + pt.possibleScores[i] + "</TD>"
							+ "<TD ALIGN=RIGHT>" + pct + "%</TD>"
							+ "<TD><div style='background-color:" + color + ";width:" + pct 
							+ "px;'/>&nbsp;</TD></TR>");
				}
				buf.append("</TABLE><p>");
				if (wrongAnswers > 0) buf.append(missedQuestions); // list of missed questions with correct answers
				else buf.append("Some questions were left blank.");
			}
			// embed ajax code to provide feedback
			buf.append(ajaxScoreJavaScript(user.getTokenSignature()));

			if (user.isAnonymous()) return buf.toString();
			debug.append("1");
			List<PlacementExamTransaction> pets = new ArrayList<PlacementExamTransaction>();
			if (user.isAnonymous()) pets = ofy().load().type(PlacementExamTransaction.class).filter("userId",user.getHashedId()).order("downloaded").list();
			else pets = ofy().load().type(PlacementExamTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).order("downloaded").list();
			
			if (pets==null || pets.isEmpty()) {
				buf.append("Sorry, we did not find any records for you in the database for this assignment.<p>");
			} else {
				debug.append("2");
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					debug.append("a");
					s = ofy().load().key(Key.create(Key.create(User.class,user.getId()),Score.class,a.id)).safe();
					if (s.numberOfAttempts != pets.size()) throw new Exception("Score is not up to date.");
				} catch (Exception e) { // create a fresh Score entity from scratch
					debug.append("b");
					s = Score.getInstance(user.getId(), a);
					ofy().save().entity(s);
				}
				debug.append("3");
				
				buf.append("<h3>Your Scores for This Placement Exam Assignment</h3>");

				buf.append("Your best score on this assignment is " + Math.round(s.getPctScore()) + "%.<br>");

				if (!user.isAnonymous()) {  // try to validate the score with the LMS grade book entry
					String lmsScore = null;
					try {
						double lmsPctScore = 0;
						boolean gotScoreOK = false;

						if (a.lti_ags_lineitem_url != null) {  // LTI version 1.3
							lmsScore = LTIMessage.readUserScore(a,user.getId());
							try {
								lmsPctScore = Double.parseDouble(lmsScore);
								gotScoreOK = true;
							} catch (Exception e) {
							}
						}

						if (gotScoreOK && Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
							buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
						} else if (gotScoreOK) { // there is a significant difference between LMS and ChemVantage scores. Please explain:
							buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
									+ "enforcement of assignment deadlines, grading policies, a delay in posting the score and/or instructor discretion.<br>"
									+ "If you think this may be due to a stale score, you may submit this assignment for grading,<br>"
									+ "even for a score of zero, and ChemVantage will try to refresh your best score to the LMS.<p>");
						} else throw new Exception();
					} catch (Exception e) {
						buf.append("ChemVantage was unable to retrieve your score for this assignment from the LMS.<br>"
								+ "Sometimes it takes several seconds for the score to be posted in the LMS grade book.<br>");
						if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that you may not have submitted a score for this assignment yet. ");
						if (user.isInstructor()) buf.append("Some LMS providers do not store scores for instructors.");
						buf.append("<p>");
					}
				}
			}

			buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Placement Exam Score (percent)</th></tr>");
			for (PlacementExamTransaction pet : pets) {
				score = 0;
				possibleScore = 0;
				for (int i=0;i<pet.scores.length;i++) {
					score += pet.scores[i];
					possibleScore += pet.possibleScores[i];
				}
				int pct = (possibleScore>0?score*100/possibleScore:0);

				buf.append("<tr><td>" + pet.id + "</td><td>" + df.format(pet.downloaded) + "</td><td align=center>" + (pet.graded==null?"-":pct + "%") +  "</td></tr>");
			}
			buf.append("</table><br>Missing scores indicate assignments that were downloaded but not submitted for scoring.<br/><br/>");

			if (a.attemptsAllowed == null) buf.append("You may repeat this assignment to improve your score.");
			else if (a.attemptsAllowed>pets.size()) buf.append("You may attempt this assignment as many as " + a.attemptsAllowed + " times to obtain a better score.");
			else buf.append("The number of allowed attempts for this assignment (" + a.attemptsAllowed + ") has been reached.");
		}
		catch (Exception e) {
			buf.append((e.getMessage()==null?e.toString():e.getMessage()) + "<br/>" + debug.toString());
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
		+ "      '<FONT COLOR=#EE0000><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
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
	
	String submissionReview(User user,User forUser) {
		StringBuffer buf = new StringBuffer();

		if (!user.getId().equals(forUser.getId()) && !user.isInstructor()) return "Access denied.";

		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();

		List<PlacementExamTransaction> pets = ofy().load().type(PlacementExamTransaction.class).filter("userId",forUser.getHashedId()).filter("assignmentId",a.id).order("downloaded").list();
		if (pets.size()==0) {
			buf.append("Sorry, we did not find any records for " + (user.getId().equals(forUser.getId())?"you":"this user") + " in the database for this assignment.<p>");
		} else {				
			Score s = null;
			try { // retrieve the score and ensure that it is up to date
				s = ofy().load().key(Key.create(Key.create(User.class,user.getId()),Score.class,a.id)).safe();
				if (s.numberOfAttempts != pets.size()) throw new Exception();
			} catch (Exception e) { // create a fresh Score entity from scratch
				s = Score.getInstance(user.getId(), a);
				ofy().save().entity(s);
			}

			buf.append("<h3>Your Scores for This Placement Exam Assignment</h3>");

			buf.append("The best score for this user on this assignment is " + Math.round(s.getPctScore()) + "%.<br>");

			buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Placement Exam Score (percent)</th></tr>");

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			int score = 0;
			int possibleScore = 0;
			PlacementExamTransaction bestPt = null;
			int bestPct = 0;
			for (PlacementExamTransaction pet : pets) {
				score = 0;
				possibleScore = 0;
				for (int i=0;i<a.conceptIds.size();i++) {
					score += pet.scores[i];
					possibleScore += pet.possibleScores[i];
				}
				int pct = (possibleScore>0?score*100/possibleScore:0);
				if (pct >= bestPct) bestPt = pet;

				buf.append("<tr><td>" + pet.id + "</td><td>" + df.format(pet.downloaded) + "</td><td align=center>" + (pet.graded==null?"-":pct + "%") +  "</td></tr>");
			}
			buf.append("</table><br>Missing scores indicate assignments that were downloaded but not submitted for scoring.<p>");
			
			List<String> conceptTitles = new ArrayList<String>();
			for (int i=0;i<a.conceptIds.size();i++) conceptTitles.add(ofy().load().type(Concept.class).id(a.conceptIds.get(i)).safe().title);
			
			buf.append("<h3>Detailed Scores for the Best Placement Exam</h3>");
			buf.append("<TABLE><TR><TD><b>Topic</b></TD><TD><b>Score</b></TD>"
					+ "<TD><b>Possible</b></TD><TD><b>Percent</b></TD><TD></TD></TR>");
			for (int i=0;i<a.conceptIds.size();i++) {
				int pct = (bestPt.possibleScores[i]>0?bestPt.scores[i]*100/bestPt.possibleScores[i]:0);
				String color = (pct>84?"#00FF00":(pct<50?"#FF0000":"#FFFF00"));
				buf.append("<TR>"
						+ "<TD>" + conceptTitles.get(i) + "</TD>"
						+ "<TD ALIGN=RIGHT>" + bestPt.scores[i] + "</TD>"
						+ "<TD ALIGN=RIGHT>" + bestPt.possibleScores[i] + "</TD>"
						+ "<TD ALIGN=RIGHT>" + pct + "%</TD>"
						+ "<TD><div style='background-color:" + color + ";width:" + pct 
						+ "px;'/>&nbsp;</TD></TR>");
			}
			buf.append("</TABLE><p>");
	
		}
		return buf.toString();
	}

	String reviewExamScores(User user,Assignment a) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "<h2>Access Denied</h2>You must be an instructor to view this page.";

			if (a == null) return "Sorry, we did not find an assignment associated with this placement exam.";
			
			if (a.lti_nrps_context_memberships_url == null || a.lti_nrps_context_memberships_url.isEmpty()) {
				return "Sorry, your LMS does not support the Memberships service, so exams cannot be reviewed.";
			}

			List<Concept> concepts = new ArrayList<Concept>(ofy().load().type(Concept.class).ids(a.conceptIds).values());

			buf.append("<h2>Placement Exam Assignment Results</h2>"
					+ "Assignment ID: " + a.id + "<br>"
					+ "Created: " + a.created + "<br>"
					+ "Topics covered:<ol>");
			for (Concept c : concepts) buf.append("<li>" + c.title + "</li>");
			buf.append("</ol>");

			// Get all of the PlacementExamTransactions associated with this assignment:
			List<PlacementExamTransaction> pets = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",a.id).list();			
			if (pets.size()==0) {
				buf.append("There are no transactions for this placement exam yet.<p>");
				return buf.toString();
			}
			
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			
			if (membership.size() == 0) {
				buf.append("The LMS returned 0 members of this group.");
				return buf.toString();
			}
			  
			int i = 0;
			buf.append("<table><tr><th>User</th><th>Attempt</th><th>Downloaded</th><th>Elapsed Time</th>");
			for (int j=1;j<=concepts.size();j++) buf.append("<th>Topic " + j + "</th>");
			buf.append("<th>Total Score</th><th>Reviewed</th><th></th><th></th></tr>");
			
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				i++; // increment the user number
				String name = entry.getValue()[1];  // user's given and family name
				if (name==null) name = "";  // LMS does not provide names
				// make a short list of each user's PlacementExamTransactions
				List<PlacementExamTransaction> userpets = new ArrayList<PlacementExamTransaction>();
				String hashedUserId = Subject.hashId(user.platformId + "/" + entry.getKey());
				for (PlacementExamTransaction pet : pets) if (hashedUserId.equals(pet.userId)) userpets.add(pet);
				pets.removeAll(userpets);
				// put the user's transactions in order of decreasing download time:
				Collections.sort(userpets,new SortPlacementExams());
				if (userpets.isEmpty()) {  // place a blank line in the table with the user's name
					buf.append("<tr style='text-align: center;background-color: " + (i%2==0?"yellow":"cyan") + "'>"
							+ "<td style='text-align: left'>" + i + ".&nbsp;" + name + "</td>" + "<td colspan=" + (7+a.conceptIds.size()) + ">(exam was not attempted)</td>");
					buf.append("</tr>");					
				} else {
					for (int k=userpets.size();k>0;k--) {  // enter the user's transactions into the table
						PlacementExamTransaction p = userpets.get(k-1);
						buf.append("<tr style='text-align: center;background-color: " + (i%2==0?"yellow":"cyan") + "'>");
						buf.append("<td style='text-align: left'>" + i + ".&nbsp;" + name + "</td><td>" + k + "</td><td>" + p.downloaded + "</td>");

						if (p.graded==null) buf.append("<td colspan=" + (4+a.conceptIds.size()) + ">(exam was not submitted for scoring)</td>");
						else {
							buf.append("<td>" + (p.graded==null?"-":(p.graded.getTime()-p.downloaded.getTime())/60000 + " min.") + "</td>");

							int score = 0;
							int possibleScore = 0;
							for (int j=0;j<a.conceptIds.size();j++) {
								score += p.scores[j];
								possibleScore += p.possibleScores[j];
								if (p.possibleScores[j] == 0) buf.append("<td>0%</td>");
								else buf.append("<td>" + String.valueOf(100*p.scores[j]/p.possibleScores[j]) + "%" + "</td>");
							}

							buf.append("<td>" + String.valueOf(100*score/possibleScore) + "%</td><td>" 
									+ (p.graded==null?" - ":(p.reviewed==null?"no":p.reviewed)) + "</td><td>"
									+ "<a href=PlacementExam?UserRequest=ReviewExam&PlacementExamTransactionId=" + p.id 
									+ "&sig=" + user.getTokenSignature() + "&UserId=" + user.platformId + "/" + entry.getKey() + ">Review</a></td>");
						}
						buf.append("<td><form method=post action=/PlacementExam onsubmit=\"return confirm('Permanently delete this submission? This action cannot be undone.');\">"
								+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
								+ "<input type=hidden name=UserRequest value=DeleteSubmission />"
								+ "<input type=hidden name=tid value='" + p.id + "' />"
								+ "<input type=submit value=Delete />"
								+ "</form></td>");
						buf.append("</tr>");					
					}
				}
			}
			buf.append("</table><p>");
			buf.append("<p>");
			buf.append("<a href=/PlacementExam?sig=" + user.getTokenSignature() + "&UserRequest=Download+CSV+File>Download CSV File</a><p>");
			buf.append("<a href=/PlacementExam?sig=" + user.getTokenSignature() + "&UserRequest=Download+Detailed+CSV+File>Download Detailed Scores CSV File</a><p>");
			buf.append("<a href=/PlacementExam?sig=" + user.getTokenSignature() + "&UserRequest=AnalyzeQuestions>Analysis of Question Items</a><p>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String generateCSVFile(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "\"Access Denied. You must be an instructor to view this page.\"\n";

			long assignmentId = user.getAssignmentId();

			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
			if (a == null) return "\"Sorry, we did not find an assignment associated with this placement exam.\"\n";
			
			if (a.lti_nrps_context_memberships_url == null || a.lti_nrps_context_memberships_url.isEmpty()) {
				return "\"Sorry, your LMS does not support the Memberships service, so exams cannot be reviewed.\"\n";
			}

			List<Concept> concepts = new ArrayList<Concept>(ofy().load().type(Concept.class).ids(a.conceptIds).values());

			buf.append("\"Placement Exam Assignment Results\"\n"
					+ "\"Assignment ID: " + assignmentId + "\"\n"
					+ "\"Created: " + a.created + "\"\n"
					+ "\"Topics covered:\"\n");
			for (Concept c : concepts) buf.append("\"" + c.title + "\"\n");
			
			// Get all of the PlacementExamTransactions associated with this assignment:
			List<PlacementExamTransaction> pets = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",assignmentId).list();			
			if (pets.size()==0) {
				buf.append("\"There are no transactions for this placement exam yet.\"\n");
				return buf.toString();
			}
			
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			
			if (membership.size() == 0) {
				buf.append("\"The LMS returned 0 members of this group.\"\n");
				return buf.toString();
			}
			  
			int i = 0;
			buf.append("\"User\",\"Name\",\"Attempt\",\"Downloaded\",\"Elapsed Time\",");
			for (Concept c : concepts) buf.append("\"" + c.title + "\",");
			buf.append("\"Total Score\",\"Reviewed\"\n");
			
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				i++; // increment the user number
				String name = entry.getValue()[1];  // user's given and family name
				if (name==null) name = "";  // LMS does not provide names
				// make a short list of each user's PlacementExamTransactions
				List<PlacementExamTransaction> userpets = new ArrayList<PlacementExamTransaction>();
				String hashedUserId = Subject.hashId(user.platformId + "/" + entry.getKey());
				for (PlacementExamTransaction pet : pets) if (hashedUserId.equals(pet.userId)) userpets.add(pet);
				pets.removeAll(userpets);
				// put the user's transactions in order of decreasing download time:
				Collections.sort(userpets,new SortPlacementExams());
				if (userpets.isEmpty()) {  // place a blank line in the table with the user's name
					buf.append(i + ".," + "\"" + name + "\"\n");
				} else {
					for (int k=userpets.size();k>0;k--) {  // enter the user's transactions into the table
						PlacementExamTransaction p = userpets.get(k-1);
						buf.append(i + ".," + "\"" + name + "\"," + k + ",\"" + p.downloaded + "\",");
						
						if (p.graded==null) buf.append("\n");
						else {
							buf.append("\"" + (p.graded.getTime()-p.downloaded.getTime())/60000 + " min.\",");

							int score = 0;
							int possibleScore = 0;
							for (int j=0;j<a.conceptIds.size();j++) {
								score += p.scores[j];
								possibleScore += p.possibleScores[j];
								if (p.possibleScores[j] == 0) buf.append("\"0%\",");
								else buf.append("\"" + String.valueOf(100*p.scores[j]/p.possibleScores[j]) + "%\",");
							}

							buf.append("\"" + String.valueOf(100*score/possibleScore) + "%\"," 
									+ "\"" + (p.reviewed==null?"no":p.reviewed) + "\"\n");
						}			
					}
				}
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String generateDetailedCSVFile(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "\"Access Denied. You must be an instructor to view this page.\"\n";

			long assignmentId = user.getAssignmentId();

			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
			if (a == null) return "\"Sorry, we did not find an assignment associated with this placement exam.\"\n";
			
			if (a.lti_nrps_context_memberships_url == null || a.lti_nrps_context_memberships_url.isEmpty()) {
				return "\"Sorry, your LMS does not support the Memberships service, so exams cannot be reviewed.\"\n";
			}

			List<Concept> concepts = new ArrayList<Concept>(ofy().load().type(Concept.class).ids(a.conceptIds).values());

			buf.append("\"Placement Exam Detailed Scores Report\"\n"
					+ "\"Assignment ID: " + assignmentId + "\"\n"
					+ "\"Created: " + a.created + "\"\n"
					+ "\"Topics covered:\"\n");
			for (Concept c : concepts) buf.append("\"" + c.title + "\"\n");
			
			// Get all of the PlacementExamTransactions associated with this assignment:
			List<PlacementExamTransaction> pets = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",assignmentId).list();			
			if (pets.size()==0) {
				buf.append("\"There are no transactions for this placement exam yet.\"\n");
				return buf.toString();
			}
			
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			
			if (membership.size() == 0) {
				buf.append("\"The LMS returned 0 members of this group.\"\n");
				return buf.toString();
			}
			 
			// Indexes: i = 1 to nUsers  j = 1 to nQuestions
			int i = 0;
			buf.append("\"User\",\"Name\",\"Attempt\",\"Downloaded\",\"Elapsed Time\",");
			for (int j=1; j<=a.questionKeys.size();j++) buf.append("\"Q" + j + "\",");
			//for (Concept c : concepts) buf.append("\"" + c.title + "\",");
			buf.append("\"Total Score\",\"Reviewed\"\n");
			
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				i++; // increment the user number
				String name = entry.getValue()[1];  // user's given and family name
				if (name==null) name = "";  // LMS does not provide names
				// make a short list of each user's PlacementExamTransactions
				List<PlacementExamTransaction> userpets = new ArrayList<PlacementExamTransaction>();
				String hashedUserId = Subject.hashId(user.platformId + "/" + entry.getKey());
				for (PlacementExamTransaction pet : pets) if (hashedUserId.equals(pet.userId)) userpets.add(pet);
				pets.removeAll(userpets);
				// put the user's transactions in order of decreasing download time:
				Collections.sort(userpets,new SortPlacementExams());
				if (userpets.isEmpty()) {  // place a blank line in the table with the user's name
					buf.append(i + ".," + "\"" + name + "\"\n");
				} else {
					for (int k=userpets.size();k>0;k--) {  // enter the user's transactions into the table
						PlacementExamTransaction p = userpets.get(k-1);
						buf.append(i + ".," + "\"" + name + "\"," + k + ",\"" + p.downloaded + "\",");
						
						if (p.graded==null) buf.append("\n");
						else {
							buf.append("\"" + (p.graded.getTime()-p.downloaded.getTime())/60000 + " min.\",");

							// print a row of scores for each question; leave blank if no response was recorded
							List<Response> responses = ofy().load().type(Response.class).filter("transactionId",p.id).list();
							Map<Key<Question>,Response> responseMap = new HashMap<Key<Question>,Response>();
							for (Response r : responses) responseMap.put(Key.create(Question.class,r.questionId), r);
							for (Key<Question> key : a.questionKeys) {
								Response r = responseMap.get(key);
								buf.append(r==null?",":r.score + ",");
							}
							
							int score = 0;
							int possibleScore = 0;
							for (int j=0;j<a.conceptIds.size();j++) {
								score += p.scores[j];
								possibleScore += p.possibleScores[j];
							}

							buf.append("\"" + String.valueOf(100*score/possibleScore) + "%\"," 
									+ "\"" + (p.reviewed==null?"no":p.reviewed) + "\"\n");
						}			
					}
				}
			}
			buf.append("\n\"Questions:\"\n");
			for (int j=1; j<= a.questionKeys.size(); j++) {
				buf.append("\"Q" + j + "\",\"https://www.chemvantage.org/item?q=" + a.questionKeys.get(j-1).getId() + "\",\n");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String reviewExam(User user, long placementExamTransactionId, String studentUserId) {
		StringBuffer buf = new StringBuffer();
		try {
			if (!user.isInstructor()) return "<h2>Access Denied</h2>You must be an instructor to view this page.";

			PlacementExamTransaction pet = ofy().load().type(PlacementExamTransaction.class).id(placementExamTransactionId).safe();
			if (pet.assignmentId != user.getAssignmentId()) return "<h2>Access Denied</h2>Go back and relaunch this assignment from your LMS.";
			
			// Get the question keys from the PlacementExamTransaction and sort them into 2 lists by point value
			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_04pt = new ArrayList<Key<Question>>();
			
			Map<Key<Question>,Question> questions = ofy().load().keys(pet.questionKeys);
			
			for (Key<Question> k : new ArrayList<Key<Question>>(questions.keySet())) {
				Question q = questions.get(k);
				switch (q.pointValue) {
				case 2: questionKeys_02pt.add(k); break;
				case 4: questionKeys_04pt.add(k); break;
				default: continue;
				}
			}	
			
			// create a HashMap of all the questionIds and student's responses for all items submitted
			List<Response> responses = ofy().load().type(Response.class).filter("transactionId",pet.id).list();
			Map<Long,String> studentAnswers = new HashMap<Long,String>();
			for (Response r : responses) studentAnswers.put(r.questionId,r.studentResponse);
			
			buf.append("<h2>General Chemistry Placement Exam</h2>");
		
			
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			List<Concept> concepts = new ArrayList<Concept>(ofy().load().type(Concept.class).ids(a.conceptIds).values());
			buf.append("Topics covered:<ol>");
			for (Concept c : concepts) buf.append("<li>" + c.title + "</li>");
			buf.append("</ol>");

			buf.append("<form action=/PlacementExam method=post>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=hidden name=StudentUserId value=" + studentUserId + " />"
					+ "<input type=hidden name=PlacementExamTransactionId value=" + String.valueOf(placementExamTransactionId) + " />");
			
			buf.append("Please review the student responses to the exam questions below. Use the sliders on the right to award "
					+ "partial credit or otherwise edit the scores as appropriate. When you are finished, click the button to "
					+ "<input type=submit name=UserRequest value='Submit Revised Exam Score'> to your LMS grade book.");
			
			int i=0; // counter for the question items
			
			// Two-point questions
			buf.append("<h3>2 point questions:</h3>");
			buf.append("<table>");
			for(Key<Question> k : questionKeys_02pt) {
				i++;
				Question q = questions.get(k);
				q.setParameters((int)(pet.id ^ q.id));
				buf.append("<tr style='vertical-align:middle'><td><b>" + i + ". </b>" 
						+ q.printAllToStudents(studentAnswers.get(q.id),true) + "</td>");

				// Try to get the question score from the PlacementExamTransaction. If null, recompute it from the student's response
				int score = 0;
				if (pet.questionScores.get(k)!=null) score = pet.questionScores.get(k);
				else if (studentAnswers.get(q.id)!=null && !studentAnswers.get(q.id).isEmpty()) {  // an answer was submitted
					score = q.isCorrect(studentAnswers.get(q.id))?q.pointValue:0;
					if (score==0 && q.agreesToRequiredPrecision(studentAnswers.get(q.id))) score = q.pointValue - 1;  // partial credit for wrong sig figs
				}
				
				buf.append("<td style='text-align:center'><span id='score" + q.id + "'>" + score + "</span> pts<br>"
						+ "<input type=range name=Range" + q.id + " value=" + score + " min=0 max=" + q.pointValue 
						+ " onchange=document.getElementById('score" + q.id + "').innerHTML=this.value;></td>");
				buf.append("</tr>");
			}
			buf.append("</table>");

			i=0;
			
			// Four-point questions
			buf.append("<h3>4 point questions:</h3>");
			buf.append("<table>");
			for(Key<Question> k : questionKeys_04pt) {
				i++;
				Question q = questions.get(k);
				q.setParameters((int)(pet.id ^ q.id));
				buf.append("<tr style='vertical-align:middle'><td><b>" + i + ". </b>" 
						+ q.printAllToStudents(studentAnswers.get(q.id),true,pet.questionShowWork.get(k)) + "</td>");

				// Try to get the question score from the PlacementExamTransaction. If null, recompute it from the student's response
				int score = 0;
				if (pet.questionScores.get(k)!=null) score = pet.questionScores.get(k);
				else if (studentAnswers.get(q.id)!=null && !studentAnswers.get(q.id).isEmpty()) {  // an answer was submitted
					score = q.isCorrect(studentAnswers.get(q.id))?q.pointValue:0;
					if (score==0 && q.agreesToRequiredPrecision(studentAnswers.get(q.id))) score = q.pointValue - 1;  // partial credit for wrong sig figs
				}
				
				buf.append("<td style='text-align:center'><span id='score" + q.id + "'>" + score + "</span> pts<br>"
						+ "<input type=range name=Range" + q.id + " value=" + score + " min=0 max=" + q.pointValue 
						+ " onchange=document.getElementById('score" + q.id + "').innerHTML=this.value;></td>");
				buf.append("</tr>");
			}
			buf.append("</table>");

			i=0;
				
			buf.append("<h3>When You Have Finished Reviewing This Exam</h3>"
					+ "Please click the button to <input type=submit name=UserRequest value='Submit Revised Exam Score'> to your LMS grade book.");

			buf.append("</form>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	boolean submitRevisedExamScore(User instructor,HttpServletRequest request) throws Exception {
		try {
			// First do some validation to make sure that the user is the instructor for this assignment and the transaction is for this assignment:
			if (!instructor.isInstructor()) throw new Exception("You must be the instructor for this course.");
			long placementExamTransactionId = Long.parseLong(request.getParameter("PlacementExamTransactionId"));
			PlacementExamTransaction pet = ofy().load().type(PlacementExamTransaction.class).id(placementExamTransactionId).safe();
			Assignment a = ofy().load().type(Assignment.class).id(instructor.getAssignmentId()).safe();
			if (!pet.assignmentId.equals(a.id)) throw new Exception("Mismatched assignment ID values");
			
			// reset the transaction scores arrays
			pet.scores = new int[a.conceptIds.size()];
			pet.possibleScores = new int[a.conceptIds.size()];
			
			// Iterate through all of the questions for this exam, getting scores from the range inputs on the review form and compiling the scores
			Map<Key<Question>,Question> questions = ofy().load().keys(pet.questionKeys);
			for (Key<Question> k : pet.questionKeys) {
				Question q = questions.get(k);
				if (q==null) try {
					q = ofy().load().key(k).safe();
					questions.put(k,q);
				} catch (Exception e) {}  // might fail if the question is deleted from the database (too bad, sorry)
				int score = Integer.parseInt(request.getParameter("Range" + k.getId()));
				pet.questionScores.put(k, score);
				pet.scores[a.conceptIds.indexOf(q.conceptId)] += score;
				pet.possibleScores[a.conceptIds.indexOf(q.conceptId)] += q.pointValue; 
			}
			
			// Record the timestamp for the exam review and save the revised transaction entity
			pet.reviewed = new Date();
			ofy().save().entity(pet).now();
			
			String studentUserId = request.getParameter("StudentUserId");
			
			// Create/store a new Score entity and submit it to the LMS grade book
			try {
				Score s = Score.getInstance(studentUserId,a);
				ofy().save().entity(s).now();
				if (a.lti_ags_lineitem_url != null) { // LTI v1.3
					LTIMessage.postUserScore(s,studentUserId);
				}
			} catch (Exception e) {}

		} catch (Exception e) {
			throw e;
		}
		return true;
	}

	String selectExamQuestionsForm(User user) {
		StringBuffer buf = new StringBuffer("<h3>Placement Exam Settings</h3>");
		try {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			
			List<Concept> concepts = new ArrayList<Concept>(ofy().load().type(Concept.class).ids(a.conceptIds).values());
			buf.append("Topics covered:<OL>");
			for (Concept c : concepts) buf.append("<LI>" + c.title + "</LI>");
			buf.append("</OL>");

			if (a.timeAllowed==null) a.timeAllowed = 3600; // default time for completing the exam

			buf.append("Each placement exam consists of items selected at random from the items below:<ul>"
					+ "<li>30 questions worth 2 points each</li>"
					+ "<li>10 questions worth 4 points each</li>"
					+ "for a total of 100 points.</ul>");
			buf.append("The default time allowed to complete the exam is 60 minutes, but you may change this "
					+ "(e.g., to create a special assignment for a student requiring extended time up to 300 minutes).<br/>");
			buf.append("<form action=/PlacementExam method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />" 
					+ "Time allowed for this assignment: <input type=text size=5 name=TimeAllowed value=" + a.timeAllowed/60. + "> minutes. "
					+ "<input type=submit name=UserRequest value='Set Allowed Time'><br>"
					+ "</form><br/>");
			
			buf.append("By default, students may attempt this placement exam as many times as they wish. This rewards students who persist "
					+ "to achieve a better score. However, you may limit the number of attempts here. Leave the field blank to permit unlimited attempts.<br/>"
					+ "<form action=/PlacementExam method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "Number of attempts allowed for this assignment: <input type=text size=10 name=AttemptsAllowed " 
					+ (a.attemptsAllowed==null?"placeholder=unlimited":"value=" + a.attemptsAllowed) + " /> "
					+ "<input type=submit name=UserRequest value='Set Allowed Attempts' /><br/>"
					+ "</form><br/><br/>");
			
			buf.append("By default, students will view the exam immediately after clicking the assignment link in your LMS. However, "
					+ "you may (optionally) set a password required to start the exam by entering it below:</br>"
					+ "<form method=post action=/PlacementExam ><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=text name=ExamPassword value='" + (a.password==null || a.password.isEmpty()?"":a.password) + "' />"
					+ "<input type=submit name=UserRequest value='Set Password' /></form><br/><br/>");
			
			buf.append("Select the items to be included in this placement exam:<br/><br/>");

			List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
			List<Key<Question>> questionKeys_04pt = new ArrayList<Key<Question>>();
			
			for (long cId : a.conceptIds) {  // Sort and collect the question keys
				questionKeys_02pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("conceptId",cId).filter("pointValue",2).keys().list());
				questionKeys_04pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("conceptId",cId).filter("pointValue",4).keys().list());
			}

			 List<Key<Question>> questionKeys = new ArrayList<Key<Question>>(questionKeys_02pt);
			 questionKeys.addAll(questionKeys_04pt);
			 Map<Key<Question>,Question> questions = ofy().load().keys(questionKeys);
			
			buf.append("<FORM NAME=DummyForm><INPUT TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick=\"for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}\""
					+ "> Select/Unselect All</FORM>");

			buf.append("<FORM NAME=Questions METHOD=POST ACTION=PlacementExam>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment'>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE=PlacementExam>"
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items'>");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			Question q = null;
			int i = 0;

			// 2-point questions:
			buf.append("<TR><TD COLSPAN=2><U>2-point Questions: (select at least 30)</U></TD></TR>");
			i=0;
			for (Key<Question> k : questionKeys_02pt) {
				i++;
				q = questions.get(k);
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
				buf.append("<b>&nbsp;" + i + ".</b></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
			}
			// 4-point questions:
			buf.append("<TR><TD COLSPAN=2><U>4-point Questions: (select at least 10)</U></TD></TR>");
			i=0;
			for (Key<Question> k : questionKeys_04pt) {
				i++;
				q = questions.get(k);
				q.setParameters();
				buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">");
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
	
	String orderResponses(String[] answers) {
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}
	
	String analyzeQuestions(User user, Assignment a) {
		StringBuffer buf = new StringBuffer(Subject.banner + "<h2>Placement Exam Question Item Analysis</h2>");
		if (!user.isInstructor()) return "Not Authorized";
		
		// iterate through all the transactions for this assignment and make a deduplicated Map<userId,score> if the best transactions
		List<PlacementExamTransaction> transactions = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",a.id).list();
		List<PlacementExamTransaction> bestTransactions = new ArrayList<PlacementExamTransaction>();
		Map<String,Integer> userScores = new HashMap<String,Integer>();
		
		// iterate through the transactions and create a deduplicated Map of users' best scores
		for (PlacementExamTransaction t : transactions) {
			if (t.graded == null) continue; // don't use unsubmitted exams for the analysis
			int score = score(t);
			if (userScores.containsKey(t.userId)) {  // this user has more than one transaction
				if (score(t) < userScores.get(t.userId)) continue;  // don't replace with a lower score
				else {  // search for the old transaction and remove it
					for (int i=0;i<bestTransactions.size();i++) if (bestTransactions.get(i).userId.equals(t.userId)) bestTransactions.remove(i);
				}
			}
			userScores.put(t.userId, score);
			bestTransactions.add(t);
		}
		
		// make a List of userScore Entries and sort it by decreasing score (top users first)
		List<Entry<String,Integer>> entries = new ArrayList<Entry<String,Integer>>(userScores.entrySet());
		Collections.sort(entries,new SortUserScores());
		
		// use the sorted List of Entries to make Lists of the top and low scoring 27% of userIds
		int groupSize = Math.round((float)(0.27 * entries.size()+0.5));
		List<String> topUserIds = new ArrayList<String>();
		List<String> lowUserIds = new ArrayList<String>();
		for (int i=0;i<groupSize;i++) {
			topUserIds.add(entries.get(i).getKey());
			lowUserIds.add(entries.get(entries.size()-1-i).getKey());
		}
		
		//Create 2 Lists of questionKeys, one for each possibleScores
		List<Key<Question>> questionKeys_02pt = new ArrayList<Key<Question>>();
		List<Key<Question>> questionKeys_04pt = new ArrayList<Key<Question>>();
		for (long cId : a.conceptIds) {  //First collect the question keys
			questionKeys_02pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("conceptId",cId).filter("pointValue",2).keys().list());
			questionKeys_04pt.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("conceptId",cId).filter("pointValue",4).keys().list());
		}
		
		//  Create Maps to hold the question statistics by questionKey
		Map<Key<Question>,Integer> topScores = new HashMap<Key<Question>,Integer>();			// total earned scores for each question by top students
		Map<Key<Question>,Integer> lowScores = new HashMap<Key<Question>,Integer>();			// total earned scores for each question by low students
		Map<Key<Question>,Integer> topPossibleScores = new HashMap<Key<Question>,Integer>();	// total possible scores for each question by top students
		Map<Key<Question>,Integer> lowPossibleScores = new HashMap<Key<Question>,Integer>();	// total possible scores for each question by low students
		
		List<Response> responses = new ArrayList<Response>();
		for (PlacementExamTransaction pet : bestTransactions) {
			if (topUserIds.contains(pet.userId)) {
				try {
					responses = ofy().load().type(Response.class).filter("assignmentType","PlacementExam").filter("transactionId",pet.id).list();
					for (Response r : responses) {  // update stats for all questions that received a Response
						Key<Question> k = Key.create(Question.class,r.questionId);
						topScores.put(k,topScores.get(k)==null?r.score:topScores.get(k)+r.score);
						topPossibleScores.put(k,topPossibleScores.get(k)==null?r.possibleScore:topPossibleScores.get(k)+r.possibleScore);
						pet.questionKeys.remove(k);
					}
					for (Key<Question> k : pet.questionKeys) {
						if (topScores.get(k)==null) topScores.put(k, 0);
						if (questionKeys_02pt.contains(k)) topPossibleScores.put(k, topPossibleScores.get(k)==null?2:topPossibleScores.get(k)+2);
						else if (questionKeys_04pt.contains(k)) topPossibleScores.put(k, topPossibleScores.get(k)==null?4:topPossibleScores.get(k)+4);
					}
				} catch (Exception e) {}
			} else if (lowUserIds.contains(pet.userId)) {
				try {
					responses = ofy().load().type(Response.class).filter("assignmentType","PlacementExam").filter("transactionId",pet.id).list();
					for (Response r : responses) {  // update stats for all questions that received a Response
						Key<Question> k = Key.create(Question.class,r.questionId);
						lowScores.put(k,lowScores.get(k)==null?r.score:lowScores.get(k)+r.score);
						lowPossibleScores.put(k,lowPossibleScores.get(k)==null?r.possibleScore:lowPossibleScores.get(k)+r.possibleScore);
						pet.questionKeys.remove(k);
					}
					for (Key<Question> k : pet.questionKeys) {
						if (lowScores.get(k)==null) lowScores.put(k, 0);
						if (questionKeys_02pt.contains(k)) lowPossibleScores.put(k, lowPossibleScores.get(k)==null?2:lowPossibleScores.get(k)+2);
						else if (questionKeys_04pt.contains(k)) lowPossibleScores.put(k, lowPossibleScores.get(k)==null?4:lowPossibleScores.get(k)+4);
					}
				} catch (Exception e) {}
			}
		}

		buf.append("Group size: " + groupSize + "<br/><br/>");
		
		int i = 0;
		int qTopScores = 0;
		int qLowScores = 0;
		int qTopPossScores = 0;
		int qLowPossScores = 0;
		double successIndex = 0;
		double discriminationIndex = 0;
		Map<Key<Question>,Question> questions = new HashMap<Key<Question>,Question>();
		
		buf.append("<h3>2-point question items</h3>");
		buf.append("<table>");
		questions = ofy().load().keys(questionKeys_02pt);
		for (Key<Question> k : questionKeys_02pt) {
			if (!a.questionKeys.contains(k)) continue;
			i++;
			Question q = questions.get(k);
			q.setParameters();
			buf.append("<TR VALIGN=TOP NOWRAP>");
			try {
				qTopScores = topScores.get(k)==null?0:topScores.get(k);
				qLowScores = lowScores.get(k)==null?0:lowScores.get(k);
				qTopPossScores = topPossibleScores.get(k)==null?0:topPossibleScores.get(k);
				qLowPossScores = lowPossibleScores.get(k)==null?0:lowPossibleScores.get(k);
				successIndex = qTopPossScores+qLowPossScores==0?0:(qTopScores + qLowScores)*100.0/(qTopPossScores+qLowPossScores);
				discriminationIndex = qTopPossScores==0?0:qTopScores*100.0/qTopPossScores;
				discriminationIndex -= qLowPossScores==0?0:qLowScores*100.0/qLowPossScores;
				discriminationIndex = discriminationIndex/100.0;
				buf.append("<TD>Success&nbsp;Percentage:&nbsp;" + String.format("%,.1f", successIndex) + "%<br/>" + "Discrimination&nbsp;Index:&nbsp;" + String.format("%,.3f", discriminationIndex) + "</TD>");
			} catch (Exception e) {
				buf.append("<TD>Insufficient Data</TD>");
			}
			buf.append("<TD>&nbsp;&nbsp;<b>"+i+".</b></TD>");
			buf.append("<TD>" + q.printAll() + "</TD>");
			buf.append("</TR>");
		}
		buf.append("</table>");
		
		i=0;
		buf.append("<h3>4-point question items</h3>");
		buf.append("<table>");
		questions = ofy().load().keys(questionKeys_04pt);
		for (Key<Question> k : questionKeys_04pt) {
			if (!a.questionKeys.contains(k)) continue;
			i++;
			Question q = questions.get(k);
			q.setParameters();
			buf.append("<TR VALIGN=TOP NOWRAP>");
			try {
				qTopScores = topScores.get(k)==null?0:topScores.get(k);
				qLowScores = lowScores.get(k)==null?0:lowScores.get(k);
				qTopPossScores = topPossibleScores.get(k)==null?0:topPossibleScores.get(k);
				qLowPossScores = lowPossibleScores.get(k)==null?0:lowPossibleScores.get(k);
				successIndex = qTopPossScores+qLowPossScores==0?0:(qTopScores + qLowScores)*100.0/(qTopPossScores+qLowPossScores);
				discriminationIndex = qTopPossScores==0?0:qTopScores*100.0/qTopPossScores;
				discriminationIndex -= qLowPossScores==0?0:qLowScores*100.0/qLowPossScores;
				discriminationIndex = discriminationIndex/100.0;
				buf.append("<TD>Success&nbsp;Percentage:&nbsp;" + String.format("%,.1f", successIndex) + "%<br/>" + "Discrimination&nbsp;Index:&nbsp;" + String.format("%,.3f", discriminationIndex) + "</TD>");
			} catch (Exception e) {
				buf.append("<TD>Insufficient Data</TD>");
			}
			buf.append("<TD>&nbsp;&nbsp;<b>"+i+".</b></TD>");
			buf.append("<TD>" + q.printAll() + "</TD>");
			buf.append("</TR>");
		}
		buf.append("</table>");
		
		return buf.toString();
	}
	
	int score(PlacementExamTransaction t) {
		int score = 0;
		try {
			for (int i=0;i<t.conceptIds.size();i++) score += t.scores[i];
		} catch (Exception e) {
			return 0;
		}
		return score;
	}
}

class SortUserScores implements Comparator<Entry<String,Integer>> {
	// used by analyzeQuestions to sort userIds by best score
	public int compare(Entry<String,Integer> a, Entry<String,Integer> b) {
		return b.getValue().compareTo(a.getValue());  // reverse order (highest scores before lower)
	}
}

class SortPlacementExams implements Comparator<PlacementExamTransaction> { 
    // Used for sorting exams in ascending order of userId, then descending order downloaded 
    public int compare(PlacementExamTransaction a, PlacementExamTransaction b) {
    	if (a.userId.equals(b.userId)) return a.downloaded.compareTo(b.downloaded);
    	else return a.userId.compareTo(b.userId); 
    } 
}
