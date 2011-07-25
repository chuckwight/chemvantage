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
import java.util.Date;
import java.util.Properties;

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


public class Feedback extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet is used by contributors to suggest new and revised Quiz and Homework questions.";
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

			if (userRequest.equals("ReportAProblem")) {
				long questionId = Long.parseLong(request.getParameter("QuestionId"));
				String notes = request.getParameter("Notes");
				if (notes.length()>0) ofy.put(new UserReport(user.id,questionId,notes));
			} else if (userRequest.equals("AjaxRating")) {
				recordAjaxRating(request);
			} else out.println(Home.getHeader(user) 
					+ feedbackForm(user) 
					+ (user.isAdministrator()?viewUserFeedback():"")
					+ Home.footer);    
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

			if (userRequest.equals("SubmitFeedback")) {
				out.println(Home.getHeader(user) + submitFeedback(user,request) + Home.footer);
				sendEmailToAdmin();
			} else if (user.isAdministrator() && userRequest.equals("Delete Report")) {
				removeReport(request);
				doGet(request,response);	
			} else if (user.isAdministrator() && userRequest.equals("Reply")) {
				out.println(Home.getHeader(user) + replyForm(user,request) + Home.footer);
			} else if (user.isAdministrator() && userRequest.equals("Send Reply")) {
				String result = sendReplyToUser(request);
				if (result.isEmpty()) doGet(request,response);
				else out.println(Home.getHeader(user) + result + Home.footer);
			}
		} catch (Exception e) {
		}
	}

	public String recordAjaxRating(HttpServletRequest request) {
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("NStars"));
			subject.addStarReport(stars);
		}
		catch (Exception e) {
			return e.toString();
		}
		return "Your rating was " + stars + " stars. The average user rating is " + subject.getAvgStars() + " stars.";
	}

	String feedbackForm(User user) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<h2>Feedback Page</h2>");
		buf.append("<b>" + user.getBothNames() + "</b><br />"
				+ new Date().toString() + "<p>");
		
		if (user.verifiedEmail) {
			buf.append("Your comments and opinions are important to us.  We use this<br>"
					+ "information to improve the functionality of the site for our users.<p>"
					+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><p><hr>");

			buf.append("<script type='text/javascript'>\n"
					+ "<!--\n"
					+ "var star1 = new Image(); star1.src='images/star1.gif';\n"
					+ "var star2 = new Image(); star2.src='images/star2.gif';\n"
					+ "var set = false;\n"
					+ "function showStars(n) {"
					+ "  if (!set) {"
					+ "    document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));"
					+ "    for (i=1;i<6;i++) document.getElementById(i).src=(i<=n?star2.src:star1.src);"
					+ "  }"
					+ "}\n"
					+ "function setStars(n) {"
					+ "  set = (n>0?true:false);"
					+ "  document.FeedbackForm.Stars.value = n;"
					+ "}\n"
					+ "// -->\n"
					+ "</script>\n");

			buf.append("Please rate your overall experience with ChemVantage:\n");

			buf.append("<div id='vote' style='color:red;'>(click a star):</div>\n");
			for (int istar=1;istar<6;istar++) {
				buf.append("<img src='images/star1.gif' id='" + istar + "' style='width:30px; height:30px; float:left;'"
						+ "onmouseover=showStars(this.id) onClick=setStars(this.id) onmouseout=showStars(0) />");
			}
			buf.append("<br clear='all'><FONT SIZE=-1>(" + subject.nStarReports + " user ratings; avg = " + subject.getAvgStars() + " stars)</FONT><p>\n");

			buf.append("<FORM NAME=FeedbackForm METHOD=POST>\n"
					+ "<div id='count'>Comments or kudos: <FONT SIZE=-1>(160 characters max.)</FONT></div>"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback>"
					+ "<INPUT TYPE=HIDDEN NAME=Stars>"
					+ "<INPUT TYPE=HIDDEN NAME=Save VALUE=Yes>"
					+ "<TEXTAREA NAME=Comments ROWS=5 COLS=40 WRAP=SOFT "
					+ "onKeyUp=\"javascript: "
					+ "document.FeedbackForm.Comments.value=document.FeedbackForm.Comments.value.substring(0,160);"
					+ "document.getElementById('count').innerHTML='Comments or kudos: <FONT SIZE=-1 COLOR=RED>"
					+ "('+(160-document.FeedbackForm.Comments.value.length)+' characters remaining)</FONT>';"
					+ "\">"
					+ "</TEXTAREA><br>");

			buf.append("<INPUT TYPE=SUBMIT VALUE='Submit Feedback'>"
					+ "<INPUT TYPE=RESET VALUE='Clear Form' "
					+ "onClick=\"javascript: document.FeedbackForm.Stars.value='';"
					+ "setStars(0);"
					+ "for (i=1;i<6;i++) document.getElementById(i).src=star1.src;"
					+ "document.getElementById('vote').innerHTML='(click a star):';"
					+ "document.getElementById('count').innerHTML="
					+ "'Comments or kudos: <FONT SIZE=-1>(160 characters max.)</FONT>';"
					+ "\">"
					+ "</FORM>");
		} else {
			buf.append("Sorry, the feedback form is not accessible to you because your email<br>"
					+ "address has not been verified by ChemVantage. Please send your<br>"
					+ "comments via email to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><p>");
					
			buf.append("<FORM ACTION=Verification METHOD=POST>"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Verify My Email Address'><p>"
					+ "</FORM>");
		}
		return buf.toString(); 
	}

	String submitFeedback(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("Stars"));
			if (request.getParameter("Save").equals("Yes")) {
				subject.addStarReport(stars);
			}
		} catch (Exception e) {
		}
		String comments = request.getParameter("Comments");
		if (stars == 0 && comments.length() == 0) return feedbackForm(user);
		
		if (comments.length() > 0) {
			UserReport r = new UserReport(user.id,stars,comments);
			ofy.put(r);
		}

		buf.append("<h2>Feedback Page</h2>");
		buf.append("<b>" + user.getBothNames() + "</b><br />");
		buf.append(new Date().toString() + "<p>");
		buf.append("Thank you for your feedback" + (stars>0?" (" + stars + " stars" + (stars==5?"!":"") + ").":"."));
		if (stars > 0) buf.append("<br>The average user rating for ChemVantage is " + subject.getAvgStars() + " stars.");
		if (comments.length() > 0) {
			buf.append("<br>If your comment requested a response, it will be sent to you at " + user.email + "<p>");
		}
		buf.append("<p><a href=Home>Return to the Home Page</a><br>");
		return buf.toString();
	}

	String viewUserFeedback() {
		StringBuffer buf = new StringBuffer();

		Query<UserReport> reports = ofy.query(UserReport.class).order("-submitted");
		if (reports.count()>0) buf.append("<hr><h3>User Feedback</h3>");
		for (UserReport r : reports) {
			buf.append(r.adminView());
		}
		return buf.toString();
	}
	
	private void removeReport(HttpServletRequest request) {
		long reportId = Long.parseLong(request.getParameter("ReportId"));
		ofy.delete(UserReport.class,reportId);
	}
	
	private void sendEmailToAdmin() {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = ofy.query(UserReport.class).order("-submitted").limit(1).get().adminView();
		
		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,
					new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject("ChemVantage Feedback Report");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
		}
	}
	
	String replyForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>Reply to User Feedback</h2>");
			long reportId = Long.parseLong(request.getParameter("ReportId"));
			UserReport report = ofy.get(UserReport.class,reportId);
			buf.append(report.adminView() + "<hr>");
			buf.append("<FORM ACTION=Feedback METHOD=POST>");
			buf.append("<TEXTAREA NAME=MessageText ROWS=25 COLS=60 WRAP=SOFT>");
			buf.append("Thank you for your feedback to ChemVantage. We value your comments and we use "
					+ "your feedback to improve the functionality of the site for our users.<p>\n\n"
					+ "<p>\n\nadmin@chemvantage.org<p>\n\n--<br>\n");
			buf.append(report.replyView());
			buf.append("</TEXTAREA><br>");
			buf.append("<INPUT TYPE=HIDDEN NAME=ReportId VALUE=" + reportId + ">" 
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send Reply'></FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String sendReplyToUser(HttpServletRequest request) {
		// send a response to a user feedback report
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = request.getParameter("MessageText");
		if (msgBody.isEmpty()) return "Message body was empty.";
		try {
			long reportId = Long.parseLong(request.getParameter("ReportId"));
			UserReport report = ofy.get(UserReport.class,reportId);
			User recipient = ofy.get(User.class,report.userId);
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,
					new InternetAddress(recipient.email,recipient.getBothNames()));
			msg.setSubject("ChemVantage Feedback Report");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
			return e.toString();
		}
		return "";
	}
}

