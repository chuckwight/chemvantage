<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*"%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />
<meta http-equiv='Pragma' content='no-cache' />
<meta http-equiv='Expires' content='0' /><meta http-equiv='Content-type' content='text/html;charset=iso-8859-1'>
<meta name='Description' content='An online quiz and homework site'>
<meta name='Keywords' content='chemistry,learning,online,quiz,homework,video,textbook,open,education'>
<meta name='msapplication-config' content='none'/><link rel='icon' type='image/png' href='/favicon.png'>
<link rel='icon' type='image/vnd.microsoft.icon' href='/favicon.ico'>
<title>ChemVantage Quiz</title>
<SCRIPT>
function toggleTimers() {
	var timer0 = document.getElementById('timer0');
	var timer1 = document.getElementById('timer1');
	var ctrl0 = document.getElementById('ctrl0');
	var ctrl1 = document.getElementById('ctrl1');
	if (timer0.style.display=='') {
		timer0.style.display='none';timer1.style.display='none';
		ctrl0.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';
		ctrl1.innerHTML='<a href=javascript:toggleTimers()>show timers</a><p>';
	} else {
		timer0.style.display='';
		timer1.style.display='';
		ctrl0.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';
		ctrl1.innerHTML='<a href=javascript:toggleTimers()>hide timers</a><p>';
	}
}
var seconds;
var minutes;
var oddSeconds;
var endMillis;
function countdown() {
	var nowMillis = new Date().getTime();
	var seconds=Math.round((endMillis-nowMillis)/1000);
	var minutes = seconds<0?Math.ceil(seconds/60.):Math.floor(seconds/60.);
	var oddSeconds = seconds%60;
	for (i=0;i<2;i++) document.getElementById('timer'+i).innerHTML='Time remaining: ' + minutes + ' minutes ' + oddSeconds + ' seconds.';
	if (seconds==30) alert('30 seconds remaining');
	if (seconds < 0) document.Quiz.submit();
	setTimeout('countdown()',1000);
}
function startTimers(m) {
	endMillis = m;
	countdown();
}
function confirmSubmission() {
	var elements = document.getElementById('quizForm').elements;
	var nAnswers;
	var i;
	var checkboxes;
	var lastCheckboxIndex;
	nAnswers = 0;
	for (i=0;i<elements.length;i++) {
		if (isNaN(elements[i].name)) continue;
		if (elements[i].type=='text' && elements[i].value.length>0) nAnswers++;
		else if (elements[i].type=='radio' && elements[i].checked) nAnswers++;
		else if (elements[i].type=='checkbox') {
			checkboxes = document.getElementsByName(elements[i].name);
			lastCheckboxIndex = i + checkboxes.length - 1;
			for (j=0;j<checkboxes.length;j++) if (checkboxes[j].checked==true) {
				nAnswers++;
				i = lastCheckboxIndex;
				break;
			}    
		}  
	}  
	if (nAnswers<10) return confirm('Submit this quiz for scoring now? ' + (10-nAnswers) + ' answers may be left blank.');
	else return true;
}
function showWorkBox(qid) {}
</SCRIPT>

</head>

<body bgcolor=#ffffff text=#000000 link=#0000cc vlink=#551a8b alink=#ff0000 topmargin=3 marginheight=3>

<%
	User user = User.getUser(request.getParameter("sig"));
	Assignment qa = null;
	long topicId = 0L;
	long assignmentId = 0L;
	try {
		assignmentId = user.getAssignmentId(); // should be non-zero for LTI user
		if (assignmentId > 0) {
			qa = ofy().load().type(Assignment.class).id(assignmentId).now();
			topicId = qa.getTopicId();
		} else { // get the requested topicId for anonymous user
			topicId = Long.parseLong(request.getParameter("TopicId"));
		}
	} catch (Exception e) { // alternative process for anonymous user
		response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
	}
	Topic topic = ofy().load().type(Topic.class).id(topicId).safe();
	Date now = new Date();
	
	// Check to see if this user has any pending quizzes on this topic:
	Date t15minAgo = new Date(now.getTime()-15*60000);  // 15 minutes ago
	QuizTransaction qt = ofy().load().type(QuizTransaction.class).filter("userId",user.getId()).filter("topicId",topicId).filter("graded",null).filter("downloaded >",t15minAgo).first().now();
	String lis_result_sourcedid = user.getLisResultSourcedid();
	if (qt == null || qt.getGraded() != null) {
		qt = new QuizTransaction(topicId,topic.getTitle(),user.getId(),now,null,0,assignmentId,0,user.getLisResultSourcedid());
		ofy().save().entity(qt).now();  // creates a long id value to use in random number generator
	} else if (qt.getLisResultSourcedid() == null && lis_result_sourcedid != null) {
		qt.putLisResultSourcedid(lis_result_sourcedid);
		ofy().save().entity(qt);
	}
	String announcement = Subject.getSubject().getAnnouncement();
	if (announcement != null && !announcement.isEmpty()) {
%>
<FONT COLOR=RED> <%= announcement %> </FONT><br>
<% } %>

