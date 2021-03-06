/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2020 ChemVantage LLC
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
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/VideoQuiz")
public class VideoQuiz extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	int nQuestions = 2;  // this is the default number of questions per quizlet at the video breaks
	
	public String getServletInfo() {
		return "This servlet presents a video with embedded quizzes for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			long videoId = 0L;
			try {
				videoId = Long.parseLong(request.getParameter("VideoId"));
			} catch (Exception e) {}
			int segment = 0;
			try {
				segment = Integer.parseInt(request.getParameter("Segment"));
			} catch (Exception e) {
				response.sendRedirect("/Video.jsp?VideoId=" + videoId + "&sig=" + user.getTokenSignature());
			}
			
			out.println(showQuizlet(user,videoId,segment));
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			out.println(scoreQuizlet(user,request));
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public String showQuizlet(User user,long videoId,int segment) {
		StringBuffer buf = new StringBuffer();
		try {
			Assignment a = null;
			long assignmentId = user.getAssignmentId();  // should be non-zero for LTI user
			if (assignmentId>0) {
				a = ofy().load().type(Assignment.class).id(assignmentId).now();
				videoId = a.videoId;
			}
		
			Video v = ofy().load().type(Video.class).id(videoId).now();
			if (v.breaks==null) v.breaks = new int[0];
			if (v.nQuestions==null) v.nQuestions = new int[0];
			if (v.questionKeys==null) v.questionKeys = new ArrayList<Key<Question>>();

			buf.append("<h4>Please answer these questions before resuming the video:</h4>\n");

			// Check to see if this user has any pending videos on this topic in the last 90 minutes.
			// Normally, a new VideoTransaction is created when the LTILaunchRequest is made, but sometimes things happen...
			Date now = new Date();
			Date then = new Date(now.getTime()-90*60000);  // 90 minutes ago
			VideoTransaction vt = ofy().load().type(VideoTransaction.class).filter("userId",user.getHashedId()).filter("videoId",v.id).order("-downloaded").first().now();

			if (vt == null || vt.graded != null || vt.downloaded.before(then)) {  // create a new VideoTransaction
				int possibleScore = 0;
				for (int i=0;i<v.breaks.length;i++) possibleScore += (v.nQuestions[i]<this.nQuestions?v.nQuestions[i]:this.nQuestions);
				vt = new VideoTransaction(v.id,v.title,v.breaks.length,user.id,assignmentId,possibleScore,user.getLisResultSourcedid());
				ofy().save().entity(vt).now();
			}

			if (segment == v.breaks.length) return finishQuizlet(user,vt); // we are at the end of video; no more quizlets; shortcut to show results

			// get a List of available Question keys for this segment of this video
			int counter = 0; // skip over this many questions to the ones in the current quizlet
			for (int i=0;i<segment;i++) counter += v.nQuestions[i];
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (int j=counter;j<counter+v.nQuestions[segment];j++) questionKeys.add(v.questionKeys.get(j));

			if (questionKeys.size()==0) return "Sorry, there are no questions at this point. <a href='/Video.jsp?VideoId=" + videoId + "&Segment=" + segment+1 + "&sig=" + user.getTokenSignature() + "'>Continue the video</a><p>";

			// Randomly select the questions to be presented, eliminating each from questionKeys as they are printed
			Random rand = new Random(vt.id % Integer.MAX_VALUE);  // create random number generator to select quiz questions
			nQuestions = (nQuestions < questionKeys.size()?nQuestions:questionKeys.size());
		
		//buf.append("Questions to be presented="+nQuestions+".<hr>");
		
		buf.append("<form id=quizlet method=post action='/VideoQuiz' onSubmit='return ajaxSubmitQuiz()'>");
				
		int i = 0;
		buf.append("<OL>\n");
		while (i<nQuestions && questionKeys.size()>0) {
			Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
			Question q = ofy().load().key(k).safe();
			// by this point we should have a valid question
			i++;  // this counter keeps track of the number of questions presented so far
			// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
			// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
			long seed = Math.abs(vt.id - q.id);
			if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
			q.setParameters(seed); // the values are subtracted to prevent (unlikely) overflow

			buf.append("\n<li>" + q.print() + "<br></li>\n");
		}
		buf.append("</OL>");

		if (a!=null) buf.append("<input type=hidden name=AssignmentId value='" + a.id + "'>");
		
		buf.append("<input type=hidden name=sig value=" + user.getTokenSignature() + ">");
		buf.append("<input type=hidden name=VideoId value=" + v.id + ">");
		buf.append("<input type=hidden name=VideoTransactionId value=" + vt.id + ">");
		buf.append("<input type=hidden name=Segment value=" + segment + ">");
		buf.append("<input type=submit value='Submit and Resume the Video'> or <a href=/Video.jsp?Segment=" + segment + "&VideoId=" + v.id + "&sig=" + user.getTokenSignature() + ">Replay This Segment</a>");
		buf.append("</form>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	public String scoreQuizlet(User user, HttpServletRequest request) throws Exception {
		StringBuffer debug = new StringBuffer("starting...");
		Date now = new Date();
		VideoTransaction vt = null;
		int segment = 0;
		try {
		// Retrieve the videoTransaction and segment
		try {
			segment = Integer.parseInt(request.getParameter("Segment"));
			debug.append("segment.");
			vt = ofy().load().type(VideoTransaction.class).id(Long.parseLong(request.getParameter("VideoTransactionId"))).safe();
			debug.append("vt.");
		} catch (Exception e) {  // this section is reached if a quizlet is not included at the end (no questions)
			debug.append("caught.");
			Date then = new Date(now.getTime()-90*60000);  // 90 minutes ago
			long videoId = Long.parseLong(request.getParameter("VideoId"));
			debug.append("vId.");
			vt = ofy().load().type(VideoTransaction.class).filter("userId",user.getHashedId()).filter("videoId",videoId).filter("graded",null).filter("downloaded >",then).first().now();
			debug.append("vt.");
		}
		
		// Make a list of the question keys. Non-numeric inputs are ignored (catch and continue).
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
			try {
				questionKeys.add(Key.create(Question.class,Long.parseLong((String) e.nextElement())));
			} catch (Exception e2) {}
		}
		debug.append("qkeys.");
		
		StringBuffer missedQuestions = new StringBuffer();	// contains solutions to questions answered incorrectly		
		List<Response> responses = new ArrayList<Response>();
		
		if (questionKeys.size()>0) {  // This is the main scoring loop:			
			int quizletScore = 0;
			Map<Key<Question>,Question> quizQuestions = ofy().load().keys(questionKeys);
			for (Key<Question> k : questionKeys) {
				try {
					String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
					if (studentAnswer != null) {
						for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
						if (studentAnswer[0].length() > 0) { // an answer was submitted
							Question q = quizQuestions.get(k);
							long seed = Math.abs(vt.id - q.id);
							if (seed==-1) seed--;  // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
							q.setParameters(seed);
							int score = q.isCorrect(studentAnswer[0])?q.pointValue:0;
							responses.add(new Response("VideoQuiz",0,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));
							quizletScore += score;
							if (score == 0) {  
								// include question in list of incorrectly answered questions
								missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer[0]) + "</LI>\n");
							}
						}
					}
				} catch (Exception e2) {
					continue;  // this parameter does not correspond to a questionId
				}
			}
			debug.append("scoring done.");
			ofy().save().entities(responses);
			vt.quizletScores.set(segment,quizletScore);
			vt.missedQuestions.set(segment,missedQuestions.toString());
		}
		ofy().save().entity(vt).now();
		debug.append("done.");
		} catch (Exception e) {
			debug.append(e.getMessage());
			return debug.toString();
		}
		return showQuizlet(user,vt.videoId,segment+1);  // return the next quizlet
	}
	
	String finishQuizlet(User user, VideoTransaction vt) {
		StringBuffer buf = new StringBuffer();  // prepare a summary of the quizlet scores and missed questions
		
		boolean noQuizlets = vt.possibleScore==0;
		
		if (noQuizlets && vt.assignmentId>0) {  // give 1 pt credit for watching the assignment
			vt.score = 1;
			vt.possibleScore = 1;
		} else {
			vt.score = 0;
			for (int s : vt.quizletScores) vt.score += s;
		}				
		vt.graded = new Date();  // vt will be saved after missed Questions List is printed later (line 322)
		
		// Try to post the score to the student's LMS:
		// Retrieve the assignment, if it exists (may be null for anonymous users)
		boolean reportScoreToLms = false;
		Assignment a = null;
		try {
			long assignmentId = user.getAssignmentId();
			if (assignmentId>0L) {
				a = ofy().load().type(Assignment.class).id(assignmentId).now();
				Score s = Score.getInstance(user.id,a);
				ofy().save().entity(s).now();
				reportScoreToLms = a.lti_ags_lineitem_url != null || (a.lis_outcome_service_url != null && user.getLisResultSourcedid() != null);
				if (reportScoreToLms) {
					Queue queue = QueueFactory.getDefaultQueue();  // Task queue used for reporting scores to the LMS
					queue.add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(user.id,"UTF-8")));  // put report into the Task Queue
				}		
			}
		} catch (Exception e) {}

		if (noQuizlets) {
			buf.append("<h4>Thanks for watching</h4>");
			buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");
			return buf.toString();
		}
		
		buf.append("<h3>Video Quiz Results - " + vt.videoTitle + "</h3>\n");
		
		if (user.isAnonymous()) buf.append("<font color=red>Anonymous User</font><br>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		buf.append(df.format(new Date()));
		
		buf.append("<h4>Your score on this quiz is " + vt.score + " point" + (vt.score==1?"":"s") + " out of a possible " + vt.possibleScore + " points.</h4>\n");
	
		// Seek some feedback for ChemVantage:
		if (vt.possibleScore > 0 && vt.score == vt.possibleScore) {
			buf.append("<H2>Congratulations on a perfect score! Good job.</H2>\n");
			buf.append("Please rate your overall experience with ChemVantage:<br />\n"
					+ "<span id='vote' style='font-family:tahoma; color:red;'>(click a star):</span><br>");

			for (int iStar=1;iStar<6;iStar++) {
				buf.append("<img src='images/star1.gif' id='" + iStar + "' "
						+ "style='width:30px; height:30px;' "
						+ "onmouseover=showStars(this.id); onClick=setStars(this.id); onmouseout=showStars(0); />");
			}
			buf.append("<span id=sliderspan style='opacity:0'>"
					+ "<input type=range id=slider min=1 max=5 value=3 onfocus=document.getElementById('sliderspan').style='opacity:1';showStars(this.value); oninput=showStars(this.value);>"
					+ "<button onClick=setStars(document.getElementById('slider').value);>submit</button>"
					+ "</span>");
			buf.append("<p>");

		} else {
			buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");
		
			String missedQuestions = "";
			for (String s : vt.missedQuestions) missedQuestions += s;
			
			if (!missedQuestions.isEmpty()) { // Give the correct answers to missed questions:
				buf.append("The following questions were answered incorrectly:<ol>" + missedQuestions + "</ol>");
			}
		}
		vt.missedQuestions.clear();
		ofy().save().entity(vt);
		
		if (!reportScoreToLms && !user.isAnonymous()) {
			buf.append("<b>Please note:</b> Your score was not reported back to the grade book of your class "
					+ "LMS because the LTI launch request did not contain enough information to do this. "
					+ (user.isInstructor()?"For instructors this is common.":"") + "<p>");				
		}

		// If a==null this is an anonymous user, otherwise is an LTI user:
		buf.append((a==null?"<a href=/Video.jsp?VideoId=" + vt.videoId + "&Segment=0&sig=" + user.getTokenSignature() + ">Watch this video again</a> or go back to the <a href=/>ChemVantage home page</a> " :
				"You may take this quiz again by clicking the assignment link in your learning management system ")			
				+ "or <a href=/Logout>logout of ChemVantage</a>.");

		return buf.toString();	
		
	}
