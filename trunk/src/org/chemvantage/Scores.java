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

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class Scores extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private static Random rand = new Random();;
	private static final String alpha = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "PZone servlet presents user's detailed scores in the Practice Zone site.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			out.println(Home.getHeader(user));
			if (userRequest.equals("AllQuizzes")) out.println(allQuizTransactions(user));
			else if (userRequest.equals("AllHomework")) out.println(allHWTransactions(user));
			else if (userRequest.equals("ShowAll") || user.myGroupId <= 0) out.println(allMyScores(user));
			else out.println(myGroupScores(user));
			out.println(Home.footer);
		} catch (Exception e) {
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
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("DeletePracticeExamScores")) {
				deleteMyPracticeExamScores(user);
				response.sendRedirect("/Scores?UserRequest=ShowAll");
				return;
			} else if (userRequest.equals("Recalculate My Scores")) {
				user.recalculateScores();
				out.println(Home.getHeader(user) + myGroupScores(user) + Home.footer);
				return;
			}
			else {  // manage notification options
				if (userRequest.equals("Turn Notifications On")) { // this option is available only to
					user.notifyDeadlines = user.verifiedEmail;          // users with verified email addresses
				} 
				else if (userRequest.equals("Cancel Notifications")) user.notifyDeadlines = false;
				else if (userRequest.equals("Register")) {
					try {
						String cellNumber = request.getParameter("CellNumber");
						String carrier = request.getParameter("Carrier");
						String code = "";
						while (code.length()<5) {
							code += alpha.charAt(rand.nextInt(alpha.length()));
						}
						if (cellNumber.length()==10 && Long.parseLong(cellNumber) > 0 && !carrier.isEmpty()) { // 10-digit number
							user.smsMessageDevice = code + cellNumber + "@" + carrier;
							sendSMSConfirmationCode(cellNumber+"@"+carrier,code);
						}
					} catch (Exception e2) {
						out.println(e2.toString());
						user.smsMessageDevice = "";
					}
				} else if (userRequest.equals("Confirm")) {
					if (request.getParameter("Code").toUpperCase().equals(user.smsMessageDevice.substring(0,5))) {
						user.smsMessageDevice = user.smsMessageDevice.substring(5);
					}
				}
				else if (userRequest.equals("Cancel")) user.smsMessageDevice = "";
				ofy.put(user);
				out.println(Home.getHeader(user) + myGroupScores(user) + Home.footer);
			}
		} catch (Exception e) {
		}
	}
	
	String allMyScores(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>My " + subject.title + " Portfolio</h2>");
			if (user.myGroupId > 0) { // user belongs to a group; give option of viewing group scores
				buf.append("<a href=Scores>Show only my group scores.</a>");
			}

			// Display a summary of practice exam scores
			buf.append("<h3>Practice Exam Scores</h3>");
			Query<PracticeExamTransaction> transactions = ofy.query(PracticeExamTransaction.class).filter("userId",user.id);
			if (transactions.count()==0) {	
				buf.append("No practice exam scores have been recorded. ");
				buf.append("Take a <a href=PracticeExam>practice exam</a> now.");
			} else {
				buf.append("Scores are cumulative and organized by topic in order to highlight your strengths<br>"
						+ "and weaknesses. If your topic score is 85% or greater, the bar is green, 50-84% is yellow,<br>"
						+ "and less than 50% is red.  You may use the button below to delete all of your exam scores.");
				buf.append("<FORM ACTION=Scores METHOD=POST><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=DeletePracticeExamScores>"
						+ "<INPUT TYPE=SUBMIT VALUE='Delete All My Practice Exam Scores Now' "
						+ "onClick=\"javascript: return confirm('Permanently delete all your exams scores. Are you sure?')\"> "
						+ "Take another <a href=PracticeExam>practice exam</a> now."
						+ "</FORM>");

				List<Long> topicIds = new ArrayList<Long>();
				List<Integer> scores = new ArrayList<Integer>();
				List<Integer> possibleScores = new ArrayList<Integer>();
				for (PracticeExamTransaction pt : transactions) {
					for (int i=0;i<pt.topicIds.size();i++) {
						if (!topicIds.contains(pt.topicIds.get(i))) {
							topicIds.add(pt.topicIds.get(i));
							scores.add(pt.scores[i]);
							possibleScores.add(pt.possibleScores[i]);
						} else {
							int j = topicIds.indexOf(pt.topicIds.get(i));
							scores.set(j,scores.get(j)+pt.scores[i]);
							possibleScores.set(j,possibleScores.get(j)+pt.possibleScores[i]);
						}
					}
				}
				
				buf.append("\n<TABLE>\n<TR><TD><b>Topic</b></TD><TD><b>Score</b></TD>"
						+ "<TD><b>Possible</b></TD><TD><b>Percent</b></TD><TD></TD></TR>");
				while (!topicIds.isEmpty()) {
					String title = ofy.get(Topic.class,topicIds.remove(0)).title;
					int score = scores.remove(0);
					int possible = possibleScores.remove(0);
					int pct = (possible>0?score*100/possible:0);
					String color = (pct>84?"#00FF00":(pct<50?"#FF0000":"#FFFF00"));
					buf.append("\n<TR>"
							+ "<TD>" + title + "</TD>"
							+ "<TD ALIGN=RIGHT>" + score + "</TD>"
							+ "<TD ALIGN=RIGHT>" + possible + "</TD>"
							+ "<TD ALIGN=RIGHT>" + pct + "%</TD>"
							+ "<TD><div style='background-color:" + color + ";width:" + pct 
							+ "px;'/>&nbsp;</TD></TR>");
				}
				buf.append("\n</TABLE>");
			}

			// Display video lectures viewed
			Query<VideoTransaction> watched = ofy.query(VideoTransaction.class).filter("userId",user.id);
			buf.append("<h3>" + watched.count() + " Video Lectures Viewed</h3>"
					+ "<div id='videoLink'><FONT SIZE=-1>("
					+ "<a href=# onCLick=document.getElementById('videoList').style.display='';"
					+ "document.getElementById('videoLink').style.display='none'>"
					+ "view list</a>)</FONT></div>");
			buf.append("<div id='videoList' style='display: none'>");
			for (VideoTransaction t : watched) {
				buf.append(t.title + "<br>\n");
			}
			buf.append("</div>");

			buf.append("<h3>Quiz and Homework Scores</h3>"
					+ "For a complete record of your assignments, click "
					+ "here for <a href=Scores?UserRequest=AllQuizzes>all quizzes</a> or "
					+ "<a href=Scores?UserRequest=AllHomework>all homework</a> transactions.<br>"
					+ "<TABLE BORDER=1 CELLSPACING=0><TR><TH ALIGN=LEFT>Topic</TH><TH>Quiz</TH><TH>Homework</TH></TR>"); 
			
			Query<Topic> topics = ofy.query(Topic.class);
			int nRows = 0;
			int score = 0;
			for (Topic t : topics) {
				Query<QuizTransaction> quizTransactions = ofy.query(QuizTransaction.class).filter("userId",user.id).filter("topicId",t.id);
				score = 0;
				for (QuizTransaction qt : quizTransactions) if (qt.score > score) score = qt.score;
				String quizScore = (quizTransactions.count()==0?"":Integer.toString(score));
				
				Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",user.id).filter("topicId",t.id);
				score = 0;
				List<Long> questionIds = new ArrayList<Long>();
				for (HWTransaction hwt : hwTransactions) {
					if (hwt.score > 0 && !questionIds.contains(hwt.questionId)) {
						score++;
						questionIds.add(hwt.questionId);
					}
				}
				String hwScore = (hwTransactions.count()==0?"":Integer.toString(score));
				if (quizScore.length() > 0 || hwScore.length() > 0) { // write a row in the scores table for this topic
					buf.append("<TR><TD>" + t.title + "</TD><TD ALIGN=CENTER>" + quizScore + "</TD><TD ALIGN=CENTER>" + hwScore + "</TD></TR>");
					nRows++;
				}
			}
			if (nRows == 0) buf.append("<TR><TD COLSPAN=3>(none)</TD></TR>");
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	public String myGroupScores(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("\n<h2>My " + subject.title + " Scores</h2>");
			buf.append("\nUser: " + user.getBothNames() + (user.email.length()>0?" (" + user.email + ")":"") + "<br>");
			if (user.myGroupId < 0) user.changeGroups(0);
			Group myGroup = null;
			if (user.myGroupId > 0) myGroup = ofy.get(Group.class,user.myGroupId);
			if (myGroup == null) { // group may have been deleted
				user.myGroupId = 0;
				ofy.put(user);
				return allMyScores(user); 
			}
			String instructorEmail = null;
			try {
				myGroup.getInstructorEmail();
			} catch (Exception e2) {
			}
			if (instructorEmail == null) instructorEmail = "";
			buf.append("\nGroup: " + myGroup.description + "<br>"
					+ (myGroup.instructorId.length()>0?"Instructor: " + myGroup.getInstructorBothNames():"(unavailable)"));
			if (instructorEmail.length()>0) buf.append(" (<a href=mailto:" + instructorEmail + ">" + instructorEmail + "</a>)");
			DateFormat df_long = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.LONG);
			df_long.setTimeZone(myGroup.getTimeZone());
			Date now = new Date();
			buf.append("<br>" + df_long.format(now) + "<p>");
			
			if (myGroup.getUsingLisOutcomeService()) {
				buf.append("This group is using a ChemVantage service that automatically returns scores to your "
						+ "class learning<br>management system (LMS). You should check there to see your scores on ChemVantage "
						+ "assignments.<p><span style='color:red'>Important:<br>You <u>must</u> begin each assignment "
						+ "by clicking the assignment link in your LMS.<br>Otherwise, ChemVantage has no way of knowing how "
						+ "to return your score for credit.</span><p>For a complete record of your assignments, click "
						+ "here for <a href=Scores?UserRequest=AllQuizzes>all quizzes</a> or "
						+ "<a href=Scores?UserRequest=AllHomework>all homework</a> transactions.<p>"
						+ "<b>Are Your Scores Being Reported To Your LMS Properly?</b><br>"
						+ "If not, please follow these steps to fix the problem. For each incomplete score:<ol>"
						+ "<li>Click on the assignment link in your LMS. This will send a code "
						+ "from the LMS to ChemVantage that is required to report your current score."
						+ "<li>Submit the assignment (quiz or homework problem). A score of zero is fine.</ol>"
						+ "ChemVantage will report your best score on that assignment to your LMS. How that score is "
						+ "handled in the LMS grade book depends on the properties of the LMS.  Some systems will report "
						+ "the most recent score, some the best score, and still others will enforce local deadlines.");
				return buf.toString();
			}
			// Calculate the next deadline:
			Date nextDeadline = myGroup.getNextDeadline();
			if (nextDeadline == null) {
				nextDeadline = now;  // in case no more deadlines exist, assign to now to avoid null pointer exceptions
				buf.append("You have no more assignments pending.");
			}
			else {
				buf.append("\nYour next deadline is <SPAN style='BACKGROUND-COLOR: #ffff00'>" + df_long.format(nextDeadline) + "</SPAN>");
			}
			
			// Show/set deadline notification options:
			buf.append("<FORM METHOD=POST>");
			if (user.hasPremiumAccount() && user.notifyDeadlines && user.verifiedEmail) {
				buf.append("ChemVantage will send you an email reminder on the morning of each due date. "
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Cancel Notifications'><br>");
				if (user.smsMessageDevice.isEmpty()) {
					buf.append("If you would like the reminder sent to your mobile SMS/text device, please "
							+ "<a href=# onClick=javascript:getElementById('register').style.display=''>register your 10-digit cell number</a>.<br>");
					buf.append("<div id=register style='display: none'><INPUT TYPE=TEXT NAME=CellNumber VALUE='10 digits only' onfocus=\"if (this.value == '10 digits only') {this.value = '';}\">");
					buf.append("<SELECT NAME=Carrier onChange= ><OPTION VALUE=''>Select your carrier</OPTION>"
							+ "<OPTION VALUE='txt.att.net'>AT&T</OPTION>"
							+ "<OPTION VALUE='message.alltel.com'>Alltel</OPTION>"
							+ "<OPTION VALUE='myboostmobile.com'>Boost</OPTION>"
							+ "<OPTION VALUE='mobile.celloneusa.com'>CellularOne</OPTION>"
							+ "<OPTION VALUE='csouth1.com'>Cellular South</OPTION>"
							+ "<OPTION VALUE='cingularme.com'>Cingular</OPTION>"
							+ "<OPTION VALUE='gocbw.com'>Cincinnati Bell</OPTION>"
							+ "<OPTION VALUE='sms.mycricket.com'>Cricket</OPTION>"
							+ "<OPTION VALUE='clearlydigital.com'>Midwest Wireless</OPTION>"
							+ "<OPTION VALUE='messaging.nextel.com'>Nextel</OPTION>"
							+ "<OPTION VALUE='messaging.sprintpcs.com'>Sprint PCS</OPTION>"
							+ "<OPTION VALUE='tmomail.net'>T-Mobile</OPTION>"
							+ "<OPTION VALUE='uscc.txtmsg.com'>US Cellular</OPTION>"
							+ "<OPTION VALUE='vtext.com'>Verizon</OPTION>"
							+ "<OPTION VALUE='vmobile.com'>Virgin Mobile</OPTION></SELECT>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Register'></div>");
				} else {
					try {
						Long.parseLong(user.smsMessageDevice.substring(0,10));
						buf.append("The reminder will be sent via SMS/text message to " + user.smsMessageDevice + "&nbsp;");
					} catch (Exception e2) {
						buf.append("A confirmation code was sent to your wireless device. If the message is not received, please<br>"
								+ "cancel and try again, or send email to admin@chemvantage.org for assistance.<br>"
								+ "To complete the SMS registration, enter the code here: "
								+ "<INPUT TYPE=TEXT SIZE=4 NAME=Code><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Confirm'>");
					}
					buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Cancel'>");
				}
			} else if (user.hasPremiumAccount()) {
				buf.append("You may elect to receive email or text message reminders of assignment deadlines"
						+ (user.verifiedEmail?".<br><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Turn Notifications On'>":
							",<br>but first you must <a href=Verification>verify your contact information</a>."));
			} 
			buf.append("</FORM>");
			int random = new Random().nextInt(99);  // selects a random integer between 0 and 9999
			// Show a table of scores earned for this group:
			DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
			df.setTimeZone(myGroup.getTimeZone());
			buf.append("\n<h3>Assigned Quiz and Homework Scores</h3>");
			buf.append("<p>\nThe scores listed here include only those earned for group assignments<br>"
					+ "completed prior to the indicated deadline. For a complete record of your assignments, click "
					+ "here for <a href=Scores?UserRequest=AllQuizzes>all quizzes</a> or "
					+ "<a href=Scores?UserRequest=AllHomework>all homework</a> transactions.<br>"
					+ "<a href=# onClick=\"javascript: document.getElementById('dots').style.display='';\">Do your scores have red dots?</a>"
					+ "<div id=dots style='display:none'>"
					+ "If a red dot appears in the table below, it means that you either missed an assignment deadline or "
					+ "your score on the assignment was low enough to trigger a concern. The red dot also appears on the class "
					+ "gradesheet to alert your instructor to a potential problem.  If you complete the assignment "
					+ "successfully after the deadline, your group score will remain unchanged, but the red dot will disappear.</div>");
			
			buf.append("\n<TABLE BORDER=1 CELLSPACING=0><TR><TH></TH><TH COLSPAN=2>Quizzes</TH><TH COLSPAN=2>Homework</TH></TR>"
					//+ "\n<TR><TH ALIGN=LEFT>Topic</TH><TH>Deadline</TH><TH>Score <FONT COLOR=GRAY>(Attempts)</FONT></TH>"
					//+ "<TH>Deadline</TH><TH>Score <FONT COLOR=GRAY>(Attempts)</FONT></TH></TR>"); 
					+ "\n<TR><TH ALIGN=LEFT>Topic</TH><TH>Deadline</TH><TH>Score</TH><TH>Deadline</TH><TH>Score</TH></TR>"); 
			// Get a list of Ids for topics assigned to this group in order of deadlines
			List<Long> topicIds = myGroup.getGroupTopicIds();
			int nRows = 0;
			myGroup.setGroupTopicIds();
			for (Long topicId : topicIds) {
				Topic t = ofy.get(Topic.class,topicId);
				long i = myGroup.getAssignmentId("Quiz",topicId);
				Assignment qa = i>0?ofy.get(Assignment.class,i):null;
				long j = myGroup.getAssignmentId("Homework",topicId);
				Assignment hwa = j>0?ofy.get(Assignment.class,j):null;
				if (qa == null && hwa == null) continue;
				buf.append("<TR><TD>" + t.title + "</TD>");
				
				if (qa != null) { // print the quiz score for this topic in the table. The random value helps defeat browser page caching
					buf.append("<TD ALIGN=CENTER" + (qa.deadline.equals(nextDeadline)?" style=background:#FFFF00>":">") + "<a href=Quiz?TopicId=" + topicId + "&r=" + random + ">" + df.format(qa.deadline) + "</a></TD>");
					buf.append("<TD ALIGN=CENTER>" + myGroup.getScore(user.id,qa).getEnhancedDotScore(qa.deadline,myGroup.rescueThresholdScore) + "</TD>");
				} else buf.append("<TD COLSPAN=2 ALIGN=CENTER style=color:#808080>(not assigned)</TD>");
				
				if (hwa != null && hwa.questionKeys.size()>0) { // print the homework score for this topic in the table. The random value helps defeat browser page caching
					buf.append("<TD ALIGN=CENTER" + (hwa.deadline.equals(nextDeadline)?" style=background:#FFFF00>":">") + "<a href=Homework?TopicId=" + topicId + "&r=" + random  + ">" + df.format(hwa.deadline) + "</a></TD>");
					buf.append("<TD ALIGN=CENTER>" + myGroup.getScore(user.id,hwa).getEnhancedDotScore(hwa.deadline,myGroup.rescueThresholdScore) + "</TD>");
				} else buf.append("<TD COLSPAN=2 ALIGN=CENTER style=color:#808080>(not assigned)</TD>");
				buf.append("</TR>");
				nRows++;
			}
			if (nRows == 0) buf.append("<TR><TD COLSPAN=5>(none)</TD></TR>");
			buf.append("</TABLE>");
			
			buf.append("<FORM METHOD=POST ACTION=Scores><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Recalculate My Scores'></FORM>");
			
			// Display a summary of practice exam scores
			buf.append("<h3>Practice Exam Scores</h3>");
			Query<PracticeExamTransaction> transactions = ofy.query(PracticeExamTransaction.class).filter("userId",user.id);
			if (transactions.count()==0) {	
				buf.append("No practice exam scores have been recorded. ");
				buf.append("Take a <a href=PracticeExam>practice exam</a> now.");
			} else {
				buf.append("Scores are cumulative and organized by topic in order to highlight your strengths<br>"
						+ "and weaknesses. If your topic score is 85% or greater, the bar is green, 50-84% is yellow,<br>"
						+ "and less than 50% is red.  You may use the button below to delete all of your exam scores.");
				buf.append("<FORM ACTION=Scores METHOD=POST><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=DeletePracticeExamScores>"
						+ "<INPUT TYPE=SUBMIT VALUE='Delete All My Practice Exam Scores Now' "
						+ "onClick=\"javascript: return confirm('Permanently delete all your exams scores. Are you sure?')\"> "
						+ "Take another <a href=PracticeExam>practice exam</a> now."
						+ "</FORM>");

				topicIds = new ArrayList<Long>();
				List<Integer> scores = new ArrayList<Integer>();
				List<Integer> possibleScores = new ArrayList<Integer>();
				for (PracticeExamTransaction pt : transactions) {
					for (int i=0;i<pt.topicIds.size();i++) {
						if (!topicIds.contains(pt.topicIds.get(i))) {
							topicIds.add(pt.topicIds.get(i));
							scores.add(pt.scores[i]);
							possibleScores.add(pt.possibleScores[i]);
						} else {
							int j = topicIds.indexOf(pt.topicIds.get(i));
							scores.set(j,scores.get(j)+pt.scores[i]);
							possibleScores.set(j,possibleScores.get(j)+pt.possibleScores[i]);
						}
					}
				}
				
				buf.append("<TABLE><TR><TD><b>Topic</b></TD><TD><b>Score</b></TD>"
						+ "<TD><b>Possible</b></TD><TD><b>Percent</b></TD><TD></TD></TR>");
				while (!topicIds.isEmpty()) {
					String title = ofy.get(Topic.class,topicIds.remove(0)).title;
					int score = scores.remove(0);
					int possible = possibleScores.remove(0);
					int pct = (possible>0?score*100/possible:0);
					String color = (pct>84?"#00FF00":(pct<50?"#FF0000":"#FFFF00"));
					buf.append("<TR>"
							+ "<TD>" + title + "</TD>"
							+ "<TD ALIGN=RIGHT>" + score + "</TD>"
							+ "<TD ALIGN=RIGHT>" + possible + "</TD>"
							+ "<TD ALIGN=RIGHT>" + pct + "%</TD>"
							+ "<TD><div style='background-color:" + color + ";width:" + pct 
							+ "px;'/>&nbsp;</TD></TR>");
				}
				buf.append("</TABLE>");
			}

		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	void sendSMSConfirmationCode(String address,String code) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		Message msg = new MimeMessage(session);
		try {
			msg.setRecipient(Message.RecipientType.TO,new InternetAddress(address));
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject("ChemVantage.org SMS Registration");
			String messageText = "Enter this on the Scores page: " + code;
			msg.setText(messageText);
			Transport.send(msg);
		} catch (Exception e) {
		}
	}
	
	void deleteMyPracticeExamScores(User user) {
		try {
			ofy.delete(ofy.query(PracticeExamTransaction.class).filter("userId",user.id).fetchKeys());
			//return "<h3>Gone!</h3>All of your practice exam scores were permanently deleted from the datastore.";
		} catch (Exception e) {
			//return e.toString();
		}
	}
	
	String allQuizTransactions(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("\n<h2>ChemVantage Quiz Transactions</h2>");
			buf.append("\nUser: " + user.getBothNames() + (user.email.length()>0?" (" + user.email + ")":"") + "<br>");
			
			try {  // give some information about the current group
				Group myGroup = ofy.get(Group.class,user.myGroupId);
				buf.append("\nGroup: " + myGroup.description + "<br>"
					+ "Instructor: " + (myGroup.instructorId.length()>0?myGroup.getInstructorBothNames():"(unavailable)"));
				String instructorEmail = myGroup.getInstructorEmail();
				if (instructorEmail.length()>0) buf.append(" (<a href=mailto:" + instructorEmail + ">" + instructorEmail + "</a>)");
			} catch (Exception e) {}
			
			DateFormat df_long = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.LONG);
			df_long.setTimeZone(TimeZone.getDefault());
			Date now = new Date();
			buf.append("<br>" + df_long.format(now) + " (all times on this page are in Universal Coordinated Time)<p>");

			// make a list of all quiz transactions for this user
			List<QuizTransaction> quizTransactions = ofy.query(QuizTransaction.class).filter("userId", user.id).list();
			buf.append("<table><tr><th>Topic</th><th>Downloaded</th><th>Graded</th><th>Score</th></tr>");
			for (QuizTransaction qt : quizTransactions) buf.append(qt.tableRow());
			buf.append("</table>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String allHWTransactions(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("\n<h2>ChemVantage Homework Transactions</h2>");
			buf.append("\nUser: " + user.getBothNames() + (user.email.length()>0?" (" + user.email + ")":"") + "<br>");
			
			try {  // give some information about the current group
				Group myGroup = ofy.get(Group.class,user.myGroupId);
				buf.append("\nGroup: " + myGroup.description + "<br>"
					+ "Instructor: " + (myGroup.instructorId.length()>0?myGroup.getInstructorBothNames():"(unavailable)"));
				String instructorEmail = myGroup.getInstructorEmail();
				if (instructorEmail.length()>0) buf.append(" (<a href=mailto:" + instructorEmail + ">" + instructorEmail + "</a>)");
			} catch (Exception e) {}
			
			DateFormat df_long = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.LONG);
			df_long.setTimeZone(TimeZone.getDefault());
			Date now = new Date();
			buf.append("<br>" + df_long.format(now) + " (all times on this page are in Universal Coordinated Time)<p>");

			// make a list of all quiz transactions for this user
			List<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId", user.id).list();
			buf.append("<table><tr><th>Topic</th><th>Question ID</th><th>Graded</th><th>Score</th></tr>");
			for (HWTransaction ht : hwTransactions) buf.append(ht.tableRow());
			buf.append("</table>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
}