<h2>Quiz - <%= topic.getTitle() %></h2>

<% if (user.isInstructor()) { %>
<mark>As the course instructor you may <a href='/Quiz?UserRequest=AssignQuizQuestions&sig=<%= user.getTokenSignature() %>'>customize this assignment</a>.</mark>
<p>
<% } %>

<% if (!user.isAnonymous()) { %>
Quiz Rules<OL>
  <LI>Each quiz must be completed within 15 minutes of the time when it is first downloaded.</LI>
  <LI>You may repeat quizzes as many times as you wish, to improve your score.</LI>
  <LI>ChemVantage always reports your best score on this assignment to your class LMS.</LI> 
</OL>
<% } %>

<div id='timer0' style='color: red'></div><div id=ctrl0 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>

<FORM NAME=Quiz id=quizForm METHOD=POST ACTION=Quiz onSubmit='return confirmSubmission()'>
  <INPUT TYPE=HIDDEN NAME='sig' VALUE='<%= user.getTokenSignature() %>'>
  <INPUT TYPE=HIDDEN NAME='AssignmentId' VALUE='<%= assignmentId %>'>
  <input type=hidden name='QuizTransactionId' value='<%= qt.getId() %>'>
  <input type=hidden name='TopicId' value='<%= topicId %>'>
  <input type=submit value='Grade This Quiz'>
  
  <%
    //create a set of available questionIds either from the group assignment or from the datastore
    List<Key<Question>> questionKeys = null;
    try { // check for assigned questions
    	questionKeys = qa.getQuestionKeys();
    } catch (Exception e) { // no assignment exists
    	questionKeys = ofy().load().type(Question.class).filter("assignmentType", "Quiz").filter("topicId", topicId).filter("isActive", true).keys().list();
    }

    // Randomly select the questions to be presented, eliminating each from questionSet as they are printed
    Random rand = new Random(); // create random number generator to select quiz questions
    rand.setSeed(qt.getId()); // random number generator seeded with QuizTransaction id value
    int possibleScore = 0;
    int nQuestions = questionKeys.size() > 10 ? 10 : questionKeys.size();

    int i = 0;
  %>
  <OL>  <!-- insert questions here --> 
  <%
   	Map<Key<Question>, Question> quizQuestions = new HashMap<Key<Question>, Question>();
   	quizQuestions.putAll(ofy().load().keys(questionKeys));
   	while (i < nQuestions && questionKeys.size() > 0) {
   		Key<Question> k = questionKeys.remove(rand.nextInt(questionKeys.size()));
   		Question q = quizQuestions.get(k);
   		if (q == null)
   			continue; // this catches cases where an assigned question no longer exists

   		// by this point we should have a valid question
   		i++; // this counter keeps track of the number of questions presented so far
   		possibleScore += q.getPointValue();
   		// the parameterized questions are seeded with a value based on the ids for the quizTransaction and the question
   		// in order to make the value reproducible for grading but variable for each quiz and from one question to the next
   		long seed = Math.abs(qt.getId() - q.getId());
   		if (seed == -1)
   			seed--; // -1 is a special value for randomly seeded Random generator; avoid this (unlikely) situation
   		q.setParameters(seed); // the values are subtracted to prevent (unlikely) overflow
   %>
   		<li> <%= q.print() %> <br></li>
   <%
   	}
   	qt.putPossibleScore(possibleScore);
	ofy().save().entity(qt);
   %>	
  </OL>

  <div id='timer1' style='color: red'></div><div id=ctrl1 style='font-size:50%;color:red;'><a href=javascript:toggleTimers()>hide timers</a><p></div>
  
  <input type=submit value='Grade This Quiz'>
</FORM>

<SCRIPT>
startTimers(<%= new Date(qt.getDownloaded().getTime() + (qa==null?900000:qa.timeAllowed*1000)).getTime() %>);
</SCRIPT>

<%= Home.footer %>