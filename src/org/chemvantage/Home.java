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
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.googlecode.objectify.Objectify;

public class Home extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static String announcement = "";
	String servername;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();
	List<Video> videos = ofy.query(Video.class).order("orderBy").list();
	List<Topic> topics = ofy.query(Topic.class).order("orderBy").list();
	List<Text> texts = ofy.query(Text.class).list();
		
	public String getServletInfo() {
		return "Default servlet for user's home page.";
	}

	public static String maintenanceAnnouncement = "Due to a problem with the Google AppEngine servers, ChemVantage is temporarily unavailable.<br>"
		+ "You can monitor the status Google AppEngine at <a href=http://code.google.com/status/appengine>http://code.google.com/status/appengine</a><br>"
		+ "Please try again later. We apologize for the inconvenience. -ChemVantage";

	public static String footer = Login.footer;

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// begin standard user authentication section
		HttpSession session = request.getSession(true);
		User user = User.getInstance(session);
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		
		// Check to see if the user has a stored groupId from the LTILaunch 
		// (usually follows account upgrade purchase)
		try {
			long groupId = Long.parseLong((String)session.getAttribute("GroupId"));
			if (user.hasPremiumAccount()) {
				user.changeGroups(groupId);
				ofy.put(user);
				session.removeAttribute("GroupId");
			}
		} catch (Exception e) {}
		
		// Check to see if the user has a stored resource_link_id from an LTILaunch
		// (usually deferred to complete a Verification step)
		String resource_link_id = (String)session.getAttribute("ResourceLinkId");
		String lis_result_sourcedid = (String)session.getAttribute("LisResultSourcedid");
		String redirectUrl = null;
		if (resource_link_id != null && user.myGroupId>0) {
			redirectUrl = "/lti?UserRequest=Go&resource_link_id=" + resource_link_id;
			if (lis_result_sourcedid != null) redirectUrl += "&lis_result_sourcedid=" + lis_result_sourcedid;
			response.sendRedirect(redirectUrl);
			session.removeAttribute("ResourceLinkId");
			session.removeAttribute("LisResultSourcedid");
			return;
		}
		
		// Check to see if the user should provide additional contact information
		if (user.requiresUpdatesNow()) {
			response.sendRedirect("/Verification");      // enter name and email address
			return;
		}
				
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		out.println(homePage(user,request));
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// begin standard user authentication section
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}

		if ("JoinGroup".equals(request.getParameter("UserRequest"))) {
			try {
				user.changeGroups(Long.parseLong(request.getParameter("GroupId")));
			} catch (Exception e) {}
		}
		doGet(request,response);
	}

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
					+ "<TABLE><TR><TD>\n"
					+ "<div id=pzon><nobr>"
					+ "<div class=pz1>ChemVantage.org</div>"
					+ " <div class=pz1><a href=Home>Home</a></div>"
					+ " <div class=pz1><a href=About>About Us</a></div>"
					+ " <div class=pz1><a href=help.html>Help</a></div>"
					+ "<div class=pz1><a href=Feedback>Feedback</a></div>");

			buf.append("<div class=pz1><a href=Contribute>Authors</a></div>");
			if (user.isEditor()) buf.append("<div class=pz1><a href=Edit>Editors</a></div>");
			if (user.isInstructor() || user.isTeachingAssistant()) buf.append("<div class=pz1><a href=Groups>Instructors</a></div>\n");
			if (user.isAdministrator()) buf.append("<div class=pz1><a href=/admin>Admins</a></div>");

			buf.append("</nobr></div>\n");

			buf.append("<div id=phzl></div><div align=right id=puzr "
					+ "style='font-size:84%;padding:0 0 4px' width=100%><nobr><b>" 
					+ user.getEmail() + "</b>");

			buf.append("&nbsp;&nbsp;");
			
			buf.append("<a href='/Logout'>Sign out</a>");
			
			buf.append("</nobr></div>");

			buf.append("<FONT COLOR=RED>" + announcement + "</FONT>");
		} catch (Exception e) {
			return e.toString();
		}
		return buf.toString();
	}
	
	String homePage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Home.getHeader(user));
		try {			
			buf.append("<TABLE><TR ALIGN=LEFT><TD COLSPAN=2>");
			buf.append("<TABLE><TR><TD VALIGN=TOP>");
			buf.append("<img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage"     + " - " + subject.title);
			buf.append("</b></FONT><br><div align=right>An OpenEducation Resource</div></TD></TR></TABLE>");      
			buf.append("</TD></TR><TR><TD VALIGN=TOP>");

			buf.append(userInfoBox(user));
			
			// Add quiz/homework select box to the page
			buf.append("<TABLE><TR><TD>");
			buf.append("<b>Quizzes and Homework Exercises</b>");
			buf.append("<div id=selectReminder style='display: none'>"
					+ "<b><FONT COLOR=RED>Please select a topic:</FONT></b><br></div>");

			buf.append("<FORM NAME='HQSelectForm' ACTION=Quiz METHOD=GET>");
			buf.append("<INPUT TYPE=HIDDEN NAME=r VALUE=" + new Random().nextInt(99) + ">");
			buf.append("<SELECT NAME='TopicId'><OPTION Value='0' SELECTED>Select a topic</OPTION>");
			
			//List<Topic> topics = ofy.query(Topic.class).order("orderBy").list();
			for (Topic t : topics) {
				if (!t.orderBy.equals("Hide"))
				buf.append("<OPTION VALUE='" + t.id + "'>" + t.title + "</OPTION>");
			}
			buf.append("</SELECT><br>"
					+ "<INPUT TYPE=BUTTON VALUE='Take This Quiz' "
					+ "onClick=\"javascript: "
					+ "if(document.HQSelectForm.elements['TopicId'].value=='0'){document.getElementById('selectReminder').style.display='';} "
					+ "else {document.HQSelectForm.action='Quiz';document.HQSelectForm.submit();}\">" 
					+ "<INPUT TYPE=BUTTON VALUE='Homework Exercises' "
					+ "onClick=\"javascript: "
					+ "if(document.HQSelectForm.elements['TopicId'].value=='0'){document.getElementById('selectReminder').style.display='';} "
					+ "else {document.HQSelectForm.action='Homework';document.HQSelectForm.submit()}\">");
			buf.append("</FORM></TD></TR></TABLE><p>\n");

			// Add a box for taking practice exams
			buf.append("<TABLE><TR><TD>");
			buf.append("<b>Practice Exams</b>");
			buf.append("<FORM METHOD=GET ACTION=PracticeExam>");
			buf.append("<INPUT TYPE=SUBMIT VALUE='Take A Practice Exam Now'>");
			buf.append("</FORM></TD></TR></TABLE><p>\n");

			// Add text resources table to the page
			//List<Text> texts = ofy.query(Text.class).list();
			buf.append("<TABLE><TR><TD NOWRAP>"
					+ "<p><b>" + texts.size() + " Free Textbook Resources</b><br>");
			for (Text t : texts) {
				buf.append("<a href=" + t.URL + ">" + t.title + "</a><br>");
			}
			buf.append("</TD></TR></TABLE>");

			buf.append("</TD>");  // end left column of home page

			// Show embedded video lectures in the right column of the page
			buf.append("<TD VALIGN=TOP>");   // start right column of home page

			//List<Video> videos = ofy.query(Video.class).order("orderBy").list();
			Video video = null;
			Long i = null;
			try {
				i = Long.parseLong(request.getParameter("Video"));
				video = ofy.get(Video.class,i);
				if (ofy.query(VideoTransaction.class).filter("userId",user.id).filter("serialNumber",video.serialNumber).get()==null)
					ofy.put(new VideoTransaction(user.id,video.serialNumber,video.title,new Date()));
			} catch (Exception e) {
				if (videos.size()>0) {
					int randVideo = new Random().nextInt(videos.size());
					video = videos.get(randVideo);
				}
			}

			if (videos.size()>0) {
				boolean isSecure = request.isSecure();
				buf.append("<iframe width='425' height='349' src='" + (isSecure?"https://":"http://") + "www.youtube.com/embed/" 
						+ video.serialNumber + (i==null?"":"?autoplay=1")
						+ "' frameborder='0' allowfullscreen></iframe>\n");

				buf.append("<TABLE><TR><TD>");
				buf.append("<b>Video Lectures</b>");
				buf.append("<FORM NAME=VideoSelectForm METHOD=GET><SELECT NAME=Video onChange=submit()>");

				for (Video v : videos) { 
					buf.append("<OPTION VALUE=" + v.id + (v.id.equals(video.id)?" SELECTED":"") + ">" + v.title + "</OPTION>");
				}
				buf.append("</SELECT></FORM>");
				buf.append("</TD></TR></TABLE><p>");
			}
			buf.append("</TD></TR></TABLE>\n");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString() + footer;
	}

	String userInfoBox(User user) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<TABLE BORDER=2 CELLSPACING=0 CELLPADDING=0 BORDERCOLOR=#008000><TR><TD>");
			buf.append("<TABLE BGCOLOR=#FFFF80>"
					+ "<TR><TD ALIGN=CENTER><b>Welcome, " + user.getFirstName() + "</b>"
					+ "&nbsp;&nbsp;<FONT SIZE=-1><a href='/Logout'>(this isn't me)</a></FONT></TD></TR>");
			buf.append("<TR><TD ALIGN=CENTER><FONT SIZE=-1> " + user.getDecoratedRole() +"</FONT></TD></TR>"); 

			Group myGroup = null;
			if (user.myGroupId > 0) {
				try {
					myGroup = ofy.get(Group.class,user.myGroupId);
				} catch (Exception e2) {
					user.myGroupId = 0; // reset myGroupId in case my group was unexpectedly deleted
					ofy.put(user);
				}
			}
			if (myGroup != null) {  // display some group information in the yellow box
				if (!myGroup.memberIds.contains(user.id)) {
					// a user has been invited to join a group when myGroupId 
					// points to a group that does not list the user as a member
					String instructorEmail = myGroup.getInstructorEmail();
					if (instructorEmail == null) instructorEmail = "";
					buf.append("<TR><TD ALIGN=CENTER><b>Please Join This Group</b></TD></TR>"
							+ "<TR><TD ALIGN=CENTER><FONT SIZE=-1>"
							+ myGroup.getInstructorBothNames() + (instructorEmail.length()>0?" (" + myGroup.getInstructorEmail() + ")":"") + "<br>"
							+ "thinks that you should be a member<br>of the ChemVantage group:<br>"
							+ myGroup.description + " (" + myGroup.getInstructorBothNames() + ")."
							+ "<FORM NAME=Invitation ACTION=Home METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=JoinGroup>"
							+ "<INPUT TYPE=HIDDEN NAME=GroupId VALUE=" + myGroup.id + ">"
							+ "<INPUT TYPE=SUBMIT VALUE='Join This Group'>"
							+ "<INPUT TYPE=SUBMIT VALUE='No Thanks' onClick=Invitation.GroupId.value=0>"
							+ "</FORM></FONT></TD></TR>");
				} else {  
					// Identify with a group of students using this site:
					buf.append("<TR><TD ALIGN=CENTER><FONT SIZE=-1>" 
							+ "Group: <i>" + myGroup.description + "</i><br>"
							+ (myGroup.instructorId==null?"":"Instructor: <a href=mailto:" + User.getEmail(myGroup.instructorId) + ">" + myGroup.getInstructorBothNames() + "</a>")
							+ "</FONT></TD></TR>");
					// find the next assignment deadline and link it to the Scores table
					Date nextDeadline = myGroup.getNextDeadline();
					if (nextDeadline != null) {
						DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
						df.setTimeZone(myGroup.getTimeZone());
						buf.append("<TR><TD ALIGN=CENTER><FONT SIZE=-1>Next Deadline: </FONT>"
								+ "<FONT SIZE=-1><i><a href=Scores?r=" + new Random().nextInt(99) + ">" + df.format(nextDeadline) + "</a></i></FONT></TD></TR>");
					}
				}
			} else if (user.domain!=null && !user.domain.isEmpty())
				buf.append("<TR><TD ALIGN=CENTER><FONT SIZE=-1><a href=Verification>Join A ChemVantage Group</a></FONT></TD></TR>");
			buf.append("<TR><TD COLSPAN=2 ALIGN=CENTER><FONT SIZE=-1><a href=Scores?r=" + new Random().nextInt(99) + ">Show My Scores</a></FONT></TD></TR>");
			buf.append("</TABLE>");
			buf.append("</TD></TR></TABLE><p>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
}
