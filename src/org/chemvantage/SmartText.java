package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
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
				out.println(Subject.header("ChemVantage Key Concept Question") + printQuestion(user,request) + Subject.footer);
				break;
			default: 
				if (user.isInstructor()) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,request) + Subject.footer);
				else out.println(Subject.header("ChemVantage Key Concept Question") + printQuestion(user,request) + Subject.footer);
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
			buf.append("<h2>SmartText - Instructor Page</h2>");
			buf.append("Chapter: " + a.title + "<br/>");
			Map<Long,Topic> topics = ofy().load().type(Topic.class).ids(a.topicIds);
			buf.append("Concepts covered:<ul>");
			for (Topic t : topics.values()) buf.append("<li>" + t.title + "</li>");
			buf.append("</ul>");
			
			buf.append("<a style='text-decoration: none' href='/SmartText?UserRequest=PrintQuestion&sig=" + user.getTokenSignature() + "'>"
					+ "<button style='display: block; width: 500px; border: 1 px; background-color: #00FFFF; color: black; padding: 14px 28px; font-size: 18px; text-align: center; cursor: pointer'>"
					+ "Show This Assignment (recommended)</button></a><br/>");
		} catch (Exception e) {
			
		}
		return buf.toString();
   }
   
   String printQuestion(User user,HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer("<h3>Key Concept Question</h3>");
	   // load the assignment pertaining to this launch
	   Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
	   Topic t = ofy().load().type(Topic.class).id(a.topicId).safe();
	   if (t.conceptIds==null || t.conceptIds.isEmpty()) return "Sorry, there are no Key Concepts identified for thie topic.";
	   
	   buf.append(a.title + "<br/><br/>");
	   
	   // load the SmartText transaction entity for this user if one exists
	   STTransaction st = null;
	   Random r = new Random();
	   int score = 0;
	   int possibleScore = 2*t.conceptIds.size();
	   
	   try {
		   st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).first().safe();
		   for (int i=0; i<st.scores.length; i++) {
			   if (st.scores[i] == 2) t.conceptIds.remove(i);
			   score += st.scores[i];
		   }
		   buf.append("Your progress toward completion: " + 100*score/possibleScore + "%<br/><br/>");
	   } catch (Exception e) {
		   st = new STTransaction(user.getHashedId(),a.id,t.conceptIds);
		   buf.append("You will be asked at least 2 questions about each of the " + t.conceptIds.size() + " concepts covered in this chapter. "
				   + "If you answer 2 questions correctly, you will be credited as mastering the concept. If you answer 2 questions on the same concept "
				   + "incorrectly, you will be directed back to the textbook for additional reading before you can continue.<br/><br/>");
	   }

	   // randomly select one of the active topics
	   long conceptId = t.conceptIds.get(r.nextInt(t.conceptIds.size()));
	   
	   // get all the question keys for the chosen topic, eliminate any not in the assignment
	   List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("conceptId",conceptId).keys().list();
	   for (Key<Question> k : questionKeys) if (!a.questionKeys.contains(k)) questionKeys.remove(k);
	   
	   // randomly select one questionKey and display the question
	   Key<Question> questionKey = questionKeys.get(r.nextInt(questionKeys.size()));
	   Question q = ofy().load().key(questionKey).now();
	   int p = r.nextInt();
	   q.setParameters(p);
	   
	   buf.append("<form method=post action=/SmartText>"
	   		+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
	   		+ "<input type=hidden name=AssignmentId value='" + a.id + "' />"
	   		+ "<input type=hidden name=ConceptIdId value='" + conceptId + "' />"
	   		+ "<input type=hidden name=QuestionId value='" + q.id + "' />"
	   		+ "<input type=hidden name=Parameter value='" + p + "' />"
	   		+ "<input type=hidden name=UserRequest value='GradeQuestion' />"
	   		+ q.print()
	   		+ "<input type=submit />"
	   		+ "</form>");
	   
	   return buf.toString();
   }
   
   String printScore(User user, HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer();
	   try {
		   long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
		   long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		   long questionId = Long.parseLong(request.getParameter("QuestionId"));
		   int p = Integer.parseInt(request.getParameter("p"));
		   Question q = ofy().load().type(Question.class).id(questionId).safe();
		   q.setParameters(p);
		   String studentAnswer = orderResponses(request.getParameterValues(Long.toString(questionId)));

		   STTransaction st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",assignmentId).first().now();
		     
		   // get the index of st.conceptIds that corresponds to the conceptId for this question:
		   int index = 0;
		   for (int i=0;i<st.conceptIds.size();i++) {
			   if (st.conceptIds.get(i) == conceptId) {
				   index = i;
				   break;
			   }
		   }

		   if (q.isCorrect(studentAnswer)) {
			   st.scores[index]++;
			   buf.append("<h3>Congratulations! Your answer was correct.</h3>");
		   } else {
			   st.missedQuestions[index]++;
			   buf.append("<h3>Sorry, your answer was incorrect.</h3>"
					   + "Don't worry if you answer some questions incorrectly. You can "
					   + "still earn 100% by completing all of the questions.<br/><br/>"
					   + "Here is the correct answer:<br/>");
		   }
		   
		   buf.append(q.printAllToStudents(studentAnswer) + "<br/><br/>");

		   int score = 0;
		   for (int i=0; i<st.scores.length; i++) score += st.scores[i];
		   int possibleScore = 2*st.conceptIds.size();
		   buf.append("Your progress toward completion: " + 100*score/possibleScore + "%<br/><br/>");

		   if (score==possibleScore) {
			   buf.append("<h2>You have mastered all of the concepts in this chapter</h2>");
		   } else if (st.missedQuestions[index]<2) {  // continue to the next question
			   buf.append("<a href=/SmartText?sig=" + user.getTokenSignature() + ">"
			   		+ "<button style='border: none; color: white; padding: 10px 10px; margin: 4px 2px; font-size: 16px; cursor: pointer; border-radius: 10px; background-color: blue;'>"
			   		+ "Continue to the Next Question</button></a><br/><br/>");
		   } else { // missed 2 questions; go back to the text
			   Concept c = ofy().load().type(Concept.class).id(conceptId).safe();
			   buf.append("<h2>Too Many Incorrect Answers</h2>"
			   		+ "You missed 2 questions on the key concept: <b>" + c.title + "</b>.<br/>"
			   		+ "Please return to the textbook and review this section before "
			   		+ "returning to the Key Concept Questions.<br/><br/>");
			   st.missedQuestions[index]=0; // reset the missed questions for this concept only
		   }
		   ofy().save().entity(st).now();
	   } catch (Exception e) {
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
