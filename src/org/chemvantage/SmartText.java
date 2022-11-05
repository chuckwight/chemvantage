package org.chemvantage;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("SmartText")
public class SmartText extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	   response.setContentType("text/html");
	   PrintWriter out = response.getWriter();

		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Long stTransactionId = null;
			try {
				stTransactionId = Long.parseLong(request.getParameter("STTransactionId"));
			} catch (Exception e) {}
			
			out.println(Subject.header("ChemVantage Key Concept Question") + printQuestion(user,a,stTransactionId) + Subject.footer);

		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	   response.setContentType("text/html");
	   PrintWriter out = response.getWriter();

	  try {
		   User user = User.getUser(request.getParameter("sig"));
		   if (user==null) throw new Exception();

		   long aId = user.getAssignmentId();		
		   Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();

		   String userRequest = request.getParameter("UserRequest");
		   if (userRequest==null) userRequest = "";

		   switch (userRequest) {
		   case "GradeQuestion":
			   out.println(Subject.header("ChemVantage Key Concept Response") + printScore(user,a,request));
		   default:
		   }

	   } catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
	   }
   }

   static String instructorPage(User user,Assignment a) {
	   // this is a placeholder until instructors are granted customization privileges
	   return printQuestion(user,a,null);
   }

   static String printTextHeader(Text t,Chapter c) {
	   StringBuffer buf = new StringBuffer();
	   buf.append("<div style=display:table><div style='display:table-row;'><div style='display:table-cell;vertical-align:top;width:450px;padding-right:20px'>");
	   buf.append("<h3>Reading Assignment</h3>");
	   buf.append("Textbook: <b>" + t.title + "</b><br/>"
	   		+ "Author: " + t.author + "<br/><br/>"
	   		+ "Chapter " + c.chapterNumber + ": " + c.title + "<br/>");
	   buf.append("<h3>Reading Options</h3>");
	   buf.append("<ul>");
	   if (c.url != null) buf.append("<li><a href='" + c.url + "' target=_blank>Read this chapter online</a></li>");
	   buf.append("<li><a href='" + t.printCopyUrl + "' target=_blank>Order a print copy of this book</a></li>");
	   buf.append("</ul>");
	   buf.append("</div><div style='display:table-cell;vertical-align:top;'>");
	   buf.append("<img src='" + t.imgUrl + "' alt='Textbook cover art'>");
	   buf.append("</div></div></div>");
	  
	   return buf.toString();
   }
   
   static String printQuestion(User user,Assignment a,Long stId) {
	   StringBuffer buf = new StringBuffer();
	   try {
		   // load the assignment pertaining to this launch
		   Text text = ofy().load().type(Text.class).id(a.textId).safe();
		   Chapter chapter = null;
		   for (Chapter ch : text.chapters) {
			   if (ch.chapterNumber == a.chapterNumber) {
				   chapter = ch;
				   break;
			   }
		   }
		   if (chapter==null) return "Sorry, we were unable to find the chapter of this textbook.";
		   
		   buf.append(printTextHeader(text,chapter) + "<hr>");
		  
		   buf.append("<h3>Key Concept Questions</h3>");
		   // load the SmartText transaction entity for this user if one exists
		   STTransaction st = null;
		   try {
			   if (stId!=null) st = ofy().load().type(STTransaction.class).id(stId).safe();
			   else st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",a.id).first().safe();
			   // bulletproofing in case of change in assignment
			   if (st.scores.length != chapter.conceptIds.size()) {
				   ofy().delete().entity(st).now();
				   throw new Exception("There was a change to the assignment settings...starting over.<br/>");
			   }
		   } catch (Exception e) {
			   st = new STTransaction(user.getHashedId(),a.id,chapter.conceptIds);
			   buf.append("Complete all key concept questions to score 100% for this assignment.<br/><br/>");
		   }
		   
		   // Make a List of completed conceptIds:
		   List<Long> completed = new ArrayList<Long>();
		   for (int i=0; i<chapter.conceptIds.size(); i++) {
			   int qCount = ofy().load().type(Question.class).filter("conceptId",chapter.conceptIds.get(i)).count();
			   st.possibleScores[i] = qCount>0?(qCount>1?2:1):0;
			   if (st.scores[i] >= st.possibleScores[i]) completed.add(chapter.conceptIds.get(i));   
		   }
		   
		   // Find a randomly selected question:
		   Random random = new Random();
		   Question q = null;
		   long conceptId=0;
		   
		   while (q==null && !completed.containsAll(chapter.conceptIds)) {
			   chapter.conceptIds.removeAll(completed);
			   if (chapter.conceptIds.size()==0) break;
			   
			   conceptId = chapter.conceptIds.get(random.nextInt(chapter.conceptIds.size()));
			   
			   // get all the question keys for the chosen conceptId
			   List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("conceptId",conceptId).keys().list();
			   
			   // remove any question keys not in the assignment or already answered correctly
			   if (!questionKeys.isEmpty() && !st.answeredKeys.isEmpty()) questionKeys.removeAll(st.answeredKeys);
			
			   // randomly select one questionKey and display the question
			   if (!questionKeys.isEmpty()) {  
				   Key<Question> questionKey = questionKeys.get(random.nextInt(questionKeys.size()));
				   q = ofy().load().key(questionKey).now();
			   } else completed.add(conceptId);
		   }
		   
		   // Determine if the assignment is complete:
		   if (completed.containsAll(chapter.conceptIds)) {
			   buf.append("This assignment is complete. Your score is 100%.<br/>");
			   return buf.toString();
		   }
		     
		   // At this point we should have a valid question q.
		   int p = random.nextInt();
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
   
   String printScore(User user, Assignment a,HttpServletRequest request) {
	   StringBuffer buf = new StringBuffer();
	   StringBuffer debug = new StringBuffer("Debug: ");
	   try {
		   long assignmentId = a==null?Long.parseLong(request.getParameter("AssignmentId")):a.id;
		   Text text = ofy().load().type(Text.class).id(a.textId).now();
		   Chapter chapter = null;
		   for (Chapter ch : text.chapters) {
			   if (ch.chapterNumber == a.chapterNumber) {
				   chapter = ch;
				   break;
			   }
		   }
		   buf.append(printTextHeader(text,chapter) + "<hr>");
		   buf.append("<h3>Key Concept Questions</h3>");
		   long conceptId = Long.parseLong(request.getParameter("ConceptId"));
		   long questionId = Long.parseLong(request.getParameter("QuestionId"));
		   int p = Integer.parseInt(request.getParameter("Parameter"));
		   
		   Question q = ofy().load().type(Question.class).id(questionId).safe();
		   q.setParameters(p);
		   
		   String studentAnswer = orderResponses(request.getParameterValues(Long.toString(questionId)));
		   STTransaction st = ofy().load().type(STTransaction.class).filter("userId",user.getHashedId()).filter("assignmentId",assignmentId).first().now();
		   
		   //  get the index of st.conceptIds that corresponds to the conceptId for this question:
		   int index = st.conceptIds.indexOf(conceptId);
		   boolean isCorrect = q.isCorrect(studentAnswer);
		   buf.append(ajaxJavaScript(user.getTokenSignature()));  // for providing user feedback on the question item
		   
		   if (isCorrect) {
			   st.scores[index]++;
			   st.answeredKeys.add(Key.create(Question.class,q.id));
			   buf.append("<b>Congratulations! Your answer was correct.</b><br/>");
			   Score s = Score.getInstance(user.getId(), a);
			   ofy().save().entity(s).now();
			   QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(assignmentId)).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue   
		   } else {
			   st.missedQuestions[index]++;
			   buf.append("<b>Sorry, your answer was incorrect.</b>"
			   		+ " (<a href=# onClick=\"document.getElementById('correctAnswer').style='display:block';return false;\">show me</a>)<br/>");
			   buf.append("<div id=correctAnswer style='display:none'><b>Here is the correct answer:</b><br/><br/>" 
					   + q.printAllToStudents(studentAnswer) + "</div><br/>");
		   }
		   
		   if (a != null) {
			   Response r = new Response("SmartText",conceptId,q.id,studentAnswer,q.getCorrectAnswer(),isCorrect?1:0,1,user.getId(),new Date());
			   ofy().save().entity(r);
		   }
		   
		   int score = 0;
		   int possibleScore = 0;
		   for (int i=0; i<st.scores.length; i++) {
			   score += st.scores[i];
			   possibleScore += st.possibleScores[i];
		   }
		   
		   if (score==possibleScore) {
			   buf.append("You have answered all of the key concept questions for this assignment. Your score is 100%.<br/><br/>");
			   buf.append(fiveStars());
		   } else if (st.missedQuestions[index]<2) {  // continue to the next question
			   buf.append("This assignment is " + 100*score/possibleScore + "% complete.<br/><br/>");
			   buf.append("<a href=/SmartText?UserRequest=PrintQuestion&STTransactionId=" + st.id + "&sig=" + user.getTokenSignature() + ">"
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

	String ajaxJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,note,email) {\n"
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
		+ "      '<FONT COLOR=#EE0000><b>Thank you. An editor will review your comment.</b></FONT><p>';\n"
		+ "    }\n"
		+ "  }\n"
		+ "  url += '&QuestionId=' + id + '&sig=" + signature + "&Notes=' + note + '&Email=' + email;\n"
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
		+ "                + 'please take a moment to <a href=/Feedback?sig=" + signature + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '2': msg='2 stars - If you are dissatisfied with ChemVantage, '"
		+ "                + 'please take a moment to <a href=/Feedback?sig=" + signature + ">tell us why</a>.';"
		+ "                break;\n"
		+ "      case '3': msg='3 stars - Thank you. <a href=/Feedback?sig=" + signature + ">Click here</a> '"
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

	String fiveStars() {
		StringBuffer buf = new StringBuffer();

		buf.append("<script type='text/javascript'>"
				+ "  var star1 = new Image(); star1.src='images/star1.gif';"
				+ "  var star2 = new Image(); star2.src='images/star2.gif';"
				+ "  var set = false;"
				+ "  function showStars(n) {"
				+ "    if (!set) {"
				+ "      document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));"
				+ "      for (i=1;i<6;i++) {document.getElementById(i).src=(i<=n?star2.src:star1.src)}"
				+ "    }"
				+ "  }"
				+ "  function setStars(n) {"
				+ "    if (!set) {"
				+ "      ajaxStars(n);"
				+ "      set = true;"
				+ "      document.getElementById('sliderspan').style='display:none';"
				+ "    }"
				+ "  }"
				+ "</script>");

		buf.append("<div>Please rate your overall experience with ChemVantage:<br />"
				+ "<span id='vote' style='font-family:tahoma; color:#EE0000;'>(click a star):</span><br>");

		for (int iStar=1;iStar<6;iStar++) {
			buf.append("<img src='images/star1.gif' id='" + iStar + "' "
					+ "style='width:30px; height:30px;' "
					+ "onmouseover=showStars(this.id); onClick=setStars(this.id); onmouseout=showStars(0); />");
		}
		buf.append("<span id=sliderspan style='opacity:0'>"
				+ "<input type=range id=slider min=1 max=5 value=3 onfocus=document.getElementById('sliderspan').style='opacity:1';showStars(this.value); oninput=showStars(this.value);>"
				+ "<button onClick=setStars(document.getElementById('slider').value);>submit</button>"
				+ "</span>");
		buf.append("</div><br/>");

		return buf.toString(); 
	}

	String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}


}
