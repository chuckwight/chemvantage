package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet("SmartText")
public class SmartText extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	   try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "PrintQuestion":
				out.println(Subject.header("ChemVantage Key Concept Question") + printQuestion(user) + Subject.footer);
				break;
			default: 
				if (user.isInstructor()) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,request) + Subject.footer);
				else out.println(Subject.header("ChemVantage Key Concept Question") + printQuestion(user) + Subject.footer);
			}

		} catch (Exception e) {
		}
   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	   try {
		   User user = User.getUser(request.getParameter("sig"));
		   if (user==null) throw new Exception();

		   response.setContentType("text/html");
		   PrintWriter out = response.getWriter();

		   String userRequest = request.getParameter("UserRequest");
		   if (userRequest==null) userRequest = "";

		   switch (userRequest) {
		   case "GradeQuestion":
			   out.println(Subject.header("ChemVantage Key Concept Response") + printScore(user,request));
		   default:
		   }

	   } catch (Exception e) {
	   }
   }

   String instructorPage(User user,HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer();		
		try {
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
			Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
			buf.append("<h2>SmartText - Instructor Page</h2>");
			buf.append("Chapter: " + t.title + "<br/>");
			Map<Long,Concept> concepts = ofy().load().type(Concept.class).ids(t.conceptIds);
			buf.append("Concepts covered:<ul>");
			for (Concept c : concepts.values()) buf.append("<li>" + c.title + "</li>");
			buf.append("</ul>");
			
			buf.append("<a style='text-decoration: none' href='/SmartText?UserRequest=PrintQuestion&sig=" + user.getTokenSignature() + "'>"
					+ "<button style='display: block; width: 500px; border: 1 px; background-color: #00FFFF; color: black; padding: 14px 28px; font-size: 18px; text-align: center; cursor: pointer'>"
					+ "Show This Assignment (recommended)</button></a><br/>");
		} catch (Exception e) {
			buf.append("<br/>Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
   }
   
   String printQuestion(User user) {
	   StringBuffer buf = new StringBuffer("<h3>Key Concept Question</h3>");
	   try {
		   // load the assignment pertaining to this launch
		   Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
		   Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
		   if (t.conceptIds==null || t.conceptIds.isEmpty()) return "Sorry, there are no Key Concepts identified for thie topic.";
		   // Make a copy of the List of conceptIds:
		   List<Long> conceptIds = new ArrayList<Long>(t.conceptIds);
		   
		   // load the SmartText transaction entity for this user if one exists
		   STTransaction st = null;
		   int score = 0;
		   int possibleScore = 0;
		   try {
			   st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).first().safe();
			   // Bulletproofing: check to ensure that conceptIds haven't been added; or start over
			   if (st.scores.length != t.conceptIds.size()) {
				   ofy().delete().entity(st).now();
				   throw new Exception();
			   }
			   // Calculate percent completion and remove completed conceptIds:
			   for (int i=0; i<st.scores.length; i++) {
				   score += st.scores[i];
				   possibleScore += st.possibleScores[i];
				   if (st.scores[i] == st.possibleScores[i]) conceptIds.remove(t.conceptIds.get(i));   
				   if (score>0 && score==possibleScore) {
					   buf.append("You have already completed this assignment.<br/>");
					   return buf.toString();
				   }
				}
			} catch (Exception e) {
			   st = new STTransaction(user.getHashedId(),a.id,t.conceptIds);
			   buf.append("Complete all key concept questions to score 100% for this assignment.<br/><br/>");
		   }
		   
		   // Find a randomly selected question:
		   Random r = new Random();
		   Question q = null;
		   long conceptId=0;
		   while (q==null) {
			   conceptId = conceptIds.get(r.nextInt(conceptIds.size()));
			   
			   // get all the question keys for the chosen conceptId
			   List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("conceptId",conceptId).keys().list();
			   
			   if (!questionKeys.isEmpty()) {  //  remove any question keys not in the assignment or already answered correctly
				   List<Key<Question>> removeThese = new ArrayList<Key<Question>>();
				   for (Key<Question> k : questionKeys) if (!a.questionKeys.contains(k)) removeThese.add(k);
				   questionKeys.removeAll(removeThese);
				   questionKeys.removeAll(st.answeredKeys);
			   }
			   if (!questionKeys.isEmpty()) {  // randomly select one questionKey and display the question
				   Key<Question> questionKey = questionKeys.get(r.nextInt(questionKeys.size()));
				   q = ofy().load().key(questionKey).now();
			   } else {  // close out this conceptId
				   int index = t.conceptIds.indexOf(conceptId);
				   st.possibleScores[index] = st.scores[index];
				   ofy().save().entity(st).now();
				   conceptIds.remove(conceptId);
				   if (conceptIds.isEmpty()) {
					   buf.append("You have completed this assignment.");
					   return buf.toString();
				   }
			   }
		   }
		   // At this point we should have a valid question q.
		   int p = r.nextInt();
		   q.setParameters(p);

		   buf.append("<form method=post action=/SmartText>"
				   + "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				   + "<input type=hidden name=AssignmentId value='" + a.id + "' />"
				   + "<input type=hidden name=ConceptId value='" + conceptId + "' />"
				   + "<input type=hidden name=QuestionId value='" + q.id + "' />"
				   + "<input type=hidden name=Parameter value='" + p + "' />"
				   + "<input type=hidden name=UserRequest value='GradeQuestion' />"
				   + q.print()
				   + "<input type=submit />"
				   + "</form>");
		   ofy().save().entity(st).now();
	   } catch (Exception e) {
		   buf.append("Error: " + (e.getMessage()==null?e.toString():e.getMessage()));
	   }
	   return buf.toString();
   }
   
   String printScore(User user, HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer("<h3>Key Concept Question</h3>");
	   StringBuffer debug = new StringBuffer("Debug: ");
	   try {
		   long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
		   long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		   long questionId = Long.parseLong(request.getParameter("QuestionId"));
		   int p = Integer.parseInt(request.getParameter("Parameter"));
		   
		   Question q = ofy().load().type(Question.class).id(questionId).safe();
		   q.setParameters(p);
		   
		   String studentAnswer = orderResponses(request.getParameterValues(Long.toString(questionId)));
		   STTransaction st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",assignmentId).first().now();
		   
//		   get the index of st.conceptIds that corresponds to the conceptId for this question:
		   int index = st.conceptIds.indexOf(conceptId);
		   if (q.isCorrect(studentAnswer)) {
			   st.scores[index]++;
			   st.answeredKeys.add(Key.create(Question.class,q.id));
			   buf.append("<b>Congratulations! Your answer was correct.</b><br/>");
		   } else {
			   st.missedQuestions[index]++;
			   buf.append("<b>Sorry, your answer was incorrect.</b>"
			   		+ " (<a href=# onClick=\"document.getElementById('correctAnswer').style='display:block';return false;\">show me</a>)<br/>");
			   buf.append("<div id=correctAnswer style='display:none'><b>Here is the correct answer:</b><br/><br/>" 
					   + q.printAllToStudents(studentAnswer) + "</div><br/>");
		   }
		   
		   int score = 0;
		   int possibleScore = 0;
		   for (int i=0; i<st.scores.length; i++) {
			   score += st.scores[i];
			   possibleScore += st.possibleScores[i];
		   }
		   
		   if (score==possibleScore) {
			   buf.append("<h2>You have mastered all of the concepts in this chapter</h2>");
		   } else if (st.missedQuestions[index]<2) {  // continue to the next question
			   buf.append("This assignment is " + 100*score/possibleScore + "% complete.<br/><br/>");
			   buf.append("<a href=/SmartText?UserRequest=PrintQuestion&sig=" + user.getTokenSignature() + ">"
			   		+ "<button style='border: none; color: white; padding: 10px 10px; margin: 4px 2px; font-size: 16px; cursor: pointer; border-radius: 10px; background-color: blue;'>"
			   		+ "Continue to the Next Question</button></a><br/><br/>");
		   } else { // missed 2 questions; go back to the text
			   Concept c = ofy().load().type(Concept.class).id(conceptId).safe();
			   buf.append("You missed 2 questions on the key concept: <b>" + c.title + "</b>.<br/>"
			   		+ "Please return to the textbook and review this section. Don't worry. You can still earn 100% "
			   		+ "by completing the remaining Key Concept questions after your review. <br/><br/>");
			   st.missedQuestions[index]=0; // reset the missed questions for this concept only
		   }
		   ofy().save().entity(st).now();
	   } catch (Exception e) {
		   buf.append("Error: " + (e.getMessage()==null?e.toString():e.getMessage()) + "<br/>" + debug.toString());
	   }
	   return buf.toString();
   }
   
	String orderResponses(String[] answers) {
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}


}
