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
import static com.googlecode.objectify.ObjectifyService.key;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

@WebServlet("/Homework")
public class Homework extends HttpServlet {

	private static final long serialVersionUID = 137L;	
	static int retryDelayMinutes = 1;  // minimum time between answer submissions for any single question

	public String getServletInfo() {
		return "This servlet presents a homework assignment for the user.";
	}

	public void init (ServletConfig config) throws ServletException {
		super.init(config);
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			switch (userRequest) {
			case "ShowScores":
				String forUserId = request.getParameter("ForUserId");
				out.println(Subject.header("ChemVantage Scores") + showScores(user,a,forUserId) + Subject.footer);
				break;
			case "ShowSummary":
				out.println(Subject.header("Your Class ChemVantage Scores") + showSummary(user, a) + Subject.footer);
				break;
			case "Review":
				forUserId = request.getParameter("ForUserId");
				String forUserName = request.getParameter("ForUserName");
				out.println(Subject.header("Homework Review") + reviewSubmissions(user,a,forUserId,forUserName));
				break;
			case "AssignHomeworkQuestions":
				if (user.isInstructor()) out.println(Subject.header("Customize ChemVantage Homework Assignment") + selectQuestionsForm(user,a,request) + Subject.footer);
				else out.println(Subject.header("Customize ChemVantage Homework Assignment") + "<h2>Forbidden</h2>You must be signed in as the instructor to perform this functuon." + Subject.footer);
				break;
			case "SynchronizeScore":
				out.println(synchronizeScore(user,a,request.getParameter("ForUserId")));
				break;
			default:
				long hintQuestionId = 0L;
				try {
					hintQuestionId = Long.parseLong(request.getParameter("Q"));
				} catch (Exception e) {}
				out.println(Subject.header("ChemVantage Homework") + printHomework(user,a,hintQuestionId) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("Invalid user token (may have expired).");
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";

			switch (userRequest) {
			case "UpdateAssignment":
				a.updateQuestions(request);
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Save New Title":
				a.title = request.getParameter("AssignmentTitle");
				ofy().save().entity(a).now();
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Set Allowed Attempts":
				a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
				try {
					a.attemptsAllowed = Integer.parseInt(request.getParameter("AttemptsAllowed"));
					if (a.attemptsAllowed<1) a.attemptsAllowed = null;
				} catch (Exception e) {
					a.attemptsAllowed = null;
				}
				ofy().save().entity(a).now();
				out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				break;
			case "Synchronize Scores":
				if (synchronizeScores(user,a,request)) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				else out.println("Synchronization request failed.");
				break;
			default: out.println(Subject.header("ChemVantage Homework Results") + printScore(user,a,request) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	static String instructorPage(User user,Assignment a) {
		if (!user.isInstructor()) return "<h2>You must be logged in as an instructor to view this page</h2>";
		
		StringBuffer buf = new StringBuffer();		
		try {
			if (a.title==null) {  // legacy assignment only provided topicId
				Topic t = ofy().load().type(Topic.class).id(a.topicId).now();
				a.title = t.title;
				if (a.conceptIds.isEmpty()) a.conceptIds = t.conceptIds;
				ofy().save().entity(a).now();
			}
			boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
			
			buf.append("<h1>Homework</1><h2>" + a.title + "</h2>");
			buf.append("<h3>Instructor Page</h3>");
			
			if (a.attemptsAllowed==null || a.attemptsAllowed<1) buf.append("This assignment allows an unlimited number of submissions for each homework question.<br/>");
			else buf.append("This assignment allows only " + a.attemptsAllowed + (a.attemptsAllowed==1?" submission":" submissions") + " for each homework question.<br/>");
			buf.append("<br/>");
			
			buf.append("From here, you may<UL>"
					+ "<LI><a href='/Homework?UserRequest=AssignHomeworkQuestions&sig=" + user.getTokenSignature() + "'>Customize this assignment</a> by selecting the assigned question items and selecting the number of submissions allowed for each question.</LI>"
					+ (supportsMembership?"<LI><a href='/Homework?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>Review your students' homework scores</a></LI>":"")
					+ "</UL><br/>");
			
			buf.append("<a href='/Homework?sig=" + user.getTokenSignature() + "' class='btn'>Show This Assignment</a><br/><br/>");
			
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + e.getMessage());
		}
		return buf.toString();
	}
	
	static String printHomework(User user, Assignment a) throws IOException {
		return printHomework(user,a,0L);
	}
	
	static String printHomework(User user, Assignment hwa, long hintQuestionId) throws IOException  {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		
		try {
			if (hwa==null) {  // anonymous user; print an assignment on Chapter 1 of the first smartText entity
				hwa = new Assignment();
				hwa.id = 0L;
				hwa.assignmentType = "Homework";
				Text text = ofy().load().type(Text.class).filter("smartText",true).first().now();
				Chapter ch = text.chapters.get(0);
				hwa.title = ch.title;
				hwa.textId = text.id;
				hwa.chapterNumber = ch.chapterNumber;
			} else if (hwa.title==null) {  // legacy Homework assignment only provided topicId
				Topic t = ofy().load().type(Topic.class).id(hwa.topicId).now();
				hwa.title = t.title;
				if (hwa.conceptIds.isEmpty()) hwa.conceptIds = t.conceptIds;
				ofy().save().entity(hwa).now();
			}
			
			// get a List of homework questionKeys for this topic:
			List<Key<Question>> allQuestionKeys = new ArrayList<Key<Question>>();
			try {
				Text text = ofy().load().type(Text.class).id(hwa.textId).now();
				Chapter ch = null;
				for (Chapter c : text.chapters) {
					if (c.chapterNumber == hwa.chapterNumber) ch = c;
				}
				for (Long cId : ch.conceptIds) allQuestionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).keys().list());
			} catch (Exception e) {  // for legacy assignments without a textId
				for (Long cId : hwa.conceptIds) allQuestionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).keys().list());	
			}
			debug.append(allQuestionKeys.size() + " concept questionKeys retrieved. ");
			
			Map<Key<Question>,Question> allQuestions = new HashMap<Key<Question>,Question>(ofy().load().keys(allQuestionKeys));
			
			if (!allQuestionKeys.containsAll(hwa.questionKeys)) {  // might be missing a few questions due to customization
				for (Key<Question> k : hwa.questionKeys) {
					try {
						if (!allQuestionKeys.contains(k)) {
							allQuestions.put(k, ofy().load().key(k).safe());
							allQuestionKeys.add(k);
						}
					} catch (Exception e) {}
				}
			}
			debug.append("Total number of questions = " + allQuestions.size());
			
			// START the presentation of the Homework assignment
			buf.append("<h1>Homework Exercises</h1><h2>" + hwa.title + "</h2>");

			buf.append("Homework Rules<UL>");
			if (hwa.attemptsAllowed==null)
				buf.append("<LI>You may rework problems and resubmit answers as many times as you wish, to improve your score.</LI>");
			else buf.append("<LI>For each problem you are allowed " + hwa.attemptsAllowed + (hwa.attemptsAllowed==1?" attempt.":" attempts.") + "</LI>");
			buf.append("<LI>There is a retry delay of " + retryDelayMinutes + " minute" +(retryDelayMinutes==1?"":"s") + " between answer submissions for any single question.</LI>");
			buf.append("<LI>Most questions are customized, so the correct answers are different for each student.</LI>");
			if (!user.isAnonymous()) buf.append("\n<LI>A checkmark will appear to the left of each correctly solved problem.</LI>");
			buf.append("</UL>");

			// Review the HWTransactions for this user to record which problems have been solved for this assignment and retrieve the current showWork strings:
			List<Long> solvedQuestions = new ArrayList<Long>();
			Map<Long,String> workStrings = new HashMap<Long,String>();
			List<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",hwa.id).order("-graded").list();
			Map<Long,Integer> priorAttempts = new HashMap<Long,Integer>();
			
			for (HWTransaction ht : hwTransactions) {
				int att = priorAttempts.get(ht.questionId)==null?1:priorAttempts.get(ht.questionId)+1; // prior attempts of this question
				priorAttempts.put(ht.questionId, att); // maintain a Map of prior attempts for each question
				if (solvedQuestions.contains(ht.questionId)) continue;
				if (ht.score > 0) solvedQuestions.add(ht.questionId);
				if (workStrings.containsKey(ht.questionId)) continue;
				workStrings.put(ht.questionId,ht.showWork);
			}
			
			StringBuffer assignedQuestions = new StringBuffer();
			assignedQuestions.append("<div style='display:table'>");
			StringBuffer optionalQuestions = new StringBuffer();
			optionalQuestions.append("<div style='display:table'>");
			
			// This is the main loop for presenting assigned and optional questions in order of increasing difficulty:
			int i=1;
			int j=1;
			for (Key<Question> k : allQuestionKeys) {
				Question q = allQuestions.get(k); 
				boolean assigned = hwa.questionKeys.contains(k);
				StringBuffer questionBuffer = new StringBuffer("<div style='display:table-row'><div style='display:table-cell;font-size:small'>");
				String hashMe = user.getId() + hwa.id;
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				
				Integer attemptsRemaining = null;
				if (hwa.attemptsAllowed!=null) {
					attemptsRemaining = hwa.attemptsAllowed - (priorAttempts.get(q.id)==null?0:priorAttempts.get(q.id));
					if (attemptsRemaining < 0) attemptsRemaining = 0;
				}
				
				if (solvedQuestions.contains(q.id)) questionBuffer.append("<IMG SRC=/images/checkmark.gif ALT='Check mark' align=top>&nbsp;");
				//else if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) questionBuffer.append("<br/><a href='" + q.learn_more_url + "' target=_blank><img src=/images/learn_more.png alt='learn more here' align=top /><br/>learn</a>&nbsp;");
				
				questionBuffer.append("</div>");
				
				questionBuffer.append("<FORM METHOD=POST ACTION=/Homework onsubmit=waitForScore('" + q.id + "'); >"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ (hwa==null?"":"<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
						+ "<div style='display:table-cell;vertical-align:text-top;padding-right:10px;'><b>" + (assigned?i:j) + ".</b></div>"
						+ "<div style='display:table-cell'>" + q.print(workStrings.get(q.id),"",attemptsRemaining) 
						+ (q.id == hintQuestionId?"Hint:<br>" + q.getHint():"")
						+ "<INPUT id=sub" + q.id + " TYPE=SUBMIT class='btn' VALUE='Grade This Exercise'><p>"
						+ "</div></div></FORM>\n");
				if (assigned) {
					assignedQuestions.append(questionBuffer);
					i++; 
				} else {
					optionalQuestions.append(questionBuffer);
					j++;
				}
			}
			
			// Print the list of problems for the student
			buf.append((i>1?"<h2>Assigned Exercises</h2>":"") + assignedQuestions + "</div>");
			if (i>1 && j>1) {
				buf.append("<hr><hr><span style='font-weight: bold;'> **** END OF ASSIGNED EXERCISES **** </span><hr><hr>");
				buf.append("<h2>Optional Exercises</h2>" + optionalQuestions + "</div>");
			}
			
			buf.append("<script>function showWorkBox(qid) {\n"
					+ "	if (qid==0) return;\n"
					+ "    document.getElementById('showWork'+qid).style.display='';\n"
					+ "    document.getElementById('answer'+qid).placeholder='Enter your answer here';\n"
					+ "}</script>");
			} catch (Exception e) {
			// buf.append("Sorry, there was an unexpected error: " + e.getMessage()==null?e.toString():e.getMessage());
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Error during Homework.printHomework: ", e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString() + "<br/>" + user.getId());
			return Logout.now(user);
		}
		return buf.toString();
	}

	static String printScore(User user,Assignment hwa,HttpServletRequest request) throws IOException {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Start...");
		JsonObject api_score = null;	// this contains the score and feedback for essay questions from ChatGPT
		
		try {
			// The Homework grader scores only one Question at a time, so first identify and load it
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = key(Question.class,questionId);
			Question q = ofy().load().key(k).safe();
			
			if (hwa==null) {  // anonymous user; use the assignment on Chapter 1 of the first smartText entity
				hwa = new Assignment();
				hwa.id = 0L;
				hwa.assignmentType = "Homework";
				Text text = ofy().load().type(Text.class).filter("smartText",true).first().now();
				hwa.title = text.chapters.get(0).title;
			}
			
			if (hwa.attemptsAllowed != null) {
				List<HWTransaction> transactions = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("questionId",questionId).list();
				List<HWTransaction> priorAttempts = new ArrayList<HWTransaction>();
				for (HWTransaction t : transactions) if (t.assignmentId==hwa.id.longValue()) priorAttempts.add(t);
				
				if (priorAttempts.size() >= hwa.attemptsAllowed) {
					buf.append("<h1>Homework</h1>"
						+ "<h2>Sorry, you are only allowed " + hwa.attemptsAllowed + " attempt" + (hwa.attemptsAllowed==1?"":"s") + " for this question.</h2>");
					DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
					buf.append("<table><tr><th>Transaction Number</th><th>Graded</th><th>Score</th></tr>");
					for (HWTransaction hwt : priorAttempts) buf.append("<tr><td>" + hwt.id + "</td><td>" + df.format(hwt.graded) + "</td><td align=center>" + hwt.score +  "</td></tr>");
					buf.append("</table><br/>");
					
					buf.append("<a href=/Homework?AssignmentId=" + hwa.id + "&sig=" + user.getTokenSignature() + ">Return to this homework assignment" + "</a> or "
							+ "<a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a> ");
				
					return buf.toString();
				}
			}
			
			// Set the Question parameters for this user (this is why we made a copy, to prevent thread collisions with a class variable)
			String hashMe = user.getId() + hwa.id;
			q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
			debug.append("question parameters set with "+hashMe.hashCode()+"...");
			
			String studentAnswer = orderResponses(request.getParameterValues(Long.toString(questionId)));
			
			String showWork = request.getParameter("ShowWork"+q.id);
			if (showWork==null) showWork="";  // required because later we check to see if showWork.isEmpty()
			
			debug.append("student answer:"+studentAnswer+"...");
			
			// This section checks for recent submissions to enforce the retry delay (discourages guessing)
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Date now = new Date();
			Date minutesAgo = new Date(now.getTime()-retryDelayMinutes*60000);  // about 2 minutes ago
			List<HWTransaction> recentTransactions = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("questionId",q.id).filter("graded >",minutesAgo).list();
			long secondsRemaining = 0;
			boolean solvedRecently = false;
			
			if (recentTransactions.size()>0) {  // may be more than one if multiple browser sessions are active for one user!
				Date lastSubmission = new Date(0L);
				for (HWTransaction ht : recentTransactions) {
					if (ht.graded.after(lastSubmission)) lastSubmission = ht.graded;
					if (ht.score>0) solvedRecently=true;
				}
				secondsRemaining = retryDelayMinutes*60 - (now.getTime()-lastSubmission.getTime())/1000;
			}
			debug.append("recent transactions = "+recentTransactions.size() + "...");
			if (secondsRemaining > 0 && !solvedRecently) {  
				buf.append("<h1>Homework</h1>"
						+ "<h2>Please Wait For The Retry Delay To Complete</h2>");
				//buf.append(df.format(now));
				buf.append("<span id=timer0 style='color: #EE0000'></span><br/>");
				buf.append("Please take these few moments to check your work carefully.  You can sometimes find alternate routes to the "
						+ "same solution, or it may be possible to use your answer to back-calculate the data given in the problem.<br/><br/>");
				//if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) 
				//	buf.append("<img src=/images/learn_more.png alt='learn more here' /> You can learn more about this topic at <a href='" 
				//	+ q.learn_more_url + "' target=_blank>" + q.learn_more_url + "</a><br/><br/>");
				buf.append("Alternatively, you may wish to "
						+ "<a href=/Homework?AssignmentId=" + hwa.id
						+ "&sig=" + user.getTokenSignature() + ">" 
						+ "return to this homework assignment</a> to work on another problem.<p>");
				buf.append("<FORM NAME=Homework METHOD=POST ACTION=Homework onsubmit=waitForRetryScore(); >"
						+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" +(hwa.id==null?0:hwa.id) + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ q.print(showWork,studentAnswer) + "<br>");

				buf.append("<INPUT TYPE=SUBMIT id='RetryButton' class='btn' DISABLED=true VALUE='Please wait' /></FORM><br/><br/>");
				buf.append("<script>"
						+ "startTimers('" + (now.getTime() + secondsRemaining*1000) + "');"
						+ "function timesUp() {"
						+ "document.getElementById('RetryButton').disabled=false;"
						+ "document.getElementById('RetryButton').value='Grade This Exercise';"
						+ "}"
						+ "</script>");

				return buf.toString();
			}
			
			buf.append("<h1>Homework Results</h1><h2>" + hwa.title + "</h2>\n");
			
			buf.append(df.format(now));
			
			int studentScore = q.isCorrect(studentAnswer)?q.pointValue:0;
			int possibleScore = q.pointValue;
						
			debug.append("score is " + studentScore + " out of " + possibleScore + " points...");
			HWTransaction ht = null;
			
			showWork = request.getParameter("ShowWork"+questionId);
			BufferedReader reader = null;
			if (!studentAnswer.isEmpty()) { // an answer was submitted
				switch (q.getQuestionType()) {
				case 6:  // Handle five-star rating response
					studentScore = q.pointValue;  // full marks for submitting a response
					break;
				case 7:  // New section for scoring essay questions with Chat GPT
					if (studentAnswer.length()>800) studentAnswer = studentAnswer.substring(0,799);
					JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
					api_request.addProperty("model","gpt-4");
					//api_request.addProperty("model","gpt-3.5-turbo");
					api_request.addProperty("max_tokens",200);
					api_request.addProperty("temperature",0.2);
					JsonObject m = new JsonObject();  // api request message
					m.addProperty("role", "user");
					String prompt = "Question: \"" + q.text +  "\"\n My response: \"" + studentAnswer + "\"\n "
							+ "Using JSON format, give a score for my response (integer in the range 0 to 5) "
							+ "and feedback for how to improve my response.";
					m.addProperty("content", prompt);
					JsonArray messages = new JsonArray();
					messages.add(m);
					api_request.add("messages", messages);
					URL u = new URL("https://api.openai.com/v1/chat/completions");
					HttpURLConnection uc = (HttpURLConnection) u.openConnection();
					uc.setRequestMethod("POST");
					uc.setDoInput(true);
					uc.setDoOutput(true);
					uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
					uc.setRequestProperty("Content-Type", "application/json");
					uc.setRequestProperty("Accept", "application/json");
					OutputStream os = uc.getOutputStream();
					byte[] json_bytes = api_request.toString().getBytes("utf-8");
					os.write(json_bytes, 0, json_bytes.length);           
					os.close();
						
					reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
					JsonObject api_response = JsonParser.parseReader(reader).getAsJsonObject();
					reader.close();
					
					// get the ChatGPT score from the response:
					try {
						String content = api_response.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
						api_score = JsonParser.parseString(content).getAsJsonObject();
						studentScore = api_score.get("score").getAsInt();
						studentScore = studentScore>=4?q.pointValue:0;
					} catch (Exception e) {}
					break;
				default:
				}
				
				ht = new HWTransaction(q.id,user.getHashedId(),now,studentScore,hwa.id,possibleScore,showWork);
				ht.studentAnswer = studentAnswer;
				ht.correctAnswer = q.getCorrectAnswer();				
				ofy().save().entity(ht).now();
				
				// create/update/store a HomeworkScore object
				try {  // throws exception if hwa==null
					if (!user.isAnonymous() && hwa.questionKeys.contains(k) && hwa.lti_ags_lineitem_url != null) {
						q.addAttemptSave(studentScore>0);
						Score s = Score.getInstance(user.getId(),hwa);
						ofy().save().entity(s).now();
						String payload = "AssignmentId=" + hwa.id + "&UserId=" + URLEncoder.encode(user.getId(),"UTF-8");
						Utilities.createTask("/ReportScore",payload);
						//QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",hwa.id.toString()).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue	
					}
				} catch (Exception e2) {
				}
			}
			// Send response to the user:
			if (studentScore > 0) {
				switch (q.getQuestionType()) {
				case 6: // Five star response
					buf.append("<h3>Thank you for the rating.</h3>");
					buf.append(q.printAllToStudents(studentAnswer) + "<br/>");
					break;
				case 7: // Essay response
					try {
						studentAnswer += "<br/><br/><b>Feedback: </b>" + api_score.get("feedback").getAsString() 
								+ "<br/><br/><b>Score: </b>" + api_score.get("score") + "/5" + "<br/>";
					} catch (Exception e) {
						buf.append("Oops, an error occurred. Please report this below.<br/>" 
								+ (reader==null?"No input stream.":reader.toString()) + "<br/>");
					}
				default:
					buf.append("<h3>Congratulations. You answered the question correctly. <IMG SRC=/images/checkmark.gif ALT='Check mark' align=bottom /></h3>"
						+ (!user.isAnonymous()?"<a id=showLink href=# onClick=document.getElementById('solution').style='display:inline';this.style='display:none'>(show me)</a>":"") 
						+ "<br/>");
				}
			}
			else if (studentAnswer.length() > 0) {
				switch (q.getQuestionType()) {
					case 5:  // Numeric question
						try {
							@SuppressWarnings("unused")
							double dAnswer = Double.parseDouble(q.parseString(studentAnswer));  // throws exception for non-numeric answer
							if (!q.agreesToRequiredPrecision(studentAnswer)) buf.append("<h3>Incorrect Answer <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h3>Your answer does not " + (q.requiredPrecision==0?"exactly match the answer in the database. ":"agree with the answer in the database to within the required precision (" + q.requiredPrecision + "%).<br/><br/>"));
							else if (!q.hasCorrectSigFigs(studentAnswer)) buf.append("<h3>Almost there!</h3>It appears that you've done the calculation correctly, but your answer does not have the correct number of significant figures appropriate for the data given in the question. "
									+ "If your answer ends in a zero, be sure to include a decimal point to indicate which digits are significant or (better!) use <a href=https://en.wikipedia.org/wiki/Scientific_notation#E_notation>scientific E notation</a>.<br/><br/>");
						}
						catch (Exception e2) {
							buf.append("<h3>Wrong Format</h3>This question requires a numeric response expressed as an integer, decimal number, "
									+ "or in scientific E notation (example: 6.022E-23). Your answer was scored incorrect because the computer "
									+ "was unable to recognize your answer as one of these types.<br/>");
						}
						break;
					case 6:  // Five star rating
						buf.append("<h3>No rating was submitted for this item.</h3>");
						break;
					case 7:  // Essay question
						int score = api_score.get("score").getAsInt();
						if (score<=1) buf.append("<h3>Your answer to this question is incorrect. <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h3>");
						else buf.append("<h3>Your answer is partly correct, but needs improvement.</h3>");
						buf.append(api_score.get("feedback").getAsString() + "<br/><br/>");
						break;
					default:  // All other types of questions
						buf.append("<h3>Incorrect Answer <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h3>Your answer was scored incorrect because it does not agree with the "
							+ "answer in the database.<br/>");
				}
				
				int nAttempts = 0;
				if (hwa.attemptsAllowed != null) {
					nAttempts = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",hwa.id).count();
					buf.append("The maximum number of attempts for each question on this assignment is " + hwa.attemptsAllowed + "<br/>");
					if (nAttempts<hwa.attemptsAllowed) buf.append("The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. ") + "<br/>");
				} else buf.append("The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. ") + "<br/><br/>");
			
				if (user.isInstructor() || user.isTeachingAssistant()) {
					buf.append("<br/>Instructor: <a href=# onClick=document.getElementById('solution').style='display:inline';this.style='display:none';>show the solution</a><br/><br/>");
				} else if (!user.isAnonymous() && user.isEligibleForHints(q.id)) {
					buf.append("<br/><form method=post action=/Help>"
							+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
							+ "<input type=hidden name=TransactionId value=" + ht.id + " />"
							+ "<input type=hidden name=HashCode value=" + hashMe.hashCode() + " />");
					buf.append("<font color=#EE0000>Do you need some help from your instructor or teaching assistant? </font>");
					buf.append("<input type=submit value='Get Some Help Here'></form><br/>");
				}				
			}  
			else {
				buf.append("<h3>The answer to the question was left blank.</h3>");
			}

			boolean offerHint = studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id);

			if (!user.isAnonymous()) {
				if (studentScore>0 || user.isInstructor()) {
					buf.append("<div id=solution style='display:none'>" + q.printAllToStudents(studentAnswer) + "</div><br/>");
				}

				if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) 
					buf.append("<img src=/images/learn_more.png alt='learn more here' /> You can learn more about this topic at <a href='" + q.learn_more_url + "' target=_blank>" + q.learn_more_url + "</a><br/><br/>");

				// if the user response was correct, seek five-star feedback:
				if (studentScore > 0) buf.append(fiveStars(user.getTokenSignature()));
				else buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");

				if (hwa != null) buf.append("You may <a href=/Homework?UserRequest=ShowScores&sig=" + user.getTokenSignature() + ">review your scores on this assignment</a>.<p>");
			}
			buf.append("<a href=/Homework?AssignmentId=" + hwa.id + "&sig=" + user.getTokenSignature()  
					+ (offerHint?"&Q=" + q.id + "><span style='color:#EE0000'>Please give me a hint</span>":">Return to this homework assignment") + "</a> or "
					+ "<a href=/Logout?sig=" + user.getTokenSignature() + ">logout of ChemVantage</a> ");
			
