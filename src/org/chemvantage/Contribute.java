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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.cmd.Query;

@WebServlet("/Contribute")
public class Contribute extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet is used by users to contribute new Quiz and Homework questions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.println(Home.header("Contribute a ChemVantage Question Item") + newQuestionForm(user,request) + Home.footer);		
		} catch (Exception e) {}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			if (userRequest.equals("Save")) {
				out.println(Home.header("Thank you for the ChemVantage Question Item") + submitQuestion(user,request) + Home.footer);
			} else out.println(Home.header("Contribute a ChemVantage Question Item") + newQuestionForm(user,request) + Home.footer);
		} catch (Exception e) {
		}
	}

	String newQuestionForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		//String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		try {
			// The values of assignmentType, questionType and topicKey are required to start editing
			String assignmentType = request.getParameter("AssignmentType");
			if (assignmentType==null) assignmentType = "";
			int questionType = 0;
			try {
				questionType = Integer.parseInt(request.getParameter("QuestionType"));
			} catch (Exception e) {}
			long topicId = 0;
			try {
				topicId = Long.parseLong(request.getParameter("TopicId"));
			} catch (Exception e) {}
			String questionText = request.getParameter("QuestionText");
			ArrayList<String> choices = new ArrayList<String>();
			char choice = 'A';
			int nChoices = 0;
			for (int i=0;i<5;i++) {
				String choiceText = request.getParameter("Choice"+ choice +"Text");
				if (choiceText==null) choiceText = "";
				choices.add(choiceText);
				if (choiceText.length() > 0) {
					nChoices = i+1;
				}
				choice++;
			}
			String correctAnswer = "";
			try {
				String[] allAnswers = request.getParameterValues("CorrectAnswer");
				for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
			} catch (Exception e2) {
				correctAnswer = request.getParameter("CorrectAnswer");
			}
			int significantFigures = 3;
			double requiredPrecision = 2.0;
			try {
				significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
				requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
			} catch (Exception e) {}
						
			String questionTag = request.getParameter("QuestionTag");
			String parameterString = request.getParameter("ParameterString");
			if (parameterString == null) parameterString = "";
			String hint = request.getParameter("Hint");
			String solution = request.getParameter("Solution");

			ProposedQuestion q = null;
			boolean preview = false;
			buf.append("<FORM NAME=NewQuestion ACTION=Contribute METHOD=POST>");
			//if (cvsToken!=null) buf.append("<INPUT TYPE=HIDDEN NAME=CvsToken VALUE=" + cvsToken + ">");
			buf.append("<INPUT TYPE=HIDDEN NAME=sig VALUE=" + user.getTokenSignature() + ">");
			
			if (assignmentType.length()>0 && questionType>0 && topicId>0) { // create the question object
				q = new ProposedQuestion(questionType);
				q.topicId = topicId;
				q.assignmentType = assignmentType;
				q.text = questionText;
				q.nChoices = nChoices;
				q.choices = choices;
				q.correctAnswer = correctAnswer;
				q.significantFigures = significantFigures;
				q.requiredPrecision = requiredPrecision;
				q.tag = questionTag;
				q.parameterString = parameterString;
				q.hint = hint;
				q.solution = solution;
				q.authorId = user.id;
				q.contributorId = user.id;
				q.editorId = "";
				q.notes = "";
				q.validateFields();

				if (request.getParameter("QuestionText")!=null) {  // preview the formatted question
					Topic topic = ofy().load().type(Topic.class).id(topicId).now();
					buf.append("<h2>" + assignmentType + " Question Preview</h2>Topic: " + topic.title + "<p>");
					preview = true;
					q.setParameters();
					buf.append(q.printAll());
					buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save'><hr>");
				}
			}

			if (preview) buf.append("<h2>Continue Editing</h2>");
			else {
				buf.append("<h2>Contribute a New Question</h2>");
				buf.append("All instructors are encouraged to contribute new question items to the "
						+ "ChemVantage database for quizzes and homework assignments.  Your contribution "
						+ "will be approved by an editor before is appears in production. By using this form, "
						+ "you certify that the question item is original (not previously copyrighted), and that "
						+ "you assign the copyrights to ChemVantage LLC with the understanding that it will be "
						+ "shared openly through a <a href=http://creativecommons.org/licenses/by/3.0/us/>"
						+ "Creative Commons Attribution 3.0 License</a>. "
						+ "For details, please see the <a href=About#copyright>About Us</a> page.<p>");
			}

			Query<Topic> topics = ofy().load().type(Topic.class);
			buf.append("<b>Topic: </b><SELECT NAME=TopicId>");
			if (topicId == 0) buf.append("<OPTION VALUE=0>Select a topic:</OPTION>");
			for (Topic t : topics) {
				buf.append("<OPTION " + ((t.id == topicId)?"SELECTED ":"")
						+ "VALUE='" + t.id + "'>" + t.title + "</OPTION>");
			}
			buf.append("</SELECT>" + (topicId>0?"<br>":"<p>"));

			if (assignmentType.length()==0) {
				buf.append("<b>Assignment Type: </b><br>"
						+ "<INPUT TYPE=RADIO NAME=AssignmentType VALUE='Quiz'>Quiz<br>"
						+ "<INPUT TYPE=RADIO NAME=AssignmentType VALUE='Homework'>Homework<p>");
			}
			else {
				buf.append("<b>Assignment Type: </b>" + assignmentType + "<br>");
				buf.append("<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "'>");
			}

			if (questionType > 0) {
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE='" + questionType + "'>");
				buf.append("<b>Question Type: </b>");
				switch (questionType) {
				case (1): buf.append("Multiple-Choice<br/>");
				buf.append("Fill in the question text and the possible answers "
						+ "(up to a maximum of 5). Be sure to select the single best "
						+ "answer to the question.<p>"); break;
				case (2): buf.append("True-False<br/>");
				buf.append("Write the question as an affirmative statement. Then "
						+ "indicate below whether the statement is true or false.<p>"); break;
				case (3): buf.append("Select-Multiple<br />");
				buf.append("Fill in the question text and the possible answers "
						+ "(up to a maximum of 5). Be sure to select all of the "
						+ "correct answers to the question.<p>"); break;
				case (4): buf.append("Fill-in-Word<br />");
				buf.append("Start the question text in the upper textarea box. Indicate "
						+ "the correct answer (and optionally, an alternative correct answer) in "
						+ "the middle boxes, and the end of the question text below that.  The answers "
						+ "are not case-sensitive or punctuation-sensitive, but spelling must "
						+ "be exact.<p>"); break;
				case (5): buf.append("Numeric<br />");
				buf.append("Fill in the question text in the upper textarea box and "
						+ "the correct numeric answer below. Also indicate the required precision "
						+ "of the student's response in percent (default = 2%). Use the Units box "
						+ "to indicate the expected dimensions or units of the student response or "
						+ "to finish any part of the question text that comes last.<p>"); break;
				default:  buf.append("An unexpected error occurred. "
						//+ "Please <a href=Contribute" + (cvsToken==null?"":"&CvsToken=" + cvsToken) + ">try again</a>.");
						+ "Please <a href=Contribute?sig=" + user.getTokenSignature() + ">try again</a>.");
				}
			}
			else {
				buf.append("<b>Question Type: </b><br>"
						+ "<INPUT TYPE=RADIO NAME=QuestionType VALUE=1>Multiple Choice<br />"
						+ "<INPUT TYPE=RADIO NAME=QuestionType VALUE=2>True/False<br />"
						+ "<INPUT TYPE=RADIO NAME=QuestionType VALUE=3>Select Multiple<br />"
						+ "<INPUT TYPE=RADIO NAME=QuestionType VALUE=4>Fill in Word<br />"
						+ "<INPUT TYPE=RADIO NAME=QuestionType VALUE=5>Numeric<br />");
			}

			if (q != null) buf.append(q.edit() 
					+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview This Question'>");
			else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Continue>");

			buf.append("</FORM");

		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	String submitQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		//String cvsToken = request.getSession().isNew()?user.getCvsToken():null;
		ProposedQuestion q = null;
		try {
			String assignmentType = request.getParameter("AssignmentType");
			long topicId = Long.parseLong(request.getParameter("TopicId"));
			int questionType = 0;
			questionType = Integer.parseInt(request.getParameter("QuestionType"));
			q = new ProposedQuestion(questionType);
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
			double requiredPrecision = 0.0; // percent
			int significantFigures = 3;
			int pointValue = 1;
			try {
				Integer.parseInt(request.getParameter("PointValue"));
			} catch (Exception e) {};
			try {
				requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
			} catch (Exception e2) {
			}
			try {
				significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
			} catch (Exception e) {
			}
			String correctAnswer = "";
			try {
				String[] allAnswers = request.getParameterValues("CorrectAnswer");
				for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
			} catch (Exception e2) {
				correctAnswer = request.getParameter("CorrectAnswer");
			}
			String questionTag = request.getParameter("QuestionTag");
			String parameterString = request.getParameter("ParameterString");
			if (parameterString == null) parameterString = "";
			String hint = request.getParameter("Hint");
			String solution = request.getParameter("Solution");

			q.topicId = topicId;
			q.assignmentType = assignmentType;
			q.text = questionText;
			q.nChoices = nChoices;
			q.choices = choices;
			q.requiredPrecision = requiredPrecision;
			q.significantFigures = significantFigures;
			q.correctAnswer = correctAnswer;
			q.tag = questionTag;
			q.pointValue = pointValue;
			q.parameterString = parameterString;
			q.hint = hint;
			q.solution = solution;
			q.authorId = user.id;
			q.contributorId = user.id;
			q.editorId = "";
			q.notes = "";
			q.validateFields();
			
			ofy().save().entity(q);
		} catch (Exception e) {
			buf.append(e.toString());
		}
		if (buf.length() == 0) { // no errors reported
			buf.append("<h3>Question Submitted Successfully</h3>"
			+ "Thank you for contributing this question item to ChemVantage.<br>"
			+ "Your contribution will be reviewed by an editor before it is added to the database.<br>"
			+ "<a href=Contribute?sig=" + user.getTokenSignature() + ">Contribute another question item</a>.");
		}
		return buf.toString();
	}
}
