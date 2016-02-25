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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gdata.util.common.base.Charsets;
import com.google.gdata.util.common.io.CharStreams;
import com.google.gdata.util.common.util.Base64;
import com.googlecode.objectify.Key;

public class Login extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static boolean lockedDown = false;

	Subject subject = Subject.getSubject();
	GoogleClient CLIENT = GoogleClient.getInstance();
	List<Video> videos = ofy().load().type(Video.class).order("orderBy").list();
	JSONObject openid_config = null;
	
    static final Map<String, String> openIdProviders;
    static final Map<String, String> openIdLogos;
    static Set<String> attributes = new HashSet<String>();
	static {
    	openIdProviders = new HashMap<String, String>();
    	openIdLogos = new HashMap<String, String>();
    	
        openIdProviders.put("AOL", "aol.com"); openIdLogos.put("AOL", "/images/openid/aol.jpg");
        openIdProviders.put("Yahoo", "yahoo.com"); openIdLogos.put("Yahoo", "/images/openid/yahoo.jpg");
        attributes.add("email");
	}
    
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
// The following section is for implementation of Google+ Sign-in
		+ "<script src='https://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js' async defer></script>\n"
		+ "<script src='https://apis.google.com/js/platform.js' async defer></script>"
		+ "<script src='https://apis.google.com/js/client:platform.js?onload=start' async defer></script>\n"
