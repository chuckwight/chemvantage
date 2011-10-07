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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Objectify;

public class Login extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static boolean lockedDown = false;

	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();
	List<Video> videos = ofy.query(Video.class).order("orderBy").list();

    private static final Map<String, String> openIdProviders;
    private static final Map<String, String> openIdLogos;
    static {
    	openIdProviders = new HashMap<String, String>();
    	openIdLogos = new HashMap<String, String>();
        
        openIdProviders.put("Google", "gmail.com"); openIdLogos.put("Google", "images/openid/google.jpg");
        openIdProviders.put("Yahoo", "yahoo.com"); openIdLogos.put("Yahoo", "images/openid/yahoo.jpg");
        openIdProviders.put("AOL", "aol.com"); openIdLogos.put("AOL", "images/openid/aol.jpg");
        openIdProviders.put("MyOpenID", "myopenid.com"); openIdLogos.put("MyOpenID", "images/openid/myopenid.jpg");
    }
	
	public static String header = "<!DOCTYPE html>"
		+"<html>\n"
		+ "<head>"
		+ "<meta HTTP-EQUIV='Content-type' CONTENT='text/html;charset=iso-8859-1'>"
		+ "<meta HTTP-EQUIV='Expires' CONTENT='" + (new Date().toString()) + "'>\n"
		+ "<meta HTTP-EQUIV='P3P' CONTENT='policyref=\"/w3c/p3p.xml\",CP=\"CURa ADMa DEVa OUR IND DSP OTI COR\"'>\n"
		+ "<meta NAME='Description' CONTENT='An online quiz and homework site'>\n"
		+ "<meta NAME='Keywords' CONTENT='learning,online,quiz,homework,video,textbook,open,education'>\n"
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
		+ " <div class=pz1><a href=Home>Home</a></div>"
		+ " <div class=pz1><a href=About>About Us</a></div>"
		+ " <div class=pz1><a href=help.html>Help</a></div>"
		+ "</nobr></div>\n"
		+ "<div id=phzl></div><div align=right id=puzr style='font-size:84%;padding:0 0 4px' width=100%>"
		+ "</div><br>";
	public static String footer = "\n<hr><CENTER><FONT SIZE=-1>"
		+ "&copy; 2007-11 ChemVantage.org. <a href=About#terms>Terms and Conditions of Use</a>"
		+ "</FONT></CENTER></TD></TR></TABLE>\n"
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

			buf.append("<TABLE><TR><TD VALIGN=TOP><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
					+ "<TD>Welcome to<br><FONT SIZE=+3><b>ChemVantage - " + subject.title + "</b></FONT>"
					+ "<br><div align=right>An OpenEducation Resource</div></TD></TR></TABLE>");
			
			StringBuffer thisURL = request.getRequestURL();

			if (thisURL.indexOf("dev-vantage.appspot.com") > 0) {
				buf.append("<span style=color:red><b>CAUTION:</b></span><p>"
						+ "You have accessed a code development server that<br>"
						+ "should be used only by permission.<p>"
						+ "To reach the ChemVantage production site "
						+ "<a href=http://www.chemvantage.org>click here</a>.<p>");

			} else if (thisURL.indexOf("chem-vantage.appspot.com") > 0) {
				buf.append("<h2>Your ChemVantage Session Has Been Closed</h2>");
				buf.append("This is normal following an extended period of inactivity. Please login again through your class learning management system.<br>"
						+ "If you reached this page unexpectedly, please see the <a href=help.html>ChemVantage Help Page</a> for further assistance.<p>");
			} else {
				buf.append("ChemVantage is a free resource for science education:<ul>"
						+ "<li>computer-graded quizzes<li>homework exercises"
						+ "<li>practice exams<li>video lectures<li>free online textbooks</ul>");
				buf.append("<a href=InformationForInstructors.pdf>Information for instructors</a>");
			}

			Set<String> attributes = new HashSet<String>();
			attributes.add("email");
			UserService userService = UserServiceFactory.getUserService();
			if (!request.getRequestURL().toString().contains("appspot")) buf.append("<h3>Please Login</h3>");
			buf.append("ChemVantage uses OpenID for account creation and authentication.<br>"
					+ "Choose one of the identity providers below to login to ChemVantage.<br>"
					+ "If you don't already have a free account, you will have the option to create one.<p>");
			
			buf.append("<TABLE cellspacing=10><TR>");
			for (String providerName : openIdProviders.keySet()) {
				String providerUrl = openIdProviders.get(providerName);
				String loginUrl = userService.createLoginURL("/Home",null,providerUrl,attributes);
				buf.append("<TD style='text-align:center'><a href='" + loginUrl + "'><img src='" 
						+ openIdLogos.get(providerName) + "' alt='" + providerName + "'><br/>" 
						+ providerName + "</a></TD>");
			}
			buf.append("</TR></TABLE>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return Login.header + buf.toString() + Login.footer;
	}
}
