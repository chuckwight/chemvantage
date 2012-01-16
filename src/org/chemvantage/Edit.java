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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

public class Edit extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "This servlet is used by editors and admins to create, edit and delete Quiz questions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getInstance(request.getSession(true));
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("ManageTopics")) {
				out.println(Home.getHeader(user) + topicsForm(user,request) + Home.footer);
			}
			else if (userRequest.equals("ManageVideos")) {
				out.println(Home.getHeader(user) + videosForm(user,request) + Home.footer);
			}
			else if (userRequest.equals("ManageTexts")) {
				out.println(Home.getHeader(user) + textsForm(user,request) + Home.footer);
			}
			else if (userRequest.equals("Preview")) {
				out.println(Home.getHeader(user) + previewQuestion(user,request) + Home.footer);
			}
			else if (userRequest.equals("NewQuestionForm")) {
				out.println(Home.getHeader(user) + newQuestionForm(user,request) + Home.footer);
			}
			//else if (userRequest.equals("Review")) { // review a pending question (problem or contribution)
			//	out.println(Home.getHeader(user) + reviewQuestion(user,request) + Home.footer);
			//}
			else if (userRequest.equals("Edit")) {  // edit a current question in the database
				out.println(Home.getHeader(user) + editCurrentQuestion(user,request) + Home.footer);
			}
			else { // show the default Editors page
				out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
			}
		} catch (Exception e) {
		}
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
		
		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = "";
		
		if (userRequest.equals("CreateTopic")) {
			createTopic(user,request);
			out.println(Home.getHeader(user) + topicsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("UpdateTopic")) {
			updateTopic(user,request); 
			out.println(Home.getHeader(user) + topicsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("DeleteTopic")) {
			deleteTopic(user,request);
			out.println(Home.getHeader(user) + topicsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Create Video")) {
			createVideo(user,request);
			out.println(Home.getHeader(user) + videosForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Update Video")) {
			updateVideo(user,request);
			out.println(Home.getHeader(user) + videosForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Delete Video")) {
			deleteVideo(user,request);
			out.println(Home.getHeader(user) + videosForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Create Text")) {
			createText(user,request);
			out.println(Home.getHeader(user) + textsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Update Text")) {
			updateText(user,request);
			out.println(Home.getHeader(user) + textsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Delete Text")) {
			deleteText(user,request);
			out.println(Home.getHeader(user) + textsForm(user,request) + Home.footer);
		}
		else if (userRequest.equals("Preview")) {
			out.println(Home.getHeader(user) + previewQuestion(user,request) + Home.footer);
		}
		else if (userRequest.equals("Save New Question")) {
			createQuestion(user,request);
			out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
		}
		else if (userRequest.equals("Update Question")) {
			try {updateQuestion(user,request);} catch (Exception e) {out.println(e.toString());};
			out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
		}
		else if (userRequest.equals("Delete Question")) {
			deleteQuestion(user,request);
			out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
		}
		else if (userRequest.equals("Discard Question")) {
			try {
				long questionId = Long.parseLong(request.getParameter("QuestionId"));
				ofy.delete(Question.class,questionId);
			} catch (Exception e) {}
			out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
		}
	}

	String editorsPage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Editors' Page for " + subject.title + "</h3>");
		try {
			int nPending = ofy.query(Question.class).filter("isActive",false).count();
			buf.append("<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageTopics>Manage Topics</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageVideos>Manage Videos</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageTexts>Manage Texts</a>");
			
			buf.append("<h4>Current Questions</h4>");
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			String assignmentType = request.getParameter("AssignmentType");
			boolean showQuestions = (topicId >0 && assignmentType != null && assignmentType.length()>0);
			buf.append("<b>Subject: " + subject.title + "</b><br>");
			buf.append("<FORM NAME=TopicSelect METHOD=GET ACTION=Edit>");
			buf.append("<FONT" + (request.getParameter("TopicId")!=null && topicId==0?" COLOR=RED>":">") + "<b>Topic:</b></FONT>" + topicSelectBox(topicId,showQuestions));
			buf.append("<FONT" + (assignmentType!=null && assignmentType.length()==0?" COLOR=RED>":">") + "<b> Assignment Type:</b></FONT>" + assignmentTypeDropDownBox(assignmentType,true));
			buf.append(" <INPUT TYPE=SUBMIT VALUE=" + (showQuestions?"Refresh>":"'Show Questions'>"));
			buf.append("</FORM>");

			if (showQuestions) {
				buf.append("<FORM NAME=NewQuestion METHOD=GET ACTION=Edit>");
				buf.append("Add a new question for this topic:<br>"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=NewQuestionForm>"
						+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topicId + "'>"
						+ "<INPUT TYPE=HIDDEN NAME=QuestionType>"
						+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=1;submit()\" VALUE='Multiple Choice'> "
						+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=2;submit()\" VALUE='True/False'> "
						+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=3;submit()\" VALUE='Select Multiple'> "
						+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=4;submit()\" VALUE='Fill in Word'> "
						+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=5;submit()\" VALUE='Numeric'>"
						+ "</FORM>");

				buf.append("This assignment draws from the following questions:");
				Topic t = ofy.get(Topic.class,topicId);
				Query<Question> questions = t.getQuestions(assignmentType);
				
				buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");
				int i=0;
				for (Question q : questions) {
					q.setParameters();
					long questionId = q.id;
					i++;
					buf.append("<FORM METHOD=GET ACTION=Edit>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topicId + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + questionId + "'>"
							+ "<TR ID=" + questionId + " VALIGN=TOP>"
							+ "<TD ALIGN=RIGHT NOWRAP><INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Edit> " + i + ".</TD><TD>");
					buf.append("\n" + q.printAll() + "</TD>");
					
				if (q.requiresParser) {
					buf.append("<TD><a href=Edit?TopicId=" + topicId + "&AssignmentType=" + assignmentType + "#" 
							+ questionId + "><FONT SIZE=-2>Refresh</FONT></a></TD>");
				}
					 
					buf.append("</TR></FORM>");
				}
				buf.append("</TABLE>");	
			}
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}

	String assignmentTypeDropDownBox(String defaultType) {
		return assignmentTypeDropDownBox(defaultType,false);
	}
	
	String assignmentTypeDropDownBox(String defaultType,boolean autoSubmit) {
		if (defaultType == null) defaultType = "";
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=AssignmentType" + (autoSubmit?" onChange=submit()>":">"));
		if (defaultType.length() == 0) buf.append("\n<OPTION VALUE=''>Select a type</OPTION>");
		buf.append("<OPTION" + (defaultType.equals("Quiz")?" SELECTED":"") + ">Quiz</OPTION>"
		+ "<OPTION" + (defaultType.equals("Homework")?" SELECTED":"") + ">Homework</OPTION>"
		+ "<OPTION" + (defaultType.equals("Exam")?" SELECTED":"") + ">Exam</OPTION>"
		+ "</SELECT>");
		return buf.toString();
	}

	String topicSelectBox(long topicId) {
		return topicSelectBox(topicId,false);
	}
	
	String topicSelectBox(long topicId,boolean autoSubmit) {
		StringBuffer buf = new StringBuffer("\n<SELECT NAME=TopicId" + (autoSubmit?" onChange=submit()>":">"));
		if (topicId == 0) buf.append("\n<OPTION VALUE=''>Select a topic</OPTION>");
		Query<Topic> topics = ofy.query(Topic.class).order("orderBy");
		for (Topic t : topics) {
			buf.append("<OPTION VALUE=" + t.id + (t.id.equals(topicId)?" SELECTED>":">")
					+ t.title + "</OPTION>\n");
		}
		buf.append("</SELECT>");
		return buf.toString();
	}

	String questionTypeDropDownBox(int questionType) {
		StringBuffer buf = new StringBuffer();
		buf.append("\n<SELECT NAME=QuestionType>"
				+ "<OPTION VALUE=1" + (questionType==1?" SELECTED>":">") + "Multiple Choice</OPTION>"
				+ "<OPTION VALUE=2" + (questionType==2?" SELECTED>":">") + "True/False</OPTION>"
				+ "<OPTION VALUE=3" + (questionType==3?" SELECTED>":">") + "Select Multiple</OPTION>"
				+ "<OPTION VALUE=4" + (questionType==4?" SELECTED>":">") + "Fill in word/phrase</OPTION>"
				+ "<OPTION VALUE=5" + (questionType==5?" SELECTED>":">") + "Numeric</OPTION>"
				+ "</SELECT>");
		return buf.toString();
	}
	
	String topicsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Quiz/Homework/Exam Topics</h3>");
		try {
			// print the table of topics for this subject:
			buf.append("<b>" + subject.title + "</b>\n");
			buf.append("<TABLE BORDER=0 CELLSPACING=3>"
					+ "<TR><TH COLSPAN=3>&nbsp;</TH><TH COLSPAN=2>View/Add/Edit Questions</TH></TR>"
					+ "<TR><TH>Order</TH><TH>Title</TH><TH>Action</TH><TH>Quiz</TH><TH>HW</TH></TR>\n");
			Query<Topic> topics = ofy.query(Topic.class).order("orderBy");
				for (Topic t : topics) { // one row for each topic
					int nQuiz = t.getQuestionCount("Quiz");
					int nHW = t.getQuestionCount("Homework");
					buf.append("\n<FORM NAME=TopicsForm" + t.id + " METHOD=POST ACTION=Edit>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateTopic>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + t.id + "'>");
					buf.append("\n<TR>"
							+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4 VALUE='" + t.orderBy + "'></TD>"
							+ "<TD ALIGN=CENTER><INPUT NAME=Title VALUE='" + CharHider.quot2html(t.title) + "'></TD>"
							+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE=Update>"
							+ ((nQuiz==0 && nHW==0)?"<INPUT TYPE=SUBMIT VALUE='Delete' "
									+ "onClick=\"javascript: document.TopicsForm" + t.id + ".UserRequest.value='DeleteTopic';\">":"")
									+ "</TD></FORM>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Quiz&TopicId=" + t.id + "><b>" + nQuiz + "</b></a></TD>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Homework&TopicId=" + t.id + "><b>" + nHW + "</b></a></TD>");

					buf.append("</TR>");
				}
			//}
			// print one-row form to add a new topic (quiz):
			buf.append("<FORM METHOD=POST ACTION=Edit><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=CreateTopic>");
			buf.append("<TR>"
					+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4></TD>"
					+ "<TD ALIGN=CENTER><INPUT NAME=Title></TD>"
					+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE='Create New Topic'></TD><TD>&nbsp;</TD><TD>&nbsp;</TD></TR></FORM>");
			buf.append("</TABLE>");
			buf.append("<FONT SIZE=-1 COLOR=RED>Notes:<OL>"
					+ "<LI>The alphanumeric value of Order controls the order that topics are arranged.</LI>"
					+ "<LI>To hide a topic from students, change the value of Order to 'Hide'.</LI>"
					+ "<LI>A topic can be deleted only if all its Quiz and Homework questions have been deleted or moved.</LI>"
					+ "</OL></FONT>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	String videosForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Videos for " + subject.title + "</h3>");
		try {
			Query<Video> videos = ofy.query(Video.class);
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>OrderBy</TH><TH>Title</TH><TH>Serial #</TH><TH>Action</TH></TR>");
			for (Video v : videos) {
				buf.append("<FORM ACTION=Edit METHOD=POST>"
						+ "<INPUT TYPE=HIDDEN NAME=VideoId VALUE=" + v.id + ">"
						+ "<TR><TD><INPUT TYPE=TEXT NAME=OrderBy VALUE=" + v.orderBy + "></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Title VALUE='" + CharHider.quot2html(v.title) + "'></TD>"
						+ "<TD>" + v.serialNumber + "</TD>"
						+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Video'>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Video'></TD></TR>"
						+ "</FORM>");
			}
			buf.append("<FORM ACTION=Edit METHOD=POST>"
					+ "<TR><TD><INPUT TYPE=TEXT NAME=OrderBy></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Title></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=SerialNumber></TD>"
					+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Create Video'></TD></TR>"
					+ "</FORM>");
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	void createVideo(User user,HttpServletRequest request) {
		Video v = new Video(request.getParameter("SerialNumber"),request.getParameter("Title"));
		v.orderBy = (request.getParameter("OrderBy"));
		ofy.put(v);
	}
	
	void updateVideo(User user,HttpServletRequest request) {
		try {	
			Video v = ofy.get(Video.class,Long.parseLong(request.getParameter("VideoId")));
			v.title = request.getParameter("Title");
			v.orderBy = request.getParameter("OrderBy");
			ofy.put(v);
		} catch (Exception e) {}
	}
	
	void deleteVideo(User user,HttpServletRequest request) {
		ofy.delete(Video.class,Long.parseLong(request.getParameter("VideoId")));
	}
	
	public String textsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Texts for " + subject.title + "</h3>");
		buf.append("This is a list of open source textbooks shown on the Home page.");
		try {
			Query<Text> texts = ofy.query(Text.class);
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>Title</TH><TH>Author</TH><TH>Publisher</TH><TH>URL</TH></TR>");
			for (Text text : texts) {
				buf.append("<FORM ACTION=Edit METHOD=POST>"
						+ "<INPUT TYPE=HIDDEN NAME=TextId VALUE=" + text.id + ">"
						+ "<TR><TD><INPUT TYPE=TEXT NAME=Title VALUE='" + CharHider.quot2html(text.title) + "'</TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Author VALUE='" + CharHider.quot2html(text.author) + "'></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Publisher VALUE='" + CharHider.quot2html(text.publisher) + "'></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=URL VALUE='" + text.URL + "'></TD>"
						+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Text'>"
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Text'></TD></TR>"
						+ "</FORM>");
			}
			buf.append("<FORM ACTION=Edit METHOD=POST><TR>"
					+ "<TD><INPUT TYPE=TEXT NAME=Title></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Author></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Publisher></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=URL></TD>"
					+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Create Text'></TD></TR>"
					+ "</FORM>");
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	void createText(User user,HttpServletRequest request) {
		Text text = new Text(request.getParameter("Title"),request.getParameter("Author"),request.getParameter("Publisher"),request.getParameter("URL"));
		ofy.put(text);
	}
	
	void updateText(User user,HttpServletRequest request) {
		try {
			Text text = ofy.get(Text.class,Long.parseLong(request.getParameter("TextId")));
			text.title = request.getParameter("Title");
			text.author = request.getParameter("Author");
			text.publisher = request.getParameter("Publisher");
			text.URL = request.getParameter("URL");
			ofy.put(text);
		} catch (Exception e) {}
	}
	
	void deleteText(User user,HttpServletRequest request) {
		ofy.delete(Text.class,Long.parseLong(request.getParameter("TextId")));
	}
	
	
	String newQuestionForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e) {}
		String assignmentType = request.getParameter("AssignmentType");

		int questionType = 0;
		try {
			questionType = Integer.parseInt(request.getParameter("QuestionType"));
			switch (questionType) {
			case (1): buf.append("<h3>New Multiple-Choice " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to select the single best "
					+ "answer to the question."); break;
			case (2): buf.append("<h3>New True-False " + assignmentType + " Question</h3>");
			buf.append("Write the question as an affirmative statement. Then "
					+ "indicate below whether the statement is true or false."); break;
			case (3): buf.append("<h3>New Select-Multiple " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text and the possible answers "
					+ "(up to a maximum of 5). Be sure to "
					+ "select all of the correct answers to the question."); break;
			case (4): buf.append("<h3>New Fill-in-Word " + assignmentType + " Question</h3>");
			buf.append("Start the question text in the upper textarea box. Indicate "
					+ "the correct answer (and optionally, an alternative correct answer) in "
					+ "the middle boxes, and the end of the question text below that.  The answers "
					+ "are not case-sensitive or punctuation-sensitive, but spelling must "
					+ "be exact."); break;
			case (5): buf.append("<h3>New Numeric " + assignmentType + " Question</h3>");
			buf.append("Fill in the question text in the upper textarea box and "
					+ "the correct numeric answer below. Also indicate the required precision "
					+ "of the student's response in percent (default = 2%). Use the bottom "
					+ "textarea box to finish the question text and/or to indicate the "
					+ "expected dimensions or units of the student's answer."); break;
			default: buf.append("An unexpected error occurred. Please try again.");
			}
			Question question = new Question(questionType);
			buf.append("<p><FORM METHOD=POST ACTION=Edit>"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + ">");
			buf.append(subject.getTopicSelectBox(topicId));
			buf.append(assignmentType.equals("Exam")?"Point Value: " + pointValueSelectBox() + "<br>":"<INPUT TYPE=HIDDEN NAME=PointValue VALUE=1>");
			buf.append(question.edit());
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview'></FORM>");
		} catch (Exception e) {
			return buf.toString() + "<br>" + e.getMessage();
		}
		return buf.toString();
	}

	String pointValueSelectBox() {
		return pointValueSelectBox(0);
	}
	
	String pointValueSelectBox(int points) {
		return "<SELECT NAME=PointValue>"
		+ "<OPTION" + (points==2?" SELECTED":"") + ">2</OPTION>"
		+ "<OPTION" + (points==10?" SELECTED":"") + ">10</OPTION>"
		+ "<OPTION" + (points==15?" SELECTED":"") + ">15</OPTION>"
		+ "</SELECT>";
	}
	
	String previewQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = 0;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
			} catch (Exception e2) {}
			boolean current = questionId>0;
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			
			Question q = assembleQuestion(request);
			if (q.requiresParser) q.setParameters();
			buf.append("<h3>Preview Question</h3>");
			buf.append("Subject: " + subject.title + "<br>");
			buf.append("Topic: " + ofy.get(Topic.class,topicId).title + "<br>");
			q.assignmentType = request.getParameter("AssignmentType");
			if (q.assignmentType==null || q.assignmentType.isEmpty()) q.assignmentType = "Quiz";
			buf.append("Assignment Type: " + q.assignmentType);
			if (q.assignmentType.equals("Exam")) { // validate point value
				if (q.pointValue < 2) q.pointValue = 2;
				buf.append(" (" + q.pointValue + " points)<p>");
			} else {
				q.pointValue = 1;
				buf.append(" (1 point)<p>");
			}
			buf.append("<FORM Action=Edit METHOD=POST>");
			
			buf.append(q.printAll());
			
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + ">");
			if (current) buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Question'>");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question'>");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save New Question'>");
			
			buf.append("<hr><h3>Continue Editing</h3>");
			buf.append("Topic:" + topicSelectBox(q.topicId));
			buf.append(" Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: " + (q.assignmentType.equals("Exam")?pointValueSelectBox(q.pointValue):"1<INPUT TYPE=HIDDEN NAME=PointValue VALUE=1>") + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	String editCurrentQuestion (User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = 0;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
			} catch (Exception e2) {}
			Question q = ofy.get(Question.class,questionId);
			Topic t = ofy.get(Topic.class,q.topicId);
			if (q.requiresParser) q.setParameters();
			buf.append("<h3>Current Question</h3>");
			buf.append("Subject: " + subject.title + "<br>");
			buf.append("Topic: " + t.title);
			buf.append(" Assignment Type: " + q.assignmentType + "<p>");
			buf.append("<FORM Action=Edit METHOD=POST>");
			
			buf.append(q.printAll());
			
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + ">");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question'>");
			
			buf.append("<hr><h3>Edit This Question</h3>");
			buf.append("Topic:" + topicSelectBox(t.id));
			buf.append(" Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: " + (q.assignmentType.equals("Exam")?pointValueSelectBox(q.pointValue):"1<INPUT TYPE=HIDDEN NAME=PointValue VALUE=1>") + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String reviewQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h2>Editorial Review</h2>\n");
		try {
			String questionId = request.getParameter("QuestionId");
			List<Key<Question>> pendingQuestionKeys = ofy.query(Question.class).filter("isActive",false).listKeys();
			if (questionId==null && pendingQuestionKeys.size()==0) return buf.toString() + "There are no more questions pending editorial review.";
			// get the questionId for the question to review (first in list or next in list if editing)
			Key<Question> k = null;
			try {
				k = new Key<Question>(Question.class,Long.parseLong(questionId));
			} catch (Exception e2) {
				k = pendingQuestionKeys.get(0);
			}
			Question q = ofy.get(k);
			if (q.requiresParser) q.setParameters();
			
			buf.append("<h3>" + (q.isActive?"Trouble with Existing Question":"Contributed New Question") + "</h3>");
			buf.append("Topic: " + ofy.get(Topic.class,q.topicId).title + "<br>");
			buf.append("Assignment Type: " + q.assignmentType + "<br>");
			buf.append("<TABLE><TR><TD BGCOLOR=#FFFF80>" + q.printAll() + "</TD></TR></TABLE>\n");

			buf.append("<TABLE>");
			buf.append("<TR><TD ALIGN=RIGHT>Notes: </TD><TD><FONT COLOR=RED>" + q.notes + "</FONT></TD></TR>\n");
			buf.append("<TR><TD ALIGN=RIGHT>Author: </TD><TD>" + q.authorId + "</TD></TR>\n");
			buf.append("<TR><TD ALIGN=RIGHT>Contributor: </TD><TD>" + q.contributorId + "</TD></TR>\n");
			buf.append("<TR><TD ALIGN=RIGHT>Editor: </TD><TD>" + q.editorId + "</TD></TR>\n");
			buf.append("</TABLE>\n");
			
			buf.append("<FORM ACTION=Edit METHOD=GET>");
			// get the questionId for the next question in sequence (defaults to first in list)
			if (pendingQuestionKeys.size()>1) {
				int i = pendingQuestionKeys.indexOf(k) + 1;
				if (i >= pendingQuestionKeys.size()) i = 0;
				long nextQuestionId = pendingQuestionKeys.get(i).getId();
				buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=Review>");
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + nextQuestionId + "'>\n");
				buf.append("<INPUT TYPE=SUBMIT VALUE=Skip>");
			}
			buf.append("</FORM>");
			
			buf.append("<FORM ACTION=Edit METHOD=POST>\n");
			buf.append("<h3>Edit This Question</h3>");
			buf.append(q.edit());
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>\n");
			buf.append("<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + q.topicId + "'>\n");
			buf.append("<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + q.assignmentType + "'>\n");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Discard Question'>");
			buf.append("</FORM>\n");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	private void createTopic(User user,HttpServletRequest request) {
		Topic t = new Topic(request.getParameter("Title"),request.getParameter("OrderBy"));
		ofy.put(t);
	}

	private void updateTopic(User user,HttpServletRequest request) {
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic t = ofy.get(Topic.class,topicId);
			t.title = request.getParameter("Title");
			t.orderBy = request.getParameter("OrderBy");
			ofy.put(t);
		} catch (Exception e) {}
	}

	private void deleteTopic(User user,HttpServletRequest request) {	
		try {
			Topic t = ofy.get(Topic.class,Long.parseLong(request.getParameter("TopicId")));
			ofy.delete(t);
		} catch (Exception e) {}
	}

	private Question assembleQuestion(HttpServletRequest request) {
		try {
			int questionType = Integer.parseInt(request.getParameter("QuestionType"));
			return assembleQuestion(request,new Question(questionType)); 
		} catch (Exception e) {
			return null;
		}
	}
	
	private Question assembleQuestion(HttpServletRequest request,Question q) {
		String assignmentType = request.getParameter("AssignmentType");
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
		} catch (Exception e) {}
		int type = q.getQuestionType();
		try {
			type = Integer.parseInt(request.getParameter("QuestionType"));
		}catch (Exception e) {}
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
		double requiredPrecision = 2.0; // percent
		int pointValue = Integer.parseInt(request.getParameter("PointValue"));
		try {
			requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
		} catch (Exception e2) {
		}
		String correctAnswer = "";
		try {
			String[] allAnswers = request.getParameterValues("CorrectAnswer");
			for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
		} catch (Exception e2) {
			correctAnswer = request.getParameter("CorrectAnswer");
		}
		String parameterString = request.getParameter("ParameterString");
		if (parameterString == null) parameterString = "";
		
		q.assignmentType = assignmentType;
		q.topicId = topicId;
		q.setQuestionType(type);
		q.text = questionText;
		q.nChoices = nChoices;
		q.choices = choices;
		q.requiredPrecision = requiredPrecision;
		q.correctAnswer = correctAnswer;
		q.tag = request.getParameter("QuestionTag");
		q.pointValue = pointValue;
		q.parameterString = parameterString;
		q.requiresParser = (parameterString.length()>0);
		q.hint = request.getParameter("Hint");
		q.solution = request.getParameter("Solution");
		q.notes = "";
		q.validateFields();
		return q;
	}
	private void createQuestion(User user,HttpServletRequest request) {
		Question q = assembleQuestion(request);
		q.authorId = user.id;
		q.contributorId = user.id;
		q.editorId = user.id;
		q.isActive = true;
		ofy.put(q);
	}

	private void updateQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			Question q = ofy.get(Question.class,questionId);
			q = assembleQuestion(request,q);
			q.editorId = user.id;
			q.isActive = true;
			ofy.put(q);
			Key<Question> k = new Key<Question>(Question.class,questionId);
			if ("Quiz".equals(q.assignmentType)) Quiz.quizQuestions.remove(k);
			else if ("Homework".equals(q.assignmentType)) Homework.hwQuestions.remove(k);
		} catch (Exception e) {
			return;
		}
	}

	private void deleteQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			ofy.delete(Question.class,questionId);
		} catch (Exception e) {
			return;
		}
	}

}