// end of Google+ Sing-in scripts
		+ "</head>\n"
		+ "<body bgcolor=#ffffff text=#000000 link=#0000cc vlink=#551a8b alink=#ff0000 topmargin=3 marginheight=3>\n"
		+ "<TABLE><TR><TD>\n"
		+ "<div id=pzon><nobr>"
		+ " <div class=pz1>ChemVantage.org</div>"
		+ " <div class=pz1><a href=/Home>Home</a></div>"
		+ " <div class=pz1><a href=/About>About Us</a></div>"
		+ " <div class=pz1><a href=/help.html>Help</a></div>"
		+ "</nobr></div>\n"
		+ "<div id=phzl></div><div align=right id=puzr style='font-size:84%;padding:0 0 4px' width=100%>"
		+ "</div><br>";
	
	public static String footer = "\n<hr><span style='font-size:smaller'><table style='width:100%;border-spacing: 20px 0px'><tr>"
		+ "<td>&copy; 2007-15 ChemVantage LLC. <a rel='license' href='https://creativecommons.org/licenses/by/3.0/'><img alt='Creative Commons License' style='border-width:0' src='https://i.creativecommons.org/l/by/3.0/80x15.png' /></a></td>"
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
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		try {
			request.getSession().invalidate();
			HttpSession session = request.getSession();
			String state = new BigInteger(130, new SecureRandom()).toString(32);
			session.setAttribute("state", state);
			out.println(homePage(request,state));
		} catch (Exception e) {
			out.println("<h3>Sorry, ChemVantage is temporarily unavailable</h3>"
					+ "The most likely reason is that Google App Engine is in a period of scheduled maintenance.<br>"
					+ "If the downtime lasts more than 2 hours, please send email to admin@chemvantage.org<br>"
					+ "or call us at 801-810-4401.  Thanks in advance for your patience.<p>");
			out.println("<a href='/help.html'>ChemVantage Help Page</a><p>" + e.getMessage() + "<p>" + e.getStackTrace());
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		
		// catch a misdirected attempt to connect using LTI (should use /lti instead)
		String lti_message_type = request.getParameter("lti_message_type");
		if (lti_message_type!=null) {  // this is a misdirected LTI request; return standard LTI error message
			String msg = "Help for LTI registration is available at https://www.chemvantage.org/lti/registration/";
			response.sendRedirect(request.getParameter("launch_presentation_return_url") + "?lti_msg=" + URLEncoder.encode(msg,"UTF-8"));
			return;
		}
		
		// handle a valid Google+ sign-in attempt
		try {  
			// Load openId configuration endpoint URLS, if necessary
			if (openid_config==null) openid_config = getOpenIdConfig();
			
			// Validate the session/parameter state variable
			HttpSession session = request.getSession();
			String sessionState = (String)session.getAttribute("state");
			String requestState = request.getParameter("state");
			if (!sessionState.equals(requestState)) {
				response.setStatus(400); // Bad request
				throw new Exception("State parameters did not match.");  // protects against request forgery attack
			}
			session.removeAttribute("state");  // state is for one-time login only

			//Exchange the one-time code for a Google+ access token JSON object
			String code = request.getParameter("code");
			JSONObject accessToken = getToken(code);
			
			// Handle possible authentication failure
			if (accessToken.containsKey("error")) {
				response.setStatus(401); // Unauthorized
				throw new Exception("Access token contained error.");
			}
			
			// Retrieve the id_token inside the accessToken and decode the JSON Web Token (JWT) payload:
			String id_token = accessToken.getString("id_token");
			String[] pieces = id_token.split("\\.");
			if (pieces.length!=3) {
				response.setStatus(501); // not implemented; server lacks the ability to fulfill the request
				throw new Exception("Invalid JWT payload.");  // JWT token structure is invalid			
			}
			JSONObject payload = JSONObject.fromObject(new String(Base64.decode(pieces[1])));
			
			// Verify that this JWT is targeted to the correct site for Google+ login
			if (!CLIENT.client_id.equals(payload.getString("aud"))) {
				response.setStatus(403); // forbidden
				throw new Exception("AuthToken not valid for this site.");  // JWT token has wrong audience
			}
			
			// Check to ensure that JWT has not expired
			Date now = new Date();
			Date expires = new Date(1000L*new Long(payload.getInt("exp")));
			if (expires.before(now)) {
				response.setStatus(498); // token expired
				throw new Exception("AuthToken expired.");
			}
			
			// Retrieve the id from the JWT 
			String userId = payload.getString("sub");
			if (userId==null) {
				response.setStatus(499); // token required
				throw new Exception("JWT did not contain a valid user id.");
			}
				
			// Everything looks OK; sign-in to ChemVantage
			session.setAttribute("UserId", userId);			

			// Check to see if this is a first-time Google+ sign-in
			if (ofy().load().type(User.class).id(userId)==null) { 
				String firstName = getUserFirstName(userId,accessToken);
				User.createGooglePlusUser(payload,firstName);
			}
			
			// Set a cookie in the user's browser for Google+ login prompt next visit
			Cookie c = new Cookie("IDProvider","Google");
			c.setMaxAge(2592000); // expires after 30 days (in seconds)
			response.addCookie(c);

		} catch (Exception e) {	
			response.setStatus(500);
		}		
	}
		
	String homePage(HttpServletRequest request,String state) {
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
			
//			UserService userService = UserServiceFactory.getUserService();
//			buf.append("<h3>Please Sign In</h3>");
/*			
			boolean showAll = "all".equals(request.getParameter("show"));
			Cookie[] cookies = request.getCookies();
			if (!showAll && cookies!=null) {  // a login cookie has been set; try to show a link to the preferred OpenID provider
				showAll = true;
				for (Cookie c : cookies) {
					if (!"IDProvider".equals(c.getName())) continue;
					
					if (openIdProviders.containsKey(c.getValue())) {
						String providerName = c.getValue();
						String providerUrl = openIdProviders.get(providerName);
						String loginUrl = userService.createLoginURL("/userService",null,providerUrl,attributes);
						buf.append("<table style='border-spacing:40px 0px'><tr><td style='text-align:center'><a id='" + providerName + "' href='" + loginUrl + "' "
								+ "onClick=\"javascript: if (self!=top) document.getElementById('" + providerName + "').target='_blank';\">"
								+ "<img src='" + openIdLogos.get(providerName) + "' border=0 alt='" + providerName + "' style='text-align:center'><br/>" 
								+ providerName + "</a></td></tr></table>");
						showAll = false;
						break;
					} else if ("Google".equals(c.getValue())) {
						buf.append("<span id='signinButton'>"
								+ "<span class='g-signin' "
								+ "data-callback='signinCallback' "
								+ "data-clientid='" + CLIENT.client_id + "' "
								+ "data-cookiepolicy='single_host_origin' "
								+ "data-redirecturi='postmessage' "  // named google+ parameter for hybrid server code exchange schema
								+ "data-scope='profile email'> "
								+ "<a href=#><img id=g+ src=/images/openid/google+.jpg border=0 alt='Google'><br/>Google</a>"
								+ "</span></span>\n");
						showAll = false;
					} else if ("BLTI".equals(c.getValue())) {
						buf.append("It appears that you are using ChemVantage in conjunction with a course learning "
								+ "management system (LMS). You should access ChemVantage from inside the LMS to access your assignments and scores. "
								+ "You may create a separate ChemVantage account for more convenient access using the login options link below. "
								+ "However, you must use <b>exactly the same name or email address</b> in order to be able to merge your accounts later "
								+ "using the information at the bottom of the 'View My Profile' page.<br>");
						showAll = false;
						break;
					}
				}
				if (!showAll) buf.append("<p><a style='font-size:smaller' href=/?show=all>Show more login options</a>");
			} else showAll = true;
			
			if (showAll) {
*/	
				buf.append("ChemVantage uses Google+ authentication; please click the Google+ icon and sign in with your Google/GMail credentials.<br>"
						+ "If this is your first ChemVantage login, a free account will be created for you.<p>");
				buf.append("<TABLE style='border-spacing:40px 0px'><TR>");
/*
				// display Google-authorized OpenID providers and logos:
				for (String providerName : openIdProviders.keySet()) {
					String providerUrl = openIdProviders.get(providerName);
					String loginUrl = userService.createLoginURL("/userService",null,providerUrl,attributes);
					buf.append("<TD style='text-align:center'><a id='" + providerName + "' href='" + loginUrl + "' "
							+ " onClick=\"javascript: if (self!=top) document.getElementById('" + providerName + "').target='_blank';\">"
							+ "<img src='" + openIdLogos.get(providerName) + "' border=0 alt='" + providerName + "'><br/> " 
							+ providerName + "</a></TD>");
				}
*/
				// Begin new section to implement Google+ Login option
				// See https://developers.google.com/+/web/signin/add-button for details.		
				buf.append("<TD style='text-align:center'><span id='signinButton'>"
						+ "<span class='g-signin' "
						+ "data-callback='signinCallback' "
						+ "data-clientid='" + CLIENT.client_id + "' "
						+ "data-cookiepolicy='single_host_origin' "
						+ "data-redirecturi='postmessage' "  // named google+ parameter for hybrid server code exchange schema
						+ "data-scope='profile email'> "
						+ "<a href=#><img id=g+ src=/images/openid/google+.jpg border=0 alt='Google'><br/>Google</a>"
						+ "</span></span></TD>\n");		  
				buf.append("</TR></TABLE>");
//			}
			
			// load javascript for processing a google+ sign-in flow
			buf.append("<script>"
						+ "function signinCallback(authResult) {"
						+ " if (authResult['status']['method']=='PROMPT' && authResult['status']['signed_in']) {"
						+ "    document.getElementById('signinButton').innerHTML='working...';"
						+ "      $.ajax({type:'POST',url:'/login',contentType:'application/x-www-form-urlencoded; charset=UTF-8', "
						+ "       data: 'state=" + state + "&code=' + authResult['code'], "
						+ "       success: function(result) {"
						+ "        document.getElementById('signinButton').innerHTML='OK';"
						+ "        window.location='/Home';},"
						+ "       error: function(xhr,ajaxOptions,thrownError){"
						+ "        document.getElementById('signinButton').innerHTML=xhr.status + ': ' + ajaxOptions;}"
						+ "      })"
						+ "}}"
						+ "</script>");
			

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

	JSONObject getOpenIdConfig() {
		try {
			URL u = new URL("https://accounts.google.com/.well-known/openid-configuration");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();			
			return JSONObject.fromObject(res.toString());	
		} catch (Exception e) {
			return null;
		}
	}

	JSONObject getToken(String code) {
		JSONObject accessToken = null;
		try {
			// Exchange the one-time authorization code for an access token:		
			URL u = new URL(openid_config.getString("token_endpoint"));
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Accept-Charset", "UTF-8");
			String queryString = "code=" + code;
			queryString += "&client_id=" + CLIENT.client_id; 
			queryString += "&client_secret=" + CLIENT.client_secret;
			queryString += "&redirect_uri=postmessage";
			queryString += "&grant_type=authorization_code";
			
			OutputStream output = uc.getOutputStream();
			output.write(queryString.getBytes());
			output.flush();

			//read the response from Google+ and convert it to a JSON object
			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			StringBuffer res = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				res.append(line);
			}
			reader.close();
			
			accessToken = JSONObject.fromObject(res.toString());
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		return accessToken;
	}
	
	String getUserFirstName(String userId,JSONObject accessToken) {
		String givenName = "";
		try {
			// Open a new URL connection to the Google People API endpoint:
			URL u = new URL("https://www.googleapis.com/plus/v1/people/" + userId);
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setRequestProperty("Authorization", "Bearer " + accessToken.getString("access_token"));
			
			//read the response from Google+ and convert it to a JSON object
			String content = CharStreams.toString(new InputStreamReader(uc.getInputStream(),Charsets.UTF_8));
			if (content==null || content.isEmpty()) throw new Exception();
			
			JSONObject userInfo = JSONObject.fromObject(content);
			givenName = userInfo.getJSONObject("name").getString("givenName");
		} catch (Exception e) {
		}
		return givenName;
	}

}
