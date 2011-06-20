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
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Objectify;

public class Login extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static boolean lockedDown = false;

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();
	List<Video> videos = ofy.query(Video.class).order("orderBy").list();
	
	public static String header = "<html>\n"
		+ "<head><meta http-equiv='content-type' content='text/html; charset=UTF-8'>"
		+ "<meta HTTP-EQUIV='Pragma' CONTENT='no-cache'>"
		+ "<meta HTTP-EQUIV='Expires' CONTENT='-1'>"
		+ "<meta NAME='Description' CONTENT='An online quiz and homework site'>"
		+ "<meta NAME='Keywords' CONTENT='learning,online,quiz,homework,video,textbook,open,education'>\n"
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
		+ "<CENTER><TABLE><TR><TD>\n"
		+ "<div id=pzon><nobr>"
		+ " <div class=pz1>ChemVantage.org</div>"
		+ " <div class=pz1><a href=Home>Home</a></div>"
		+ " <div class=pz1><a href=About>About Us</a></div>"
		+ "</nobr></div>\n"
		+ "<div id=phzl></div><div align=right id=puzr style='font-size:84%;padding:0 0 4px' width=100%>"
		+ "</div><br>";
	public static String footer = "\n<hr><CENTER><FONT SIZE=-1>"
		+ "&copy; 2007-11 ChemVantage.org. <a href=About#terms>Terms and Conditions of Use</a>"
		+ "</FONT></CENTER></TD></TR></TABLE></CENTER>\n"
		+ "</body></html>";

	public String getServletInfo() {
		return "Default servlet for user's login page in the ChemVantage site.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		request.getSession().invalidate();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(homePage(request));
	}

	String homePage(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			if (Home.announcement.length() > 0) { // post the announcement at the top of the page
				buf.append("<br><FONT COLOR=RED>" + Home.announcement + "</FONT>");
			}

			buf.append("<TABLE><TR><TD COLSPAN=2>"
					+ "<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - " + subject.title + "</b></FONT>"
					+ "<br><div align=right>An OpenEducation Resource</div></TD></TR></TABLE>"
					+ "</TD></TR><TR><TD VALIGN=TOP>");
			
			StringBuffer thisURL = request.getRequestURL();

			if (thisURL.indexOf("dev-vantage.appspot.com") > 0) {
				buf.append("<FONT COLOR=RED><b>CAUTION:</b></FONT><p>"
						+ "You have accessed a code development server that<br>"
						+ "should be used only by permission.<p>"
						+ "To reach the ChemVantage production site "
						+ "<a href=http://www.chemvantage.org>click here</a>.<p>"
						+ "<CENTER><button type=button onClick=location.href='" 
						+ UserServiceFactory.getUserService().createLoginURL("/Home") 
						+ "'>Authorized users only</button></CENTER><p>");
			} else {
				buf.append("ChemVantage is a free resource for science education:<ul>"
						+ "<li>computer-graded quizzes<li>homework exercises"
						+ "<li>practice exams<li>video lectures<li>free online textbooks</ul>");

				if (thisURL.indexOf("chemvantage.org") > 0 || thisURL.indexOf("localhost") > 0) {
					buf.append("<button type=button onClick=location.href='" 
							+ UserServiceFactory.getUserService().createLoginURL("/Home") 
							+ "'>Login to ChemVantage using your Google account</button></CENTER><p>");
				} else {
					buf.append("To launch a ChemVantage session, please click<br>"
							+ "the authorized link inside the learning management<br>"
							+ "system (LMS) used by your class (e.g., Blackboard,<br>"
							+ "Moodle, or Canvas).<p>"
							+ "<CENTER><a href=http://www.chemvantage.org>Click here if you are not using an LMS</a></CENTER><p>");

					buf.append("If you reached this page unexpectedly, here are some of the likely reasons:<OL>"
							+ "<LI>Your web session may have timed out or may have been reset by the server. "
							+ "In this case, please return to your class LMS and click the ChemVantage link to login again."
							+ "<LI>If you are using Internet Explorer, your browser security setting may be too high. "
							+ "To solve this problem you can either lower the security settings in Internet Explorer, "
							+ "or designate the web domain for your class LMS to be a trusted Internet zone.");
				}
			}
			buf.append("</TD><TD VALIGN=TOP>");   // start right column of home page
			
			Video video = null;
			try {
				video = videos.get(Integer.parseInt(request.getParameter("Video")));
			} catch (Exception e) {
				video = videos.get(0);
			}
			
			

			buf.append("<iframe width=560 height=349 src=http://www.youtube.com/embed/" + video.serialNumber + " frameborder=0 allowfullscreen></iframe>");
/*			buf.append("<object width='425' height='344'><param name='movie' "
					+ "value='http://www.youtube.com/v/" + requestedVideo + "&hl=en_US&fs=1'>"
					+ "</param><param name='allowFullScreen' value='true'></param><param "
					+ "name='allowscriptaccess' value='always'></param><embed "
					+ "src='http://www.youtube.com/v/" + requestedVideo + "&hl=en_US&fs=1' "
					+ "type='application/x-shockwave-flash' allowscriptaccess='always' "
					+ "allowfullscreen='true' width='425' height='344'></embed></object><br>");
*/			
			// video select box:
			if (videos.size()>0) { 
				buf.append("<TABLE BGCOLOR=#DDDDDD><TR><TD>");
				buf.append("<b>Video Lectures</b>");
				buf.append("<FORM NAME=VideoSelectForm METHOD=GET><SELECT NAME=Video onChange=submit()>"); 
					for (Video v : videos) buf.append("<OPTION VALUE=" + videos.indexOf(v) + (v.id.equals(video.id)?" SELECTED":"") + ">" + v.title + "</OPTION>");
				buf.append("</SELECT></FORM>");
				buf.append("</TD></TR></TABLE><p>");
			}
			buf.append("</TD></TR></TABLE>\n");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return Login.header + buf.toString() + Login.footer;
	}
}
