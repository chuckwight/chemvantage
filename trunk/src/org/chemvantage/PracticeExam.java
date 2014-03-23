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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class PracticeExam extends HttpServlet {
	// parameters that determine the properties of the exam program:
	// Warning! do not use any user-specific variables here. Not thread-safe!

	int nSubjectAreas = 1;               // default number of subject areas for exam overridden by values read from AssignmentInfo database
	int nQuestionsPerSubjectArea = 10;   // number of questions presented in each area also overridden in method printExam()
	int timeLimit = 60;                  // minutes; set to zero for no time limit to complete the exam
	int waitForNewDownload = 0;          // minutes; set to zero for unlimited rate of exam downloads
	boolean enforceDeadlines = true;     // true means that exam score is not recorded after the deadline
	boolean allowMultipleTries = true;   // false allows only one attempt at each exam; true is recommended
	boolean scrambleQuestions = true;    // false presents the same questions each attempt; true is recommended
	boolean allowWorkAhead = false;      // true makes every exam available; false makes available after the deadline for the previous exam expires
	boolean showMissedQuestions = true;  // true reveals questions that were answered incorrectly as part of the grading process
	boolean useSectionDeadlines = false; // true uses default deadlines for all sections of the course
	int numberOfSections = 1;            // 
	boolean trackAnswers= false;         // true keeps track of missed questions for students
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();
	static Map<Key<Question>,Question> examQuestions = new HashMap<Key<Question>,Question>();

	public String getServletInfo() {
		return "This servlet presents and scores an exam for the user.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.getHeader(user) + printExam(user,request) + Home.footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Home.getHeader(user) + printScore(user,request) + Home.footer);
	}

	String designExam(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>Practice " + subject.title + " Exam</h2>");
			buf.append("<div id=topicsForm>");

			buf.append("Please select <b>at least 3 topics</b> below to be covered on this practice exam.<p>");
			buf.append("<FORM NAME=TopicForm METHOD=GET>");
			buf.append("\n<TABLE>");
			List<Topic> topics = ofy.query(Topic.class).list();
			int i = 0;
			for (Topic t : topics) {
				if ("Hide".equals(t.orderBy)) continue;
				buf.append(i%3==0?"<TR><TD>":"<TD>");
				buf.append("<INPUT TYPE=CHECKBOX NAME=TopicId VALUE='" + t.id + "' "
						+ "onClick=\"javascript: var checked=0; "
						+ "for(i=0;i<document.TopicForm.TopicId.length;i++) if(document.TopicForm.TopicId[i].checked) checked++;"
						+ "document.TopicForm.begin.disabled=(checked<3);"
						+ "if(document.TopicForm.begin.disabled) document.TopicForm.begin.value='Select at least 3 topics';"
						+ "else document.TopicForm.begin.value='Begin the exam';\">" 
						+ t.title + "<br>\n");
				buf.append(i%3==2?"</TD></TR>\n":"</TD>");
				i++;
			}
			buf.append("</TABLE><p>");
			buf.append("You will have " + timeLimit + " minutes to submit this exam for scoring.<br>");
			buf.append("<INPUT TYPE=SUBMIT NAME=begin DISABLED=true VALUE='Select at least 3 topics'>");
			buf.append("</FORM></div>");
		} catch (Exception e) {
			buf.append("designExam: " + e.toString());
		}
		return buf.toString();
	}

	String printExam(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			// Get requested topic ids for this exam
			long[] topicIds = new long[0];
			long assignmentId = 0;
			Assignment a = null;
			try {  // this branch works if the practice exam is assigned
				assignmentId=Long.parseLong(request.getParameter("AssignmentId"));
				a = ofy.get(Assignment.class,assignmentId);
				topicIds = new long[a.topicIds.size()];
				for (int i=0;i<topicIds.length;i++) topicIds[i] = a.topicIds.get(i);
			} catch (Exception e) {  // otherwise this is a student-designed exam
				String[] topicStringIds = request.getParameterValues("TopicId");
				if (topicStringIds!=null) {
					topicIds = new long[topicStringIds.length];
					for (int i=0;i<topicStringIds.length;i++) topicIds[i] = Long.parseLong(topicStringIds[i]);
				}
			}
			
			// Check to see if this user has any pending exams:
			Date now = new Date();
			Date then = new Date(now.getTime()-timeLimit*60000);  // timeLimit minutes ago
			
			PracticeExamTransaction pt = ofy.query(PracticeExamTransaction.class).filter("userId",user.id).filter("graded",null).filter("downloaded >",then).get();
			if (pt != null) topicIds = pt.topicIds;  // continue an interrupted exam
			else if (topicIds.length < 3) return designExam(user,request);  // redirect to get a valid set of 3+ topic keys
			else {  // this is a valid request for a new exam with at least 3 topicIds; create a new transaction
				pt = new PracticeExamTransaction(topicIds,user.id,now,null,new int[topicIds.length],new int[topicIds.length],request.getRequestURI());
				ofy.put(pt);	
			}
			
			// past this point we will present a practice exam to the student
			List<Long> topicList = new ArrayList<Long>();
			for (long id : topicIds) topicList.add(topicList.size(),id); // keep same order of topicIds in array and List
			
			List <Key<Question>> questionKeys_02pt = ofy.query(Question.class).filter("assignmentType","Exam").filter("pointValue",2).filter("topicId in",topicList).listKeys();
			List <Key<Question>> questionKeys_10pt = ofy.query(Question.class).filter("assignmentType","Exam").filter("pointValue",10).filter("topicId in",topicList).listKeys();
			List <Key<Question>> questionKeys_15pt = ofy.query(Question.class).filter("assignmentType","Exam").filter("pointValue",15).filter("topicId in",topicList).listKeys();
			
			if (a != null) {  // eliminate any questionKeys not listed in the assignment
				for (Key<Question> k : questionKeys_02pt) if (!a.questionKeys.contains(k)) questionKeys_02pt.remove(k);
				for (Key<Question> k : questionKeys_10pt) if (!a.questionKeys.contains(k)) questionKeys_10pt.remove(k);
				for (Key<Question> k : questionKeys_15pt) if (!a.questionKeys.contains(k)) questionKeys_15pt.remove(k);
			}
			
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId==0?null:ofy.get(Group.class,user.myGroupId);
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			buf.append("\n<h2>" + subject.title + " Exam</h2>");
			buf.append("\n<b>" + user.getBothNames() + "</b><br>");
			Date downloaded = pt.downloaded;
			buf.append(df.format(downloaded) + "<p>");
			int secondsRemaining = (int) (timeLimit*60 - (now.getTime() - downloaded.getTime())/1000);

			buf.append("Topics covered on this exam:<OL>");
			for (long topicId : topicIds) {
				buf.append("<LI>" + ofy.get(Topic.class,topicId).title + "</LI>");
			}
			buf.append("</OL>");

			buf.append("This exam must be submitted for grading within " + timeLimit + " minutes of when it is first downloaded.");

			Random rand = new Random();  // create random number generator to select exam questions
			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			rand.setSeed(pt.id);  // random number generator seeded with PracticeExamTransaction id value

			buf.append(ajaxExamJavaScript());  // this code allows users to use the Google SOAP search spell checking function
			
			buf.append("\n<FORM METHOD=POST ACTION=PracticeExam "
					+ "onSubmit=\"return confirm('Submit this exam for grading now. Are you sure?')\">");

			buf.append("<div id='timer0' style='color: red'></div>");
			buf.append("\n<input type=submit value='Grade This Practice Exam'><p>");

			// Randomly select the questions to be presented, eliminating each from questionSet as they are printed
			int[] possibleScores = new int[topicIds.length];

			// Two-point questions
			buf.append("<U>2 point questions:</U>");
			buf.append("<OL>\n");
			int nQuestions = 10;
			int i = 0;
			while (i<nQuestions && questionKeys_02pt.size()>0) {
				Key<Question> k = questionKeys_02pt.remove(rand.nextInt(questionKeys_02pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy.get(k);
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicList.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
			buf.append("</OL>");

			// 10-point questions
			buf.append("<U>10 point questions:</U>");
			buf.append("<OL>\n");
			nQuestions = 5;
			i=0;
			while (i<nQuestions && questionKeys_10pt.size()>0) {
				Key<Question> k = questionKeys_10pt.remove(rand.nextInt(questionKeys_10pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy.get(k);
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicList.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
						buf.append("</OL>");

			// 15-point questions
			buf.append("<U>15 point questions:</U>");
			buf.append("<OL>\n");
			nQuestions = 2;
			i = 0;
			while (i<nQuestions && questionKeys_15pt.size()>0) {
				Key<Question> k = questionKeys_15pt.remove(rand.nextInt(questionKeys_15pt.size()));
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy.get(k);
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;  // this catches cases where an assigned question no longer exists
					}
				}
				i++;
				possibleScores[topicList.indexOf(q.topicId)] += q.pointValue;
				q.setParameters((int)(pt.id - q.id));
				buf.append("\n<li>" + q.print() + "<br></li>\n");
			}
			buf.append("</OL>");

			pt.possibleScores = possibleScores;
			ofy.put(pt);

			buf.append("\n<input type=hidden name='ExamId' value=" + pt.id + ">");
			buf.append("\n<input type=hidden name='UserRequest' value='GradeExam'>");
			buf.append("<div id='timer1' style='color: red'></div>");
			buf.append("\n<input type=submit value='Grade This Practice Exam'>");
			buf.append("\n</form>");

			buf.append("<SCRIPT language='JavaScript'>"
					+ "function countdown(seconds) {"
					+ "var minutes = Math.floor(seconds/60);"
					+ "var oddSeconds = seconds%60;"
					+ "for(i=0;i<2;i++)"
					+ "document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';"
					+ "setTimeout('countdown(' + (seconds-1) + ')',1000);"
					+ "}"
					+ "countdown(" + secondsRemaining + ");"
					+ "</SCRIPT>"); 

		} catch (Exception e) {
			buf.append("printExam: " + e.toString());
		}
		return buf.toString();
	}

	String printScore(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			buf.append("<h2>Practice Exam Results</h2>");
			buf.append("\n<b>" + user.getBothNames() + "</b><br>");
			
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
			Group myGroup = user.myGroupId==0?null:ofy.get(Group.class,user.myGroupId);
			TimeZone tz = myGroup==null?TimeZone.getDefault():myGroup.getTimeZone();
			df.setTimeZone(tz);

			Date now = new Date();
			buf.append(df.format(now) + "<br>");

			long examId = Long.parseLong(request.getParameter("ExamId"));
			PracticeExamTransaction pt = ofy.get(PracticeExamTransaction.class,examId);
			if (pt.graded != null) return "Sorry, this exam has been graded already.";
			if (now.getTime() - pt.downloaded.getTime() > 60000*(this.timeLimit+1)) return "Sorry, the grading period for this exam has expired.";

			// if everything is still OK, score the exam:
			long[] topicIds = pt.topicIds;
			List<Long> topicList = new ArrayList<Long>();
			for (long id : topicIds) topicList.add(topicList.size(),id); // keep same order of topicIds in array and List
			
			buf.append("Topics covered on this exam:<OL>");
			for (long topicId : topicIds) buf.append("<LI>" + ofy.get(Topic.class,topicId).title + "</LI>");
			buf.append("</OL>");
			
			// create a buffer to hold the correct solutions to missed questions:
			StringBuffer missedQuestions = new StringBuffer();
			missedQuestions.append("The following questions were answered incorrectly. There may be additional questions (not shown) that were left unanswered.");			
			missedQuestions.append("<OL>");

			int[] studentScores = new int[topicIds.length];
			int wrongAnswers = 0;

			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			for (Enumeration<?> e = request.getParameterNames();e.hasMoreElements();) {
				try {
					questionKeys.add(new Key<Question>(Question.class,Long.parseLong((String) e.nextElement())));
				} catch (Exception e2) {}
			}
			
			// begin the main scoring loop:
			for (Key<Question> k : questionKeys) {
			
				String studentAnswer[] = request.getParameterValues(Long.toString(k.getId()));
				if (studentAnswer != null) for (int i = 1; i < studentAnswer.length; i++) studentAnswer[0] += studentAnswer[i];
				else studentAnswer = new String[] {""};
				Question q = examQuestions.get(k);
				if (q==null) {
					try {
						q = ofy.get(k);
						examQuestions.put(k,q);
					} catch (Exception e) {
						continue;
					}
				}
				q.setParameters((int)(pt.id - q.id));
				int score = studentAnswer[0].length()==0?0:q.isCorrect(studentAnswer[0])?q.pointValue:0;
				if (score > 0) studentScores[topicList.indexOf(q.topicId)] += score;
				if (studentAnswer[0].length() > 0) ofy.put(new Response("PracticeExam",q.topicId,q.id,studentAnswer[0],q.getCorrectAnswer(),score,q.pointValue,user.id,now));
				if (score == 0) {
					// include question in list of incorrectly answered questions
					wrongAnswers++;
					missedQuestions.append("\n<LI>" + q.printAllToStudents(studentAnswer[0]) + "</LI>\n");
				}
			}
			missedQuestions.append("</OL>\n");
			pt.scores = studentScores;
			pt.graded = now;
			ofy.put(pt);
			
			int score = 0;
			int possibleScore = 0;
			for (int i=0;i<topicIds.length;i++) {
				score += studentScores[i];
				possibleScore += pt.possibleScores[i];
			}
			buf.append("<b>Your score on this exam is " + score + " out of a possible " + possibleScore + " points.</b><p>");
			if (score > 0 && score == possibleScore) buf.append ("<b>Congratulations on a perfect score!</b>");
			else {
				if (wrongAnswers > 0) buf.append(missedQuestions);
				else buf.append("Some questions were left blank.");
			}
			// embed ajax code to provide feedback
			buf.append(ajaxScoreJavaScript(user.verifiedEmail));
		}
		catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String ajaxExamJavaScript() {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSpellCheck(id) {\n"
		+ "  var xmlhttp;\n"
		+ "  var answer = document.getElementById(id).value.trim();\n"
		+ "  if (answer.length==0) {\n"
		+ "    document.getElementById('status').innerHTML='Nothing to check';\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "  var correctedAnswer;\n"
		+ "  var status=document.getElementById('status'+id);\n"
		+ "  var answerField=document.getElementById(id);\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      correctedAnswer = xmlhttp.responseText.trim();\n"
		+ "      if (correctedAnswer=='Spell checker is offline, sorry') {\n"
		+ "      status.innerHTML=correctedAnswer; return false;\n"
		+ "    }\n"
		+ "    answerField.value=correctedAnswer;\n"
		+ "    if (correctedAnswer==answer) status.innerHTML='Spelling is OK';\n"
		+ "    else status.innerHTML='Did you mean this instead?';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','SpellingChecker?UserRequest=SpellCheck&Answer='+answer,true);\n"
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
	}

	String ajaxScoreJavaScript(boolean verifiedEmail) {
		return "<SCRIPT TYPE='text/javascript'>\n"
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
		+ "      '<FONT COLOR=RED><b>Thank you. An editor will review your comment. "
		+ (!verifiedEmail?"However, no response is possible unless you verify the email address in your <a href=/Verification>user profile</a>.":"") 
		+ "</b></FONT><p>';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  url += '&QuestionId=' + id + '&Notes=' + note;\n"
		+ "  xmlhttp.open('GET',url,true);\n"
		+ "  xmlhttp.send(null);\n"
		+ "  return false;\n"
		+ "}\n"
		+ "function ajaxStars(nStars) {\n"
		+ "  var xmlhttp;\n"
		+ "  if (nStars==0) return false;\n"
		+ "  xmlhttp=GetXmlHttpObject();\n"
		+ "  if (xmlhttp==null) {\n"
		+ "    alert ('Sorry, your browser does not support AJAX!');\n"
		+ "    return false;\n"
		+ "  }\n"
		+ "  xmlhttp.onreadystatechange=function() {\n"
		+ "    var msg;\n"
		+ "    switch (nStars) {\n"
		+ "      case '1': msg='1 star - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to tell us why:'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=1><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - How we can serve you better?</a>'"
		+ "                + '<FORM ACTION=Feedback METHOD=POST><INPUT TYPE=HIDDEN NAME=Stars Value=2><INPUT TYPE=HIDDEN NAME=Save VALUE=No>'"
		+ "                + '<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback><INPUT NAME=Comments SIZE=60><INPUT TYPE=SUBMIT></FORM>';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=Feedback>Click here</a> '"
		+ "                + 'to provide additional feedback.';"
		+ "                break;\n"
		+ "      case '4': msg='4 stars - Thank you';"
		+ "                break;\n"
		+ "      case '5': msg='5 stars - Thank you!';"
		+ "                break;\n"
		+ "      default: msg='You clicked ' + nStars + ' stars.';\n"
		+ "    }\n"
		+ "    if (xmlhttp.readyState==4) {\n"
		+ "      document.getElementById('vote').innerHTML=msg;\n"
		+ "    }\n"
		+ "  }\n"
		+ "  xmlhttp.open('GET','Feedback?UserRequest=AjaxRating&NStars='+nStars,true);\n"
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
	}
}
