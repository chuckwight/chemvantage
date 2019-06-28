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
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class CalculateScores extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet calculates Score objects for one assignment.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		doPost(request,response);
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		
		HttpSession session = request.getSession();
		User user = null;
		String nonce = null;
		if (session.isNew()) {
			user = Nonce.getUser(request.getParameter("Nonce"));
			nonce = Nonce.createInstance(user);
		}
		else user = User.getInstance(session);
		if (user==null) {
			response.sendRedirect("/Logout");
			return;
		}
			
		if (user==null || !(user.isInstructor() || user.isAdministrator() || user.isChemVantageAdmin())) {
			response.sendRedirect("/Logout");
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		Assignment assignment = null;
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			assignment = ofy().load().type(Assignment.class).id(assignmentId).now();
		} catch (Exception e) {
			out.println("No valid assignment found.");
			return;
		}
		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = ""; // prevents null pointer Exception
		
		boolean recalculate = (userRequest.contentEquals("Recalculate"))?true:false;
		
		if (userRequest.contentEquals("ViewTransactions")) {  // view assignment transactions for a single student
			User student = ofy().load().type(User.class).id(request.getParameter("UserId")).now();
			out.println(Home.header + viewTransactions(student,assignment,nonce) + Home.footer);
		} else if (userRequest.contentEquals("Post this Score")) {  // EXPERIMENTAL SECTION
			User student = ofy().load().type(User.class).id(request.getParameter("UserId")).now();
			try {
				double pctScore = Double.parseDouble(request.getParameter("PctScore"));
				if (pctScore < 0 || pctScore > 100) throw new Exception("<b>Score must be a percentage in the range 0-100.</b>");
				if (student == null) throw new Exception("<b>There are no records for this student in the database.</b>");
				postRevisedScore(student,assignment,pctScore);
			} catch (Exception e) {
				out.println(e.toString());
				return;
			}
			out.println(Home.header + viewTransactions(student,assignment,nonce) + Home.footer);			
		} else {  // view group Score objects for one assignment
			out.println(Home.header + viewGroupScores(assignment,recalculate,nonce) + Home.footer); 
		}
	}
	
	public String viewTransactions(User student,Assignment a,String nonce) {
		StringBuffer buf = new StringBuffer();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		try {
			if ("Quiz".contentEquals(a.assignmentType)) {
				buf.append("<h2>Quiz Transactions</h2>");
				buf.append("ChemVantage UserID: " + student.getIdHash() + "<br>");
				buf.append("Assignment Number: " + a.id + "<br>");
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("Topic: "+ t.title + "<br>");
				buf.append("Valid: " + df.format(now) + "<p>");
				
				List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).filter("userId",student.id).order("downloaded").list();
				
				if (qts.size()==0) {
					buf.append("Sorry, we did not find any records for this student in the database for this assignment.");
					return buf.toString();
				}
				
				Key<Score> k = Key.create(Key.create(User.class, student.id),Score.class,a.id);
				Score s = ofy().load().key(k).now();
				if (s==null) s = Score.getInstance(student.id, a);
				buf.append("This student's best score on the assignment is " + Math.round(100.*s.score/s.maxPossibleScore) + "%.<p>");

				if (s != null && s.lis_result_sourcedid != null) {
					try {
						Group g = ofy().load().type(Group.class).id(student.myGroupId).safe();
						String messageFormat = g.getLisOutcomeFormat();
						String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
						String oauth_consumer_key = g.domain;
						String replyBody = new LTIMessage(messageFormat,body,g.lis_outcome_service_url,oauth_consumer_key).send();

						if (replyBody.contains("success")) {
							int beginIndex = replyBody.indexOf("<textString>") + 12;
							int endIndex = replyBody.indexOf("</textString>");
							replyBody = replyBody.substring(beginIndex,endIndex);
							double lmsPctScore = 100.*Double.parseDouble(replyBody);
							if (Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
								buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
							} else { // there is a significant difference between LMS and ChemVantage scores. Please explain:
								buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
										+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<p>");
							}
						} else buf.append("We attempted to validate the score contained in your class LMS grade book,<br>but the readResult operation failed, sorry.<p>");

						// This section allows an instructor to send a revised score to the LMS ===== EXPERIMENTAL =====				
						buf.append("<form method=post action=CalculateScores>"
								+ "You may use the form below to post a revised percentage score for this assignment<br>to the class LMS grade book: "
								+ "<input type=hidden name=UserId value=" + student.id + ">"
								+ "<input type=hidden name=AssignmentId value=" + a.id + ">"
								+ "<input type=hidden name=Nonce value=" + nonce + ">"
								+ "<input type=text size=4 name=PctScore>% "
								+ "<input type=submit name=UserRequest value='Post this Score'> (value must be in the range 0-100)"
								+ "</form><p>");
						// End of EXPERIMENTAL section ==================================================================

					} catch (Exception e) {
						buf.append("An unexpected error occured: " + e.toString());
					}
				}

				buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Quiz Score</th></tr>");
				for (QuizTransaction qt : qts) {
					buf.append("<tr><td>" + qt.id + "</td><td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":100.*qt.score/qt.possibleScore + "%") +  "</td></tr>");
				}
				buf.append("</table><br>Missing scores indicate quizzes that were downloaded but not submitted for scoring.<p>");
			} else if ("Homework".contentEquals(a.assignmentType)) {
				buf.append("<h2>Homework Transactions</h2>");
				buf.append("ChemVantage UserID: " + student.getIdHash() + "<br>");
				buf.append("Assignment Number: " + a.id + "<br>");
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				buf.append("Topic: "+ t.title + "<br>");
				buf.append("Valid: " + df.format(now) + "<p>");
				
				List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).filter("userId",student.id).order("graded").list();
				
				if (hwts.size()==0) {
					buf.append("Sorry, we did not find any records for this student in the database for this assignment.");
					return buf.toString();
				}
				
				Key<Score> k = Key.create(Key.create(User.class, student.id),Score.class,a.id);
	    		Score s = ofy().load().key(k).now();
	    		if (s==null) s = Score.getInstance(student.id, a);
	    		buf.append("This student's overall score on the assignment is " + Math.round(100.*s.score/s.maxPossibleScore) + "%.<p>");
				
				buf.append("<table><tr><th>Transaction Number</th><th>QuestionID</th><th>Graded</th><th>Score</th></tr>");
				for (HWTransaction hwt : hwts) {
					buf.append("<tr align=center><td>" + hwt.id + "</td><td>" + hwt.questionId + "</td><td>" + df.format(hwt.graded) + "</td><td>" + hwt.score +  "</td></tr>");
				}
				buf.append("</table><p>");		
			} else buf.append("Sorry, we are unable to report transactions for this assignment type.");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();	
	}
	
	public String viewGroupScores(Assignment a,boolean recalculate,String nonce) {
		StringBuffer buf = new StringBuffer();
		try {
			Group group = ofy().load().type(Group.class).id(a.groupId).now();
			if (recalculate) group.reviseScores(a);

			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
			buf.append(df.format(new Date()) + "<p>");

			// make a list of student users, removing instructors, admins and TAs
			List<User> groupMembers = new ArrayList<User>(ofy().load().type(User.class).ids(group.memberIds).values());
			List<User> instructors = new ArrayList<User>();
			for (User u : groupMembers) if (u.isAdministrator() || u.isInstructor() || u.isTeachingAssistant()) instructors.add(u);
			groupMembers.removeAll(instructors);  // leaves only the members with a student role

			Score s = null;
			int counter = 0;
			if (groupMembers.size()==0) buf.append("There are no students in this group yet.");
			else {
				if (recalculate) {
					buf.append("These scores have been recalculated just now:<p>");
				} else buf.append("To protect the privacy of our users, ChemVantage does not store names or other personally identifiable information (PII). "
						+ "Scores are listed only by an opaque user ID that is derived from the value provided by your class learning management system. "
						+ "You may click any student's UserID below to get a complete list of transactions for this assignment. If you think there may be "
						+ "a problem with the scores, you may <a href=/CalculateScores?UserRequest=Recalculate&AssignmentId=" + a.id + (nonce==null?"":"&Nonce=" + nonce) + ">"
						+ "click here to recalculate all student scores for this assignment</a> (this may take a few minutes).<p>");
				// print a table of scores for this group assignment
				buf.append("<table cellspacing=0 border=1><tr><th>ChemVantage UserID</th><th>Best Score</th><th>Attempts</th><th>Most Recent Attempt</th></tr>");
				Queue queue = QueueFactory.getDefaultQueue();
				for (User u : groupMembers) {
					counter++;
					s = u.getScore(a);
					if (s.needsLisReporting()) queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(a.id)).param("UserId",u.id));
					double pct = s.score*100./s.maxPossibleScore;
					buf.append("<tr><td align=center><a href=/CalculateScores?UserRequest=ViewTransactions&AssignmentId=" + a.id 
							+ "&UserId=" + u.id + (nonce==null?"":"&Nonce=" + nonce) + ">" + u.getIdHash() + "</a></td><td align=center>" 
							+ (s.numberOfAttempts>0?String.format("%.0f", pct) + "%":"-") + "</td><td align=center>" + s.numberOfAttempts + "</td><td>" 
							+ (s.mostRecentAttempt==null?"":df.format(s.mostRecentAttempt)) + "</td></tr>");
				}
				buf.append("</table><br>" + counter + " student" + (counter!=1?"s":""));
			}

		} catch (Exception e) {
		}
		return buf.toString();
	}
	
	void postRevisedScore(User student,Assignment a,double pctScore) 
	throws Exception {
		Group g = ofy().load().type(Group.class).id(a.groupId).safe();
		String oauth_consumer_key = g.domain;
		Score s = Score.getInstance(student.id, a);
		
		String messageFormat = g.getLisOutcomeFormat();			
		String body = LTIMessage.xmlReplaceResult(s.lis_result_sourcedid,Double.toString(pctScore/100.));
		String replyBody = new LTIMessage(messageFormat,body,g.lis_outcome_service_url,oauth_consumer_key).send();
		
		if (!replyBody.contains("success")) throw new Exception("Sorry, the attempted score posting failed unexpectedly.");
		return;
	}
}