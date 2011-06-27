/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

			if (userRequest.equals("ShowAll") || user.myGroupId == 0)
				out.println(Home.getHeader(user) + allMyScores(user) + Home.footer);
			else out.println(Home.getHeader(user) + myGroupScores(user) + Home.footer);
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
			} 
			else if (userRequest.equals("Turn Notifications On")) { // this option is available only to
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
					for (int i=0;i<pt.topicIds.length;i++) {
						if (!topicIds.contains(pt.topicIds[i])) {
							topicIds.add(pt.topicIds[i]);
							scores.add(pt.scores[i]);
							possibleScores.add(pt.possibleScores[i]);
						} else {
							int j = topicIds.indexOf(pt.topicIds[i]);
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
							+ ";'/><p>&nbsp;</p></TD></TR>");
				}
				buf.append("</TABLE>");
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
			Group myGroup = ofy.get(Group.class,user.myGroupId);
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
			buf.append("<br>" + df_long.format(now));
			
			buf.append("<p>\nThe scores listed on this page include only those earned for assignments<br>"
					+ "specified by your instructor and completed prior to the indicated deadline.<br>"
					+ "<a href=Scores?UserRequest=ShowAll>Show all my scores.</a><p>");
			
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
			
			// Show a table of scores earned for this group:
			DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
			df.setTimeZone(myGroup.getTimeZone());
			buf.append("\n<h3>Quiz and Homework Assignments</h3>"
					+ "\n<TABLE BORDER=1 CELLSPACING=0><TR><TH></TH><TH COLSPAN=2>Quizzes</TH><TH COLSPAN=2>Homework</TH></TR>"
					+ "\n<TR><TH ALIGN=LEFT>Topic</TH><TH>Deadline</TH><TH>Score <FONT COLOR=GRAY>(Attempts)</FONT></TH>"
					+ "<TH>Deadline</TH><TH>Score <FONT COLOR=GRAY>(Attempts)</FONT></TH></TR>"); 
			// Get a list of Ids for topics assigned to this group in order of deadlines
			List<Long> topicIds = myGroup.getGroupTopicIds();
			int nRows = 0;
			for (Long topicId : topicIds) {
				Topic t = ofy.get(Topic.class,topicId);
				long i = myGroup.getAssignmentId("Quiz",topicId);
				Assignment qa = i>0?ofy.get(Assignment.class,i):null;
				long j = myGroup.getAssignmentId("Homework",topicId);
				Assignment hwa = j>0?ofy.get(Assignment.class,j):null;
				if (qa == null && hwa == null) continue;
				buf.append("<TR><TD>" + t.title + "</TD>");
				
				if (qa != null) { // print the quiz score for this topic in the table
					buf.append("<TD ALIGN=CENTER" + (qa.deadline.equals(nextDeadline)?" BGCOLOR=#FFFF00><a href=Quiz?TopicId=" + topicId + ">" + df.format(qa.deadline) + "</a></TD>":">" + df.format(qa.deadline) + "</TD>"));
					buf.append("<TD ALIGN=CENTER>" + myGroup.getScore(user.id,qa).getEnhancedScore() + "</TD>");
				} else buf.append("<TD COLSPAN=2 ALIGN=CENTER><FONT COLOR=808080>(not assigned)</FONT></TD>");
				
				if (hwa != null && hwa.questionKeys.size()>0) { // print the homework score for this topic in the table
					buf.append("<TD ALIGN=CENTER" + (hwa.deadline.equals(nextDeadline)?" BGCOLOR=#FFFF00><a href=Homework?TopicId=" + topicId + ">" + df.format(hwa.deadline) + "</a></TD>":">" + df.format(hwa.deadline) + "</TD>"));
					buf.append("<TD ALIGN=CENTER>" + myGroup.getScore(user.id,hwa).getEnhancedScore() + "</TD>");
				} else buf.append("<TD COLSPAN=2 ALIGN=CENTER><FONT COLOR=808080>(not assigned)</FONT></TD>");
				buf.append("</TR>");
				nRows++;
			}
			if (nRows == 0) buf.append("<TR><TD COLSPAN=5>(none)</TD></TR>");
			buf.append("</TABLE>");
			
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
					for (int i=0;i<pt.topicIds.length;i++) {
						if (!topicIds.contains(pt.topicIds[i])) {
							topicIds.add(pt.topicIds[i]);
							scores.add(pt.scores[i]);
							possibleScores.add(pt.possibleScores[i]);
						} else {
							int j = topicIds.indexOf(pt.topicIds[i]);
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
							+ ";'/><p>&nbsp;</p></TD></TR>");
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
	
}
/*
	void deleteMyExamScores (User user, String subject) {
			Connection conn = null;
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				int myGroupID = user.getGroupID(subject,conn);
				String sql = "DELETE FROM ExamResultsG" + myGroupID + " WHERE UserID='" + user.getID() + "'";
				conn.createStatement().executeUpdate(sql);
			} catch (Exception e) {
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return;
		}

		String quizScoresDetail(User user,String subject) {
			StringBuffer buf = new StringBuffer();
			int myGroupID = user.getGroupID(subject);
			Connection conn = null;
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				Statement stmt1 = conn.createStatement();
				String sqlQueryString = "SELECT Subject,Title,Downloaded,Graded,Score,PossibleScore "
					+ "FROM QuizTransactionsG" + myGroupID + " AS t1 LEFT JOIN AssignmentInfo AS t2 ON t1.TopicID=t2.TopicID "
					+ "WHERE Subject='" + subject + "' AND UserID='" + user.getUserID() 
					+ "' ORDER BY Downloaded DESC";
				ResultSet rsScores = stmt1.executeQuery(sqlQueryString);
				buf.append("<B>" + subject + " Quizzes</B>");
				buf.append("<TABLE CELLSPACING=5><TR><TH>Downloaded</TH><TH>Topic</TH>"
						+ "<TH>Graded</TH><TH>Score</TH></TR>");
				while (rsScores.next()) {
					buf.append("<TR><TD ALIGN=CENTER>" + rsScores.getTimestamp("Downloaded") + "</TD>" 
							+ "<TD ALIGN=CENTER>" + rsScores.getString("Title") + "</TD>"
							+ "<TD ALIGN=CENTER>" + rsScores.getTimestamp("Graded") + "</TD>"
							+ "<TD ALIGN=CENTER>" + rsScores.getString("Score") + " / " 
							+ rsScores.getString("PossibleScore") + "</TD></TR>");
				}
				buf.append("</TABLE>");
				rsScores.close();
				stmt1.close();
			} catch (Exception e) {
				return e.getMessage();
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return buf.toString();
		}

		String homeworkScoresDetail(User user,String subject) {
			StringBuffer buf = new StringBuffer();
			Connection conn = null;
			int myGroupID = user.getGroupID(subject);
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				Statement stmt1 = conn.createStatement();
				String sqlQueryString = "SELECT Subject,Title,QuestionID,Graded,Score,Response,PossibleScore "
					+ "FROM HomeworkTransactionsG" + myGroupID + " AS t1 LEFT JOIN AssignmentInfo AS t2 ON t1.TopicID=t2.TopicID "
					+ "WHERE Subject='" + subject + "' AND UserID='" + user.getUserID() 
					+ "' ORDER BY Graded DESC";
				ResultSet rsScores = stmt1.executeQuery(sqlQueryString);
				buf.append("<B>" + subject + " Homework</B>");
				buf.append("<TABLE CELLSPACING=5><TR><TH>Graded</TH><TH>Topic</TH>"
						+ "<TH>QuestionID</TH><TH>Response</TH><TH>Score</TH></TR>");
				while (rsScores.next()) {
					buf.append("<TR><TD ALIGN=CENTER>" + rsScores.getTimestamp("Graded") + "</TD>" 
							+ "<TD ALIGN=CENTER>" + rsScores.getString("Title") + "</TD>"
							+ "<TD ALIGN=CENTER>" + rsScores.getString("QuestionID") + "</TD>"
							+ "<TD ALIGN=CENTER>" + CharHider.quot2html(rsScores.getString("Response")) + "</TD>"
							+ "<TD ALIGN=CENTER>" + rsScores.getString("Score") + " / " 
							+ rsScores.getString("PossibleScore") + "</TD></TR>");
				}
				buf.append("</TABLE>");
				rsScores.close();
				stmt1.close();
			} catch (Exception e) {
				return e.getMessage();
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return buf.toString();
		}

		String classScores(int userID,int groupID,String type) {
			StringBuffer buf = new StringBuffer();
			buf.append("<h2>" + type + " Scores</h2>");

			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(getTimeZone(groupID));
			buf.append(df.format(new Date()));

			Connection conn = null;
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				Statement stmt = conn.createStatement();

				ResultSet rsGroup = stmt.executeQuery("SELECT InstructorID,Description "
						+ "FROM Groups WHERE GroupID='" + groupID + "'");
				rsGroup.next();
				buf.append("<br>Group: " + new User().getFullName(rsGroup.getInt("InstructorID")) 
						+ " - " + rsGroup.getString("Description"));
				rsGroup.close();

				// link to save as comma separated values (CSV) file:
				buf.append("<br><a href=Scores?Type=" + type + "&GroupID=" + groupID + "&Format=CSV>"
						+ "Save this page as a comma separated variables (.csv) file</a>");

				// first make a table of assignments for this group
				buf.append("<p><b>" + type + " Assignments for this Group</b><br>");
				String sql = "SELECT t1.TopicID,Title,Deadline FROM " + type + "Deadlines AS t1 "
				+ "LEFT JOIN AssignmentInfo AS t2 ON t1.TopicID=t2.TopicID "
				+ "WHERE GroupID='" + groupID + "' ORDER BY Deadline,t1.TopicID";
				ResultSet rsAssignments = stmt.executeQuery(sql);
				Vector<Integer> vecTopicIDs = new Vector<Integer>();
				buf.append("<TABLE BORDER=1 CELLSPACING=0>"
						+ "<TR><TH>#</TH><TH ALIGN=LEFT>Topic</TH><TH ALIGN=LEFT>Deadline</TH></TR>");
				int numberOfAssignments = 0;
				while (rsAssignments.next()) {
					numberOfAssignments++;
					buf.append("<TR><TD><b>" + (type.equals("Quiz")?"Q":"HW") + numberOfAssignments + "</b></TD>"
							+ "<TD>" + rsAssignments.getString("Title") + "</TD>"
							+ "<TD>" + rsAssignments.getString("Deadline") + "</TD></TR>");
					vecTopicIDs.add(new Integer(rsAssignments.getInt("TopicID")));
				}
				buf.append("</TABLE>");
				Integer[] topicIDs = new Integer[vecTopicIDs.size()];
				topicIDs = vecTopicIDs.toArray(topicIDs);
				rsAssignments.close();

				// count the number of students in this group:
				sql = "SELECT COUNT(*) AS N FROM GroupMembers LEFT JOIN Users USING (UserID) WHERE GroupID=" + groupID;
				ResultSet rsNStudents = stmt.executeQuery(sql);
				rsNStudents.next();
				int nStudents = rsNStudents.getInt("N");
				rsNStudents.close();

				Date start = new Date();

				// next make a table of student scores
				if (type.equals("Quiz")) {
					sql = "SELECT t1.UserID,CONCAT(LastName,', ',FirstName) AS Name,Email,t1.TopicID,Deadline,Score "
						+ "FROM (SELECT UserID,TopicID,Deadline FROM GroupMembers AS t1 "
						+ "LEFT JOIN QuizDeadlines AS t2 ON t1.GroupID=t2.GroupID WHERE t1.GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN (select UserID,t1.TopicID,MAX(Score) AS Score FROM QuizDeadlines AS t1 "
						+ "LEFT JOIN QuizTransactionsG" + groupID + " AS t2 ON t1.TopicID=t2.TopicID WHERE Downloaded<Deadline "
						+ "AND GroupID='" + groupID + "' GROUP BY UserID,t1.TopicID) AS t2 "
						+ "USING (UserID,TopicID) LEFT JOIN Users AS t3 ON t1.UserID=t3.UserID "
						+ "ORDER BY Name,t1.UserID,Deadline,t1.TopicID";
				}
				else if (type.equals("Homework")) {
					sql = "SELECT t1.UserID,CONCAT(LastName,', ',FirstName) AS Name,Email,t1.TopicID,Deadline,Score FROM "
						+ "(SELECT UserID,TopicID,Deadline FROM GroupMembers AS t1 "
						+ "LEFT JOIN HomeworkDeadlines AS t2 ON t1.GroupID=t2.GroupID WHERE t1.GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN (SELECT UserID,TopicID,SUM(QuestionScore) AS Score FROM "
						+ "(SELECT UserID,t1.TopicID,t2.QuestionID,MAX(Score) AS QuestionScore FROM "
						+ "(SELECT TopicID,Deadline FROM HomeworkDeadlines WHERE GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN HomeworkTransactionsG" + groupID + " AS t2 ON t1.TopicID=t2.TopicID "
						+ "LEFT JOIN (SELECT TopicID,QuestionID FROM HomeworkAssignments WHERE GroupID='" + groupID + "') AS tC "
						+ "ON t2.QuestionID=tC.QuestionID "
						+ "WHERE Graded<Deadline AND tC.QuestionID IS NOT NULL GROUP BY UserID,t2.QuestionID) AS t2Inner "
						+ "GROUP BY UserID,TopicID) AS t2 USING (UserID,TopicID) LEFT JOIN Users AS t3 ON t1.UserID=t3.UserID "
						+ "ORDER BY Name,t1.UserID,Deadline,t1.TopicID";
				}
				ResultSet rsScores = stmt.executeQuery(sql);
				boolean finished = true;
				if (rsScores.next()) finished=false;
				buf.append("<p><b>Scores for this Group (" + nStudents + " users)</b><br>");
				buf.append("<TABLE BORDER=1 CELLSPACING=0>"
						+ "<TR ALIGN=LEFT><TH></TH><TH>Name</TH>");
				for (int i=1;i<=numberOfAssignments;i++) // add a header for each assigned quiz or homework
					buf.append("<TH>" + (type.equals("Quiz")?"Q":"HW") + i + "</TH>");
				buf.append("</TR>");
				nStudents = 0;
				int[] nScores = new int[numberOfAssignments];
				int[] sumScores = new int[numberOfAssignments];

				while (!finished) {  // more students in ResultSet
					nStudents++;
					int studentID = rsScores.getInt("UserID");
					buf.append("<TR><TD>" + nStudents + ".</TD><TD><A href=mailto:" + rsScores.getString("Email") + ">" 
							+ rsScores.getString("Name") + "</A></TD>");
					for (int i=0;i<numberOfAssignments;i++) {
						buf.append("<TD ALIGN=CENTER>");
						if (!finished && topicIDs[i].intValue()==rsScores.getInt("TopicID") && rsScores.getInt("UserID")==studentID) {
							String score = rsScores.getString("Score");
							if (score==null) buf.append("&nbsp");
							else {
								buf.append(score);
								nScores[i]++;
								try {
									sumScores[i] += Integer.parseInt(score);
								}
								catch (Exception e2) {
								}
							}
							if (!rsScores.next()) finished=true;
						} else buf.append("err");
						buf.append("</TD>");
					}
					buf.append("</TR>"); // finish the row of scores for this student
					if (!finished && rsScores.getInt("UserID")==studentID) { // no scores for this student; proceed
						if (!rsScores.next()) finished=true;
					}
				}
				// print a row of average scores
				buf.append("<TR><TD></TD><TD>Avg Score</TD>");
				for (int i=0;i<numberOfAssignments;i++) {
					if (nScores[i] == 0) buf.append("<TD>&nbsp;</TD>");
					else buf.append("<TD ALIGN=CENTER>" + Math.round(10.0*sumScores[i]/nScores[i])/10.0  + "</TD>");
				}
				buf.append("</TR>");
				buf.append("</TABLE>"); // finish the table of scores
				buf.append("Elapsed time = " + (new Date().getTime()-start.getTime())/1000. + " seconds");
				rsScores.close();
				stmt.close();
			} catch (Exception e) {
				return buf.toString() + "<HR>" + e.getMessage();
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return buf.toString();
		}

		String classScoresCSV(int userID,int groupID,String type) {
			StringBuffer buf = new StringBuffer();
			buf.append(type + " Scores\n");

			DateFormat df = DateFormat.getDateTimeInstance();
			df.setTimeZone(getTimeZone(groupID));
			buf.append(df.format(new Date()));
			Connection conn = null;
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				Statement stmt = conn.createStatement();

				ResultSet rsGroup = stmt.executeQuery("SELECT InstructorID,Description "
						+ "FROM Groups WHERE GroupID='" + groupID + "'");
				rsGroup.next();
				buf.append("\"Group: " + new User().getFullName(rsGroup.getInt("InstructorID")) 
						+ " - " + rsGroup.getString("Description") + "\"\n\n");
				rsGroup.close();

				// first make a table of assignments for this group
				buf.append(type + " Assignments for this Group\n");
				String sql = "SELECT t1.TopicID,Title,Deadline FROM " + type + "Deadlines AS t1 "
				+ "LEFT JOIN AssignmentInfo AS t2 ON t1.TopicID=t2.TopicID "
				+ "WHERE GroupID='" + groupID + "' ORDER BY Deadline,t1.TopicID";
				ResultSet rsAssignments = stmt.executeQuery(sql);
				Vector<Integer> vecTopicIDs = new Vector<Integer>();
				buf.append("#,Topic,Deadline\n");
				int numberOfAssignments = 0;
				while (rsAssignments.next()) {
					numberOfAssignments++;
					buf.append((type.equals("Quiz")?"Q":"HW") + numberOfAssignments + ","
							+ "\"" + rsAssignments.getString("Title") + "\",\"" + rsAssignments.getString("Deadline") + "\"\n");
					vecTopicIDs.add(new Integer(rsAssignments.getInt("TopicID")));
				}
				Integer[] topicIDs = new Integer[vecTopicIDs.size()];
				topicIDs = vecTopicIDs.toArray(topicIDs);
				rsAssignments.close();

				// next make a table of student scores
				if (type.equals("Quiz")) {
					sql = "SELECT t1.UserID,CONCAT(LastName,', ',FirstName) AS Name,Email,t1.TopicID,Deadline,Score "
						+ "FROM (SELECT UserID,TopicID,Deadline FROM GroupMembers AS t1 "
						+ "LEFT JOIN QuizDeadlines AS t2 ON t1.GroupID=t2.GroupID WHERE t1.GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN (select UserID,t1.TopicID,MAX(Score) AS Score FROM QuizDeadlines AS t1 "
						+ "LEFT JOIN QuizTransactionsG" + groupID + " AS t2 ON t1.TopicID=t2.TopicID WHERE Downloaded<Deadline "
						+ "AND GroupID='" + groupID + "' GROUP BY UserID,t1.TopicID) AS t2 "
						+ "USING (UserID,TopicID) LEFT JOIN Users AS t3 ON t1.UserID=t3.UserID "
						+ "ORDER BY Name,t1.UserID,Deadline,t1.TopicID";
				}
				else if (type.equals("Homework")) {
					sql = "SELECT t1.UserID,CONCAT(LastName,', ',FirstName) AS Name,Email,t1.TopicID,Deadline,Score FROM "
						+ "(SELECT UserID,TopicID,Deadline FROM GroupMembers AS t1 "
						+ "LEFT JOIN HomeworkDeadlines AS t2 ON t1.GroupID=t2.GroupID WHERE t1.GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN (SELECT UserID,TopicID,SUM(QuestionScore) AS Score FROM "
						+ "(SELECT UserID,t1.TopicID,t2.QuestionID,MAX(Score) AS QuestionScore FROM "
						+ "(SELECT TopicID,Deadline FROM HomeworkDeadlines WHERE GroupID='" + groupID + "') AS t1 "
						+ "LEFT JOIN HomeworkTransactionsG" + groupID + " AS t2 ON t1.TopicID=t2.TopicID "
						+ "LEFT JOIN (SELECT TopicID,QuestionID FROM HomeworkAssignments WHERE GroupID='" + groupID + "') AS tC "
						+ "ON t2.QuestionID=tC.QuestionID "
						+ "WHERE Graded<Deadline AND tC.QuestionID IS NOT NULL GROUP BY UserID,t2.QuestionID) AS t2Inner "
						+ "GROUP BY UserID,TopicID) AS t2 USING (UserID,TopicID) LEFT JOIN Users AS t3 ON t1.UserID=t3.UserID "
						+ "ORDER BY Name,t1.UserID,Deadline,t1.TopicID";
				}
				ResultSet rsScores = stmt.executeQuery(sql);
				boolean finished = true;
				if (rsScores.next()) finished=false;
				buf.append("\nScores for this Group\n");
				buf.append("Name,");
				for (int i=1;i<=numberOfAssignments;i++) // add a header for each assigned quiz or homework
					buf.append((type.equals("Quiz")?"Q":"HW") + i + ",");
				buf.append("\n");
				while (!finished) {  // more students in ResultSet
					int studentID = rsScores.getInt("UserID");
					buf.append("\"" + rsScores.getString("Name") + "\",");
					for (int i=0;i<numberOfAssignments;i++) {
						if (!finished && topicIDs[i].intValue()==rsScores.getInt("TopicID") && rsScores.getInt("UserID")==studentID) {
							String score = rsScores.getString("Score");
							if (score==null) buf.append("");
							else buf.append(score);
							if (!rsScores.next()) finished=true;
						} else buf.append("err");
						buf.append(",");
					}
					buf.append("\n"); // finish the row of scores for this student
					if (!finished && rsScores.getInt("UserID")==studentID) { // no scores for this student; proceed
						if (!rsScores.next()) finished=true;
					}
				}
				rsScores.close();
				stmt.close();
			} catch (Exception e) {
				return buf.toString() + "\nThere was an unexpected error: ,\"" + e.getMessage() + "\"\n";
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return buf.toString();
		}

		TimeZone getTimeZone(int groupID) {
			TimeZone groupTimeZone = null;
			Connection conn = null;
			try {
				if (_ds == null) assemble();
				conn = _ds.getConnection();
				Statement stmt = conn.createStatement();
				String sql = "SELECT TimeZone FROM Groups WHERE GroupID='" + groupID + "'";
				ResultSet rsTimeZone = stmt.executeQuery(sql);
				rsTimeZone.next();
				try {
					groupTimeZone = TimeZone.getTimeZone(rsTimeZone.getString("TimeZone"));
				}
				catch (Exception e2) {
					groupTimeZone = TimeZone.getDefault();
				}
				rsTimeZone.close();
				stmt.close();
			}
			catch (Exception e) {
			}
			finally {
				try {
					conn.close();
				}
				catch (Exception e) {
				}
			}
			return groupTimeZone;
		}

		TimeZone getTimeZone(int groupID,Connection conn) {
			TimeZone groupTimeZone = null;
			try {
				Statement stmt = conn.createStatement();
				String sql = "SELECT TimeZone FROM Groups WHERE GroupID='" + groupID + "'";
				ResultSet rsTimeZone = stmt.executeQuery(sql);
				rsTimeZone.next();
				groupTimeZone = TimeZone.getTimeZone(rsTimeZone.getString("TimeZone"));
				rsTimeZone.close();
				stmt.close();
			}
			catch (Exception e) {
				groupTimeZone = TimeZone.getDefault();
			}
			return groupTimeZone;
		}

	}
 */
