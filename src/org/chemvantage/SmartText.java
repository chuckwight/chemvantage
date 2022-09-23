package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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
			default: 
				if (user.isInstructor()) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,request) + Subject.footer);
				else out.println(Subject.header("ChemVantage Concept Question") + printQuestion(user,request) + Subject.footer);
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
			   out.println(Subject.header("ChemVantage SmartText") + printScore(user,request));
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
			
			
			
		} catch (Exception e) {
			
		}
		return buf.toString();
   }
   
   String printQuestion(User user,HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer();
	   // load the assignment pertaining to this launch
	   Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).safe();
	   
	   // load the SmartText transaction entity for this user if one exists
	   STTransaction st = null;
	   try {
		   st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).first().now();
	   } catch (Exception e) {
		   st = new STTransaction(user.getHashedId(),a.id,a.topicIds);
	   }

	   // start the process of selecting a random question from a random active topic
	   Random r = new Random();
	   // get the full list of topics
	   List<Long> topicIds = new ArrayList<Long>(a.topicIds);
	   if (st.scores != null) {  // remove any Topics that have been completed
		   for (int i=0; i<st.scores.length; i++) {
			   if (st.scores[i] == 2) topicIds.remove(i);
		   }
	   }
	   // randomly select one of the active topics
	   long topicId = topicIds.get(r.nextInt(topicIds.size()));
	   
	   // get all the question keys for the chosen topic, eliminate any not in the assignment
	   List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("topicId",topicId).keys().list();
	   for (Key<Question> k : questionKeys) if (!a.questionKeys.contains(k)) questionKeys.remove(k);
	   
	   // randomly select one questionKey and display the question
	   Key<Question> questionKey = questionKeys.get(r.nextInt(questionKeys.size()));
	   Question q = ofy().load().key(questionKey).now();
	   int p = r.nextInt();
	   q.setParameters(p);
	   
	   buf.append("<form method=post action=/SmartText>"
	   		+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
	   		+ "<input type=hidden name=AssignmentId value='" + a.id + "' />"
	   		+ "<input type=hidden name=TopicId value='" + topicId + "' />"
	   		+ "<input type=hidden name=UserRequest value='GradeQuestion' />"
	   		+ q.print()
	   		+ "<input type=submit />"
	   		+ "</form>");
	   
	   return buf.toString();
   }
   
   String printScore(User user, HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer();
	   
	   return buf.toString();
   }
}
