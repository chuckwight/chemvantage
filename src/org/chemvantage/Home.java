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
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns={"/","/Home"})
public class Home extends HttpServlet {

	private static final long serialVersionUID = 137L;
	String servername;
	Subject subject = Subject.getSubject();	
	List<Video> videos; 
	List<Topic> topics; 
	List<Text> texts; 
	
	public String getServletInfo() {
		return "Default servlet for user's home page.";
	}

	public static String maintenanceAnnouncement = "Due to a problem with the Google AppEngine servers, ChemVantage is temporarily unavailable.<br/>"
		+ "You can monitor the status Google AppEngine at <a href=http://code.google.com/status/appengine>http://code.google.com/status/appengine</a><br/>"
		+ "Please try again later. We apologize for the inconvenience. -ChemVantage";

	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		out.println(header() + homePage(request) + footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		doGet(request,response);
	}

	public static String header(String title) {
		String announcement = Subject.getSubject().announcement;
		return "<!DOCTYPE html>"
				+"<html>\n"
				+ "<head>\n"
				+ "<meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />"
				+ "<meta http-equiv='Pragma' content='no-cache' />"
				+ "<meta http-equiv='Expires' content='0' />"
				+ "<meta http-equiv='Content-type' content='text/html;charset=iso-8859-1' />"
				+ "<meta name='Description' content='An online quiz and homework site' />\n"
				+ "<meta namew='Keywords' content='chemistry,learning,online,quiz,homework,video,textbook,open,education' />"
				+ "<meta name='msapplication-config' content='none'/>\n"
				+ "<link rel='icon' type='image/png' href='/favicon.png' />\n"
				+ "<link rel='icon' type='image/vnd.microsoft.icon' href='/favicon.ico' />\n"
				+ "<title>" + (title==null || title.isEmpty()?"ChemVantage":title) + "</title>\n"
				+ "</head>\n"
				+ "<body bgcolor=#ffffff text=#000000 link=#0000cc vlink=#551a8b alink=#ff0000 topmargin=3 marginheight=3>\n"
				+ ((announcement==null || announcement.isEmpty())?"":"<FONT COLOR=RED>" + announcement + "</FONT><br/>\n");
	}
	
	public static String header() {
		return header("ChemVantage");
	}

	public static String footer = "\n<hr/><img src=/images/CVLogo_tiny.png alt='ChemVantage logo' style='vertical-align:middle' /> "
			+ "<a href=/About>About ChemVantage</a> | "
			+ "<a href=/About#terms>Terms and Conditions of Use</a> | "
			+ "<a href=/About#privacy>Privacy Policy</a> | "
			+ "<a href=/About#copyright>Copyright</a>\n"
			+ "</body>\n</html>";

	public static String banner = "<a href=https://www.chemvantage.org><img src=/images/CVLogo_thumb.png alt='ChemVantage Logo' align=left></a>\n"
			+ "Welcome to<br/><FONT SIZE=+3><b>ChemVantage - General Chemistry</b></FONT><br/>An Open Education Resource<br/><br/>";
	
