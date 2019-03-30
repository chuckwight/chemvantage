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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

public class Edit extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	
	public String getServletInfo() {
		return "This servlet is used by editors and admins to create, review, edit and delete question items.";
	}
	
/* ====================== NOTES ========================
 * 
 * The general strategy here is to provide a space for editors to receive/edit/activate/delete question items 
 * submitted from the community, and to create/edit/delete questions of their own.
 * Proposed questions are stored temporarily as ProposedQuestion items in the database, and if approved, are
 * then converted to regular Question items with a new ID number.
 * 
 */
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			if (ofy().load().type(User.class).id(userId).now()==null) User.createUserServiceUser(userService.getCurrentUser());
			
			HttpSession session = request.getSession();
			session.setAttribute("UserId",userId);
			
			User user = User.getInstance(session,false);  // no 2-factor authentication at this point
				
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
			else if (userRequest.equals("Review") || userRequest.equals("Skip")) { // review a pending question (problem or contribution)
				out.println(Home.getHeader(user) + reviewProposedQuestion(user,request) + Home.footer);
			}
			else if (userRequest.equals("Edit")) {  // edit a current question in the database
				out.println(Home.getHeader(user) + editCurrentQuestion(user,request) + Home.footer);
			}
			else if (userRequest.equals("Discard Question")) {
				try {
					long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
					ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId));
				} catch (Exception e) {}
				out.println(Home.getHeader(user) + reviewProposedQuestion(user,request) + Home.footer);
			}
			else { // show the default Editors page
				out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
			}
		} catch (Exception e) {
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		User user = User.getInstance(request.getSession());
		
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
		else if (userRequest.equals("Activate This Question")) {
			out.println(createQuestion(user,request));
			try {
				long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId)).now();
			} catch (Exception e) {}
			out.println(Home.getHeader(user) + reviewProposedQuestion(user,request) + Home.footer);
		}
		else if (userRequest.equals("Discard Question")) {
			try {
				long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId)).now();
			} catch (Exception e) {}
			out.println(Home.getHeader(user) + reviewProposedQuestion(user,request) + Home.footer);
		}
		else { // show the default Editors page
			out.println(Home.getHeader(user) + editorsPage(user,request) + Home.footer);
		}
	}

	String editorsPage(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Editors' Page for " + Subject.getSubject().title + "</h3>");
		try {
			int nPending = ofy().load().type(ProposedQuestion.class).count();
			buf.append("<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageTopics>Manage Topics</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageVideos>Manage Videos</a><br>");
			buf.append("<a href=Edit?UserRequest=ManageTexts>Manage Texts</a><p>");
			
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			String assignmentType = request.getParameter("AssignmentType");
			boolean showQuestions = (topicId >0 && assignmentType != null && assignmentType.length()>0);
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

				Topic t = ofy().load().type(Topic.class).id(topicId).safe();
				Query<Question> questions = ofy().load().type(Question.class).filter("topicId", t.id).filter("assignmentType",assignmentType).order("pointValue");
				
				buf.append("<h4>Current Questions</h4>");
				
				buf.append("This assignment draws from the following " + questions.count() + " questions:");
				
				buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");
				int i=0;
				for (Question q : questions) {
					q.setParameters();
					i++;
					int nSuccessful = ofy().load().type(Response.class).filter("questionId",q.id).filter("score >",0).count();
					int nTotalAttmp = ofy().load().type(Response.class).filter("questionId",q.id).count();
					int successPct = nTotalAttmp>0?100*nSuccessful/nTotalAttmp:0;
					
					buf.append("<FORM METHOD=GET ACTION=Edit>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topicId + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>"
							+ "<TR ID=" + q.id + " VALIGN=TOP>"
							+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Edit><p><FONT SIZE=-2>" + successPct + "%&nbsp;avg&nbsp;score</FONT></TD><TD ALIGN=RIGHT NOWRAP> " + i + ".</TD><TD>");
							//+ "<TD ALIGN=RIGHT NOWRAP><INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Edit> " + i + ".</TD><TD>");
					buf.append("\n" + q.printAll() + "</TD>");
					
				if (assignmentType.equals("Exam")) {
					buf.append("<TD><FONT SIZE=-2>(" + q.pointValue + "&nbsp;pts)</FONT></TD>");
				}
					 
					buf.append("</TR></FORM>");
				}
				buf.append("</TABLE>");	
			} else {  // show the number of questions in each topic and assignment type
				buf.append("<h4>Numbers of Questions By Topic and Assignment Type</h4>");
				
				buf.append("<TABLE><TR><TH>Topic</><TH>Quiz</TH><TH>Homework</TH><TH>Exam</TH></TR>");
				Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
				int nqt = 0;
				int nht = 0;
				int net = 0;
				for (Topic t:topics) {
					buf.append("<TR><TD>" + t.title + "</TD>");
					int nq = ofy().load().type(Question.class).filter("topicId",t.id).filter("assignmentType","Quiz").count();
					nqt += nq; // running total
					int nh = ofy().load().type(Question.class).filter("topicId",t.id).filter("assignmentType","Homework").count();
					nht += nh; // running total
					int ne = ofy().load().type(Question.class).filter("topicId",t.id).filter("assignmentType","Exam").count();
					net += ne; // running total
					buf.append("<TD style='text-align:center'>" + nq + "</TD><TD style='text-align:center'>" + nh + "</TD><TD style='text-align:center'>" + ne + "</TD></TR>");
				}
				buf.append("<TR style='font-weight:bold'><TD>Totals</TD><TD style='text-align:center'>" + nqt + "</TD><TD style='text-align:center'>" + nht + "</TD><TD style='text-align:center'>" + net + "</TD></TR>");
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
		Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
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
			Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
				for (Topic t : topics) { // one row for each topic
					int nQuiz = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("topicId",t.id).count();
					int nHW = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",t.id).count();
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
	
	void createTopic(User user,HttpServletRequest request) {
		Topic t = new Topic(request.getParameter("Title"),request.getParameter("OrderBy"));
		ofy().save().entity(t).now();
	}

	void updateTopic(User user,HttpServletRequest request) {
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic t = ofy().load().type(Topic.class).id(topicId).safe();
			t.title = request.getParameter("Title");
			t.orderBy = request.getParameter("OrderBy");
			ofy().save().entity(t).now();
		} catch (Exception e) {}
	}

	void deleteTopic(User user,HttpServletRequest request) {	
		try {
			Topic t = ofy().load().type(Topic.class).id(Long.parseLong(request.getParameter("TopicId"))).safe();
			ofy().delete().entity(t).now();
		} catch (Exception e) {}
	}

	String videosForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Videos for " + subject.title + "</h3>");
		try {
			Query<Video> videos = ofy().load().type(Video.class);
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
		ofy().save().entity(v).now();
	}
	
	void updateVideo(User user,HttpServletRequest request) {
		try {	
			Video v = ofy().load().type(Video.class).id(Long.parseLong(request.getParameter("VideoId"))).safe();
			v.title = request.getParameter("Title");
			v.orderBy = request.getParameter("OrderBy");
			ofy().save().entity(v).now();
		} catch (Exception e) {}
	}
	
	void deleteVideo(User user,HttpServletRequest request) {
		ofy().delete().key(Key.create(Video.class,Long.parseLong(request.getParameter("VideoId")))).now();
	}
	
	String textsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Texts for " + subject.title + "</h3>");
		buf.append("This is a list of open source textbooks shown on the Home page.");
		try {
			Query<Text> texts = ofy().load().type(Text.class);
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
		ofy().save().entity(text).now();
	}
	
	void updateText(User user,HttpServletRequest request) {
		try {
			Text text = ofy().load().type(Text.class).id(Long.parseLong(request.getParameter("TextId"))).safe();
			text.title = request.getParameter("Title");
			text.author = request.getParameter("Author");
			text.publisher = request.getParameter("Publisher");
			text.URL = request.getParameter("URL");
			ofy().save().entity(text).now();
		} catch (Exception e) {}
	}
	
	void deleteText(User user,HttpServletRequest request) {
		ofy().delete().key(Key.create(Text.class,Long.parseLong(request.getParameter("TextId")))).now();
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
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
					+ "<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + user.id + "'>");
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
		+ "<OPTION" + (points==1?" SELECTED":"") + ">1</OPTION>"
		+ "<OPTION" + (points==2?" SELECTED":"") + ">2</OPTION>"
		+ "<OPTION" + (points==10?" SELECTED":"") + ">10</OPTION>"
		+ "<OPTION" + (points==15?" SELECTED":"") + ">15</OPTION>"
		+ "</SELECT>";
	}
	
	String previewQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = 0;
			boolean current = false;
			boolean proposed = false;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
				current = true;
			} catch (Exception e2) {}
			long proposedQuestionId = 0;
			try {
				proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				proposed = true;
				current = false;
			} catch (Exception e2) {}
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			
			Question q = assembleQuestion(request);
			if (q.requiresParser()) q.setParameters();
			
			buf.append("<h3>Preview Question</h3>");
			buf.append("Subject: " + subject.title + "<br>");
			buf.append("Topic: " + ofy().load().type(Topic.class).id(topicId).safe().title + "<br>");
			q.assignmentType = request.getParameter("AssignmentType");
			if (q.assignmentType==null || q.assignmentType.isEmpty()) q.assignmentType = "Quiz";
			buf.append("Assignment Type: " + q.assignmentType);
			if (q.assignmentType.equals("Exam")) { // validate point value
				if (q.pointValue < 2) q.pointValue = 2;
				buf.append(" (" + q.pointValue + " points)<br>");
			} else {
				q.pointValue = 1;
				buf.append(" (1 point)<br>");
			}
			buf.append("Author: " + q.authorId + "<br>");
			buf.append("Editor: " + user.id + "<p>");
			
			buf.append("<FORM Action=Edit METHOD=POST>");
			
			buf.append(q.printAll());
			
			if (q.authorId==null) q.authorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + user.id + "'>");
			
			
			if (current) {
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + ">");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Question'>");
			}
			if (proposed) {
				buf.append("<INPUT TYPE=HIDDEN NAME=ProposedQuestionId VALUE=" + proposedQuestionId + ">");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Activate This Question'>");
			} else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save New Question'>");
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit'>");
			
			buf.append("<hr><h3>Continue Editing</h3>");
			buf.append("Topic:" + topicSelectBox(q.topicId));
			buf.append(" Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			
			if (q.assignmentType.equals("Exam")) {
				if (q.pointValue!=2 && q.pointValue!=10 && q.pointValue!=15) q.pointValue = 2;
			} else q.pointValue = 1;
			buf.append(" Point Value: " + pointValueSelectBox(q.pointValue) + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	String editCurrentQuestion (User user,HttpServletRequest request) {
		try {
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Question q = ofy().load().type(Question.class).id(questionId).safe();
			return editCurrentQuestion(user,q);
		} catch (Exception e) {
			return "Sorry, the question was not found in the database.";
		}
	}
	
	String editCurrentQuestion (User user,Question q) {
		StringBuffer buf = new StringBuffer();
		try {
			long questionId = q.id;
			Topic t = ofy().load().type(Topic.class).id(q.topicId).safe();
			if (q.requiresParser()) q.setParameters();
			buf.append("<h3>Current Question</h3>");
			buf.append("Subject: " + subject.title + "<br>");
			buf.append("Topic: " + t.title + "<br>");
			buf.append("Assignment Type: " + q.assignmentType + " (" + q.pointValue + (q.pointValue>1?" points":" point") + ")<br>");
			buf.append("Author: " + q.authorId + "<br>");
			buf.append("Editor: " + q.editorId + "<br>");
			
			// Calculate the current success rate for this question:
			int nSuccessful = ofy().load().type(Response.class).filter("questionId",q.id).filter("score >",0).count();
			int nTotalAttmp = ofy().load().type(Response.class).filter("questionId",q.id).count();
			double successPct = nTotalAttmp>0?100.*nSuccessful/nTotalAttmp:0;
			buf.append("Success Rate: " + nSuccessful + "/" + nTotalAttmp + " (" + successPct + "%)<p>");
			
			buf.append("<FORM Action=Edit METHOD=POST>");
			
			buf.append(q.printAll());
			
			if (q.authorId==null) q.authorId="";
			if (q.editorId==null) q.editorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + q.editorId + "'>");
			
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + ">");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question'>");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit'>");
			
			buf.append("<hr><h3>Edit This Question</h3>");
			buf.append("Topic:" + topicSelectBox(t.id));
			buf.append(" Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: " + pointValueSelectBox(q.pointValue) + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	String reviewProposedQuestion (User user, HttpServletRequest request) {
		// identifies a ProposedQuestion item, either from a specific questionId or next in the list
		StringBuffer buf = new StringBuffer("<h3>Proposed Question</h3>");
		try {
			List<Key<ProposedQuestion>> pendingQuestionKeys = ofy().load().type(ProposedQuestion.class).keys().list();
			if (pendingQuestionKeys.size()==0) return editorsPage(user,request);
			
			String questionId = request.getParameter("NextQuestionId");
			ProposedQuestion q = null;
			if (questionId==null) { // get the first question in the list
				q = ofy().load().key(pendingQuestionKeys.get(0)).safe();
				questionId = String.valueOf(q.id);
			} else { // get the designated question in the list
				q = ofy().load().type(ProposedQuestion.class).id(Long.parseLong(questionId)).now();
			}
			if (q.requiresParser()) q.setParameters();
			
			// If the list contains more than one proposed question, get the index of the next one
			String nextQuestionId = null;
			if (pendingQuestionKeys.size()>1) {
				Key<ProposedQuestion> k = Key.create(ProposedQuestion.class,q.id);
				int i = (pendingQuestionKeys.indexOf(k) + 1)%pendingQuestionKeys.size();				
				nextQuestionId = String.valueOf(pendingQuestionKeys.get(i).getId());
			}
			
			Topic t = ofy().load().type(Topic.class).id(q.topicId).safe();
			buf.append("Subject: " + subject.title + "<br>");
			buf.append("Topic: " + t.title + "<br>");
			buf.append("Assignment Type: " + q.assignmentType + " (" + q.pointValue + (q.pointValue>1?" points":" point") + ")<br>");
			buf.append("Author: " + q.authorId + "<p>");
			buf.append("<FORM Action=Edit METHOD=GET>");
			
			buf.append(q.printAll());
			
			buf.append("<INPUT TYPE=HIDDEN NAME=ProposedQuestionId VALUE='" + questionId + "'>");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Discard Question'>");
			if (nextQuestionId != null) {
				buf.append("<INPUT TYPE=HIDDEN NAME=NextQuestionId VALUE='" + nextQuestionId + "'>\n");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Skip>");
			}
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "'>");
			
			buf.append("<hr><h3>Edit This Question</h3>");
			buf.append("Topic:" + topicSelectBox(t.id));
			buf.append(" Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: " + pointValueSelectBox(q.pointValue) + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");

		} catch (Exception e) {
			buf.append("<p>" + e.getMessage());
		}
		return buf.toString();
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
		double requiredPrecision = 0.; // percent
		int significantFigures = 0;
		int pointValue = 1;
		try {
			pointValue = Integer.parseInt(request.getParameter("PointValue"));
		} catch (Exception e) {
		}
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
		
		q.assignmentType = assignmentType;
		q.topicId = topicId;
		q.setQuestionType(type);
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
		q.validateFields();
		return q;
	}
	
	private long createQuestion(User user,HttpServletRequest request) {
		try {
			Question q = assembleQuestion(request);
			q.isActive = true;
			ofy().save().entity(q).now();
			return q.id;
		} catch (Exception e) {
			return 0;
		}
	}

	private void updateQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			Question q = ofy().load().type(Question.class).id(questionId).safe();
			q = assembleQuestion(request,q);
			q.editorId = user.id;
			q.isActive = true;
			ofy().save().entity(q).now();
			Key<Question> k = Key.create(Question.class,questionId);
			if ("Quiz".equals(q.assignmentType)) Quiz.quizQuestions.remove(k);
			else if ("Homework".equals(q.assignmentType)) Homework.hwQuestions.remove(k);
		} catch (Exception e) {
			return;
		}
	}

	private void deleteQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		String assignmentType;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			ofy().delete().key(Key.create(Question.class,questionId)).now();
			assignmentType = ofy().load().type(Question.class).id(questionId).safe().assignmentType;
			Key<Question> k = Key.create(Question.class,questionId);
			if ("Quiz".equals(assignmentType)) Quiz.quizQuestions.remove(k);
			else if ("Homework".equals(assignmentType)) Homework.hwQuestions.remove(k);
		} catch (Exception e) {
			return;
		}
	}

}
