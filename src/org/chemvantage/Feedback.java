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
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;


@WebServlet("/Feedback")
public class Feedback extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	
	public String getServletInfo() {
		return "This servlet is used by contributors to suggest new and revised Quiz and Homework questions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("Token"));
			if (user == null) {
				user = new User("anonymous"+new Random().nextInt());
				user.setToken();				
			}
		
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("ReportAProblem")) {
				String userId = user==null?"":user.id;
				long questionId = Long.parseLong(request.getParameter("QuestionId"));
				String notes = request.getParameter("Notes");
				UserReport r = new UserReport(userId,questionId,notes);
				ofy().save().entity(r);
				sendEmailToAdmin(r,new User(userId),null);
			} else if (userRequest.equals("AjaxRating")) {
				recordAjaxRating(request);
			} else out.println(Home.header + feedbackForm(user) + Home.footer);    
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("Token"));
			if (user == null) throw new Exception();
		
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("SubmitFeedback")) {
				out.println(Home.header + submitFeedback(user,request) + Home.footer);
			} else if (userRequest.equals("Delete Report")) {
				ofy().delete().key(Key.create(UserReport.class,Long.parseLong(request.getParameter("ReportId")))).now();
				out.println(Home.header + feedbackForm(user) + Home.footer);
			} else 	out.println(Home.header + feedbackForm(user) + Home.footer);			
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

		buf.append("<FORM NAME=FeedbackForm ACTION=Feedback METHOD=POST>\n"
				+ "Comments or kudos: <FONT SIZE=-1>(160 characters max.)</FONT><br>"
				+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback>"
				+ "<INPUT TYPE=HIDDEN NAME=Stars>"
				+ "<INPUT TYPE=HIDDEN NAME=Token VALUE='" + user.token + "'>"
				+ "<TEXTAREA NAME=Comments ROWS=5 COLS=60 WRAP=SOFT "				
				+ "onKeyUp=javascript:{document.FeedbackForm.Comments.value=document.FeedbackForm.Comments.value.substring(0,160);document.getElementById('cbox').style.visibility='visible';}>"
				+ "</TEXTAREA><br>");

		buf.append("<label id=cbox style='visibility:hidden'>Email: <input type=text size=30 name=Email> (optional)</label><p>");
		
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Submit Feedback'>"
				+ "<INPUT TYPE=RESET VALUE='Clear Form' "
				+ "onClick=\"javascript: document.FeedbackForm.Stars.value='';"
				+ "setStars(0);"
				+ "for (i=1;i<6;i++) document.getElementById(i).src=star1.src;"
				+ "document.getElementById('vote').innerHTML='(click a star):';"
				+ "document.getElementById('cbox').style.visibility='hidden';"
				+ "\">"
				+ "</FORM>");
		
		String reports = viewUserFeedback(user);
		if (!reports.isEmpty()) buf.append("<hr><h3>" + (user.isChemVantageAdmin()?"User":"Your") + " Feedback</h3>" + reports);
		
		return buf.toString(); 
	}

	String submitFeedback(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("Stars"));
			if (stars>0) subject.addStarReport(stars);
		} catch (Exception e) {
		}
		String comments = request.getParameter("Comments");
		if (stars == 0 && (comments == null || comments.isEmpty())) return feedbackForm(user);   //(user==null?anonymousFeedbackForm():feedbackForm(user));
		
		String userId = user==null?null:user.id;
		String email = request.getParameter("Email");
		try {
			String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w]+\\.)+[\\w]+[\\w]$";
			if (!email.matches(regex)) email = null;
		} catch (Exception e) {
			email = null;
		}
	
		if (comments.length() > 0) {
			UserReport r = new UserReport(userId,stars,comments);
			ofy().save().entity(r);
			sendEmailToAdmin(r,user,email);
		}

		buf.append("<h2>Feedback Page</h2>");
		buf.append(new Date().toString() + "<p>");
		buf.append("Thank you for your feedback" + (stars>0?" (" + stars + " stars" + (stars==5?"!":"") + ").":"."));
		if (stars > 0) buf.append("<br>The average user rating for ChemVantage is " + subject.getAvgStars() + " stars (" + subject.nStarReports + " user ratings).<p>");
		if (comments.length() > 0) {
			buf.append("Your comment: <font color=red>" + comments + "</font><p>");
		
			if (email==null) buf.append("We will review your comment, but you will not receive a response because you did not provide an email address.<p>");
			else buf.append("We will review your comment. Any response will be sent to " + email + ".<p>");
		}
	
		if (user.isAnonymous()) buf.append("<p><a href=Home>Return to the Home page</a><br>");
		return buf.toString();
	}

	String viewUserFeedback(User user) {
		StringBuffer buf = new StringBuffer();
		Query<UserReport> reports = ofy().load().type(UserReport.class).order("-submitted");		
		for (UserReport r : reports) {
			String report = r.view(user);  // returns report only for ChemVantage admins, domainAdmins and report author
			if (report != null) buf.append(report + "<hr>");
		}
		return buf.toString();
	}
	
	private void sendEmailToAdmin(UserReport r,User user,String email) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = r.view(user);
		if (email != null) msgBody += "Respond to " + email;
		
		if (msgBody.length()==0) return;  // no reports exist
		
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
/*	
	String replyForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		//String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		try {
			buf.append("<h2>Reply to User Feedback</h2>");
			long reportId = Long.parseLong(request.getParameter("ReportId"));
			UserReport report = ofy().load().type(UserReport.class).id(reportId).safe();
			buf.append(report.adminView(user) + "<hr>");
			buf.append("<FORM ACTION=Feedback METHOD=POST>");
			buf.append("<TEXTAREA NAME=MessageText ROWS=25 COLS=60 WRAP=SOFT>");
			buf.append("Thank you for your feedback to ChemVantage. We value your comments and we use "
					+ "your feedback to improve the functionality of the site for our users.<p>\n\n"
					+ "<p>\n\nadmin@chemvantage.org<p>\n\n--<br>\n");
			buf.append(report.view());
			buf.append("</TEXTAREA><br>");
			buf.append("<INPUT TYPE=HIDDEN NAME=ReportId VALUE=" + reportId + ">"
					//+ (cvsToken==null?"":"<INPUT TYPE=HIDDEN NAME=CvsToken VALUE=" + cvsToken + ">")
					+ "<INPUT TYPE=HIDDEN NAME=Token VALUE=" + user.token + ">"
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Send Reply'></FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	*/
}

