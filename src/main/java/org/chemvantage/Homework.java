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

import static com.googlecode.objectify.ObjectifyService.key;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
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
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.Key;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
			case "GetExplanation":
				long questionId = Long.parseLong(request.getParameter("QuestionId"));
				long parameter = Long.parseLong(request.getParameter("Parameter"));
				Question q = ofy().load().type(Question.class).id(questionId).now();
				if (q.requiresParser()) q.setParameters(parameter);
				out.println(q.getExplanation());
				break;
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
			case "CreateCustomQuestion":
				out.println(Subject.header("Create Question") + newQuestionForm(user,request) + Subject.footer);
				break;
			case "Logout":
				out.println(Subject.header() + Logout.now(user) + Subject.footer);
				break;
			case "Preview":
				out.println(Subject.header() + previewQuestion(user,request) + Subject.footer);
				break;
			default:
				long hintQuestionId = 0L;
				try {
					hintQuestionId = Long.parseLong(request.getParameter("Q"));
				} catch (Exception e) {}
				boolean showOptional = Boolean.parseBoolean(request.getParameter("ShowOptional"));
				out.println(Subject.header("Homework") + printHomework(user,a,hintQuestionId,showOptional) + Subject.footer);
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
			case "Save Question":
				saveQuestion(user,request);
				out.println(Subject.header("ChemVantage Instructor Page") + selectQuestionsForm(user,a,request) + Subject.footer);
				break;
			case "Delete Question":
				deleteQuestion(user,request);
				out.println(Subject.header("ChemVantage Instructor Page") + selectQuestionsForm(user,a,request) + Subject.footer);
				break;
			case "Quit":
				out.println(Subject.header("Customize ChemVantage Homework Assignment") + selectQuestionsForm(user,a,request) + Subject.footer);
				break;
			case "Preview":
				out.println(Subject.header() + previewQuestion(user,request) + Subject.footer);
				break;
			case "Synchronize Scores":
				if (synchronizeScores(user,a,request)) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				else out.println("Synchronization request failed.");
				break;
			case "IncludeCustomQuestions":
				if (user.isInstructor()) {
					includeCustomQuestions(user,a,request);
					out.println(Subject.header("Customize ChemVantage Homework Assignment") + selectQuestionsForm(user,a,request) + Subject.footer);
				}
				break;
			default: out.println(Subject.header("ChemVantage Homework Results") + printScore(user,a,request) + Subject.footer);
			}
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	static Question assembleQuestion(HttpServletRequest request) throws Exception {
		int questionType = Integer.parseInt(request.getParameter("QuestionType"));
		String assignmentType = request.getParameter("AssignmentType");
		Question q = new Question(questionType);
		try {
			q.id = Long.parseLong(request.getParameter("QuestionId"));
		} catch (Exception e) {}
		String questionText = request.getParameter("QuestionText");
		ArrayList<String> choices = new ArrayList<String>();
		int nChoices = 0;
		char choice = 'A';
		for (int i=0;i<5;i++) {
			String choiceText = request.getParameter("Choice"+ choice +"Text");
			if (choiceText==null) choiceText = "";
			if (choiceText.length() > 0) {
				choices.add(choiceText);
				nChoices++;
			}
			choice++;
		}
		double requiredPrecision = 0.; // percent
		int significantFigures = 0;
		int pointValue = 1;
		try {
			requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
		} catch (Exception e) {
		}
		try {
			significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
		} catch (Exception e) {
		}
		String correctAnswer = "";
		try {
			String[] allAnswers = request.getParameterValues("CorrectAnswer");
			for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
		} catch (Exception e) {
			correctAnswer = request.getParameter("CorrectAnswer");
		}
		String parameterString = request.getParameter("ParameterString");
		if (parameterString == null) parameterString = "";
		
		q.setQuestionType(questionType);
		q.assignmentType = assignmentType;
		q.text = questionText;
		q.nChoices = nChoices;
		q.choices = choices;
		q.requiredPrecision = requiredPrecision;
		q.significantFigures = significantFigures;
		q.correctAnswer = correctAnswer;
		q.tag = request.getParameter("QuestionTag");
		q.pointValue = pointValue;
		q.parameterString = parameterString;
		q.hint = request.getParameter("Hint");
		q.solution = request.getParameter("Solution");
		q.notes = "";
		q.authorId = request.getParameter("AuthorId");
		q.editorId = request.getParameter("EditorId");
		q.scrambleChoices = Boolean.parseBoolean(request.getParameter("ScrambleChoices"));
		q.strictSpelling = Boolean.parseBoolean(request.getParameter("StrictSpelling"));
		q.validateFields();
		return q;
	}
	
	static void deleteQuestion(User user, HttpServletRequest request) {
		if (!user.isInstructor()) return;
		Question q = null;
		try {
			q = ofy().load().type(Question.class).id(Long.parseLong(request.getParameter("QuestionId"))).safe();
		} catch (Exception e) {}
		if (user.getId().equals(q.authorId)) ofy().delete().entity(q).now();
	}
	
	static String fiveStars(String sig) {
		StringBuffer buf = new StringBuffer();
		
		buf.append("<bbr/>Please rate your overall experience with ChemVantage:<br />"
				+ "<span id='vote' style='font-family:tahoma; color:#EE0000;'>(click a star):</span><br>");
	
		for (int iStar=1;iStar<6;iStar++) {
			buf.append("<img src='images/star1.gif' id='" + iStar + "' "
					+ "style='width:30px; height:30px;' alt='star " + iStar + " for rating' "
					+ "onmouseover=showStars(this.id); onClick=setStars(this.id,'" + sig + "'); onmouseout=showStars(0); />");
		}
		buf.append("<span id=sliderspan style='opacity:0'>"
				+ "<input type=range id=slider min=1 max=5 value=3 aria-label='slider for rating ChemVantage' onfocus=document.getElementById('sliderspan').style='opacity:1';showStars(this.value); oninput=showStars(this.value);>"
				+ "<button onClick=setStars(document.getElementById('slider').value,'" + sig + "');>submit</button>"
				+ "</span>");
		buf.append("<p>");
	
		return buf.toString(); 
	}
	
	static void includeCustomQuestions(User user, Assignment a, HttpServletRequest request) {
		if (!user.isInstructor()) return;
		String[] questionIds = request.getParameterValues("QuestionId");
		if (questionIds == null) return;
		for (String id : questionIds) {  // eliminate any duplicate question keys
			try {
				Key<Question> k = key(Question.class,Long.parseLong(id));
				if (!a.questionKeys.contains(k)) a.questionKeys.add(k);
			} catch (Exception e) {}
		}
		ofy().save().entity(a).now();
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
			
			buf.append("<h1>Homework</h1>"
					+ "<h2>" + a.title + "</h2>");
			buf.append("<h3>Instructor Page</h3>");
			
			buf.append("<a href='/Homework?sig=" + user.getTokenSignature() + "' class='btn btn-primary'>Show This Assignment</a><br/><br/>");
			
			buf.append("Currently, this assignment has " + a.questionKeys.size() + " assigned question items.<br/>");
			
			if (a.attemptsAllowed==null || a.attemptsAllowed<1) buf.append("An unlimited number of submissions is allowed for each question.<br/>");
			else buf.append("Only " + a.attemptsAllowed + (a.attemptsAllowed==1?" submission is ":" submissions are ") + "allowed for each question.<br/>");
			
			buf.append("<UL>"
					+ "<LI><a href='/Homework?UserRequest=AssignHomeworkQuestions&sig=" + user.getTokenSignature() + "'>Customize this assignment</a> by selecting the assigned question items and selecting the number of submissions allowed for each question.</LI>"
					+ (supportsMembership?"<LI><a href='/Homework?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>Review your students' homework scores</a></LI>":"")
					+ "</UL>");
			
			buf.append("Need help? Please <a href=/Feedback?sig=" + user.getTokenSignature() + "&AssignmentId=" + a.id + ">submit a comment, question or request here</a>.<br/><br/>");			
			
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).now();
			if (d.price > 0 && d.nLicensesRemaining > 0) {		
				buf.append("Your account has " + d.nLicensesRemaining + " unclaimed student license" + (d.nLicensesRemaining>1?"s":"") + " remaining.<br/><br/>");
			}
			
		} catch (Exception e) {
			buf.append("<br/>Instructor page error: " + e.getMessage());
		}
		return buf.toString();
	}
	
	static String newQuestionForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h1>Create Custom Homework Question</h1>");
		if (!user.isInstructor()) return null;
		
		String assignmentType = "Custom";
		
		int questionType = 0;
		try {
			questionType = Integer.parseInt(request.getParameter("QuestionType"));
		} catch (Exception e) {
			buf.append("Use this tool to create your own custom homework question for this assignment.<br/>"
					+ "First, choose the type of question to create:<br/>");
			
			buf.append("<FORM NAME=NewQuestion METHOD=GET ACTION=/Homework>");
			buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=CreateCustomQuestion>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + user.getAssignmentId() + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=QuestionType>"
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=1;submit()\" VALUE='Multiple Choice'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=2;submit()\" VALUE='True/False'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=3;submit()\" VALUE='Select Multiple'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=4;submit()\" VALUE='Fill in Word'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=5;submit()\" VALUE='Numeric'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=6;submit()\" VALUE='Five Star'> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=7;submit()\" VALUE='Essay'>"
					+ "</FORM><p><p>");
			
			List<Question> myCustomQuestions = ofy().load().type(Question.class).filter("assignmentType =","Custom").filter("authorId =",user.getId()).list();
			
			if (myCustomQuestions.isEmpty()) return buf.toString();  // done
			
			// Print a list of this user's custom questions
			buf.append("<hr><p>"
					+ "Or, you may include selected questions below that you created previously.<br/>"
					+ "<form method=post action=/Homework>"
					+ "<input type=hidden name=UserRequest value=IncludeCustomQuestions />"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "'/>"
					+ "<input type=submit value='Add the selected questions below to this assignment' /><p>");
			buf.append("<TABLE>");
			
			for (Question q : myCustomQuestions) {
				q.setParameters();  // creates randomly selected parameters
				buf.append("\n<TR>"
						+ "<TD style='vertical-align:top;' NOWRAP>"
						+ "<label><INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'>Select</label><br/>"
						+ "&nbsp;or&nbsp;<a href=/Homework?UserRequest=Preview&sig=" + user.getTokenSignature() + "&QuestionId=" + q.id + ">Edit</a>"
						+ "</TD>"
						+ "<TD style='width:20px'></TD>"
						+ "<TD>" + q.printAll() + "</TD>"
						+ "</TR>");
			}
			buf.append("</TABLE><p>"
					+ "<input type=submit value='Add the selected questions below to this assignment' />"
					+ "</form><p>");
			return buf.toString();
		}
		
		// if questionType has already been selected:
		switch (questionType) {
		case (1): buf.append("<h3>Multiple-Choice " + assignmentType + " Question</h3>");
		buf.append("Fill in the question text and the possible answers "
				+ "(up to a maximum of 5). Be sure to select the single best "
				+ "answer to the question."); break;
		case (2): buf.append("<h3>True-False " + assignmentType + " Question</h3>");
		buf.append("Write the question as an affirmative statement. Then "
				+ "indicate below whether the statement is true or false."); break;
		case (3): buf.append("<h3>Select-Multiple " + assignmentType + " Question</h3>");
		buf.append("Fill in the question text and the possible answers "
				+ "(up to a maximum of 5). Be sure to "
				+ "select all of the correct answers to the question."); break;
		case (4): buf.append("<h3>Fill-in-Word " + assignmentType + " Question</h3>");
		buf.append("Start the question text in the upper textarea box. Indicate "
				+ "the correct answer (and optionally, an alternative correct answer) in "
				+ "the middle boxes, and the end of the question text below that.  The answers "
				+ "are not case-sensitive or punctuation-sensitive, but spelling must "
				+ "be exact."); break;
		case (5): buf.append("<h3>Numeric " + assignmentType + " Question</h3>");
		buf.append("Fill in the question text in the upper textarea box and "
				+ "the correct numeric answer below. Also indicate the required precision "
				+ "of the student's response in percent (default = 2%). Use the bottom "
				+ "textarea box to finish the question text and/or to indicate the "
				+ "expected dimensions or units of the student's answer."); break;
		case (6): buf.append("<h3>Five Star " + assignmentType + " Question</h3>");
		buf.append("Fill in the question text. The user will be asked to provide a rating "
				+ "from 1 to 5 stars."); break;
		case (7): buf.append("<h3>Essay " + assignmentType + " Question</h3>");
		buf.append("Fill in the question text. The user will be asked to provide a short "
				+ "essay response."); break;
		default: buf.append("An unexpected error occurred. Please try again.");
		}
		Question question = new Question(questionType);
		buf.append("<p><FORM METHOD=POST ACTION=/Homework>"
				+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
				+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
				+ "<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + user.getId() + "'>");
		buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + ">");

		question.pointValue = 1;
		buf.append(question.edit());
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview'></FORM>");

		return buf.toString();
	}

	static String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

	String previewQuestion(User user,HttpServletRequest request) throws Exception {
		if (!user.isInstructor()) throw new Exception("You must be an instructor for this.");
		StringBuffer buf = new StringBuffer();
		
		Question q = null;
		try {
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			q = ofy().load().type(Question.class).id(questionId).safe();
		} catch (Exception e) {}
		try {
			q = assembleQuestion(request);
		} catch (Exception e) {}
		
		if (q.requiresParser()) q.setParameters();

		buf.append("<h1>Custom Homework Question</h1><h2>Preview</h2>");

		buf.append("<FORM ACTION=/Homework METHOD=POST>");

		buf.append(q.printAll());

		if (q.authorId==null) q.authorId = user.getId();
		buf.append("<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>");
		buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "'>");
		buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + user.getId() + "'>");
		buf.append("<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='Custom' />");
		
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save Question' />&nbsp;");		
		if (q.id != null) {
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "' />");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question' />&nbsp;");
		}
		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit' />");

		buf.append("<hr><h2>Continue Editing</h2>");
		buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()) + "<br/>");
		
		buf.append(q.edit());

		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview' />");
		buf.append("</FORM>");
		return buf.toString();
	}

	static String printHomework(User user, Assignment hwa, long hintQuestionId, boolean showOptional) throws Exception  {
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
				hwa.conceptIds = ch.conceptIds;
				for (Long cId : hwa.conceptIds) hwa.questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).keys().list());
				Random rand = new Random();  // use a random number generator to select 10 keys
				while (hwa.questionKeys.size() > 10) hwa.questionKeys.remove(rand.nextInt(hwa.questionKeys.size()));
			} else if (hwa.title==null) {  // legacy Homework assignment only provided topicId
				Topic t = ofy().load().type(Topic.class).id(hwa.topicId).now();
				hwa.title = t.title;
				if (hwa.conceptIds.isEmpty()) hwa.conceptIds = t.conceptIds;
				ofy().save().entity(hwa).now();
			}
						
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
			
			Map<Key<Question>,Question> questions = ofy().load().keys(hwa.questionKeys);  // container for the questions to be presented
			
			buf.append("<h2>Assigned Exercises</h2>");
			buf.append("<div style='display:table'>"); // start the table of questions
			// This is the main loop for presenting assigned questions in order of increasing difficulty:
			int i=0;
			for (Key<Question> k : hwa.questionKeys) {
				Question q = questions.get(k); 
				if (q==null) continue;
				i++;
				buf.append("<div id=q" + i + " style='display:table-row'><div style='display:table-cell;font-size:small'>");
				String hashMe = user.getId() + hwa.id;
				q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments

				Integer attemptsRemaining = null;
				if (hwa.attemptsAllowed!=null) {
					attemptsRemaining = hwa.attemptsAllowed - (priorAttempts.get(q.id)==null?0:priorAttempts.get(q.id));
					if (attemptsRemaining < 0) attemptsRemaining = 0;
				}

				if (solvedQuestions.contains(q.id)) buf.append("<IMG SRC=/images/checkmark.gif ALT='Check mark' align=top>&nbsp;");
				
				buf.append("</div>");

				buf.append("<FORM METHOD=POST ACTION=/Homework onsubmit=waitForScore('" + q.id + "'); >"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>"
						+ "<input type=hidden name=QNumber value=" + i + " />"  // this is the assigned question number on the page
						+ (hwa==null?"":"<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
						+ "<div style='display:table-cell;vertical-align:text-top;padding-right:10px;'><b>" + i + ".</b></div>"
						+ "<div style='display:table-cell'>" + q.print(workStrings.get(q.id),"",attemptsRemaining) 
						+ (q.id == hintQuestionId?"Hint:<br>" + q.getHint():"")
						+ "<INPUT id=sub" + q.id + " role='button' aria-label='submit this answer for scoring' TYPE=SUBMIT class='btn btn-primary' VALUE='Grade This Exercise'><p>"
						+ "</div></div></FORM>\n");
			}
			if (i==0) buf.append("(none)");
			buf.append("</div>");  // end of assigned questions table
			
			if (showOptional) {
				List<Key<Question>> allQuestionKeys = new ArrayList<Key<Question>>();
				for (Long cId : hwa.conceptIds) allQuestionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).keys().list());
				allQuestionKeys.removeAll(hwa.questionKeys);
				questions = ofy().load().keys(allQuestionKeys); 
				
				buf.append("<h2>Optional Exercises</h2>");
				buf.append("<div style='display:table'>"); // start the table of questions
				// This is the main loop for presenting assigned questions in order of increasing difficulty:
				i=0;
				for (Key<Question> k : allQuestionKeys) {
					Question q = questions.get(k); 
					if (q==null) continue;
					i++;
					buf.append("<div style='display:table-row'><div style='display:table-cell;font-size:small'>");
					String hashMe = user.getId() + hwa.id;
					q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments

					Integer attemptsRemaining = null;
					if (hwa.attemptsAllowed!=null) {
						attemptsRemaining = hwa.attemptsAllowed - (priorAttempts.get(q.id)==null?0:priorAttempts.get(q.id));
						if (attemptsRemaining < 0) attemptsRemaining = 0;
					}

					if (solvedQuestions.contains(q.id)) buf.append("<IMG SRC=/images/checkmark.gif ALT='Check mark' align=top>&nbsp;");
					
					buf.append("</div>");

					buf.append("<FORM METHOD=POST ACTION=/Homework onsubmit=waitForScore('" + q.id + "'); >"
							+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
							+ (hwa==null?"":"<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + hwa.id + "'>")
							+ "<div style='display:table-cell;vertical-align:text-top;padding-right:10px;'><b>" + i + ".</b></div>"
							+ "<div style='display:table-cell'>" + q.print(workStrings.get(q.id),"",attemptsRemaining) 
							+ (q.id == hintQuestionId?"Hint:<br>" + q.getHint():"")
							+ "<INPUT id=sub" + q.id + " role='button' TYPE=SUBMIT class='btn btn-primary' VALUE='Grade This Exercise'><p>"
							+ "</div></div></FORM>\n");
				}
				if (i==0) buf.append("(none)");
				buf.append("</div>");  // end of assigned questions table
			} else if (!user.isAnonymous()) buf.append("<a href=/Homework?sig=" + user.getTokenSignature() + "&ShowOptional=true >Show Optional Questions</a>");
			
			buf.append("<script>function showWorkBox(qid) {\n"
					+ "	if (qid==0) return;\n"
					+ "    document.getElementById('showWork'+qid).style.display='';\n"
					+ "    document.getElementById('answer'+qid).placeholder='Enter your answer here';\n"
					+ "}</script>");
			} catch (Exception e) {
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Error during Homework.printHomework: ", e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString() + "<br/>" + user.getId());
			return Logout.now(user);
		}
		return buf.toString();
	}

	static String printScore(User user,Assignment hwa,HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Start...");
		JsonObject essay_score = new JsonObject(); // contains essay score and feedback
		
		try {
			// The Homework grader scores only one Question at a time, so first identify and load it
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			String qn = request.getParameter("QNumber");
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
							+ "<a href=/Homework?sig=" + user.getTokenSignature() + "&UserRequest=Logout >logout of ChemVantage</a> ");
				
					return buf.toString();
				}
			}
			
			// Set the Question parameters for this user (this is why we made a copy, to prevent thread collisions with a class variable)
			String hashMe = user.getId() + hwa.id;
			q.setParameters(hashMe.hashCode());  // creates different parameters for different assignments
			debug.append("questionId " + q.id + (q.requiresParser()?" parameters set with " + hashMe.hashCode():"") + "...");
			
			String studentAnswer = orderResponses(request.getParameterValues(Long.toString(questionId)));
			
			String showWork = request.getParameter("ShowWork"+q.id);
			if (showWork==null) showWork="";  // required because later we check to see if showWork.isEmpty()
			
			debug.append("student answer:" + studentAnswer + "...");
			
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
				secondsRemaining = retryDelayMinutes*60 - (now.getTime()-lastSubmission.getTime())/1000L;
			}
			debug.append("recent transactions = "+recentTransactions.size() + "...");
			if (secondsRemaining > 0 && !solvedRecently) {  
				buf.append("<h1>Homework</h1>"
						+ "<h2>Please Wait For The Retry Delay To Complete</h2>");
				//buf.append(df.format(now));
				buf.append("<span id=timer0 style='color: #EE0000'></span><br/>");
				buf.append("Please take these few moments to check your work carefully.  You can sometimes find alternate routes to the "
						+ "same solution, or it may be possible to use your answer to back-calculate the data given in the problem.<br/><br/>");
				buf.append("Alternatively, you may wish to "
						+ "<a href=/Homework?AssignmentId=" + hwa.id
						+ "&sig=" + user.getTokenSignature() + (qn==null?"":"#q" + qn) + ">" 
						+ "return to this homework assignment</a> to work on another problem.<p>");
				buf.append("<FORM NAME=Homework METHOD=POST ACTION=Homework onsubmit=waitForRetryScore(); >"
						+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" +(hwa.id==null?0:hwa.id) + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>" 
						+ (qn==null?"":"<input type=hidden name=QNumber value=" + qn + " />")  // this is the assigned question number on the page
						+ q.print(showWork,studentAnswer) + "<br>");

				buf.append("<INPUT TYPE='submit' id='RetryButton' class='btn btn-primary' DISABLED=true VALUE='Please wait' /></FORM><br/><br/>");
				buf.append("<script>"
						+ "startTimers(" + (secondsRemaining*1000L) + ");"
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
			if (!studentAnswer.isEmpty()) { // an answer was submitted
				switch (q.getQuestionType()) {
				case 6:  // Handle five-star rating response
					studentScore = q.pointValue;  // full marks for submitting a response
					break;
				case 7:  // New section for scoring essay questions with Chat GPT
					debug.append("essay question...");
					if (studentAnswer.length()>800) studentAnswer = studentAnswer.substring(0,799);
					
					BufferedReader reader = null;
					JsonObject api_request = new JsonObject();
					api_request.addProperty("model", Subject.getGPTModel());
					JsonObject prompt = new JsonObject();
					prompt.addProperty("id", "pmpt_68b05dd3c7e88190b02ec3c4a41e412003d177cd13da4c5d");
					JsonObject variables = new JsonObject();
					variables.addProperty("question_item", q.printForSage());
					variables.addProperty("student_answer", studentAnswer);
					prompt.add("variables", variables);
					api_request.add("prompt", prompt);

					URL u = new URI("https://api.openai.com/v1/responses").toURL();
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

					int response_code = uc.getResponseCode();
					debug.append("HTTP Response Code: " + response_code);

					JsonObject api_response = null;
					if (response_code/100==2) {
						reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
						api_response = JsonParser.parseReader(reader).getAsJsonObject();
						debug.append(api_response.toString());
						reader.close();
					} else {
						reader = new BufferedReader(new InputStreamReader(uc.getErrorStream()));
						debug.append(JsonParser.parseReader(reader).getAsJsonObject().toString());
						reader.close();
					}

					// Find the output text buried in the response JSON:
					JsonArray output = api_response.get("output").getAsJsonArray();
					JsonObject message = null;
					JsonObject output_text = null;
					for (JsonElement element0 : output) {
						message = element0.getAsJsonObject();
						if (message.has("content")) {
							JsonArray content = message.get("content").getAsJsonArray();
							for (JsonElement element1 : content) {
								output_text = element1.getAsJsonObject();
								if (output_text.has("text")) {
									essay_score = JsonParser.parseString(output_text.get("text").getAsString()).getAsJsonObject();
									studentScore = essay_score.get("score").getAsInt()>=4?q.pointValue:0;
									break;
								}
							}
							break;
						}
					}
					debug.append("e");
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
					buf.append("<b>Thank you for the rating.</b>");
					buf.append(q.printAllToStudents(studentAnswer) + "<br/>");
					break;
				case 7: // Essay response
						studentAnswer += "<br/><br/><b>Feedback: </b>" + essay_score.get("feedback").getAsString() 
								+ "<br/><br/><b>Score: </b>" + essay_score.get("score").getAsInt() + "/5 (full credit)" + "<br/>";
				default:
					buf.append("<div style='display:flex'>"
							+ "<div>"
							+ "<h2>Congratulations!</h2><b>You answered the question correctly.</b> <IMG SRC=/images/checkmark.gif ALT='Check mark' align=bottom /><br/>"
							+ (!user.isAnonymous()?"<a id=showLink role='button' href=# onClick=document.getElementById('solution').style='display:inline';"
							+ "document.getElementById('polly').style='display:none';this.style='display:none'>(show me)</a>":"") 
							+ "</div>"
							+ "<img id=polly src='/images/parrot.png' alt='Fun parrot character' style='max-width:30%; height:auto; margin:10px'>"
						+ "</div>");
				}
			}
			else if (studentAnswer.length() > 0) {
				switch (q.getQuestionType()) {
					case 5:  // Numeric question
						try {
							@SuppressWarnings("unused")
							double dAnswer = Double.parseDouble(q.parseString(studentAnswer));  // throws exception for non-numeric answer
							if (!q.agreesToRequiredPrecision(studentAnswer)) buf.append("<h2>Incorrect Answer <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h2>"
									+ "Your answer does not " + (q.requiredPrecision==0?"exactly match the answer in the database. ":"agree with the answer in the database to within the required precision (" + q.requiredPrecision + "%).<br/><br/>"));
							else if (!q.hasCorrectSigFigs(studentAnswer)) buf.append("<h2>Almost there!</h2>It appears that you've done the calculation correctly, but your answer does not have the correct number of significant figures appropriate for the data given in the question. "
									+ "If your answer ends in a zero, be sure to include a decimal point to indicate which digits are significant or (better!) use <a href=https://en.wikipedia.org/wiki/Scientific_notation#E_notation>scientific E notation</a>.<br/><br/>");
						}
						catch (Exception e2) {
							buf.append("<h2>Wrong Format</h2>"
									+ "This question requires a numeric response expressed as an integer, decimal number, "
									+ "or in scientific E notation (example: 6.022E-23). Your answer was scored incorrect because the computer "
									+ "was unable to recognize your answer as one of these types.<br/>");
						}
						break;
					case 6:  // Five star rating
						buf.append("<h2>No rating was submitted for this item.</h2>");
						break;
					case 7:  // Essay question
						int score = essay_score.get("score").getAsInt();
						if (score<=1) buf.append("<h2>Your answer to this question is incorrect. <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h2>");
						else buf.append("<h2>Your answer is partly correct, but needs improvement.</h2>");
						buf.append(essay_score.get("feedback").getAsString() + "<br/><br/>");
						break;
					default:  // All other types of questions
						buf.append("<h2>Incorrect Answer <IMG SRC=/images/xmark.png ALT='X mark' align=middle></h2>Your answer was scored incorrect because it does not agree with the "
							+ "answer in the database.<br/>");
				}
				
				int nAttempts = 0;
				if (hwa.attemptsAllowed != null) {
					nAttempts = ofy().load().type(HWTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",hwa.id).count();
					buf.append("The maximum number of attempts for each question on this assignment is " + hwa.attemptsAllowed + "<br/>");
					if (nAttempts<hwa.attemptsAllowed) buf.append("The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. ") + "<br/>");
				} else buf.append("The retry delay for this question is " + retryDelayMinutes + (retryDelayMinutes>1?" minutes. ":" minute. ") + "<br/><br/>");
			
				if (user.isInstructor() || user.isTeachingAssistant()) {
					buf.append("<br/>Instructor: <a role='button' href=# onClick=document.getElementById('solution').style='display:inline';this.style='display:none';>show the solution</a><br/><br/>");
				} else if (!user.isAnonymous() && user.isEligibleForHints(q.id)) {
					buf.append("<br/><form method=post action=/Help>"
							+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
							+ "<input type=hidden name=TransactionId value=" + ht.id + " />"
							+ "<input type=hidden name=HashCode value=" + hashMe.hashCode() + " />");
					buf.append("<font color=#EE0000>Do you need some help from your instructor or teaching assistant? </font>");
					buf.append("<input type='submit' value='Get Some Help Here'></form><br/>");
				}				
			}  
			else {
				buf.append("<h3>The answer to the question was left blank.</h3>");
			}

			boolean offerHint = studentScore==0 && q.hasHint() && user.isEligibleForHints(q.id);

			if (!user.isAnonymous()) {
				if (studentScore>0 || user.isInstructor()) {
					buf.append("<div id=solution style='display:none'>"
							+ q.printAllToStudents(studentAnswer)
							+ " <p>\n"
							+ " <div id=explanation style='max-width:800px'>"
							+ " <button id=explainThis class='btn btn-primary' onclick=getExplanation();>Please explain this answer</button>"
							+ " </div>\n"
							+ "<script>\n"
							+ "function getExplanation() {\n"
							+ "  document.getElementById('explainThis').innerHTML='Please wait a moment...';\n"
							+ "  try {\n"
							+ "    var xmlhttp = GetXmlHttpObject();\n"
							+ "    if (xmlhttp==null) {\n"
							+ "      alert('Sorry, your browser does not support AJAX!');\n"
							+ "	     return false;\n"
							+ "    }\n"
							+ "	   xmlhttp.onreadystatechange=function() {\n"
							+ "      if (xmlhttp.readyState==4) {\n"
							+ "        document.getElementById('explanation').innerHTML = xmlhttp.responseText;\n"  // Sage explanation
							+ "        var mathjax = document.createElement('script');\n"
							+ "        mathjax.type = 'text/javascript';\n"
							+ "        mathjax.src = 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js';\n"
							+ "        document.head.appendChild(mathjax);\n"
							+ "      }\n"
							+ "    }\n"
							+ "  } catch (error) {}\n"
							+ "  xmlhttp.open('GET','/Homework?sig=" + user.getTokenSignature() + "&UserRequest=GetExplanation&QuestionId=" + q.id + "&Parameter=" + hashMe.hashCode() + "',true);\n"
							+ "  xmlhttp.send(null);\n"
							+ "}\n"
							+ "</script>\n"
							+ "</div><p>\n");				
				}

				if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) 
					buf.append("<img src=/images/learn_more.png alt='learn more here' /> You can learn more about this topic at <a aria-label='Opens in a new tab' href='" + q.learn_more_url + "' target=_blank>" + q.learn_more_url + "</a><br/><br/>");

				// if the user response was correct, seek five-star feedback:
				if (studentScore > 0) buf.append(fiveStars(user.getTokenSignature()));
				else buf.append("Please take a moment to <a href=/Feedback?sig=" + user.getTokenSignature() + ">tell us about your ChemVantage experience</a>.<p>");

				buf.append("<a class='btn btn-primary' href=/Homework?AssignmentId=" + hwa.id + "&sig=" + user.getTokenSignature() + (offerHint?"&Q=" + q.id:"") + (qn==null?"":"#q" + qn) + ">"
				+ (offerHint?"Please give me a hint":"Continue with this assignment") 
				+ "</a><br clear=left /><br/>");
				
				if (hwa != null) buf.append("You may also <a href=/Homework?UserRequest=ShowScores&sig=" + user.getTokenSignature() + ">review your scores on this assignment</a> "
						+ "or <a href=/Homework?sig=" + user.getTokenSignature() + "&UserRequest=Logout >logout of ChemVantage</a>");
			} else { // user is anonymous
				buf.append("<a class='btn btn-primary' href=/Homework?AssignmentId=" + hwa.id + "&sig=" + user.getTokenSignature() + ">Continue with this assignment</a><br clear=left /><br/>");
				
				buf.append("You may also <a href=/>Return to the ChemVantage home page</a> or <a href=/Homework?sig=" + user.getTokenSignature() + "&UserRequest=Logout >logout of ChemVantage</a> ");
			}
		} catch (Exception e) {
			buf.append("Sorry, there was an unexpected error: " + e.getMessage()==null?e.toString():e.getMessage());
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Error during Homework.printScore: ", e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString() + "<br/>" + user.getId());
			return Logout.now(request,e);
		}
		return buf.toString();
	}

	static String questionTypeDropDownBox(int questionType) {
		StringBuffer buf = new StringBuffer();
		buf.append("\n<SELECT NAME=QuestionType>"
				+ "<OPTION VALUE=1" + (questionType==1?" SELECTED>":">") + "Multiple Choice</OPTION>"
				+ "<OPTION VALUE=2" + (questionType==2?" SELECTED>":">") + "True/False</OPTION>"
				+ "<OPTION VALUE=3" + (questionType==3?" SELECTED>":">") + "Select Multiple</OPTION>"
				+ "<OPTION VALUE=4" + (questionType==4?" SELECTED>":">") + "Fill in word/phrase</OPTION>"
				+ "<OPTION VALUE=5" + (questionType==5?" SELECTED>":">") + "Numeric</OPTION>"
				+ "<OPTION VALUE=6" + (questionType==6?" SELECTED>":">") + "Five Star</OPTION>"
				+ "<OPTION VALUE=7" + (questionType==7?" SELECTED>":">") + "Essay</OPTION>"
				+ "</SELECT>");
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
	
	static void saveQuestion(User user, HttpServletRequest request) {
		if (!user.isInstructor()) return;
		try {
			Question q = assembleQuestion(request);
			q.isActive = true;
			ofy().save().entity(q).now();
			long assignmentId = user.getAssignmentId();
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			a.questionKeys.add(0, key(q));
			ofy().save().entity(a).now();
		} catch (Exception e) {}
	}

	String selectQuestionsForm(User user,Assignment a,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug: ");
		try {
			buf.append("<h1>Customize Homework Assignment</h1>");
			buf.append("<form action=/Homework method=post>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<label><b>Title:</b>&nbspHomework - <input type=text size=25 name=AssignmentTitle value='" + a.title + "' /></label>&nbsp;"
					+ "<input type=submit name=UserRequest value='Save New Title' /></form><br/>\n");
							
			buf.append("By default, students may submit answers to the homework problems as many times as they wish. This rewards students who persist "
					+ "to achieve a better score. However, you may limit the number of attempts here. Leave the field blank to permit unlimited attempts.<br/>"
					+ "<form action=/Homework method=post><input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<label>Attempts allowed:&nbsp;<input type=text size=10 name=AttemptsAllowed " 
					+ (a.attemptsAllowed==null?"placeholder=unlimited":"value=" + a.attemptsAllowed) + " /></label> "
					+ "<input type=submit name=UserRequest value='Set Allowed Attempts' />"
					+ "</form><br/>\n");
			
			// Allow instructor to pick individual question items from all active questions:
			buf.append("Select the homework questions below to be assigned for grading, "
					+ "then click the 'Use Selected Items' button. All questions have equal point value. "
					+ "If you want to include a question that is not included here, you may "
					+ "<a href=/Homework?UserRequest=CreateCustomQuestion&sig=" + user.getTokenSignature() 
					+ ">create a custom question for this assignment</a>.<p>"
					+ "Students may work the optional problems; however, these are not included in the scores "
					+ "reported to the class LMS.<p>\n");
	
			// Show a List of concepts covered by this assignment
			Long newConceptId = null;
			try {  // add a new conceptId
				newConceptId = Long.parseLong(request.getParameter("ConceptId"));
				a.conceptIds.add(0,newConceptId);
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
				buf.append("</ul>\n");
			}
	
			// Create a short form to select one additional key concept to include (will exclude the previous selection, if any)
			buf.append("<form method=get action=/Homework>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value=AssignHomeworkQuestions />"
					+ "<label>You may include additional question items from: "
					+ "<select name=ConceptId onchange=this.form.submit();><option value='Select'>Select a key concept</option>");
			for (Key<Concept> k : conceptKeys) {
				try {
					if (a.conceptIds.contains(k.getId()) || keyConcepts.get(k).orderBy.startsWith(" 0")) continue;  // skip current and hidden conceptIds
					buf.append("<option value='" + k.getId() + "'" + (newConceptId!=null && k.getId()==newConceptId?" selected>":">") + keyConcepts.get(k).title + "</option>");
				} catch (Exception e) {}
			}
			buf.append("</select></label></form><br/>\n<hr>\n");
			
			// now we have all of the relevant conceptIds. Make 2 lists of Assigned and Optional questions:
			StringBuffer assignedQuestions = new StringBuffer();
			StringBuffer optionalQuestions = new StringBuffer();
			int i = 1;  // counter for assigned questions
			int j = 1;  // counter for optional questions
			
			for (Long cId : a.conceptIds) {
				List<Question> questions = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",cId).list();		
				if (questions.size()>1) Collections.sort(questions,new SortBySuccessPct());
				for (Question q : questions) {
					boolean assigned = a.questionKeys.remove(key(q));
					StringBuffer qbuf = new StringBuffer();
					q.setParameters();  // creates randomly selected parameters
					int successRate = q.getPctSuccess();
					qbuf.append("\n<TR>"
							+ "<TD style='vertical-align:top;' NOWRAP>"
							+ "<label>"
							+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'"
							+ (assigned?" CHECKED />":" />")
							+ "<b>&nbsp;" + (assigned?i:j) + ".</b>"
							+ "</label><br/>"
							+ "<span style='font-size:0.5em'>" + (successRate==0?"new item&nbsp;&nbsp;":successRate + "% correct&nbsp;&nbsp;") + "</span></TD>"
							+ "<TD>" + q.printAll() + "</TD>"
							+ "</TR>");
					if (assigned) {
						assignedQuestions.append(qbuf);
						i++;
					} else {
						optionalQuestions.append(qbuf);
						j++;
					}
				}
			}
			
			// If any orphan questions remain, add them to assignedQuestions (this is uncommon)
			if (!a.questionKeys.isEmpty()) {
				List<Question> questions = new ArrayList<Question>(ofy().load().keys(a.questionKeys).values());		
				for (Question q : questions) {
					boolean assigned = a.questionKeys.remove(key(q));
					StringBuffer qbuf = new StringBuffer();
					q.setParameters();  // creates randomly selected parameters
					int successRate = q.getPctSuccess();
					qbuf.append("\n<TR>"
							+ "<TD style='vertical-align:top;' NOWRAP>"
							+ "<label>"
							+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE='" + q.id + "'"
							+ (assigned?" CHECKED >":" />")
							+ "<b>&nbsp;" + (assigned?i:j) + ".</b>"
							+ "</label><br/>"
							+ "<span style='font-size:0.5em'>" + (successRate==0?"new item&nbsp;&nbsp;":successRate + "% correct&nbsp;&nbsp;") + "</span></TD>"
							+ "<TD>" + q.printAll() + "</TD>"
							+ "</TR>");
					if (assigned) {
						assignedQuestions.append(qbuf);
						i++;
					} else {
						optionalQuestions.append(qbuf);
						j++;
					}
				}
			}
			
			buf.append("Currently, this assignment has " + (i-1) + " assigned question items and " + (j-1) + " optional questions.<br/><br/>");
			
			buf.append("<b>Select the assigned question items for this assignment</b><br/>");
			
			// This dummy form uses javascript to select/deselect all questions
			buf.append("<FORM style='display:inline;' NAME=DummyForm><label><INPUT id=selectAll TYPE=CHECKBOX NAME=SelectAll "
					+ "onClick='for (var i=0;i<document.Questions.QuestionId.length;i++)"
					+ "{document.Questions.QuestionId[i].checked=document.DummyForm.SelectAll.checked;}'"
					+ "> Select/Unselect All</label></FORM>&nbsp;&nbsp;&nbsp;");
			buf.append("<script>document.getElementById('selectAll').indeterminate=true;</script>");
			
			// Make a list of individual questions that can be selected or deselected for this assignment
			buf.append("<FORM style='display:inline;' NAME=Questions METHOD=POST ACTION=/Homework />"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + " />"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE='UpdateAssignment' />"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentId VALUE='" + a.id + "' />"
					+ (newConceptId==null?"":"<input type=hidden name=NewConceptId value=" + newConceptId + " />")
					+ "<INPUT TYPE=SUBMIT Value='Use Selected Items' /><br/><br/>");
			
			// Make a table of assigned and optional questions
			buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");
			if (!assignedQuestions.isEmpty()) {
				buf.append("<TR><TD COLSPAN=2><b>Assigned Questions:</b></TD></TR>");
				buf.append(assignedQuestions);
			}
			if (!optionalQuestions.isEmpty()) {
				buf.append("<TR><TD COLSPAN=2><b>Optional Questions:</b></TD></TR>");
				buf.append(optionalQuestions);
			}
			buf.append("</TABLE><INPUT TYPE=SUBMIT Value='Use Selected Items'></FORM><br/>");
		} catch (Exception e) {
			buf.append(e.toString() + " " + e.getMessage() + "<br/>" + debug.toString());
		}
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
			buf.append("<table><tr><th>#</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th><th>Scores Detail</th></tr>");
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
	
	static String synchronizeScore(User user, Assignment a, String forUserId) {
		try {
			if (!user.isInstructor()) throw new Exception();  // only instructors can use this function
			if (a==null) throw new Exception();  // can only do this for a known assignment
			if (LTIMessage.postUserScore(Score.getInstance(forUserId,a), forUserId).contains("Success")) return "OK";
		} catch (Exception e) {}
		return "Failed. Check assignment settings in the LMS.";
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
	
	class SortBySuccessPct implements Comparator<Question> {
		
		SortBySuccessPct() {}
		
		public int compare(Question q1, Question q2) {
			int rank = q2.getPctSuccess() - q1.getPctSuccess(); 
			if (rank==0) rank = q2.id.compareTo(q1.id);
			return rank;
		}
	}	
}

