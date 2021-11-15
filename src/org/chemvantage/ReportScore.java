/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2019 ChemVantage LLC
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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/ReportScore")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class ReportScore extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet reports a single Score object back to a user's LMS as a Task "
				+ "using the IMS LTI 1.1 Learning Information Services API or LTIAdvantage 1.3.0 API.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		if (request.getParameter("AssignmentId") == null || request.getParameter("UserId") == null) {
			PrintWriter out = response.getWriter();
			response.setContentType("text/html");
			out.println(Home.header("ChemVantage Unreported Scores") + "<h3>Unreported Scores</h3>");
			out.println("For each of the scores below, click the link to report it manually.<p>");
			List<Score> scores = ofy().load().type(Score.class).filter("lisReportComplete",false).list();
			for (Score s : scores) {
				String userId = s.owner.getName();
				String url = "/ReportScore?UserId=" + userId + "&AssignmentId=" + s.assignmentId;
				if (s.lisReportComplete) out.println("Report is complete: " + url + "<br>");
				else if (s.lis_result_sourcedid==null) continue; //out.println("No lisResultSourcedId: " + url + "<br>");
				else if (s.needsLisReporting()) out.println("Score needs reporting: <a href=" + url + ">" + url + "</a><br>");
				else out.println("No reporting URL provided: " + url + "<br>");
			}
			if (scores.size()==0) out.println("None. All scores are up to date.");
			out.println(Home.footer);
		} else 	doPost(request,response);	
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		try {  // post single user score
			String userId = URLDecoder.decode(request.getParameter("UserId"),"UTF-8");
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			ofy().save().entity(Score.getInstance(userId, a)).now();
			int attempts = 1;
			try {
				attempts = Integer.parseInt(request.getParameter("Retry")) + 1;
			} catch (Exception e) {}

			if (a.lti_ags_lineitem_url != null && !a.lti_ags_lineitem_url.contains("localhost")) {  // use LTIAdvantage reporting specs
				out.println(postUserScore(userId,a,attempts));
			} else if (a.lis_outcome_service_url != null && !a.lis_outcome_service_url.contains("localhost")) { // use LTI 1.1 reporting
				out.println(postUserScore(userId,a,attempts,""));  // use LTI v1.1 specs
			}
		} catch (Exception e) {
			out.println(e.getMessage());
		}

	}
	
	String postUserScore(String userId,Assignment a,int attempts,String dummy) { // LTIv1.1 only
		String oauth_consumer_key = null;
		StringBuffer buf = new StringBuffer();
		try {		
			Key<Score> k = Key.create(Key.create(User.class, Subject.hashId(userId)),Score.class,a.id);
			Score s = ofy().load().key(k).now();
			if (s == null || !s.needsLisReporting()) return "Score does not need to be reported";
			buf.append("LisResultSourcedId:<br>" + s.lis_result_sourcedid + "<p>");
			
			// compute a scaled score in the range 0.0-1.0 for LIS services specification
			double score = (double)s.score / (double)s.maxPossibleScore;
			if (score < 0.0 || score > 1.0) throw new Exception();

			oauth_consumer_key = a.domain;

			String messageFormat = "application/xml";
			String body = LTIMessage.xmlReplaceResult(s.lis_result_sourcedid,String.valueOf(score));
			buf.append("XML file:<br><pre>" + body + "</pre><p>");
			String replyBody = new LTIMessage(messageFormat,body,a.lis_outcome_service_url,oauth_consumer_key).send();
			buf.append("LMS Replied:<br>" + replyBody);

			if (replyBody.toLowerCase().contains("success")) {
				s.lisReportComplete = true;
				ofy().save().entity(s);
			} else if (attempts < 10){
				long countdownMillis = (long) Math.pow(2,attempts)*60000;
				Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
				queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",URLEncoder.encode(userId, "UTF-8")).param("Retry",Integer.toString(attempts)).countdownMillis(countdownMillis));			
			} else throw new Exception("User " + userId + " earned a score of " + s.getPctScore() + "% on assignment "
					+ a.id + "; however, the score could not be posted to the LMS grade book, even after " + attempts + " attempts.");
		} catch (Exception e) {
			buf.append(e.toString());
			if (attempts < 10){
				try{
					long countdownMillis = (long) Math.pow(2,attempts)*60000;
					Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
					queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",URLEncoder.encode(userId, "UTF-8")).param("Retry",Integer.toString(attempts)).countdownMillis(countdownMillis));			
				} catch (Exception e2) {}
			} else {
				sendEmailToDomainAdmin(userId,a,oauth_consumer_key,e.getMessage());
			}
		}
		return buf.toString();
	}
	
	String postUserScore(String userId,Assignment a,int attempts) {  // LTI v1.3 only
		StringBuffer buf = new StringBuffer();
		try {
			Key<Score> k = Key.create(Key.create(User.class,Subject.hashId(userId)),Score.class,a.id);
			Score s = ofy().load().key(k).safe();
			String response = LTIMessage.postUserScore(s,userId);
			buf.append(response);
			if (response.contains("Success") || response.contains("422"));
			else {			
				if (attempts < 3) {
					long countdownMillis = (long) Math.pow(2,attempts)*60000;
					Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
					queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",userId).param("Retry",Integer.toString(attempts)).countdownMillis(countdownMillis));			
				} else throw new Exception("User " + userId + " earned a score of " + s.getPctScore() + "% on assignment "
						+ a.id + "; however, the score could not be posted to the LMS grade book, even after " + attempts + " attempts. "
						+ "The response from your LMS was: " + response);
			}
		} catch (Exception e) {
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).now();
			sendEmailToLmsAdmin(userId,a,d,e.getMessage());
		}
		return buf.toString();
	}
	
	void sendEmailToLmsAdmin(String userId,Assignment assignment,Deployment d,String errorMsg) {  // LTIAdvantage
		try {
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);

			//String recipient = d.email;
			Topic t = ofy().load().type(Topic.class).id(assignment.topicId).safe();
			Key<Score> k = Key.create(Key.create(User.class, userId),Score.class,assignment.id);
			Score s = ofy().load().key(k).now();
			if (s==null) s=Score.getInstance(userId,assignment);
			if (!s.needsLisReporting()) return;  // everything is OK and the situation is resolved
			
			String msgBody = "<h3>ChemVantage LIS ReportScore Failure</h3>"
					+ "You are receiving this message because you are listed as the LMS administrator for an LTI "
					+ "connection to the ChemVantage app using the client_id " + d.client_id + "<p>"
					+ "The ChemVantage server encountered the following error while attempting to report a score to your LMS:<br>"
					+ errorMsg + "<p>"
					+ "Assignment = " + t.title + "<br>"
					+ "ChemVantage domain = " + d.platform_deployment_id + "<br>"
					+ "Domain contact = " + d.email + "<p>"
					+ "If you are using moodle, a common error is that a configuration error on your server is blocking PHP from "
					+ "accessing the Authorization header of incoming packets. The LTI grade posts were therefore rejected. You can "
					+ "remedy this by editing your .htaccess file to add the following two commands:<p>"
					+ "RewriteCond %{HTTP:Authorization} ^(.+) <br>"
					+ "RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}] <p>"
					+ "See <a href=https://moodle.org/mod/forum/discuss.php?d=323197#p1320091>this post in the Moodle Forum</a> for details.<p>"
					+ "This message was generated automatically to make you aware of a potential problem with the connection to "
					+ "your LMS. If you need help, please contact Chuck Wight (admin@chemvantage.org) for assistance.<p>"
					+ "Thank you,<p>"
					+ "Chuck Wight<br>ChemVantage LLC";
					
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			//msg.addRecipient(Message.RecipientType.TO,new InternetAddress(recipient, ""));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject("ChemVantage LIS Reporting Error");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
		}

	}
	
	void sendEmailToDomainAdmin(String userId,Assignment assignment,String oauth_consumer_key,String errorMsg) {  // LTIv1p1
		try {
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);

			BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).safe();
			String recipient = c.email;
			Topic t = ofy().load().type(Topic.class).id(assignment.topicId).safe();
			Key<Score> k = Key.create(Key.create(User.class, userId),Score.class,assignment.id);
			Score s = ofy().load().key(k).now();
			if (s==null) s=Score.getInstance(userId,assignment);
			if (!s.needsLisReporting()) return;  // everything is OK and the situation is resolved
				
			String msgBody = "<h3>ChemVantage LIS ReportScore Failure</h3>"
					+ "You are receiving this message because you received LTI credentials at this address to "
					+ "establish a connection to the ChemVantage app using the consumer_key " + c.oauth_consumer_key + "<p>"
					+ "The ChemVantage server encountered the following error while attempting to report a score to your LMS:<br>"
					+ errorMsg + "<p>"
					+ "Details:<br>"
					+ "UserId: " + userId + "<br>"
					+ "AssignmentId: " + assignment.id + "<br>"
					+ "Assignment: " + assignment.assignmentType + (t==null?"":" - " + t.title) + "<br>"
					+ "Current score= " + s.getPctScore() + "% after " + s.numberOfAttempts + " attempts<br>"
					+ "ChemVantage domain = " + oauth_consumer_key + "<br>"
					+ "Domain contact = " + c.email + "<br>"
					+ "Service URL = " + assignment.lis_outcome_service_url + "<br>"
					+ "POST message = " + LTIMessage.xmlReplaceResult(s.lis_result_sourcedid,String.valueOf(s.score)) + "<p>"
					
					+ "Please note</b>: Several Moodle users have experienced difficulty getting "
					+ "scores returned to the Moodle grade book using LTI. We believe that this is due to the Moodle server being "
					+ "configured in a way that refuses this type of LTI connection. You may rectify the situation by adding the "
					+ "following rewrite rule into the .htaccess file on the Moodle server:<p>"
					+ "RewriteCond %{HTTP:Authorization} ^(.+)" 
					+ "RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]<p>"

					+ "This message was generated automatically to make you aware of a potential problem with the connection to "
					+ "your LMS. If you need help, please contact me for assistance.<p>"
					+ "Thank you,<p>"
					+ "Chuck Wight<br>ChemVantage LLC<br>admin@chemvantage.org";
			
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(recipient, ""));
			msg.addRecipient(Message.RecipientType.CC,new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject("ChemVantage LIS Reporting Error");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
		}
	}

}