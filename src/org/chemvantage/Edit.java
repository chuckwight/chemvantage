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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/Edit")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class Edit extends HttpServlet {

	private static final long serialVersionUID = 137L;
	static Map<Long,int[]> nConceptQuestions = new HashMap<Long,int[]>();  // size=3 in order: Quiz/Homework/Exam
	Map<Key<Question>,Question> questions = new HashMap<Key<Question>,Question>();
	Map<Key<Question>,Integer> successPct = new HashMap<Key<Question>,Integer>();
	Map<Key<Question>,Integer> pointValue = new HashMap<Key<Question>,Integer>();
	List<Concept> concepts = new ArrayList<Concept>();
	
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
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			user.setToken();
			
			out.println(Subject.getHeader(user));
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			switch (userRequest) {
			case "CountConceptQuestions":
				countConceptQuestions(request);
			case "ManageConcepts":
				out.println(conceptsForm(request));
				break;
			case "ManageTopics": 
				out.println(topicsForm(request)); 
				break;
			case "ManageVideos": 
				out.println(videosForm()); 
				break;
			case "EditVideo": 
				out.println(editVideoForm(request)); 
				break;
			case "ManageTexts": 
				out.println(textsForm(user,request)); 
				break;
			case "Preview": 
				out.println(previewQuestion(user,request)); 
				break;
			case "NewQuestionForm": 
				out.println(newQuestionForm(user,request)); 
				break;
			case "Review": 
				out.println(reviewProposedQuestion(user,request)); 
				break;
			case "Edit": 
				out.println(editCurrentQuestion(user,request));
				break;
			case "Discard Question":
				try {
					long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
					ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId));
				} catch (Exception e) {}
				out.println(Subject.getHeader(user) + reviewProposedQuestion(user,request) + Subject.footer);
				break;
			case "ViewConceptQuestions":
				out.println(viewConceptQuestions(user,request));
				break;
			case "Refresh":
				questions.clear();
			default: out.println(editorsPage(user,request));
			}
			
		} catch (Exception e) {
			out.println(e.getMessage()==null?e.toString():e.getMessage());
		}
		out.println(Subject.footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			UserService userService = UserServiceFactory.getUserService();
			String userId = userService.getCurrentUser().getUserId();
			User user = new User("https://"+request.getServerName(), userId);
			user.setIsChemVantageAdmin(true);
			user.setToken();
			
			out.println(Subject.getHeader(user));
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			switch (userRequest) {
			case "CreateTopic":
				createTopic(user,request);
				out.println(topicsForm(request));
				break;
			case "UpdateTopic": 
				updateTopic(user,request); 
				out.println(topicsForm(request));
				break;
			case "DeleteTopic": 
				deleteTopic(user,request);
				out.println(topicsForm(request));
				break;
			case "CreateConcept":
				createConcept(user,request);
				out.println(conceptsForm(request));
				break;
			case "UpdateConcept": 
				updateConcept(user,request); 
				out.println(conceptsForm(request));
				break;
			case "DeleteConcept": 
				deleteConcept(user,request);
				out.println(conceptsForm(request));
				break;
			case "Create Video":
				createVideo(user,request);
				out.println(videosForm()); 
				break;
			case "Update Video":
				updateVideo(request);
				out.println(videosForm()); 
				break;
			case "Delete Video":
				deleteVideo(user,request);
				out.println(videosForm()); 
				break;
			case "Create Quizlet":
				out.println(updateQuizlet(request)); 
				break;
			case "Update Quizlet":
				out.println(updateQuizlet(request)); 
				break;
			case "Delete Quizlet":
				out.println(deleteQuizlet(request)); 
				break;
			case "Create Text":
				createText(user,request);
				out.println(textsForm(user,request));
				break;
			case "Update Text":
				updateText(user,request);
				out.println(textsForm(user,request));
				break;
			case "Delete Text":
				deleteText(user,request);
				out.println(textsForm(user,request));
				break;
			case "Create Chapter":
				createChapter(request);
				out.println(textsForm(user,request));
				break;
			case "Update Chapter":
				updateChapter(request);
				out.println(textsForm(user,request));
				break;
			case "Delete Chapter":
				deleteChapter(request);
				out.println(textsForm(user,request));
				break;
			case "Preview": 
				out.println(previewQuestion(user,request)); 
				break;
			case "Save New Question":
				createQuestion(user,request);
				out.println(editorsPage(user,request)); 
				break;
			case "Update Question":
				updateQuestion(user,request);
				out.println(editorsPage(user,request)); 
				break;
			case "Delete Question":
				deleteQuestion(user,request);
				out.println(editorsPage(user,request)); 
				break;
			case "Activate This Question":
				createQuestion(user,request);
				try {
					long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
					ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId)).now();
				} catch (Exception e) {}
				out.println(reviewProposedQuestion(user,request));
				break;
			case "Discard Question":
				try {
					long proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
					ofy().delete().key(Key.create(ProposedQuestion.class,proposedQuestionId)).now();
				} catch (Exception e) {}
				out.println(reviewProposedQuestion(user,request));
				break;
			case "CopyAllQuestions":
				copyQuestionsToPracticeExam(user,request);
				out.println(editorsPage(user,request));
				break;
			case "DeleteAllQuestions":
				deleteAllQuestions(user,request);
				out.println(editorsPage(user,request));
				break;
			case "Assign Key Concepts":
				assignKeyConcepts(user,request);
				out.println(editorsPage(user,request));
				break;
			case "UnlinkConceptId":
				unlinkConceptId(user,request);
				out.println(viewConceptQuestions(user,request));
				break;
			default: out.println(editorsPage(user,request));
			}

		} catch (Exception e) {
			out.println("Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		out.println(Subject.footer);
	}

	String editorsPage(User user,HttpServletRequest request) throws Exception {
		if (request.getParameter("ConceptId")!=null) return viewConceptQuestions(user,request);

		StringBuffer buf = new StringBuffer("<h3>Editors' Page</h3>");
		try {
			int nPending = ofy().load().type(ProposedQuestion.class).count();
			buf.append("<a href=Edit?UserRequest=Review>"
					+ nPending + " items are currently pending editorial review.</a><br/>");
			buf.append("<a href=/Edit?UserRequest=ManageTopics>Manage Topics</a><br/>");
			buf.append("<a href=/Edit?UserRequest=ManageConcepts>Manage Concepts</a><br/>");
			buf.append("<a href=/Edit?UserRequest=ManageVideos>Manage Videos</a><br/>");
			buf.append("<a href=/Edit?UserRequest=ManageTexts>Manage Texts</a><br/>");
			buf.append("<a href=/Edit?UserRequest=ViewConceptQuestions>View Concept Questions</a><br/><br/>");	
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e2) {}
			String assignmentType = request.getParameter("AssignmentType");
			boolean showQuestions = (topicId >0 && assignmentType != null && assignmentType.length()>0);
			buf.append("<FORM NAME=TopicSelect METHOD=GET ACTION=/Edit>");
			//buf.append("<FONT" + (request.getParameter("TopicId")!=null && topicId==0?" COLOR=RED>":">") + "<b>Topic:</b></FONT>" + topicSelectBox(topicId,showQuestions));
			buf.append("<FONT" + (request.getParameter("TopicId")!=null && topicId==0?" COLOR=RED>":">") + "<b>Topic:</b></FONT>" + topicSelectBox(topicId,false));
			//buf.append("<FONT" + (assignmentType!=null && assignmentType.length()==0?" COLOR=RED>":">") + "<b> Assignment Type:</b></FONT>" + assignmentTypeDropDownBox(assignmentType,true));
			buf.append("<FONT" + (assignmentType!=null && assignmentType.length()==0?" COLOR=RED>":">") + "<b> Assignment Type:</b></FONT>" + assignmentTypeDropDownBox(assignmentType,false));
			buf.append(" <INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Show Questions' />");
			buf.append(" <INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Assign Key Concepts' />");
			buf.append(" <INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Refresh' />");
			buf.append("</FORM>");
			
			boolean assignKeyConcepts = showQuestions && "Assign Key Concepts".equals(request.getParameter("UserRequest"));
			
			if (assignKeyConcepts) {
				Map<Key<Concept>,Concept> concepts = new HashMap<Key<Concept>,Concept>();
				Topic t = ofy().load().type(Topic.class).id(topicId).now();
				try {
					List<Key<Concept>> conceptKeys = ofy().load().type(Concept.class).keys().list();
					concepts = ofy().load().keys(conceptKeys);
					buf.append("<h4>Key Concepts</h4>");
					if (concepts.isEmpty()) buf.append("No concepts are assigned to this topic.<br/>");
					else {
						for (Concept c : concepts.values()) {
							if (t.conceptIds.contains(c.id)) {
								int nQuestions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",t.id).filter("conceptId",c.id).count();
								buf.append("&nbsp;&nbsp;" + c.orderBy + " - " + c.title + " (" + nQuestions + ")<br/>");
							}
						}
					}
					buf.append("<br/>");
				} catch (Exception e) {}
				
				//List<Key<Question>> questionKeys = loadQuestions(assignmentType,topicId);
				List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).list();
				
				buf.append("<form method=post action=/Edit>"
						+ "<input type=hidden name=UserRequest value='Assign Key Concepts' />"
						+ "<input type=hidden name=AssignmentType value=" + request.getParameter("AssignmentType") + ">"
						+ "<input type=hidden name=TopicId value=" + request.getParameter("TopicId") + ">");
				buf.append("Select a key concept and check all the corresponding questions. " + conceptSelectBox());
				buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");				
				
				for (Question q : questions) {
					q.setParameters();
					String conceptTitle = "";
					String conceptColor = "";
					try {
						conceptTitle = concepts.get(Key.create(Concept.class,q.conceptId)).title;
						if (!t.conceptIds.contains(q.conceptId)) conceptColor="cyan";
					} catch (Exception e) {
						conceptColor = "yellow";
					}
					buf.append("<tr style='vertical-align: top'><td" + (conceptColor.isEmpty()?">":" style='background-color:" + conceptColor + "'>") + "<input type=checkbox name=QuestionId value=" + q.id + " /> " + conceptTitle + "</td><td>" + q.printAll() + "</td></tr>");
				}
				buf.append("</TABLE>");
				buf.append("<input id=subConceptAssign type=submit value='Assign these questions to the selected key concept' disabled=true />");
				buf.append("</form>");
				buf.append("<script>"
						+ "function enableSubmit() {"
						+ "  var selectConcept = document.getElementById('selectConcept');"
						+ "  var subConceptAssign = document.getElementById('subConceptAssign');"
						+ "  if (selectConcept.selectedIndex>0) subConceptAssign.disabled=false;"
						+ "}"
						+ "</script>");
			} else if (showQuestions) {
				Map<Long,Concept> concepts = new HashMap<Long,Concept>();
				try {
					Topic t = ofy().load().type(Topic.class).id(topicId).now();
					concepts = ofy().load().type(Concept.class).ids(t.conceptIds);
					buf.append("<h4>Key Concepts</h4>");
					if (concepts.isEmpty()) buf.append("No concepts are assigned to this topic.<br/>");
					else {
						for (Concept c : concepts.values()) {
							int nQuestions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",t.id).filter("conceptId",c.id).count();
							buf.append("&nbsp;&nbsp;" + c.orderBy + " - " + c.title + " (" + nQuestions + ")<br/>");
						}
					}
					buf.append("<br/>");
				} catch (Exception e) {}
				
				buf.append("<FORM NAME=NewQuestion METHOD=GET ACTION=/Edit>");
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

				List<Key<Question>> questionKeys = loadQuestions(assignmentType,topicId);
				
				buf.append("<a href=# onClick=document.getElementById('bulkform').style.display='';><h4>Current Questions</h4></a>");				
				buf.append("<div id=bulkform style='display:none'>");
				if (assignmentType.equals("Quiz") || assignmentType.equals("Homework")) {
					buf.append("<form method=post action=/Edit onSubmit=\"return confirm('Copy all questions; are you sure?');\">"
							+ "<input type=hidden name=AssignmentType value=" + assignmentType + ">"
							+ "<input type=hidden name=TopicId value=" + topicId + ">"
							+ "<input type=hidden name=UserRequest value=CopyAllQuestions>"
							+ "Copy all of the questions below to " + (assignmentType.equals("Quiz")?"2-point":"10-point") + " Exam questions: "
							+ "<input type=submit value='Copy All Questions'>"
							+ "</form>");
				}
				buf.append("<form method=post action=/Edit onSubmit=\"return confirm('Delete all questions; are you sure?');\">"
						+ "<input type=hidden name=AssignmentType value=" + assignmentType + ">"
						+ "<input type=hidden name=TopicId value=" + topicId + ">"
						+ "<input type=hidden name=UserRequest value=DeleteAllQuestions>"
						+ "Delete all of the questions below (cannot be undone): "
						+ "<input type=submit value='Delete All Questions'>"
						+ "</form><br>"
						+ "</div>");

				buf.append("<b>This assignment draws from the following " + questionKeys.size() + " questions:</b><br/><br/>");

				buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");
				int i=0;
				int pts = 0;
				for (Key<Question> k : questionKeys) {
					Question q = questions.get(k).clone();
					q.setParameters();
					
					if ("Exam".equals(assignmentType) && q.pointValue != pts) { // print a header for new section of questions
						pts = q.pointValue;
						i=0;
						buf.append("<tr><td bgcolor=cyan>" + q.pointValue + "&nbsp;pt.&nbsp;questions:</td><td colspan=2>&nbsp;<p>&nbsp;</td></tr>");
					}
					i++;
					Concept c = concepts.get(q.conceptId);
					buf.append("\n<span id=q" + q.id + "></span>"
							+ "<FORM METHOD=GET ACTION=/Edit>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + topicId + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + q.id + "'>"
							+ "<TR VALIGN=TOP>"
							+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Edit><br/><FONT SIZE=-2>"
							+ (c==null?"":"Concept:&nbsp;" + c.orderBy + "<br/>")
							+ successPct.get(Key.create(q)) + "%&nbsp;avg&nbsp;score</FONT>"
							+ (q.learn_more_url != null && !q.learn_more_url.isEmpty()?"<br/><a href='" + q.learn_more_url + "' target=_blank><img src=/images/learn_more.png /></a>":"")
							+ "</TD>"
							+ "<TD ALIGN=RIGHT NOWRAP> " + i + ". </TD>");
					buf.append("<TD>" + q.printAll() + "</TD>");
					buf.append("</TR></FORM>");
				}
				buf.append("</TABLE><br/>");			
			} 
		} catch (Exception e) {
			buf.append("Error: " + e.getMessage()==null?e.toString():e.getMessage());
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
		+ "<OPTION" + (defaultType.equals("Video")?" SELECTED":"") + ">Video</OPTION>"
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
	
	List<Key<Question>> loadQuestions(String assignmentType,long topicId) throws Exception {
		List<Key<Question>> keys = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).keys().list();
		if (keys.isEmpty()) return keys;
		
		List<Key<Question>> additions = new ArrayList<Key<Question>>(keys);
		List<Key<Question>> deletions = new ArrayList<Key<Question>>();
		for (Key<Question> k : questions.keySet()) {
			if (keys.contains(k)) additions.remove(k);  // questions map already contains this Question
			else deletions.add(k);						// this question is not in the current AssignmentType and Topic
		}
		
		for (Key<Question> k : deletions) questions.remove(k);
		if (!additions.isEmpty()) questions.putAll(ofy().load().keys(additions));
		
		Collections.sort(keys, new SortBySuccessPct());
		
		return keys;
	}
	
	String topicsForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("<h3>Manage Quiz/Homework/Exam Topics</h3>");
		try {
			// print the table of topics for this subject:
			buf.append("<b>" + Subject.getTitle() + "</b>\n");
			buf.append("<TABLE BORDER=0 CELLSPACING=3>"
					+ "<TR><TH COLSPAN=3>&nbsp;</TH><TH COLSPAN=2>View/Add/Edit Questions</TH><TH></TH></TR>"
					+ "<TR><TH>Order (in use)</TH><TH>Title</TH><TH>Action</TH><TH>Quiz</TH><TH>HW</TH><TH>Exam</TH><TH>Video</TH><TH>OpenStax</TH><TH>Concepts</TH></TR>\n");
			Query<Topic> topics = ofy().load().type(Topic.class).order("orderBy");
				for (Topic t : topics) { // one row for each topic
					int nQuiz = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("topicId",t.id).count();
					int nHW = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId",t.id).count();
					int nExam = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",t.id).count();
					int nVideo = ofy().load().type(Question.class).filter("assignmentType","Video").filter("topicId",t.id).count();
					buf.append("\n<FORM NAME=TopicsForm" + t.id + " METHOD=POST ACTION=/Edit>"
							+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateTopic>"
							+ "<INPUT TYPE=HIDDEN NAME=TopicId VALUE='" + t.id + "'>");
					buf.append("\n<TR>"
							+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4 VALUE='" + t.orderBy + "'> (" + ofy().load().type(Assignment.class).filter("topicId",t.id).count() + ")</TD>"
							+ "<TD ALIGN=CENTER><INPUT NAME=Title VALUE='" + Question.quot2html(t.title) + "'></TD>"
							+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE=Update>"
							+ ((nQuiz==0 && nHW==0 && nExam==0 && nVideo==0)?"<INPUT TYPE=SUBMIT VALUE='Delete' "
									+ "onClick=\"javascript: document.TopicsForm" + t.id + ".UserRequest.value='DeleteTopic';\">":"")
									+ "</TD>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Quiz&TopicId=" + t.id + "><b>" + nQuiz + "</b></a></TD>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Homework&TopicId=" + t.id + "><b>" + nHW + "</b></a></TD>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Exam&TopicId=" + t.id + "><b>" + nExam + "</b></a></TD>");
					buf.append("<TD ALIGN=CENTER><a href=Edit?AssignmentType=Exam&TopicId=" + t.id + "><b>" + nVideo + "</b></a></TD>");
					// The next column is a checkbox to indicate if the topic is aligned with OpenStax textbook
					buf.append("<TD ALIGN=CENTER><INPUT TYPE=CHECKBOX NAME=TopicGroup VALUE=1" + (t.topicGroup%2/1==1?" CHECKED":"") + "></TD>");
					// Add a drop-down to select Concepts for this Topic
					buf.append("<TD>" + conceptsDropDownBox(t) + "</TD>");
					buf.append("</FORM></TR>");
				}
			//}
			// print one-row form to add a new topic (quiz):
			buf.append("<FORM METHOD=POST ACTION=/Edit><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=CreateTopic>");
			buf.append("<TR>"
					+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4></TD>"
					+ "<TD ALIGN=CENTER><INPUT NAME=Title></TD>"
					+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE='Create New Topic'></TD><TD ALIGN=CENTER>0</TD><TD ALIGN=CENTER>0</TD><TD ALIGN=CENTER>0</TD><TD ALIGN=CENTER>0</TD><TD ALIGN=CENTER><INPUT TYPE=CHECKBOX NAME=TopicGroup VALUE=1></TD></TR></FORM>");
			buf.append("</TABLE>");
			buf.append("<FONT SIZE=-1 COLOR=RED>Notes:<OL>"
					+ "<LI>The alphanumeric value of Order controls the order that topics are arranged.</LI>"
					+ "<LI>To hide a topic from students, change the value of Order to 'Hide'.</LI>"
					+ "<LI>A topic can be deleted only if all its questions have been deleted or moved.</LI>"
					+ "</OL></FONT>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	String conceptsDropDownBox(Topic t) {
		StringBuffer buf = new StringBuffer();
		if (concepts.isEmpty() || concepts.size() != ofy().load().type(Concept.class).count()) {
			concepts = ofy().load().type(Concept.class).order("orderBy").list();
		}
		buf.append("<SELECT NAME=ConceptId SIZE=4 MULTIPLE>");
		for (Concept c : concepts) buf.append("<OPTION VALUE=" + c.id + (t.conceptIds.contains(c.id)?" SELECTED>":">") + c.title + "</OPTION>");
		buf.append("</SELECT>");
		return buf.toString();
	}
	
	String conceptsDropDownBox(Chapter ch) {
		StringBuffer buf = new StringBuffer();
		if (ch==null) ch = new Chapter();
		if (concepts.isEmpty() || concepts.size() != ofy().load().type(Concept.class).count()) {
			concepts = ofy().load().type(Concept.class).order("orderBy").list();
		}
		buf.append("<SELECT NAME=ConceptId SIZE=4 MULTIPLE>");
		for (Concept c : concepts) buf.append("<OPTION VALUE=" + c.id + (ch.conceptIds.contains(c.id)?" SELECTED>":">") + c.title + "</OPTION>");
		buf.append("</SELECT>");
		return buf.toString();
	}
	
	String conceptSelectBox(Topic t, long conceptId) {
		StringBuffer buf = new StringBuffer();
		if (concepts.isEmpty() || concepts.size() != ofy().load().type(Concept.class).count()) {
			concepts = ofy().load().type(Concept.class).order("orderBy").list();
		}
		buf.append("<SELECT NAME=ConceptId><OPTION VALUE=0>Select a key concept</OPTION>");
		for (Concept c : concepts) buf.append("<OPTION VALUE=" + c.id + (c.id==conceptId?" SELECTED>":">") + c.title + "</OPTION>");
		buf.append("</SELECT>");
		
		return buf.toString();
	}
	
	String conceptSelectBox() {
		StringBuffer buf = new StringBuffer();
		if (concepts.isEmpty() || concepts.size() != ofy().load().type(Concept.class).count()) {
			concepts = ofy().load().type(Concept.class).order("orderBy").list();
		}
		buf.append("<SELECT id=selectConcept NAME=ConceptId onChange=javascript:enableSubmit(); ><OPTION VALUE=0>Select a key concept</OPTION>");
		for (Concept c : concepts) buf.append("<OPTION VALUE=" + c.id + ">" + c.title + "</OPTION>");
		buf.append("</SELECT>");
		
		return buf.toString();
	}
	
	String conceptsForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		try {
			// print the table of key concepts for General Chemistry, roughly ordered by topic/chapter/semester, etc.:
			buf.append("<b>Key Concepts in General Chemistry</b>\n");
			buf.append("<TABLE BORDER=0 CELLSPACING=3>"
					+ "<TR><TH>Order</TH><TH>Title</TH><TH>Action</TH><TH>Quiz</TH><TH>Homework</TH><TH>Exam</TH></TR>");
			List<Concept> concepts = ofy().load().type(Concept.class).order("orderBy").list();
			for (Concept c : concepts) { // one row for each concept
				int[] nQuestions = nConceptQuestions.get(c.id);
				buf.append("<FORM NAME=ConceptsForm" + c.id + " METHOD=POST ACTION=/Edit>"
						+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=UpdateConcept />"
						+ "<INPUT TYPE=HIDDEN NAME=ConceptId VALUE='" + c.id + "' />");
				buf.append("<TR>"
						+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4 VALUE='" + c.orderBy + "' /></TD>"
						+ "<TD ALIGN=CENTER><INPUT NAME=Title VALUE='" + Question.quot2html(c.title) + "' /></TD>"
						+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE=Update />"
						+ "<INPUT TYPE=SUBMIT VALUE='Delete' onClick=\"javascript: document.ConceptsForm" + c.id + ".UserRequest.value='DeleteConcept';\" />"
						+ "</TD>"
						+ "<TD ALIGN=CENTER><a href=/Edit?UserRequest=ViewConceptQuestions&AssignmentType=Quiz&ConceptId=" + c.id + ">" + (nQuestions==null?"View":"<b>" + nQuestions[0] + "</b>") + "</a></TD>"
						+ "<TD ALIGN=CENTER><a href=/Edit?UserRequest=ViewConceptQuestions&AssignmentType=Homework&ConceptId=" + c.id + ">" + (nQuestions==null?"View":"<b>" + nQuestions[1] + "</b>") + "</a></TD>"
						+ "<TD ALIGN=CENTER><a href=/Edit?UserRequest=ViewConceptQuestions&AssignmentType=Exam&ConceptId=" + c.id + ">" + (nQuestions==null?"View":"<b>" + nQuestions[2] + "</b>") + "</a></TD>"
						+ "<TD><a href=/Edit?UserRequest=CountConceptQuestions&ConceptId=" + c.id + ">Count</a></TD>"
						+ "</FORM></TR>");
			}
			
//			print one-row form to add a new Concept:
			buf.append("<FORM METHOD=POST ACTION=/Edit><INPUT TYPE=HIDDEN NAME=UserRequest VALUE=CreateConcept>");
			buf.append("<TR>"
					+ "<TD ALIGN=CENTER><INPUT NAME=OrderBy SIZE=4></TD>"
					+ "<TD ALIGN=CENTER><INPUT NAME=Title></TD>"
					+ "<TD ALIGN=CENTER><INPUT TYPE=SUBMIT VALUE='Create'></TD><TD></TD><TD></TD><TD></TD><TD></TD></TR></FORM>");
			buf.append("</TABLE>");
		} catch (Exception e) {
			buf.append(e.getMessage());
		}
		return buf.toString();
	}
	
	void assignKeyConcepts(User user,HttpServletRequest request) throws Exception {
		long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		String[] questionIdStrings = request.getParameterValues("QuestionId");
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		for (int i=0; i<questionIdStrings.length; i++) questionKeys.add(Key.create(Question.class,Long.parseLong(questionIdStrings[i])));
		List<Question> questions = new ArrayList<Question>(ofy().load().keys(questionKeys).values());
		for (Question q : questions) q.conceptId = conceptId;
		ofy().save().entities(questions).now();
	}
	
	void countConceptQuestions(HttpServletRequest request) throws Exception {
		Long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		int[] nQuestions = {0,0,0};
		nQuestions[0] = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("conceptId",conceptId).count();
		nQuestions[1] = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("conceptId",conceptId).count();
		nQuestions[2] = ofy().load().type(Question.class).filter("assignmentType","Exam").filter("conceptId",conceptId).count();
		nConceptQuestions.put(conceptId, nQuestions);
	}
	
	void unlinkConceptId(User user,HttpServletRequest request) throws Exception {
		Long questionId = Long.parseLong(request.getParameter("QuestionId"));
		Question q = ofy().load().type(Question.class).id(questionId).safe();
		q.conceptId = null;
		ofy().save().entity(q).now();
	}
	
	String viewConceptQuestions(User user, HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer();
		String assignmentType = request.getParameter("AssignmentType");
		Long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		Concept c = ofy().load().type(Concept.class).id(conceptId).safe();
		List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("conceptId",conceptId).list();
		Collections.sort(questions, new AlphabetizeQuestions());
		buf.append("<h3>Concept Questions: " + c.title + "</h3>");
		buf.append("Click Unlink to remove the conceptId from the question item or <a href=/Edit?UserRequest=ManageConcepts>return to the Concepts page</a><br/><br/>");
		buf.append("<table>");
		for (Question q : questions) {
			q.setParameters();
			buf.append("<tr style=vertical-align:text-top><td style=padding-right:5px>" 
			+  "<form method=post action=/Edit>"
			+ "<input type=hidden name=UserRequest value=UnlinkConceptId />"
			+ "<input type=hidden name=AssignmentType value=" + assignmentType + " />"
			+ "<input type=hidden name=ConceptId value=" + conceptId + " />"
			+ "<input type=hidden name=QuestionId value=" + q.id + " />"
			+ "<input type=submit value=Unlink />&nbsp;"
			+ "<a href=/Edit?UserRequest=Edit&QuestionId=" + q.id + ">Edit</a>&nbsp;&nbsp;&nbsp;"
			+ (questions.indexOf(q)+1) + ".</form>"
			+ "</td><td>" + q.printAll() + "</td></tr>");
		}
		buf.append("</table>");
		return buf.toString();
	}
	
	void createTopic(User user,HttpServletRequest request) {
		String title = request.getParameter("Title");
		if (title==null) title = "";
		String orderBy = request.getParameter("OrderBy");
		if (orderBy==null) orderBy = "";
		int topicGroup = 0;
		String[] alignments = request.getParameterValues("TopicGroup");
		if (alignments != null) {
			for (String text : alignments) topicGroup += Integer.parseInt(text);
		}
		
		Topic t = new Topic(title,orderBy,topicGroup);
		
		ofy().save().entity(t).now();
	}

	void createConcept(User user,HttpServletRequest request) {
		String title = request.getParameter("Title");
		if (title==null) title = "";
		String orderBy = request.getParameter("OrderBy");
		if (orderBy==null) orderBy = "";
		Concept c = new Concept(title,orderBy);		
		
		ofy().save().entity(c).now();
	}
	
	void updateTopic(User user,HttpServletRequest request) {
		long topicId = 0;
		try {
			topicId = Long.parseLong(request.getParameter("TopicId"));
			Topic t = ofy().load().type(Topic.class).id(topicId).safe();
			t.title = request.getParameter("Title");
			if (t.title == null) t.title = "";
			t.orderBy = request.getParameter("OrderBy");
			if (t.orderBy == null) t.orderBy = "";
			t.topicGroup = 0;
			String[] alignments = request.getParameterValues("TopicGroup");
			if (alignments != null) {
				for (String text : alignments) t.topicGroup += Integer.parseInt(text);
			}
			String[] conceptIds = request.getParameterValues("ConceptId");
			if (conceptIds != null) {
				t.conceptIds.clear();
				for (String cId : conceptIds) t.conceptIds.add(Long.parseLong(cId));
			}
			
			ofy().save().entity(t).now();
		} catch (Exception e) {}
	}

	void updateConcept(User user,HttpServletRequest request) {
		long conceptId = 0;
		try {
			conceptId = Long.parseLong(request.getParameter("ConceptId"));
			Concept c = ofy().load().type(Concept.class).id(conceptId).safe();
			c.title = request.getParameter("Title");
			if (c.title == null) c.title = "";
			c.orderBy = request.getParameter("OrderBy");
			if (c.orderBy == null) c.orderBy = "";
			
			ofy().save().entity(c).now();
		} catch (Exception e) {}
	}
	
	void deleteTopic(User user,HttpServletRequest request) {	
		try {
			Topic t = ofy().load().type(Topic.class).id(Long.parseLong(request.getParameter("TopicId"))).safe();
			ofy().delete().entity(t).now();
		} catch (Exception e) {}
	}

	void deleteConcept(User user,HttpServletRequest request) {
		try {
			Concept c = ofy().load().type(Concept.class).id(Long.parseLong(request.getParameter("ConceptId"))).safe();
			ofy().delete().entity(c).now();
		} catch (Exception e) {}
	}
	
	String videosForm() {
		StringBuffer buf = new StringBuffer("<h3>Manage Videos</h3>");
		try {
			List<Video> videos = ofy().load().type(Video.class).order("orderBy").list();
			
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>OrderBy</TH><TH>Title</TH><TH>Serial No.</TH><TH>Breaks</TH><TH>Questions</TH><TH>Action</TH></TR>");
			for (Video v : videos) {
				buf.append("<FORM ACTION=Edit METHOD=POST>"
						+ "<INPUT TYPE=HIDDEN NAME=VideoId VALUE='" + v.id + "'>"
						+ "<TR><TD><INPUT TYPE=TEXT NAME=OrderBy VALUE='" + v.orderBy + "'></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Title VALUE='" + Question.quot2html(v.title) + "'></TD>"
						+ "<TD>" + v.serialNumber + "</TD>"
						+ "<TD>" + (v.breaks==null?"0":v.breaks.length) + "</TD>"
						+ "<TD>" + (v.questionKeys==null?0:v.questionKeys.size()) + "</TD>"
						+ "<TD><a href=/Edit?UserRequest=EditVideo&VideoId=" + v.id +">Select Questions</a> "
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Video'> "
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Video'></TD></TR>"
						+ "</FORM>");
			}
			buf.append("<FORM ACTION=Edit METHOD=POST>"
					+ "<TR><TD><INPUT TYPE=TEXT NAME=OrderBy></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Title></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=SerialNumber></TD>"
					+ "<TD></TD>"
					+ "<TD></TD>"
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
		v.breaks = new int[0];
		v.questionKeys = new ArrayList<Key<Question>>();
		ofy().save().entity(v).now();
	}
	
	void updateVideo(HttpServletRequest request) {
		Video v = ofy().load().type(Video.class).id(Long.parseLong(request.getParameter("VideoId"))).safe();
		v.title = request.getParameter("Title");
		v.orderBy = request.getParameter("OrderBy");
		ofy().save().entity(v).now();
	}
	
	String updateQuizlet(HttpServletRequest request) { 		
		StringBuffer buf = new StringBuffer();
		Video v = null; 
		int segment = -1;
		
		try {
			v= ofy().load().type(Video.class).id(Long.parseLong(request.getParameter("VideoId"))).safe();
			buf.append("Video: " + v.title + "<br>");
			
			String stringTopicId = request.getParameter("TopicId");
			if (stringTopicId != null) v.topicId = Long.parseLong(stringTopicId);
			
			try {
				segment = Integer.parseInt(request.getParameter("Segment"));
			} catch (Exception e) {}

			int time=-1; // negative value indicates "end of video" (unreachable time)
			try {
				time = Integer.parseInt(request.getParameter("Seconds")); // position of the new breakpoint for this segment
			} catch (Exception e) {}

			if (segment>=0) {
				// Determine the number of breaks: 1) first break (1), revised break (v.breaks.length), or new break (v.breaks.length+1)
				String[] questionIds = request.getParameterValues("QuestionId");			
				if (questionIds==null) questionIds = new String[0];

				int nBreaks = (v.breaks==null?1:(segment<v.breaks.length?v.breaks.length:v.breaks.length+1));
				int[] breaks = new int[nBreaks];
				int[] nQuestions = new int[nBreaks];

				// Copy the breakpoints from the current video, insert or append the new breakpoint, and copy back to the video:
				for (int i=0;i<breaks.length;i++) {
					breaks[i] = (i==segment?time:v.breaks[i]);
					nQuestions[i] = (i==segment?questionIds.length:v.nQuestions[i]);
				}

				buf.append("Created/updated the breakpoint.<br>");

				// Create a new List of questionKeys for the video
				ArrayList<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
				int counter = 0;
				for (int i = 0;i<nBreaks;i++) {
					if (i==segment) {
						for (String id : questionIds) questionKeys.add(Key.create(Question.class,Long.parseLong(id)));
						counter += (v.nQuestions==null || i==v.nQuestions.length)?0:v.nQuestions[i];  // skip this many keys that are being replaced
					} else {
						for (int j=counter;j<counter+v.nQuestions[i];j++) questionKeys.add(v.questionKeys.get(j));
						counter += v.nQuestions[i];
					}
				}
				buf.append("Created/updated the List of question keys.<br>");

				// Update the video parameters to the new values:
				v.breaks = breaks;
				v.nQuestions = nQuestions;
				v.questionKeys = questionKeys;
			}
			ofy().save().entity(v).now();
		} catch (Exception e) {
			return buf.toString() + e.getMessage();
		}
		return editVideoForm(v,v.breaks[segment]<0?segment:segment+1);
	}

	String deleteQuizlet(HttpServletRequest request) {
		Video v = null; 
		int segment = 0;
		
		try {
			v= ofy().load().type(Video.class).id(Long.parseLong(request.getParameter("VideoId"))).safe();
			segment = Integer.parseInt(request.getParameter("Segment"));
			if (segment==0 && v.breaks.length==1) { // deleting the only segment
				v.breaks = null;
				v.nQuestions = null;
				v.questionKeys = null;
			} else {  // copy breaks and nQuestions arrays except for current segment
				int[] breaks = new int[v.breaks.length-1];
				int[] nQuestions = new int[v.breaks.length-1];
				for (int i=0;i<breaks.length;i++) {
					if (i==segment) continue;
					breaks[i] = i<segment?v.breaks[i]:v.breaks[i+1];
					nQuestions[i] = i<segment?v.nQuestions[i]:v.nQuestions[i+1];
				}
				// delete the appropriate questionKeys
				int count = 0;
				for (int i=0;i<segment;i++) count += nQuestions[i];
				for (int j=count;j<count+v.nQuestions[segment];j++) v.questionKeys.remove(count);
				
				// replace the arrays with the modified ones
				v.breaks = breaks;
				v.nQuestions = nQuestions;
			}
			ofy().save().entity(v).now();
		} catch (Exception e) {			
		}
		return editVideoForm(v,segment==0?0:segment-1);
	}
	
	void deleteVideo(User user,HttpServletRequest request) {
		ofy().delete().key(Key.create(Video.class,Long.parseLong(request.getParameter("VideoId")))).now();
	}
	
	String textsForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer("");
		try {
			long textId = Long.parseLong(request.getParameter("TextId"));
			Text t = ofy().load().type(Text.class).id(textId).safe();
			buf.append("<h3>Manage Chapters</h3>");
			buf.append("<a href=/Edit?UserRequest=ManageTexts>Return to Manage Texts</a><br/><br/>");
			buf.append("Title: " + t.title + "<br/>"
				+ "Author: " + t.author + "<br/>"
				+ "Publisher: " + t.publisher + "<br/>"
				+ "URL: " + t.URL + "<br/><br/>");
			
			if (t.chapters==null) t.chapters = new ArrayList<Chapter>();
			
			buf.append("<table>"
				+ "<tr><th>Chapter</th><th>Title</th><th>URL</th><th>Key Concepts</th><th>Action</th></tr>");
			for (Chapter c : t.chapters) {
				buf.append("<tr><form action=/Edit method=post>"
					+ "<input type=hidden name=TextId value=" + textId + " />"
					+ "<input type=hidden name=ChapterIndex value=" + t.chapters.indexOf(c) + " />"
					+ "<td><input type=text size=4 name=ChapterNumber value='" + c.chapterNumber + "' /></td>"
					+ "<td><input type=text size=20 name=ChapterTitle value='" + c.title + "' /></td>"
					+ "<td><input type=text size=20 name=ChapterUrl value='" + c.url + "' /></td>"
					+ "<td>" + conceptsDropDownBox(c) + "</td>"
					+ "<td><input type=submit name=UserRequest value='Update Chapter'/><input type=submit name=UserRequest value='Delete Chapter'/></td>"
					+ "</form></tr>");
			}
			Chapter c = new Chapter();
			buf.append("<tr><form action=/Edit method=post>"		// extra row to add a new chapter
					+ "<input type=hidden name=TextId value=" + textId + " />"
					+ "<td><input type=text size=4 name=ChapterNumber value=" + (t.chapters.size()+1) + " /></td>"
					+ "<td><input type=text size=20 name=ChapterTitle /></td>"
					+ "<td><input type=text size=20 name=ChapterUrl /></td>"
					+ "<td>" + conceptsDropDownBox(c) + "</td>"
					+ "<td><input type=submit name=UserRequest value='Create Chapter'/></td>"
					+ "</form></tr>");
			
			buf.append("</table>");
			
			return buf.toString();
		} catch (Exception e) {
		buf.append("This is a list of textbooks served by ChemVantage, especially as SmartText objects.");
		try {
			buf.append("<h3>Manage Texts</h3>");
			Query<Text> texts = ofy().load().type(Text.class);
			buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>Title</TH><TH>Author</TH><TH>Publisher</TH><TH>Text URL</TH><TH>Image URL</TH><TH>Print Copy URL</TH><TH>Smart</TH><TH></TH></TR>");
			for (Text text : texts) {
				buf.append("<FORM ACTION=Edit METHOD=POST>"
						+ "<INPUT TYPE=HIDDEN NAME=TextId VALUE=" + text.id + " />"
						+ "<TR><TD><INPUT TYPE=TEXT NAME=Title VALUE='" + Question.quot2html(text.title) + "' /></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Author VALUE='" + Question.quot2html(text.author) + "' /></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=Publisher VALUE='" + Question.quot2html(text.publisher) + "' /></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=URL VALUE='" + text.URL + "' /></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=ImgURL VALUE='" + text.imgUrl + "' /></TD>"
						+ "<TD><INPUT TYPE=TEXT NAME=PrintCopyURL VALUE='" + text.printCopyUrl + "' /></TD>"
						+ "<TD style=text-align:center><INPUT TYPE=CHECKBOX NAME=SmartText VALUE=True " + (text.smartText?"CHECKED ":"") + " /></TD>"
						+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Text'/ > "
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Text' /> "
						+ "<a href=/Edit?UserRequest=ManageTexts&TextId=" + text.id + ">Chapters</a>&nbsp;</TD></TR>"
						+ "</FORM>");
			}
			buf.append("<FORM ACTION=Edit METHOD=POST><TR>"
					+ "<TD><INPUT TYPE=TEXT NAME=Title></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Author></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=Publisher></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=URL></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=ImgURL></TD>"
					+ "<TD><INPUT TYPE=TEXT NAME=PrintCopyURL></TD>"
					+ "<TD style=text-align:center><INPUT TYPE=CHECKBOX NAME=SmartText VALUE=True /></TD>"
					+ "<TD><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Create Text'></TD></TR>"
					+ "</FORM>");
			buf.append("</TABLE>");
		} catch (Exception e2) {
			buf.append(e2.toString());
		}
		return buf.toString();
		}
	}

	void createText(User user,HttpServletRequest request) {
		Text text = new Text(request.getParameter("Title"),request.getParameter("Author"),request.getParameter("Publisher"),request.getParameter("URL"),request.getParameter("ImgURL"),request.getParameter("PrintCopyURL"));
		ofy().save().entity(text).now();
	}
	
	void updateText(User user,HttpServletRequest request) {
		try {
			Text text = ofy().load().type(Text.class).id(Long.parseLong(request.getParameter("TextId"))).safe();
			text.title = request.getParameter("Title");
			text.author = request.getParameter("Author");
			text.publisher = request.getParameter("Publisher");
			text.URL = request.getParameter("URL");
			text.imgUrl = request.getParameter("ImgURL");
			text.printCopyUrl = request.getParameter("PrintCopyURL");
			text.smartText = Boolean.parseBoolean(request.getParameter("SmartText"));
			ofy().save().entity(text).now();
		} catch (Exception e) {}
	}
	
	void deleteText(User user,HttpServletRequest request) {
		ofy().delete().key(Key.create(Text.class,Long.parseLong(request.getParameter("TextId")))).now();
	}
	
	void createChapter(HttpServletRequest request) throws Exception {
			Text text = ofy().load().type(Text.class).id(Long.parseLong(request.getParameter("TextId"))).safe();
			if (text.chapters==null) text.chapters = new ArrayList<Chapter>();
			Chapter ch = new Chapter();
			ch.chapterNumber = Integer.parseInt(request.getParameter("ChapterNumber"));
			ch.title = request.getParameter("ChapterTitle");
			ch.url =  request.getParameter("ChapterUrl");
			String[] conceptIds = request.getParameterValues("ConceptId");
			ch.conceptIds.clear();
			for (String cId : conceptIds) ch.conceptIds.add(Long.parseLong(cId));
			
			int chapterIndex = 0;
			for (Chapter c : text.chapters) {
				if (c.chapterNumber > ch.chapterNumber) break;
				else chapterIndex++;
			}
			text.chapters.add(chapterIndex,ch);
			ofy().save().entity(text).now();
	}
	
	void updateChapter(HttpServletRequest request) {
		try {
			Text text = ofy().load().type(Text.class).id(Long.parseLong(request.getParameter("TextId"))).safe();
			int chapterIndex = Integer.parseInt(request.getParameter("ChapterIndex"));
			Chapter ch = text.chapters.get(chapterIndex);
			ch.chapterNumber = Integer.parseInt(request.getParameter("ChapterNumber"));
			ch.title = request.getParameter("ChapterTitle");
			ch.url =  request.getParameter("ChapterUrl");
			String[] conceptIds = request.getParameterValues("ConceptId");
			ch.conceptIds.clear();
			for (String cId : conceptIds) ch.conceptIds.add(Long.parseLong(cId));
			
			text.chapters.set(chapterIndex,ch);
			ofy().save().entity(text).now();
		} catch (Exception e) {}
	}
	
	void deleteChapter(HttpServletRequest request) {
		Text text = ofy().load().type(Text.class).id(Long.parseLong(request.getParameter("TextId"))).safe();
		int chapterIndex = Integer.parseInt(request.getParameter("ChapterIndex"));
		text.chapters.remove(chapterIndex);
		ofy().save().entity(text).now();
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
					+ "<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + user.getId() + "'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + ">");
			
			buf.append("Topic: " + topicSelectBox(topicId) + "<br>");
			
			buf.append("Point Value: " + pointValueSelectBox(assignmentType) + "<br>");
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
	
	String pointValueSelectBox(String assignmentType) {
		switch (assignmentType) {
		  case "Quiz": return pointValueSelectBox(1);
		  case "Homework": return pointValueSelectBox(1);
		  case "Exam": return pointValueSelectBox(2); 
		  case "Video": return pointValueSelectBox(1);
		  default: return null;
		}		
	}
	
	String pointValueSelectBox(int points) {
		return "<SELECT NAME=PointValue>"
		+ "<OPTION" + (points==1?" SELECTED":"") + ">1</OPTION>"
		+ "<OPTION" + (points==2?" SELECTED":"") + ">2</OPTION>"
		+ "<OPTION" + (points==4?" SELECTED":"") + ">4</OPTION>"
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
			
			Long conceptId = null;
			try {
				conceptId = Long.parseLong(request.getParameter("ConceptId"));
			} catch (Exception e) {}
			
			Question q = assembleQuestion(request);
			if (q.requiresParser()) q.setParameters();
			
			buf.append("<h3>Preview Question</h3>");
			
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
			Topic t = ofy().load().type(Topic.class).id(topicId).safe();
			Concept c = conceptId==null || conceptId==0?null:ofy().load().type(Concept.class).id(conceptId).now();
			buf.append("Topic: " + t.title + "<br>");
			buf.append("Concept: " + (c==null?"n/a":c.title) + "<br/>");
			
			if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) buf.append("Learn more at: " + q.learn_more_url + "</br>");
			
			buf.append("Author: " + q.authorId + "<br>");
			buf.append("Editor: " + user.getId() + "<p>");
			
			buf.append("<FORM ACTION=/Edit#q" + q.id + " METHOD=POST>");
			
			buf.append(q.printAll());
			
			if (q.authorId==null) q.authorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "'>");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + user.getId() + "'>");
			
			
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
			buf.append("Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Topic:" + topicSelectBox(q.topicId) + "<br>");
			buf.append("Concept:" + conceptSelectBox(t,conceptId));
			buf.append("Learn More URL: <input type=text size=40 name=LearnMoreURL value='" + (q.learn_more_url == null?"":q.learn_more_url) + "' placeholder='(optional)' /><br/>");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			
			if (q.assignmentType.equals("Exam")) {
				if (q.pointValue!=2 && q.pointValue!=4 && q.pointValue!=10 && q.pointValue!=15) q.pointValue = 2;
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
			Concept c = q.conceptId==null || q.conceptId==0?null:ofy().load().type(Concept.class).id(q.conceptId).now();
			if (q.requiresParser()) q.setParameters();
			buf.append("<h3>Current Question</h3>");
			buf.append("Assignment Type: " + q.assignmentType + " (" + q.pointValue + (q.pointValue>1?" points":" point") + ")<br>");
			buf.append("Topic: " + t.title + "<br>");
			buf.append("Concept: " + (c==null?"n/a":c.title) + "<br/>");
			if (q.learn_more_url != null && !q.learn_more_url.isEmpty()) buf.append("Learn more at: " + q.learn_more_url + "</br>");
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
			
			buf.append("Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Topic:" + topicSelectBox(t.id) + "<br>");
			buf.append("Concept:" + conceptSelectBox(t,q.conceptId) + "<br/>");
			buf.append("Learn More URL: <input type=text size=40 name=LearnMoreURL value='" + (q.learn_more_url == null?"":q.learn_more_url) + "' placeholder='(optional)' /><br/>");
			
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
			buf.append("Assignment Type:" + assignmentTypeDropDownBox(q.assignmentType) + "<br>");
			buf.append("Topic:" + topicSelectBox(t.id) + "<br>");
			
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: " + pointValueSelectBox(q.pointValue) + "<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview>");
			buf.append("</FORM>");

		} catch (Exception e) {
			return "Error: " + (e.getMessage()==null?e.toString():e.getMessage());
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
		long conceptId = 0;
		try {
			conceptId = Long.parseLong(request.getParameter("ConceptId"));
		} catch (Exception e) {}
		String learn_more_url = request.getParameter("LearnMoreURL");
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
		q.conceptId = conceptId;
		q.learn_more_url = learn_more_url;
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
		q.scrambleChoices = Boolean.parseBoolean(request.getParameter("ScrambleChoices"));
		q.validateFields();
		return q;
	}
	
	private void createQuestion(User user,HttpServletRequest request) { //previously type long
		try {
			Question q = assembleQuestion(request);
			q.isActive = true;
			ofy().save().entity(q).now();
	} catch (Exception e) {}
	}

	private void updateQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));	
			Question q = ofy().load().type(Question.class).id(questionId).safe();
			q = assembleQuestion(request,q);
			q.editorId = user.getId();
			q.isActive = true;
			ofy().save().entity(q).now();
			questions.remove(Key.create(q));
		} catch (Exception e) {
			return;
		}
	}

	private void deleteQuestion(User user,HttpServletRequest request) {
		long questionId = 0;
		try {
			questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			ofy().delete().key(k).now();
			questions.remove(Key.create(Question.class,questionId));
		} catch (Exception e) {
			return;
		}
	}

	private void deleteAllQuestions(User user, HttpServletRequest request) throws Exception {
		if (!user.isChemVantageAdmin()) return;
		String assignmentType = request.getParameter("AssignmentType");
		if (assignmentType==null) return;
		long topicId = Long.parseLong(request.getParameter("TopicId"));
		List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).keys().list();
		if (questionKeys.size()>0) ofy().delete().keys(questionKeys);
	}
	
	private void copyQuestionsToPracticeExam(User user,HttpServletRequest request) {
		if (!user.isChemVantageAdmin()) return;
		String assignmentType = request.getParameter("AssignmentType");
		if (assignmentType==null || !(assignmentType.equals("Quiz") || assignmentType.equals("Homework"))) return;
		long topicId = Long.parseLong(request.getParameter("TopicId"));
		List<Question> questions = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).list();
		for (Question q : questions) {
			q.id = null;
			if (q.assignmentType.equals("Quiz")) q.pointValue = 2;
			else q.pointValue = 10;
			q.assignmentType = "Exam";		
		}
		ofy().save().entities(questions);
	}
	
	String editVideoForm(HttpServletRequest request) {
		Video v = null;
		try {
			v = ofy().load().type(Video.class).id(Long.parseLong(request.getParameter("VideoId"))).safe();
		} catch (Exception e) {
			return "Video was not found, sorry.";
		}
		int segment = 0;		
		try {
			segment = Integer.parseInt(request.getParameter("Segment"));
		}catch (Exception e) {
			segment = v.breaks==null?0:v.breaks.length;  // default to create a new breakpoint and segment
		}
		return editVideoForm(v,segment);
	}
	
	String editVideoForm(Video v, int segment) {
		StringBuffer buf = new StringBuffer();
		if (v.breaks == null) {
			v.breaks = new int[0];
			v.nQuestions = new int[0];
			v.questionKeys = new ArrayList<Key<Question>>();
		}
		
		try {
			buf.append("<h3>Embed quiz questions in a video</h3>");

			buf.append("Video: " + v.title + "<p>");
			
			buf.append("Use the form below to create or edit breakpoints in this video where 2-question quizlets will be "
					+ "presented to the viewer. Each segment must be edited separately, and breakpoints must be created in "
					+ "increasing order of seconds from the start of the video. You may (optionally) create a quiz at the "
					+ "end of the video by entering 'end' or by leaving the breakpoint time blank.<ol>"
					+ "<li>Select a topic appropriate to the subject of the video (for selecting the questions)</li>"
					+ "<li>Select one of the existing video segments or create a new one</li>"
					+ "<li>If you selected an existing segment, wait for the page to reload</li>"
					+ "<li>Enter the breakpoint (in seconds from the start of the video) or 'end'</li>"
					+ "<li>Select several questions from below to be drawn at random for the quizlet</li>"
					+ "<li>Click the 'Update Video' button to submit the breakpoint value and question items</li>"
					+ "</ol>");

			buf.append("<form method=post action=/Edit>");  // This starts the master form for editing a single break point for hte video
			buf.append("<input type=hidden name=VideoId value=" + v.id + ">");
			buf.append("<input type=hidden name=Segment value=" + segment + ">");

			buf.append("Topic: " + topicSelectBox(v.topicId,true) + "<p>");				

			if (v.topicId == 0L) {
				buf.append("<input type=hidden name=UserRequest value='Update Quizlet'>");
			} else {
				// Print a table of existing segments/breakpoints, and add one row at the end to create a new one unless the end has already been specified
				// The number of table rows should be v.breaks.length if the last break is at the end of the video
				// Otherwise, it should be v.breaks.length +1 to allow for creating a new segment (default)
				int nRows = v.breaks.length;
				boolean addNew = (segment==v.breaks.length && (segment==0 || v.breaks[v.breaks.length-1]>0));
				if (addNew) nRows++;

				buf.append("<table><tr><th>Select</th><th>Segment</th><th>Start</th><th>End</th><th>#Questions</th><th>Action</th></tr>");

				for (int i=0;i<nRows;i++) {
					buf.append("<tr style='text-align:center'>"
							+ "<td>" + (i==segment?"edit&rarr;":"<a href=/Edit?UserRequest=EditVideo&VideoId=" + v.id + "&Segment=" + i + ">select</a>") + "</td>"
							+ "<td>" + (i==v.breaks.length && i==segment?"new":i) + "</td>"
							+ "<td>" + (i==0?0:v.breaks[i-1]) + "</td>"
							+ "<td>" + (i==segment?("<input type=text name=Seconds size=5 value=" + (segment==v.breaks.length?"":(v.breaks[i]<0?"end":v.breaks[i])) + ">"):(v.breaks[i]<0?"end":v.breaks[i])) + "</td>"
							+ "<td>" + (v.nQuestions.length>i?v.nQuestions[i]:0) + "</td>"
							+ "<td>" + (i==segment?"<input type=submit name=UserRequest value=" + (addNew?"'Create Quizlet'> ":"'Update Quizlet'> ") + (i==v.breaks.length-1?"<input type=submit name=UserRequest value='Delete Quizlet'> ":"") + "<a href=/Edit?UserRequest=ManageVideos>Done</a>":"") + "</td>"
							+ "</tr>");
				}
				buf.append("</table><p>");

				if (segment==v.breaks.length) buf.append("Select several questions below to be selected at random for the new quizlet:<p>");
				else buf.append("Select several questions below to be selected at random for the quizlet at " + (v.breaks[segment]<0?"the end of the video:":"t = " + v.breaks[segment] + " seconds:") + "<p>");

				buf.append("You may create/edit questions <a href=Edit?TopicId=" + v.topicId + "&AssignmentType=Video>here</a>.<p>");

				// Make a List of all of the available video questions
				List<Question> questions = ofy().load().type(Question.class).filter("assignmentType","Video").filter("topicId",v.topicId).list();

				// Now make a list of all the current questions for this quizlet, if any:
				List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();		
				int nPriorQuestions = 0;
				if (v.nQuestions.length>segment) {
					for (int j=0;j<segment;j++) nPriorQuestions += v.nQuestions[j];  // add up the number of questions in preceding quizlets
					for (int j=nPriorQuestions;j<nPriorQuestions+v.nQuestions[segment];j++) questionKeys.add(v.questionKeys.get(j));
				}

				// Make a table of available questions, marking the current questions as already selected
				buf.append("<TABLE BORDER=0 CELLSPACING=3 CELLPADDING=0>");
				int k=0;
				for (Question q : questions) {
					k++;
					q.setParameters();
					buf.append("\n<TR><TD VALIGN=TOP NOWRAP>"
							+ "<INPUT TYPE=CHECKBOX NAME=QuestionId VALUE=" + q.id + (questionKeys.contains(Key.create(Question.class,q.id))?" CHECKED>":">"));
					buf.append("<b>&nbsp;" + k + ".</b></TD>");
					buf.append("\n<TD>" + q.printAll() + "</TD>");
					buf.append("</TR>");
				}
				buf.append("</TABLE>");
			}
			buf.append("</form>");
		} catch (Exception e) {
			return buf.toString() + e.getMessage();
		}
		return buf.toString();
	}
	
	class AlphabetizeQuestions implements Comparator<Question> {
		public int compare(Question q1,Question q2) {
			return q1.text.compareTo(q2.text);
		}
	}
	
	class SortBySuccessPct implements Comparator<Key<Question>> {
		public int compare(Key<Question> k1,Key<Question> k2) {
			
			if (pointValue.get(k1)==null) {
				Question q1 = questions.get(k1);
				if (q1==null) q1 = ofy().load().key(k1).now();
				pointValue.put(k1, q1.pointValue);
			}
			if (pointValue.get(k2)==null) {
				Question q2 = questions.get(k2);
				if (q2==null) q2 = ofy().load().key(k1).now();
				pointValue.put(k2, q2.pointValue);
			}
			int rank = pointValue.get(k1) - pointValue.get(k2); // primary sort by pointValue for Exam questions
			
			if (rank == 0) { // for Question items with same point value, sort by successPct (high to low)
				if (successPct.get(k1)==null) {
					int totalResponses = ofy().load().type(Response.class).filter("questionId",k1.getId()).count();
					if (totalResponses==0) successPct.put(k1,100);  // put new questions first
					else {
						int successResponses = ofy().load().type(Response.class).filter("questionId",k1.getId()).filter("score >",0).count();
						successPct.put(k1,successResponses*100/totalResponses);
					}
				}
				if (successPct.get(k2)==null) {
					int totalResponses = ofy().load().type(Response.class).filter("questionId",k2.getId()).count();
					if (totalResponses==0) successPct.put(k2,100);  // put new questions first
					else {
						int successResponses = ofy().load().type(Response.class).filter("questionId",k2.getId()).filter("score >",0).count();
						successPct.put(k2,successResponses*100/totalResponses);
					}
				}
			rank = successPct.get(k2)-successPct.get(k1); // this reverses the normal Comparator to give higher rank to lower successPct
			}
			if (rank==0) rank = k1.compareTo(k2); // tie breaker required else TreeMap will overwrite existing entry
			
			return rank;  
		}
	}

}
