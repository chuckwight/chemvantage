package org.chemvantage;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
			if (user==null) throw new Exception("User token expired.");
			
			long aId = user.getAssignmentId();		
			Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			switch (userRequest) {
			case "ReviewScores":
				out.println(Subject.header() + reviewScores(user,a) + Subject.footer);
				return;
				default:
			}
			
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
		   if (user==null) throw new Exception("Invalid token (may have expired after 90 minutes).");

		   long aId = user.getAssignmentId();		
		   Assignment a = aId==0?null:ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();

		   String userRequest = request.getParameter("UserRequest");
		   if (userRequest==null) userRequest = "";

		   switch (userRequest) {
		   case "GradeQuestion":
			   out.println(Subject.header("ChemVantage Key Concept Response") + printScore(user,a,request) + Subject.footer);
			   break;
		   case "Synchronize Scores":
				if (synchronizeScores(user,a,request)) out.println(Subject.header("ChemVantage Instructor Page") + instructorPage(user,a) + Subject.footer);
				else out.println("Synchronization request failed.");
				break;
			default:
		   }

	   } catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
	   }
   }

   static String instructorPage(User user,Assignment a) {
	   // this is a placeholder until instructors are granted customization privileges
	   return user.isInstructor()?printQuestion(user,a,null):"";
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
		   if (a==null) { // example assignment for anonymous user
			   a = new Assignment();
			   a.id=0L;
			   List<Text> texts = ofy().load().type(Text.class).list();
			   for (Text t : texts) {
				   if (t.smartText) {
					   a.textId = t.id;
					   a.chapterNumber=1;
					   break;
				   }
			   }
		   }
		   // load the assignment pertaining to this launch
		   Text text = ofy().load().type(Text.class).id(a.textId).safe();
		   Chapter chapter = null;
		   for (Chapter ch : text.chapters) {
			   if (ch.chapterNumber == a.chapterNumber) {
				   chapter = ch;
				   break;
			   }
		   }
		   if (chapter==null) return "Sorry, we were unable to find the assigned chapter for this textbook.";
		   
		   buf.append(printTextHeader(text,chapter));
		   
		   if (user.isInstructor()) {
			   buf.append("<b>Instructor: </b><a href=/SmartText?UserRequest=ReviewScores&sig=" + user.getTokenSignature() + ">Review student scores</a><br/><hr>");
		   }
		  
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
		   
		   // Count the missed questions and make a List of completed conceptIds:
		   List<Long> completed = new ArrayList<Long>();
		   boolean complete = true;
		   for (Long cId : st.conceptIds) {
			   int index = st.conceptIds.indexOf(cId);
			   int qCount = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("conceptId",cId).count();
			   st.possibleScores[index] = qCount > 2?2:qCount;
			   if (st.scores[index] >= st.possibleScores[index]) completed.add(cId);
			   else complete = false;
		   }

		   if (complete) {
			   buf.append("<b>This assignment is complete. Your score is 100%. You may continue just for practice.</b><br/><br/>");
			   completed.clear();
		   }
		   
		   // Create a random number generator and (possibly) seed it with st.graded to prevent question-shopping
		   Random random = new Random();
		   if (!complete && st.graded!=null) random.setSeed(st.graded.getTime());
		   
		   Question q = null;
		   Long conceptId = null;
		   List<Long> availableConceptIds = new ArrayList<Long>(st.conceptIds);
		   if (!completed.isEmpty()) availableConceptIds.removeAll(completed);
		   
		   while (q==null) {
			   // Randomly select a conceptId from the remaining concepts
			   conceptId = availableConceptIds.get(random.nextInt(availableConceptIds.size()));

			   // get all the question keys for the chosen conceptId
			   List<Key<Question>> questionKeys = ofy().load().type(Question.class).filter("assignmentType","Quiz").filter("conceptId",conceptId).keys().list();

			   // Remove any question keys already answered correctly and select one remaining key at random
			   if (!complete) questionKeys.removeAll(st.answeredKeys);
			   else if (questionKeys.isEmpty()) {
				   availableConceptIds.remove(conceptId);
				   continue;
			   }
			   if (availableConceptIds.isEmpty()) return "Sorry, there are no available questins.";
			   
			   Key<Question> questionKey = questionKeys.get(random.nextInt(questionKeys.size()));
			   q = ofy().load().key(questionKey).now();
		   }
		   // At this point we should have a valid question q.
		   int p = random.nextInt();
		   q.setParameters(p);

		   buf.append("<form method=post action=/SmartText>"
				   + "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				   + "<input type=hidden name=ConceptId value='" + conceptId + "' />"
				   + "<input type=hidden name=QuestionId value='" + q.id + "' />"
				   + "<input type=hidden name=Parameter value='" + p + "' />"
				   + "<input type=hidden name=UserRequest value='GradeQuestion' />"
				   + q.print()
				   + "<input type=submit onclick=this.style.opacity=0.2; />"
				   + "</form>");
		   st.armed = true;
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
		   long assignmentId;
		   if (a==null) { // example assignment for anonymous user
			   a = new Assignment();
			   List<Text> texts = ofy().load().type(Text.class).list();
			   for (Text t : texts) {
				   if (t.smartText) {
					   a.textId = t.id;
					   a.chapterNumber=1;
					   break;
				   }
			   }
			   assignmentId=0;
		   } else assignmentId = a.id;
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
		   
		   // determine if this assignment has already been completed
		   boolean completed = true;
		   for (int i=0;i<st.conceptIds.size();i++) {
			   if (st.scores[i]<st.possibleScores[i]) {
				   completed = false;
				   break;
			   }
		   }
		   
		   //  get the index of st.conceptIds that corresponds to the conceptId for this question:
		   int index = st.conceptIds.indexOf(conceptId);
		   boolean isCorrect = q.isCorrect(studentAnswer);
		   buf.append(ajaxJavaScript(user.getTokenSignature()));  // for providing user feedback on the question item
		   
		   if (!st.armed) buf.append("<b>Sorry, it looks like this question has already been scored.</b><br/>");
		   else if (isCorrect) {
			   if (!completed) {
				   st.scores[index]++;
				   st.answeredKeys.add(Key.create(Question.class,q.id));
			   }
			   buf.append("<b>Congratulations! Your answer was correct.</b><br/>");
		   } else {
			   st.missedQuestions[index]++;
			   buf.append("<b>Sorry, your answer was incorrect.</b>"
					   + " (<a href=# onClick=\"document.getElementById('correctAnswer').style='display:block';return false;\">show me</a>)<br/>");
			   buf.append("<div id=correctAnswer style='display:none'><b>Here is the correct answer:</b><br/><br/>" 
					   + q.printAllToStudents(studentAnswer) + "</div><br/>");
		   }

		   if (st.armed && !user.isAnonymous()) {
			   Response r = new Response("SmartText",conceptId,q.id,studentAnswer,q.getCorrectAnswer(),isCorrect?1:0,1,user.getId(),st.id,new Date());
			   ofy().save().entity(r);
		   }

		   int score = 0;
		   int possibleScore = 0;
		   for (int i=0; i<st.scores.length; i++) {
			   score += st.scores[i];
			   possibleScore += st.possibleScores[i];
		   }

		   String continueButton = "<form method=get action=/SmartText>"
			   		+ "<input type=hidden name=UserRequest value=PrintQuestion />"
			   		+ "<input type=hidden name=STTransactionId value=" + st.id + " />"
			   		+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
			   		+ "<button id=btn type=submit style='border:none;color:white;padding:10px 10px;margin:4px 2px;font-size:16px;cursor:pointer;border-radius:10px;background-color:blue;' "
			   		+ "onclick=this.style.opacity=0.2;>Continue to the Next Question</button></form>";

		   if (score==possibleScore) { // finished
			   if (completed) {  // this was just for practice
				   buf.append(continueButton);
			   } else {  // just completed for first time
				   buf.append("You have answered all of the key concept questions for this assignment. Your score is 100%.<br/><br/>");
				   buf.append(fiveStars());
			   }
		   } else if (st.missedQuestions[index]<2) {  // continue to the next question
			   buf.append("This assignment is " + 100*score/possibleScore + "% complete.<br/><br/>");
			   buf.append(continueButton);
		   } else { // missed 2 questions; go back to the text
			   Concept c = ofy().load().type(Concept.class).id(conceptId).safe();
			   buf.append("You missed 2 questions on the key concept: <b>" + c.title + "</b>.<br/><br/>");
				//	   + "Please return to the textbook and review this chapter. ");
			   buf.append("<a href='" + chapter.url + "' target=_blank>"
			   		+ "<button style='border: none; color: white; padding: 10px 10px; margin: 4px 2px; font-size: 16px; cursor: pointer; border-radius: 10px; background-color: blue;'>"
			   		+ "To continue, click here to review this chapter in the textbook."
			   		+ "</button>"
			   		+ "</a><br/>");
			   if (completed) buf.append("Don't worry. Your score on this assignment is still 100%.<br/><br/>");
			   else buf.append("Don't worry. You can still earn 100% by relaunching this assignment after completing your review.<br/><br/>");
			   st.missedQuestions[index]=0; // reset the missed questions for this concept only
			   ofy().delete().entity(user).now();
		   }
		   st.armed = false;
		   
		   if (!completed && !user.isAnonymous()) {
			   st.graded = new Date();
			   ofy().save().entity(st).now();
			   QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(assignmentId)).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue   
		   } else ofy().save().entity(st).now();

	   } catch (Exception e) {
		   buf.append("Error: " + (e.getMessage()==null?e.toString():e.getMessage()) + "<br/>" + debug.toString());
	   }
	   
	   buf.append(ajaxJavaScript(user.getTokenSignature())); // load javascript for AJAX problem reporting form
		
	   return buf.toString();
   }

	String ajaxJavaScript(String signature) {
		return "<SCRIPT TYPE='text/javascript'>\n"
		+ "function ajaxSubmit(url,id,studentAnswer,note,email) {\n"
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
		+ "  url += '&QuestionId=' + id + '&sig=" + signature + "&Notes=' + note + '&Email=' + email + '&StudentAnswer=' + studentAnswer;\n"
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

	static String reviewScores(User user, Assignment a) {
		StringBuffer buf = new StringBuffer();
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";

		try {
			if (a.lti_nrps_context_memberships_url==null) throw new Exception("No Names and Roles Provisioning support.");

			buf.append("<h3>" + a.assignmentType + " - " + a.title + "</h3>");
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
				keys.put(id,Key.create(Key.create(User.class,Subject.hashId(platform_id+id)),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>CV Score</th></tr>");
			int i=0;
			boolean synched = true;
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				String s = scores.get(entry.getKey());
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				//String forUserId = platform_id + entry.getKey();  // only send hashed values through links
				i++;
				buf.append("<tr><td>" + i + ".&nbsp;</td>"
						+ "<td>" + entry.getValue()[1] + "</td>"
						+ "<td>" + entry.getValue()[2] + "</td>"
						+ "<td>" + entry.getValue()[0] + "</td>"
						+ "<td align=center>" + (s == null?" - ":s + "%") + "</td>"
						+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td>"
						//+ "<td align=center><a href=/Homework?UserRequest=Review&sig=" + user.getTokenSignature() + "&ForUserId=" + forUserId + "&ForUserName=" + entry.getValue()[1].replaceAll(" ","+") + ">show</a></td>"
						+ "</tr>");
				// Flag this score set as unsynchronizde only if there is one or more non-null ChemVantage Learner score that is not equal to the LMS score
				// Ignore Instructor scores because the LMS often does not report them, and ignore null cvScore entities because they cannot be reported.
				synched = synched && (!"Learner".equals(entry.getValue()[0]) || (cvScore!=null?String.valueOf(cvScore.getPctScore()).equals(s):true));
			}
			buf.append("</table><br/>");
			if (!synched) {
				buf.append("If any of the Learner scores above are not synchronized, you may use the button below to launch a background task " 
						+ "where ChemVantage will resubmit them to your LMS. This can take several seconds to minutes depending on the "
						+ "number of scores to process. Please note that you may have to adjust the settings in your LMS to accept the "
						+ "revised scores. For example, in Canvas you may need to change the assignment settings to Unlimited Submissions. "
						+ "This may also cause the submission to be counted as late if the LMS assignment deadline has passed.<br/>"
						+ "<form method=post action=/Homework >"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
						+ "<input type=submit value='Synchronize Scores' />"
						+ "</form>");
			}
			return buf.toString();
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	
		
	}
	
	boolean synchronizeScores(User user,Assignment a,HttpServletRequest request) {
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
				keys.put(id,Key.create(Key.create(User.class,hashedUserId),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				if (cvScore==null) continue;
				String s = scores.get(entry.getKey());
				if (String.valueOf(cvScore.getPctScore()).equals(s)) continue;  // the scores match (good!)
				QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(platform_id + entry.getKey(),"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	String orderResponses(String[] answers) {
		if (answers==null) return "";
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}


}
