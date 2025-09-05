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

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.cmd.Query;


@WebServlet("/Feedback")
public class Feedback extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet is used by users to submit comments, questions or requests.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user == null) user = new User();  // creates new anonymous User
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			case "ReportAProblem":
				String userId = user==null?"":user.getId();
				long questionId = Long.parseLong(request.getParameter("QuestionId"));
				int[] params = {0,0,0,0};
				try {
					String[] sparams = request.getParameter("Params").replace("[","").replace("]","").replaceAll("\\s", "").split(",");
					for (int i=0;i<sparams.length;i++) params[i]=Integer.parseInt(sparams[i]);
				} catch (Exception e) {}
				String notes = request.getParameter("Notes");
				String email = request.getParameter("Email");
				String studentAnswer = request.getParameter("StudentAnswer");
				UserReport r = new UserReport(userId,questionId,params,studentAnswer,notes);
				ofy().save().entity(r);
				sendEmailToAdmin(r,user,email);
				break;
			case "AjaxRating":
				recordAjaxRating(request);
				break;
			default:
				out.println((user.isChemVantageAdmin()?Subject.getHeader(user):Subject.header("ChemVantage Feedback Form")) + feedbackForm(user) + Subject.footer);
			}
		} catch (Exception e) {
			response.getWriter().println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) user = new User(); // anonymous user
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			out.println(user.isChemVantageAdmin()?Subject.getHeader(user):Subject.header("ChemVantage Feedback"));
			switch (userRequest) {
			case "SubmitFeedback":
				out.println(submitFeedback(user,request));
				break;
			case "Delete Report":
				ofy().delete().key(key(UserReport.class,Long.parseLong(request.getParameter("ReportId")))).now();
			default:
				out.println(feedbackForm(user));
			}
			out.println(Subject.footer);
		} catch (Exception e) {
			response.getWriter().println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public String recordAjaxRating(HttpServletRequest request) {
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("NStars"));
			Subject.addStarReport(stars);
		}
		catch (Exception e) {
			return e.toString();
		}
		return "Your rating was " + stars + " stars. The average user rating is " + Subject.getAvgStars() + " stars.";
	}

	String feedbackForm(User user) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h1>ChemVantage Feedback Page</h1>");

		buf.append("Your comments and opinions are important to us.  We use this<br>"
				+ "information to improve the functionality of the site for our users.<p>");

		buf.append("Please rate your overall experience with ChemVantage:<br/>");

		buf.append("<span id='vote' style='color:red;'>(click a star):</span><br/>");
		for (int istar=1;istar<6;istar++) {
			buf.append("<img src='images/star1.gif' id='" + istar + "' style='width:30px; height:30px;' "        // properties
					+ "onmouseover=showStars(this.id) onmouseout=showStars('0') onClick=\"set=false;showStars(this.id);setFeedbackStars(this.id)\"; />" ); // mouse actions
		}
		buf.append("&nbsp;&nbsp;&nbsp;&nbsp;<input type=range min=1 max=5 style='opacity:0' onfocus=this.style='opacity:1' oninput=\"set=false;showStars(this.value);setFeedbackStars(this.value);\">");
		buf.append("<br clear='all'>");
		buf.append("<FONT SIZE=-1>(" + Subject.getNStarReports() + " user ratings; avg = " + Subject.getAvgStars() + " stars)</FONT><p>\n");

		if (user.isAnonymous()) buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");				
		
		String[] member = getMember(user);  // role, name, email
		String email = member!=null && member[2]!=null?member[2]:null;
		
		buf.append("<FORM NAME=FeedbackForm id=FeedbackForm ACTION=Feedback METHOD=POST>\n"
				+ "Comments, suggestions or requests: <FONT SIZE=-1>(160 characters max.)</FONT><br>"
				+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback />"
				+ "<INPUT TYPE=HIDDEN NAME=Stars />"
				+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />"
				+ "<TEXTAREA NAME=Comments ROWS=5 COLS=60 WRAP=SOFT "				
				+ "onKeyUp=javascript:{document.FeedbackForm.Comments.value=document.FeedbackForm.Comments.value.substring(0,160);document.getElementById('cbox').style.visibility='visible';}>"
				+ "</TEXTAREA><br>");

		if (email==null || email.isEmpty()) buf.append("<label id=cbox style='visibility:hidden'>Email: <input type=text size=50 placeholder=' optional, if you want a response to your comment' name=Email></label><p>");
		else buf.append("<span id=cbox style='visibility:hidden'>Our response will be sent to you at " + email + "<br/></span>"
				+ "<input type=hidden name=Email value=" + email + " />");
		
		// If the user is anonymous, insert the Google reCaptcha tool (version 2) on the page
		if (user.isAnonymous()) buf.append("<div class='g-recaptcha' data-sitekey='" + Subject.getReCaptchaSiteKey() + "'></div><p>");				
		
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Submit Feedback'>"
				+ "<INPUT TYPE=RESET VALUE='Clear Form' "
				+ "onClick=\"javascript: document.FeedbackForm.Stars.value='';"
				+ "setStars(0);"
				+ "for (i=1;i<6;i++) document.getElementById(i).src=star1.src;"
				+ "document.getElementById('vote').innerHTML='(click a star):';"
				+ "document.getElementById('cbox').style.visibility='hidden';"
				+ "\">"
				+ "</FORM>");
		
		if (user.isChemVantageAdmin()) {
			String reports = viewUserFeedback(user);
			if (!reports.isEmpty()) buf.append("<hr><h3>User Feedback</h3>" + reports);
		}
		return buf.toString(); 
	}

	String[] getMember(User user) {
		String[] member = null;
		try {
			if (user.isAnonymous()) throw new Exception();
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			String userId = user.getId();  // includes platform_id
			member = membership.get(userId.substring(userId.lastIndexOf("/")+1));  // LMS userId
		} catch (Exception e) {}
		return member;
	}
	
	String submitFeedback(User user,HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer();
		
		try { 
			if (!request.getServerName().equals("localhost") && user.isAnonymous() && !reCaptchaOK(request)) throw new Exception();
		} catch (Exception e) {
			return "<h1>Submission Failed</h1>"
					+ "The ReCAPTCHA was not validated, sorry. "
					+ "Please click the BACK button on your browser and try again.";
		}
		
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("Stars"));
			if (stars>0) Subject.addStarReport(stars);
		} catch (Exception e) {}
		
		String comments = request.getParameter("Comments");
		if (stars==0 && (comments == null || comments.isEmpty())) {
			return feedbackForm(user);
		}
		
		String userId = user==null?null:user.getId();
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
			if (!user.isAnonymous()) sendEmailToAdmin(r,user,email);
		}

		buf.append("<h1>ChemVantage Feedback Page</h1>");
		buf.append(new Date().toString() + "<p>");
		buf.append("Thank you for your feedback" + (stars>0?" (" + stars + " stars)":"") + ". ");
		
		if (comments.length() > 0) {
			buf.append("Your comment was: <p><font color=red>" + comments + "</font><p>");
		
			if (email==null) buf.append("We will review your comment, but we're unable to provide a response because you did not provide a valid email address.<p>");
			else buf.append("We will review your comment. Any response will be sent to " + email + ".<p>");
			buf.append("Feel free to email any additional comments to us at <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><p>");
		}
		
		if (user.isAnonymous()) buf.append("<p><a href=Home>Return to the Home page</a><br>");
		return buf.toString();
	}

	boolean reCaptchaOK(HttpServletRequest request) throws Exception {
		OutputStreamWriter writer = null;
		BufferedReader reader = null;
		JsonObject captchaResponse = null;
		try {
			String queryString = "secret=" + Subject.getReCaptchaSecret() + "&response=" 
					+ request.getParameter("g-recaptcha-response") + "&remoteip=" + request.getRemoteAddr();
			URL u = new URI("https://www.google.com/recaptcha/api/siteverify").toURL();
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Content-Length", String.valueOf(queryString.length()));

			writer = new OutputStreamWriter(uc.getOutputStream());
			writer.write(queryString);
			writer.flush();
			writer.close();

			// read & interpret the JSON response from Google
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			captchaResponse = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();
		} catch (Exception e) {
		} finally {
			writer.close();
			reader.close();
		}
		return captchaResponse.get("success").getAsBoolean();
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
		String msgBody = r.view();
		String name = "";
		
		try {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			Map<String,String[]> members = LTIMessage.getMembership(a);
			String userId = user.getId();
			String raw_id = userId.substring(userId.lastIndexOf("/")+1);  // userId according to the platform
			String[] member = members.get(raw_id);
			if (member[1]!=null && !member[1].isEmpty()) name=member[1];
			if (member[2]!=null && !member[2].isEmpty()) email = member[2];
		} catch (Exception e) {}

		if (email==null || email.isEmpty()) return;  // nowhere to send
		else msgBody += "<p>Respond to" + (user.isInstructor()?" instructor: ":": ") + name + " &lt;" + email + "&gt;";
			
		if (msgBody.length()==0) return;  // no reports exist
		
		try {
		Utilities.sendEmail("ChemVantage","admin@chemvantage.org","ChemVantage Feedback Report",msgBody);
		} catch (Exception e) {}
	}
}