			if (user.isAnonymous()) buf.append(" or go back to the <a href=/>ChemVantage home page</a>.<br/><br/>");
			}
		catch (Exception e) {
			buf.append("Sorry, there was an unexpected error: " + e.getMessage()==null?e.toString():e.getMessage());
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Error during Homework.printScore: ", e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString() + "<br/>" + user.getId());
			return Logout.now(user);
		}
		return buf.toString();
	}

	static String fiveStars(String sig) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("Please rate your overall experience with ChemVantage:<br />"
				+ "<span id='vote' style='font-family:tahoma; color:#EE0000;'>(click a star):</span><br>");

		for (int iStar=1;iStar<6;iStar++) {
			buf.append("<img src='images/star1.gif' id='" + iStar + "' "
					+ "style='width:30px; height:30px;' "
					+ "onmouseover=showStars(this.id); onClick=setStars(this.id,'" + sig + "'); onmouseout=showStars(0); />");
		}
		buf.append("<span id=sliderspan style='opacity:0'>"
				+ "<input type=range id=slider min=1 max=5 value=3 onfocus=document.getElementById('sliderspan').style='opacity:1';showStars(this.value); oninput=showStars(this.value);>"
				+ "<button onClick=setStars(document.getElementById('slider').value,'" + sig + "');>submit</button>"
				+ "</span>");
		buf.append("<p>");

		return buf.toString(); 
	}

	static String showScores(User user, Assignment a, String forUserId) {
		if (!user.isInstructor() || forUserId==null) forUserId = user.getId();  // user is viewing their own scores
		
		StringBuffer buf = new StringBuffer("<h1>Homework Transactions</h1>");
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
		Date now = new Date();
		
		try {
			buf.append("<h2>Topic: "+ a.title + "</h2>");
			buf.append("Assignment Number: " + a.id + "<br>");
			buf.append("Valid: " + df.format(now) + "<p>");
			
			List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).filter("userId",Subject.hashId(forUserId)).filter("assignmentId",a.id).order("graded").list();
			
			if (hwts.size()==0) {
				buf.append("Sorry, we did not find any records for this user in the database for this assignment.<p>");
				return buf.toString();
			} else {
				Score s = null;
				try { // retrieve the score and ensure that it is up to date
					s = ofy().load().key(key(key(User.class,forUserId),Score.class,a.id)).safe();
					if (s.numberOfAttempts != hwts.size()) throw new Exception();
				} catch (Exception e) { // create a fresh Score entity from scratch
					s = Score.getInstance(forUserId, a);
					ofy().save().entity(s);
				}
				
				buf.append("This user's overall score on the assignment is " + 10.*Math.round(s.getPctScore())/10. + "%.<br>");

				try {
					double lmsPctScore = 0;
					String lmsScore = null;
					boolean gotScoreOK = false;

					if (a.lti_ags_lineitem_url != null) {  // LTI version 1p3
						lmsScore = LTIMessage.readUserScore(a, forUserId);
						try {
							lmsPctScore = Double.parseDouble(lmsScore);
							gotScoreOK = true;
						} catch (Exception e) {
							//buf.append("The LMS returned: " + lmsScore + "<br/>");
						}
					}

					if (gotScoreOK && Math.abs(lmsPctScore-s.getPctScore())<1.0) { // LMS readResult agrees to within 1%
						buf.append("This score is accurately recorded in the grade book of your class learning management system.<p>");
					} else if (gotScoreOK) { // there is a significant difference between LMS and ChemVantage scores. Please explain:
						buf.append("The score recorded in your class LMS is " + Math.round(10.*lmsPctScore)/10. + "%. The difference may be due to<br>"
								+ "enforcement of assignment deadlines, grading policies and/or instructor discretion.<br>"
								+ "If you think this may be due to a stale score, you may submit this assignment for grading,<br>"
								+ "even for a score of zero, and ChemVantage will try to refresh the best score to the LMS.<p>");
					} else throw new Exception();
				} catch (Exception e) {
					if (s.score==0 && s.numberOfAttempts==0) buf.append("It appears that this assignment may not have been submitted for a score yet.<br/>");
					buf.append("<br/>");
				}
				buf.append("<table><tr><th>QuestionID</th><th>Graded</th><th>Score</th></tr>");
				for (HWTransaction hwt : hwts) {
					buf.append("<tr align=center><td>" + hwt.questionId + "</td><td>" + df.format(hwt.graded) + "</td><td>" + hwt.score +  "</td></tr>");
				}
				buf.append("</table><br/><br/>");
				
				if (a.attemptsAllowed != null) buf.append("The maximum number of submissions for each question on this assignment is " + a.attemptsAllowed + "<br/><br/>");
				
			}
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	static String showSummary(User user,Assignment a) {
		StringBuffer buf = new StringBuffer();
		if (a==null) return "No assignment was specified for this request.";

		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		try {
			if (a.lti_nrps_context_memberships_url==null) throw new Exception("No Names and Roles Provisioning support.");

			buf.append("<h1>Homework Scores</h1>");
			buf.append("Title: " + a.title + "<br/>");
			buf.append("Assignment ID: " + a.id + "<br/>");
			buf.append("Valid: " + new Date() + "<p>");
			buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
					+ "and may or may not include user's names or emails, depending on the settings of your LMS.<br/><br/>");

			Map<String,String> scores = LTIMessage.readMembershipScores(a);
			if (scores==null) scores = new HashMap<String,String>();  // in case service call fails

			Map<String,String[]> membership = LTIMessage.getMembership(a);
			if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails

			Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
			String platform_id = d.getPlatformId() + "/";
			for (String id : membership.keySet()) {
				keys.put(id,key(key(User.class,Subject.hashId(platform_id+id)),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th><th>Scores Detail</th></tr>");
			int i=0;
			int nMismatched = 0;
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				String lmsScoreString = scores.get(entry.getKey());
				lmsScoreString = (lmsScoreString==null?" - ":lmsScoreString + "%");
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				String cvScoreString = cvScore==null?" - ":String.valueOf(cvScore.getPctScore() + "%");
				boolean synched = !"Learner".equals(entry.getValue()[0]) || cvScoreString.equals(lmsScoreString);
				String forUserId = platform_id + entry.getKey();  // only send hashed values through links
				i++;
				buf.append("<tr><td>" + i + ".&nbsp;</td>"
						+ "<td>" + entry.getValue()[1] + "</td>"
						+ "<td>" + entry.getValue()[2] + "</td>"
						+ "<td>" + entry.getValue()[0] + "</td>"
						+ "<td align=center>" + lmsScoreString + "</td>"
						+ "<td align=center>" + cvScoreString + "</td>"
						+ "<td align=center><a href=/Homework?UserRequest=Review&sig=" + user.getTokenSignature() + "&ForUserId=" + forUserId + "&ForUserName=" + entry.getValue()[1].replaceAll(" ","+") + ">show</a></td>"
						+ (synched?"":"<td><span id='cell" + forUserId + "'><button onClick=this.disabled=true;this.style.opacity=0.5;synchronizeScore('" + forUserId + "','" + user.getTokenSignature() + "','/Homework'); >sync</button></span></td>")
						+ "</tr>");
				// Flag this score set as unsynchronized only if there is one or more non-null ChemVantage Learner score that is not equal to the LMS score
				// Ignore Instructor scores because the LMS often does not report them, and ignore null cvScore entities because they cannot be reported.
				if (!synched) nMismatched++;
			}
			buf.append("</table><br/>");
			if (nMismatched > 0) {
				//buf.append(ajaxJavaScript(user.getTokenSignature()));
				buf.append("You may use the individual 'sync' buttons above to resubmit any ChemVantage score to the LMS. Note that in some cases, mismatched scores are expected (e.g., when "
						+ "the instructor overrides a score or when a late submission is not accepted by the LMS). You may have to adjust the settings in your LMS to accept the "
						+ "revised score (e.g., change the due date, grade override or allowed number of submissions). ");
			}
			if (nMismatched>1) {
				buf.append("Use the button below to synchronize all of the Learner scores. This might take a minute, depending on the number of mismatches.<br/>"
					+ "<form method=post action=/Homework onsubmit=waitforSync(); >"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
					+ "<input type=submit id=syncAll value='Synchronize All Scores' />"
					+ "</form>");
			}
				return buf.toString();
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	static String reviewSubmissions(User user, Assignment a, String forUserId, String forUserName) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		try {
			// this line restricts non-instructor users to viewing their own scores
			String forUserHashedId = user.isInstructor()?Subject.hashId(forUserId):user.getHashedId();
			
			Map<Key<Question>,Question> questions = ofy().load().keys(a.questionKeys);
			List<HWTransaction> transactions = ofy().load().type(HWTransaction.class).filter("userId",forUserHashedId).filter("assignmentId",a.id).order("-graded").list();
			
			buf.append("<h1>Homework Submissions</h1>"
					+ (forUserName==null || forUserName.isEmpty()?"":"Name: " + forUserName + "<br/>")
					+ "Assignment: " + a.title + "<br/>"
					+ "Date: " + new Date() + "<br/><br/>");
			debug.append("0");
			
			buf.append("<table>");
			for (Key<Question> k : a.questionKeys) {  // this is the main loop through the assigned questions
				Question q = questions.get(k);
				String hashMe = forUserId + a.id;
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
				debug.append("1");
				
				List<HWTransaction> qTransactions = new ArrayList<HWTransaction>();
				for (HWTransaction t : transactions) if (q.id.longValue() == t.questionId) qTransactions.add(t);
				debug.append("2");
				
				String studentAnswer = null;
				String showWork = null;
				HWTransaction hwt = qTransactions.isEmpty()?null:qTransactions.get(0);
				if (hwt!=null) {
					showWork = hwt.showWork;
					studentAnswer = hwt.studentAnswer;
				}
				debug.append("3");
				
				buf.append("<tr><td style='text-align:right;vertical-align:text-top;padding-right:10px;'><b>" + (a.questionKeys.indexOf(k)+1) + ".</b></td><td>" + q.printAllToStudents(studentAnswer,true,true,showWork) + "<br/></td></tr>");
				
				// print a small table of student submissions for this question
				buf.append("<tr><td></td><td>");
				if (!qTransactions.isEmpty()) {
					buf.append("<table style='text-align: center'><tr><th style='padding-right:20px'>Timestamp</th><th style='padding-right:20px'>Student Response</th><th style='padding-right:20px'>Correct Answer</th><th>Correct</th></tr>");
					for (HWTransaction t : qTransactions) {
						if (t.studentAnswer==null) buf.append("<tr><td style='padding-right:20px'>" + t.graded + "</td><td colspan=2 style='padding-right:20px'>(response detail is unavailable)</td>");
						else buf.append("<tr><td style='padding-right:20px'>" + t.graded + "</td><td style='padding-right:20px'>" + t.studentAnswer + "</td><td style='padding-right:20px'>" + t.correctAnswer + "</td>");
						
						if (t.score==1) buf.append("<td><img src=/images/checkmark.gif alt='checkmark' height=24 width=17></td>");
						else if (q.agreesToRequiredPrecision(t.studentAnswer)) buf.append("<td><img src=/images/partCredit.png alt='partial credit' height=25 width=25></td>");
						else buf.append("<td><img src=/images/xmark.png alt='x-mark' height=24 width=24></td>");
						buf.append("</tr>");
					}
					buf.append("</table><br/>");
				}
				buf.append("</td></tr>");
			}
			buf.append("</table><br/>");
			//buf.append(ajaxJavaScript(user.getTokenSignature()));
		} catch (Exception e) {
			buf.append("Error: " + (e.getMessage()==null?e.toString():e.getMessage()) + "<br/>" + debug.toString());
		}
		return buf.toString();
	}
	
	static boolean synchronizeScores(User user,Assignment a,HttpServletRequest request) {
		// This method looks for assignment scores that are different from the LMS scores and resubmits the score to the LMS
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
			if (a==null) throw new Exception();  // can only do this for a known assignment
			if (a.lti_ags_lineitem_url == null || a.lti_nrps_context_memberships_url == null) throw new Exception(); // need both of these to work
			Map<String,String> scores = LTIMessage.readMembershipScores(a);
			if (scores==null || scores.size()==0) throw new Exception();  // this only works if we can get info from the LMS
			Map<String,String[]> membership = LTIMessage.getMembership(a);
			if (membership==null || membership.size()==0) throw new Exception();  // there must be some members of this class
			Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
			String platform_id = d.getPlatformId() + "/";
			for (String id : membership.keySet()) {
				String hashedUserId = Subject.hashId(platform_id + id);
				keys.put(id,key(key(User.class,hashedUserId),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				if (cvScore==null) continue;
				String s = scores.get(entry.getKey());
				if (String.valueOf(cvScore.getPctScore()).equals(s)) continue;  // the scores match (good!)
				String payload = "AssignmentId=" + a.id + "&UserId=" + URLEncoder.encode(platform_id + entry.getKey(),"UTF-8");
				Utilities.createTask("/ReportScore",payload);
				//QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(platform_id + entry.getKey(),"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	static String synchronizeScore(User user, Assignment a, String forUserId) {
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
			if (a==null) throw new Exception();  // can only do this for a known assignment
			if (LTIMessage.postUserScore(Score.getInstance(forUserId,a), forUserId).contains("Success")) return "OK";
		} catch (Exception e) {}
		return "Failed. Check assignment settings in the LMS.";
	}
	
	String selectQuestionsForm(User user,Assignment a,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		try {
			buf.append("<h1>Customize Homework Assignment</h1>");
			buf.append("<form action=/Homework method=post>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<b>Title:</b>&nbspHomework - <input type=text size=25 name=AssignmentTitle value='" + a.title + "' />&nbsp;"
					+ "<input type=submit name=UserRequest value='Save New Title' /></form><br/>");
							
			buf.append("By default, students may submit answers to the homework problems as many times as they wish. This rewards students who persist "
					+ "to achieve a better score. However, you may limit the number of attempts here. Leave the field blank to permit unlimited attempts.<br/>"
					+ "<form action=/Homework method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=text size=10 name=AttemptsAllowed " 
					+ (a.attemptsAllowed==null?"placeholder=unlimited":"value=" + a.attemptsAllowed) + " /> "
					+ "<input type=submit name=UserRequest value='Set Allowed Attempts' />"
					+ "</form><br/>");
			
			// Allow instructor to pick individual question items from all active questions:
			buf.append("Select the homework questions below to be assigned for grading. "  // The questions are presented below in "
				//	+ "approximate order of increasing difficulty, as measured by the percentage of correct submissions. "
					+ "Then click the 'Use Selected Items' button. Each question is worth 1 point, so the maximum possible "
					+ "score is equal to the number of questions selected. Students may work the optional problems; "
					+ "however, these are not included in the scores reported to the class LMS. "
					+ "If you don't see a question you want to include, you may "
					+ "<a href=/Contribute?AssignmentType=Homework&sig=" + user.getTokenSignature() 
					+ ">contribute a new question item</a> to the database.<p>");

			// Show a List of concepts covered by this assignment
			Long newConceptId = null;
			try {  // add a new conceptId
				newConceptId = Long.parseLong(request.getParameter("ConceptId"));
				a.conceptIds.add(newConceptId);
			} catch (Exception e) {}
			
			List<Key<Concept>> conceptKeys = ofy().load().type(Concept.class).order("orderBy").keys().list();
			Map<Key<Concept>,Concept> keyConcepts = ofy().load().keys(conceptKeys);
			if (a.conceptIds.size()>0) {
				buf.append("The questions listed below cover the following key concepts:<ul>");
				for (Long cId : a.conceptIds) {
					Concept c = keyConcepts.get(key(Concept.class,cId));
					try {
						buf.append("<li>" + c.title + "</li>");
					} catch (Exception e) {
						a.conceptIds.remove(cId);  // remove id for null Concept
					}
				}
				buf.append("</ul>");
			}

			// Create a short form to select one additional key concept to include (will exclude the previous selection, if any)
			buf.append("<form method=get action=/Homework>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "You may include additional question items from: "
					+ "<input type=hidden name=UserRequest value=AssignHomeworkQuestions />"
					+ "<select name=ConceptId onchange=this.form.submit();><option value='Select'>Select a key concept</option>");
			for (Key<Concept> k : conceptKeys) {
				try {
					if (a.conceptIds.contains(k.getId()) || keyConcepts.get(k).orderBy.startsWith(" 0")) continue;  // skip current and hidden conceptIds
					buf.append("<option value='" + k.getId() + "'" + (newConceptId!=null && k.getId()==newConceptId?" selected>":">") + keyConcepts.get(k).title + "</option>");
				} catch (Exception e) {}
			}
			buf.append("</select></form><hr>");
			
			
			// now we have all of the relevant conceptIds. Make a list of questions carrying these attributes:
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Long cId : a.conceptIds) questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).keys().list());
			if (!questionKeys.containsAll(a.questionKeys)) {  // might be missing a few questions due to customization
				for (Key<Question> k : a.questionKeys) if (!questionKeys.contains(k)) questionKeys.add(k);
			}
			
			// create an ordered List of Questions (by difficulty)
			List<Question> orderedQuestions = new ArrayList<Question>(ofy().load().keys(questionKeys).values());		
			if (orderedQuestions.size()>1) Collections.sort(orderedQuestions,new SortBySuccessPct());
			
			buf.append("<b>Select assigned questions</b><br/>"
					+ "Current number of assigned questions = " + a.questionKeys.size() + " out of " + orderedQuestions.size());
			
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM NAME=DummyForm><INPUT id=selectAll TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick='for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}'"
					+ "> Select/Unselect All</FORM>");
			buf.append("<script>document.getElementById('selectAll').indeterminate=true;</script>");
			
			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM NAME=Questions METHOD=POST ACTION=/Homework />"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + " />"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment' />"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "' />"
					+ (newConceptId==null?"":"<input type=hidden name=NewConceptId value=" + newConceptId + " />")
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items' />");
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");

			int i=0;
			for (Question q : orderedQuestions) {
				q.setParameters();  // creates randomly selected parameters
				buf.append("\n<TR><TD style='vertical-align:text-top;' NOWRAP>"
						+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'");
				buf.append(a.questionKeys.contains(key(Question.class,q.id))?" CHECKED>":">");
				i++;
				buf.append("<b>&nbsp;" + i + ".</b><br/>"
						+ "<span style='font-size:0.5em'>" + q.getSuccess() + "</span></TD>");
				buf.append("\n<TD>" + q.printAll() + "</TD>");
				buf.append("</TR>");
				if (q.conceptId!=null && !a.conceptIds.contains(q.conceptId)) a.conceptIds.add(q.conceptId);
			}
			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM><br/>");
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage() + "<br/>" + debug.toString());
		}
		return buf.toString();
	}
	
	static String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

	class SortBySuccessPct implements Comparator<Question> {
		
		SortBySuccessPct() {}
		
		public int compare(Question q1, Question q2) {
			int rank = q2.getPctSuccess() - q1.getPctSuccess(); 
			if (rank==0) rank = q2.id.compareTo(q1.id);
			return rank;
		}
	}	
}

