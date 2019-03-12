/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2016 ChemVantage LLC
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
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;

public class Login extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static boolean lockedDown = false;

	Subject subject = Subject.getSubject();
	List<Video> videos = ofy().load().type(Video.class).order("orderBy").list();
    
	public static String header = "<!DOCTYPE html>"
		+"<html>\n"
		+ "<head>"
		+ "<meta HTTP-EQUIV='Content-type' CONTENT='text/html;charset=iso-8859-1'>"
		+ "<meta HTTP-EQUIV='Expires' CONTENT='" + (new Date().toString()) + "'>\n"
		+ "<meta HTTP-EQUIV='P3P' CONTENT='policyref=\"/w3c/p3p.xml\",CP=\"CURa ADMa DEVa OUR IND DSP OTI COR\"'>\n"
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
		+ " <div class=pz1>ChemVantage.org</div>"
		+ " <div class=pz1><a href=/Home>Home</a></div>"
		+ " <div class=pz1><a href=/About>About Us</a></div>"
		+ "</nobr></div>\n"
		+ "<div id=phzl></div><div align=right id=puzr style='font-size:84%;padding:0 0 4px' width=100%>"
		+ "</div><br>";
	
	public static String footer = "\n<hr><span style='font-size:smaller'><table style='width:100%;border-spacing: 20px 0px'><tr>"
		+ "<td>&copy; 2007-19 Charles Wight. <a rel='license' href='https://creativecommons.org/licenses/by/3.0/'><img alt='Creative Commons License' style='border-width:0' src='https://i.creativecommons.org/l/by/3.0/80x15.png' /></a></td>"
		+ "<td align=center><a href=/About#terms>Terms and Conditions of Use</a></td>"
		+ "<td align=right><a href='http://code.google.com/appengine/'><img src=/images/GAE.gif border=0 "
		+ "alt='Powered by Google App Engine'></a></td></tr></table>"
		+ "</span>"
		+ "</TD></TR></TABLE>\n"
		+ "</body></html>";

	String ajaxSubmitScript = "<SCRIPT TYPE='text/javascript'>\n"
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

	public String getServletInfo() {
		return "Default servlet for user's login page in the ChemVantage site.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		request.getSession().invalidate();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(loginPage(request));
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		// catch a misdirected attempt to connect using LTI (should use /lti instead)
		String lti_message_type = request.getParameter("lti_message_type");
		if (lti_message_type!=null) {  // this is a misdirected LTI request; return standard LTI error message
			String msg = "LTI Registration Error: the correct URL is https://www.chemvantage.org/lti/registration/";
			response.sendRedirect(request.getParameter("launch_presentation_return_url") + "?lti_msg=" + URLEncoder.encode(msg,"UTF-8"));
			return;
		} else doGet(request,response);
	}

	
	String loginPage(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		try {
			if (Home.announcement.length() > 0) { // post the announcement at the top of the page
				buf.append("<br><FONT COLOR=RED>" + Home.announcement + "</FONT>");
			}

			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - " + subject.title + "</b></FONT>"
					+ "<br><div align=right>An Open Education Resource</TD></TR></TABLE>");
			
			String thisURL = request.getRequestURL().toString();
			thisURL = thisURL.substring(0,thisURL.indexOf("/login.html"));

			if (thisURL.indexOf("dev-vantage") > 0) {
				buf.append("<p><hr><span style=color:red><b>CAUTION: </b>"
						//+ thisURL
						+ "This is a code development server. Go to the "
						+ "<a href=http://www.chemvantage.org>production server instead</a>.</span><hr>");
			}
			buf.append("ChemVantage is a free resource for science education:"
					+ "<table><tr><td>"
					+ "<ul><li>computer-graded quizzes<li>homework exercises<li>practice exams</ul>"
					+ "</td><td>"
					+ "<ul><li>video lectures<li>free online textbooks"
					+ "<li><a href='/lti/registration/'>Connect using LTI</a></ul>"
					+ "</td></tr></table>");

			buf.append("View the <a href=https://www.youtube.com/watch?v=PWDPQMhvghA>ChemVantage video</a>.<hr><p>");
			
			buf.append("<h3>Please Sign In</h3>"
					+ "<div id=signin>ChemVantage uses third-party authentication by Google.<br>"
					+ "Click the Google icon below to sign in with your Google/GMail credentials.<br>"
					+ "If this is your first ChemVantage login, a free account will be created for you.<p>");
			buf.append("<div style=margin-left:40px><a href='" + UserServiceFactory.getUserService().createLoginURL("/userServiceLaunch") + "'><img src=/images/google.png border=0 alt='Google login'></a></div></div><p>");

			buf.append("<script>function inIframe() {try {return window.self !== window.top;} catch (e) {return true;}}"
					+ "if (inIframe()) {document.getElementById('signin').innerHTML='To login to ChemVantage from inside a learning management system (LMS), click on the assignment link.<br>"
					+ "To access the public ChemVantage site, click <a href=https://www.chemvantage.org target=_blank>here</a>.'}</script>");
			
			buf.append("<hr><h3>Try One Question</h3>");
			buf.append("<table width=650><tr><td>" + printOneQuestion(request) + "</td></tr></table>");
			
		
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return Login.header + buf.toString() + Login.footer;
	}
	
	String printOneQuestion(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			if (topicId == 0) {  // choose a random topic
				List<Key<Topic>> topicKeys = ofy().load().type(Topic.class).keys().list();
				int random = new Random().nextInt(topicKeys.size());
				topicId=ofy().load().key(topicKeys.get(random)).now().id;
			}
			Topic topic = ofy().load().type(Topic.class).id(topicId).now();

			String questionType = request.getParameter("QuestionType");
			if (questionType==null) questionType = "Quiz";
			
			//buf.append("\n<b>Sample quiz question: <u>" + topic.title + "</u></b>");
			
			buf.append("\n<FORM ACTION=/q METHOD=POST>");
			
			// create a set of available questionIds either from the group assignment or from the datastore
			List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("topicId", topicId).filter("assignmentType",questionType).filter("isActive",true).keys().list();
			if (questionKeys.size() == 0) {
				buf.append("No questions are available for this topic, sorry. <a href=/q>Try Again.</a>");
				return buf.toString();
			}
			// Randomly select one questions to be presented
			Random rand = new Random();  // create random number generator to select quiz questions
			Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
			Question q = ofy().load().key(k).now();
			int param = rand.nextInt();
			q.setParameters(param);  // randomizes parameterized questions
			buf.append("\n" + q.print() + "<br>\n");
			
			buf.append("\n<input type=hidden name='TopicId' value=" + topic.id + ">");
			buf.append("\n<input type=hidden name='Param' value=" + param + ">");
			buf.append("\n<input type=submit>");
			buf.append("\n</form>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

}