/*	
	String showScores (User user, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Your Quiz Transactions</h2>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		Assignment a = null;
		try {
			long assignmentId = user.getAssignmentId();
			a = ofy().load().type(Assignment.class).id(assignmentId).safe();
		} catch (Exception e) {
			buf.append("Invalid assignment.");
			return buf.toString();
		}

		try {
			buf.append("Assignment Number: " + a.id + "<br>");
			Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
			buf.append("Topic: "+ t.title + "<br>");
			buf.append("Valid: " + df.format(now) + "<p>");
			
			List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).filter("userId",user.id).filter("assignmentId",a.id).order("downloaded").list();
			
			if (qts.size()==0) {
				buf.append("Sorry, we did not find any records for you in the database for this assignment.<p>"
						+ "<a href=Quiz?AssignmentId=" + a.id 
						+ "&sig=" + user.getTokenSignature() 
						+ ">Take me back to the quiz now.</a>");
			} else {
				// create a fresh Score entity to calculate the best score on this assignment
				Score s = Score.getInstance(user.id, a);

				buf.append("Your best score on this assignment is " + Math.round(10*s.getPctScore())/10. + "%.<br>");

				// try to validate the score with the LMS grade book entry
				String lmsScore = null;
				try {
					double lmsPctScore = 0;
					boolean gotScoreOK = false;
					
					if (a.lti_ags_lineitem_url != null) {  // LTI version 1.3
						lmsScore = LTIMessage.readUserScore(a,user.id);
						try {
							lmsPctScore = Double.parseDouble(lmsScore);
							gotScoreOK = true;
						} catch (Exception e) {
						}
					}
					else if (a.lis_outcome_service_url != null && s.lis_result_sourcedid != null) {  // LTI version 1.1
						String messageFormat = "application/xml";
						String body = LTIMessage.xmlReadResult(s.lis_result_sourcedid);
						String oauth_consumer_key = user.id.substring(0, user.id.indexOf(":"));
						String replyBody = new LTIMessage(messageFormat,body,a.lis_outcome_service_url,oauth_consumer_key).send();

						if (replyBody.contains("success")) {
							int beginIndex = replyBody.indexOf("<textString>") + 12;
							int endIndex = replyBody.indexOf("</textString>");
							lmsScore = replyBody.substring(beginIndex,endIndex);
							lmsPctScore = 100.*Double.parseDouble(lmsScore);
							gotScoreOK = true;
						}
					}
					
					if (gotScoreOK && Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
						buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
					} else if (gotScoreOK) { // there is a significant difference between LMS and ChemVantage scores. Please explain:
						buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
								+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<br>"
								+ "If you think this may be due to a stale score, you may submit this assignment for grading,<br>"
								+ "even for a score of zero, and ChemVantage will try to refresh your best score to the LMS.<p>");
					} else throw new Exception();
				} catch (Exception e) {
					buf.append("ChemVantage was unable to retrieve your score for this assignment from the LMS.<br>");
					if (s.score==0 && s.numberOfAttempts<=1) buf.append("It appears that you may not have submitted a score for this quiz yet. ");
					if (user.isInstructor()) buf.append("Some LMS providers do not store scores for instructors.");
					buf.append("<p>");
				}

				buf.append("<table><tr><th>Transaction Number</th><th>Downloaded</th><th>Quiz Score</th></tr>");
				for (QuizTransaction qt : qts) {
					buf.append("<tr><td>" + qt.id + "</td><td>" + df.format(qt.downloaded) + "</td><td align=center>" + (qt.graded==null?"-":100.*qt.score/qt.possibleScore + "%") +  "</td></tr>");
				}
				buf.append("</table><br>Missing scores indicate quizzes that were downloaded but not submitted for scoring.<p>");
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String showSummary(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		if (a==null) return "No assignment was specified for this request.";
		
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		if (a.lti_ags_lineitem_url != null && a.lti_nrps_context_memberships_url != null) {
			try { // code for LTI version 1.3
				Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();

				buf.append("<h3>" + a.assignmentType + " - " + t.title + "</h3>");
				//buf.append("Group: " + g.description + "<br>");
				buf.append("Assignment ID: " + a.id + "<br>");
				buf.append("Valid: " + new Date() + "<p>");
				buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
						+ "and may or may not include user's names or emails, depending on the settings of your LMS. The easiest way to "
						+ "resolve any discrepancies between scores reported by the LMS grade book and ChemVantage is for the user to "
						+ "submit the assignment again (even for a score of zero). This causes ChemVantage to recalculate the "
						+ "user's best score and report it to the LMS. However, some discrepancies are to be expected, for example "
						+ "if the instructor adjusts a score in the LMS manually or if an assignment was submitted after the "
						+ "deadline and was not accepted by the LMS.<p>");

				Map<String,String> scores = LTIMessage.readMembershipScores(a);
				if (scores==null) scores = new HashMap<String,String>();  // in case service call fails
				
				buf.append("We downloaded " + scores.size() + " scores from your LMS.<br>");
				
				Map<String,String[]> membership = LTIMessage.getMembership(a);
				if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails
				
				buf.append("There are " + membership.size() + " members of this group.<p>");
				
				Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
				Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
				String platform_id = d.getPlatformId() + "/";
				
				for (String id : membership.keySet()) {
					String userId = platform_id + id;
					keys.put(id,Key.create(Key.create(User.class,userId),Score.class,a.id));
				}
				Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
				
				buf.append("<table><tr><th>User ID</th><th>Role</th><th>Name</th><th>Email</th><th>LMS Score</th><th>CV Score</th></tr>");
				for (Map.Entry<String,String[]> entry : membership.entrySet()) {
					if (entry == null) continue;
					String s = scores.get(entry.getKey());
					Score cvScore = cvScores.get(keys.get(entry.getKey()));
					buf.append("<tr><td>" + entry.getKey() + "</td>"
							+ "<td>" + entry.getValue()[0] + "</td>"
							+ "<td>" + entry.getValue()[1] + "</td>"
							+ "<td>" + entry.getValue()[2] + "</td>"
							+ "<td align=center>" + (s == null?" - ":s + "%") + "</td>"
							+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td></tr>");
				}
				buf.append("</table>");
				return buf.toString();
			} catch (Exception e) {
				buf.append(e.toString());
			}
		} else {
			buf.append("Sorry, there is not enough information available from your LMS to support this request.<p>");			
			buf.append("<a href=/Quiz?sig=" + user.getTokenSignature() + ">Return to this quiz</a>.<p>");
		}
		return buf.toString();
	}
	*/
}
