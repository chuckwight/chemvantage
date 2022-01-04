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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class Subject {
	static Subject subject;
	@Id Long id;
	private String title;
	private String HMAC256Secret;
	private String salt;
	private String announcement;
	private int nStarReports;
	private double avgStars;
	
	Subject() {}
	static { refresh(); }
	
	static void refresh() {
		try {
		subject = ofy().load().type(Subject.class).first().safe();
		} catch (Exception e) {
			subject = new Subject();
			subject.id = 1L;
			subject.title = "General Chemistry";
			subject.HMAC256Secret = "ChangeMeInTheDataStoreManuallyForYourProtection";
			subject.salt = "ChangeMeInTheDataStoreManuallyToKeepStoredUsedIdValuesSecure";
			ofy().save().entity(subject);
		}
	}
	
	static String getTitle() { return subject.title; }
	static String getHMAC256Secret() { return subject.HMAC256Secret; }
	static String getSalt() { return subject.salt; }
	public static String getAnnouncement() { return subject.announcement; }
	static int getNStarReports() { return subject.nStarReports; }
	
	static void setAnnouncement(String msg) {
		refresh();
		subject.announcement = msg;
		ofy().save().entity(subject);
	}
	
	static double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(subject.avgStars));
	}
	
	static void addStarReport(int stars) {
		refresh();
		subject.avgStars = (subject.avgStars*subject.nStarReports + stars)/(subject.nStarReports+1);
		subject.nStarReports++;
		ofy().save().entity(subject);
	
	}
		
	static String hashId(String userId) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] bytes = md.digest((userId + subject.salt).getBytes(StandardCharsets.UTF_8));
        	StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
		} catch (Exception e) {
        	return null;
        }
	}
	
	public static String header(String title) {
		String announcement = Subject.getAnnouncement();
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
				+ "<link rel='icon' type='image/png' href='/images/favicon.png' />"
				+ "<title>" + (title==null || title.isEmpty()?"ChemVantage":title) + "</title>\n"
				+ "</head>\n"
				+ "<body style='padding: 10px; font-family: Calibri,Arial,sans-serif; background-color: white; color: black;'>\n"
				+ ((announcement==null || announcement.isEmpty())?"":"<FONT COLOR=RED>" + announcement + "</FONT><br/>\n");
	}
	
	public static String header() {
		return header("ChemVantage");
	}

	public static String getHeader(User user) {
		String announcement = Subject.getAnnouncement();

		return "<!DOCTYPE html>"
		+"<html>\n"
		+ "<head>\n"
		+ "<meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />"
		+ "<meta http-equiv='Pragma' content='no-cache' />"
		+ "<meta http-equiv='Expires' content='0' />"
		+ "<meta http-equiv='Content-type' content='text/html;charset=iso-8859-1' />"
		+ "<meta name='Description' content='An online quiz and homework site' />"
		+ "<meta namew='Keywords' content='chemistry,learning,online,quiz,homework,video,textbook,open,education' />"
		+ "<meta name='msapplication-config' content='none'/>\n"
		+ "<link rel='icon' type='image/png' href='/favicon.png' />"
		+ "<link rel='icon' type='image/vnd.microsoft.icon' href='/favicon.ico' />\n"
		+ "<title>ChemVantage Administrator</title>\n"
		+ "</head>\n"
		+ "<body style='padding: 10px; font-family: Calibri,Arial,sans-serif; background-color: white; color: black;'>\n"
		+ "<div>"
		+ "<a href=/ style='padding-right:25px'>Home</a> "
		+ "<a href=/About style='padding-right:25px'>About Us</a> "
		+ "<a href='/Feedback?sig=" + user.getTokenSignature() + "' style='padding-right:25px'>Feedback</a> "
		+ "<a href='/Contribute?sig=" + user.getTokenSignature() + "' style='padding-right:25px'>Authors</a> "
		+ "<a href='/Edit?sig=" + user.getTokenSignature() + "' style='padding-right:25px'>Editors</a> "
		+ "<a href='/Admin?sig=" + user.getTokenSignature() + "' style='padding-right:25px'>Admin</a> "
		+ "<a href=/Logout>Sign out</a>"
		+ "</div><br/>"
		+ ((announcement==null || announcement.isEmpty())?"":"<FONT COLOR=RED>" + announcement + "</FONT><br/>\n");
	}
	
	public static String footer = "\n<hr/><img src=/images/CVLogo_tiny.png alt='ChemVantage logo' style='vertical-align:middle' /> "
			+ "<a href=/about.html>About ChemVantage</a> | "
			+ "<a href=/about.html#terms>Terms and Conditions of Use</a> | "
			+ "<a href=/about.html#privacy>Privacy Policy</a> | "
			+ "<a href=/about.html#copyright>Copyright</a>\n"
			+ "</body>\n</html>";

	public static String banner = "<div style='padding=30px; font-family: Calibri,Arial,sans-serif;'>"
			+ "<a href='/' style='text-decoration: none;'>"
			+ "	<span style='color: blue; font-size: 2em; font-weight: bold;'>Chem</span><span style='color: red; font-size: 2em; font-weight: bold;'>Vantage</span><br/>\n"
			+ "</a>"
			+ "	An Open Education Resource for General Chemistry<br/>"
			+ "	</div>\n";
}