	static String getHeader(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			String now = new Date().toString();

			buf.append("<!DOCTYPE html>"
					+"<html >"
					+ "<head>"
					+ "<meta HTTP-EQUIV='Content-type' CONTENT='text/html;charset=iso-8859-1'>"
					+ "<meta HTTP-EQUIV='Expires' CONTENT='" + now + "'>\n"
					+ "<meta HTTP-EQUIV='P3P' CONTENT='policyref=\"http://www.chemvantage.org/w3c/p3p.xml\",CP=\"CURa ADMa DEVa OUR IND DSP OTI COR\"'>\n"
					+ "<meta NAME='Description' CONTENT='An online quiz and homework site'>\n"
					+ "<meta NAME='Keywords' CONTENT='learning,online,quiz,homework,video,textbook,open,education'>\n"
					+ "<meta name='msapplication-config' content='none'/>"
					+ "<link rel='P3Pv1' href='/w3c/p3p.xml'>\n"
					+ "<title>ChemVantage</title>\n"
					+ "<style><!-- body,td,a,p,.h {font-family:arial,sans-serif}"
					+ "#pzon{float:left;font-weight:bold;height:22px;padding-left:2px}"
					+ "#phzl{border-top:1px solid#c9d7f1;font-size:0;height:0;position:absolute;right:0;top:24px;width:200%}"
					+ "#pzbg{background:#fff;border:1px solid;border-color:#c9d7f1 #36c #36c#a2bae7;font-size:13px;top:24px;z-index:1000}"
					+ "#puzr{padding-bottom:7px !important}"
					+ "#pzon,#puzr{font-size:13px;padding-top:1px!important}"
					+ ".pz1,.pz2{display:inline;height:22px;margin-right:1em;vertical-align:top}"
					+ "#pzbg,.pz3{display:none;position:absolute;width:7em}"
					+ ".pz3{z-index:1001}"
					+ "#pzon a,#pzon a:active,#pzon a:visited{color:#00c;font-weight:normal}"
					+ ".pz3 a,.pz2 a{text-decoration:none}"
					+ ".pz3 a{display:block;padding:.2em .5em}"
					+ "#pzon .pz3 a:hover{background:#36c;color:#fff}"
					+ "--> </style>\n"
					+ "</head>\n"
					+ "<body bgcolor=#ffffff text=#000000 link=#0000cc vlink=#551a8b alink=#ff0000 topmargin=3 marginheight=3>\n"
					+ "<div id=pzon>"
					+ " <div class=pz1>ChemVantage.org</div>"
					+ " <div class=pz1><a href=/Home>Home</a></div>"
					+ " <div class=pz1><a href=/About>About Us</a></div>"
					+ " <div class=pz1><a href='/Feedback?sig=" + user.getTokenSignature() + "'>Feedback</a></div>"
					+ " <div class=pz1><a href='/Contribute?sig=" + user.getTokenSignature() + "'>Authors</a></div>");
			if (user.isEditor()) buf.append("<div class=pz1><a href=/Edit>Editors</a></div>");
			if (user.isAdministrator()) buf.append("<div class=pz1><a href=/Admin>Admin</a></div><br/>");
			buf.append("<div class=pz1><a href=/Logout>Sign out</a></div>");
			
			//buf.append("</div>\n");

			buf.append("</div>&nbsp;");
			String announcement = Subject.getSubject().announcement;
			if (announcement==null) announcement = "";
			buf.append("<div style='color:red'><br/>" + announcement + "</div>");
		} catch (Exception e) {
			return e.toString();
		}
		return buf.toString();
	}
	
	String homePage(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {			
			buf.append(banner);
			
			// Create a table-like cell for the left side of the home page:
			buf.append("<div style='display:table'><div style='dispay:table-row'><div style='display:table-cell;vertical-align:top'>");
			
			buf.append("<p><h3><font color=red>Anonymous User</font></h3>");

			buf.append("Select a topic from the dropdown box below and then take a quiz<br/>"
					+ "or solve some homework problems on that topic. You can also test<br/>"
					+ "yourself by taking a 1 hour practice exam on any three (or more) topics.<p>"
					+ "If you are a chemistry teacher or professor, you can use<br/>"
					+ "ChemVantage in your class by using an LTI connection to return<br/>"
					+ "student scores to your class learning management system. Learn<br/>"
					+ "more about <a href='/lti/registration/'>how to connect using LTI</a>.<p>"
					+ "ChemVantage is always free to use. <a href=/About>Read more about us here</a>.<p>");
			
			// Add quiz/homework select box to the page
			buf.append("<div>");
			buf.append("<b>Quizzes and Homework Exercises</b>");
			buf.append("<div id=selectReminder style='display: none'>"
					+ "<b><FONT COLOR=RED>Please select a topic:</FONT></b><br/></div>");
			
			buf.append("<FORM NAME='HQSelectForm' ACTION=Quiz METHOD=GET>");
			
			User user = new User(); // anonymous User constructor
			
			buf.append("<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">");
			
			buf.append("<SELECT NAME='TopicId'><OPTION Value='0' SELECTED>Select a topic</OPTION>");
			
			if (topics == null) topics = ofy().load().type(Topic.class).order("orderBy").list();
			for (Topic t : topics) {
				if (!t.orderBy.equals("Hide"))
				buf.append("<OPTION VALUE='" + t.id + "'>" + t.title + "</OPTION>");
			}
			buf.append("</SELECT><br/>"
					+ "<INPUT TYPE=BUTTON VALUE='Take This Quiz' "
					+ "onClick=\"javascript: "
					+ "if(document.HQSelectForm.elements['TopicId'].value=='0'){document.getElementById('selectReminder').style.display='';} "
					+ "else {document.HQSelectForm.action='Quiz.jsp';document.HQSelectForm.submit();}\">" 
					+ "<INPUT TYPE=BUTTON VALUE='Homework Exercises' "
					+ "onClick=\"javascript: "
					+ "if(document.HQSelectForm.elements['TopicId'].value=='0'){document.getElementById('selectReminder').style.display='';} "
					+ "else {document.HQSelectForm.action='Homework';document.HQSelectForm.submit()}\">");
			buf.append("</FORM></div><p>");
			
			// Add a box for taking practice exams
			buf.append("<div>");
			buf.append("<b>Practice Exams</b>");
			buf.append("<FORM METHOD=GET ACTION=PracticeExam>");
			buf.append("<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">");
			buf.append("<INPUT TYPE=SUBMIT VALUE='Take A Practice Exam Now'>");
			buf.append("</FORM></div>");
			
			// Add text resources table to the page
			if (texts == null) texts = ofy().load().type(Text.class).list();
			buf.append("<div>");
			buf.append("<p><b>" + texts.size() + " Free Textbook Resources</b><br/>");
			for (Text t : texts) {
				buf.append("<a href=" + t.URL + ">" + t.title + "</a><br/>");
			}
			buf.append("</div>");
			
			// End of left column of home page, start of right column
			buf.append("</div><div style='display:table-cell;vertical-align:top'>");
			
			// Show embedded video lectures in the right column of the page
			if (videos == null) videos = ofy().load().type(Video.class).order("orderBy").list();
			Video video = null;
			Long i = null;
			try {
				i = Long.parseLong(request.getParameter("Video"));
				video = ofy().load().type(Video.class).id(i).now();
			} catch (Exception e) {
				if (videos.size()>0) {
					int randVideo = new Random().nextInt(videos.size());
					video = videos.get(randVideo);
				}
			}
			
			if (videos.size()>0) {
				buf.append("<iframe width='425' height='349' src=https://www.youtube.com/embed/"
						+ video.serialNumber + (i==null?"":"?autoplay=1")
						+ " frameborder='0' allowfullscreen></iframe>\n");

				buf.append("<div>");
				buf.append("<b>Video Lectures</b>");
				buf.append("<FORM NAME=VideoSelectForm METHOD=GET><SELECT NAME=Video onChange=submit()>");

				for (Video v : videos) { 
					buf.append("<OPTION VALUE=" + v.id + (v.id.equals(video.id)?" SELECTED":"") + ">" + v.title + "</OPTION>");
				}
				buf.append("</SELECT></FORM>");
				buf.append("</div>");
			}
			
			// Complete the large two-column table for the Home page (divs for cell, row, table):
			buf.append("</div></div></div>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
}
