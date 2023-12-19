<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*"%>

<%
long encrypt = new Date().getTime() + 5400000L;
try {
	long mask = 0xfffL;
	long iv = encrypt & mask;
	long code;
	for (int i=0;i<3;i++) { 
		code = (mask & encrypt) << 12;
		encrypt = encrypt ^ code;
		mask = mask << 12;
	}
	mask = 0xfffffffff000L;
	encrypt = encrypt ^ (new Random(iv).nextLong() & mask); 
	mask = 0xfffffffffL;
	code = (encrypt & mask) << 12;
	encrypt = encrypt ^ code;  
} catch (Exception e) {
	encrypt = 0L;
}
String sig = Long.toHexString(encrypt);
%>

<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />
<meta http-equiv='Pragma' content='no-cache' />
<meta http-equiv='Expires' content='0' /><meta http-equiv='Content-type' content='text/html;charset=iso-8859-1'>
<meta name='Description' content='An online quiz and homework site'>
<meta name='Keywords' content='chemistry,learning,online,quiz,homework,video,textbook,open,education'>
<meta name='msapplication-config' content='none'/><link rel='icon' type='image/png' href='/favicon.png'>
<link rel='icon' type='image/png' href='/images/favicon.png' />
<title>ChemVantage Assignments</title>
<style>
 body     {background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 1000px}
 div.top  {padding:30px;}
 div.seg1 {padding-left: 30px; padding-bottom: 30px; overflow: auto}
 div.seg2 {padding-left: 30px; padding-bottom: 30px; padding-right: 20px; overflow: auto; background-color: #DDDDDD;}
 img.float {float: right; vertical-align: text-top; margin: 20px; max-width: 40%;}
</style>
</head>

<body>

<div class='top'>
<a href="/" style='text-decoration: none;'>
<img style='float:left;vertical-align: middle;width:60px;' src=/images/logo_sq.png alt='ChemVantage logo' />
<span style='color: navy; font-size: 2em; font-weight: bold;'>ChemVantage</span><br/>
</a>
An Open Educational Resource for General Chemistry<br/>
</div>

<main><h1 style='display: none'>About ChemVantage</h1>
<div class='seg1'>
<br/><b>ChemVantage Assignments</b><br/><br/>
Students don't do optional assignments, but most students will actually work hard on them if
<ul>
<li>they receive immediate feedback on the assignment,</li>
<li>they feel that they are progressing in their learning, and</li>
<li>the assignment counts for even a small portion of the course grade,</li>
</ul>
Immediate feedback helps them to correct mistakes and guides their learning. That's where ChemVantage fits in. Our 
assignments are designed to challenge and engage students for extended periods of time, allowing the correction of errors and 
repeated submissions to improve their score. ChemVantage assignments are designed to be learning tools, not assessment 
tools, so a good practice is to make each quiz or homework assignment worth no more than about 1-2% of the overall course grade.
</div>

<div class='seg2'>
<img class='float' src='images/img04.jpg' alt='boys studying'/>
<br/><b>Quizzes</b><br/><br/>
Each ChemVantage quiz consists of 10 short-answer question items randomly selected from a bank of 60-80 items. Question types
include multiple choice, true/false, checkbox, fill-in-the-blank, and numeric answers. The quizzes are designed to 
ensure that students have completed the reading assignment before coming to class. Students are permitted to repeat the 
quizzes (in the spirit of being a learning tool rather than an assessment). However, it is unlikely that any two quizzes 
will contain exactly the same set of questions. Of course, you can configure your LMS to restrict the number of retries. 
In my classes, I encourage students to collaborate on quizzes as a way of encouraging them to form study groups. A group 
of three students will have to take at least 3 quizzes together for everyone to get credit for completing the assignment.
<br/><br/>

<div style='text-align: center;'><a href='/Quiz?sig=<%= sig %>'>Try a sample quiz</a></div>
</div>

<div class='seg1'>
<br/><b>Homework</b><br/><br/>
Homework assignments give students practice in solving quantitative problems having numeric answers. Answers are scored correct 
if the solution agrees with the solution found in the database to within a specified tolerance (indicated for each problem). 
For many problems, answers must be expressed with the appropriate number of significant figures. Students are allowed to rework 
incorrectly solved problems and resubmit the answers. Nearly all of the problems are parameterized, so the correct answer is 
different for each student. This makes them suitable for collaboration and working in teams, because even though the group may 
work to find a correct algorithm for solving a problem, each student will have to use the algorithm to find the detailed solution 
to his or her own problem. If students get stuck and needs help, they can email a link to an instructor or teaching assistant that 
shows their work and previous submissions.<br/><br/>

<div style='text-align: center;'><a href='/Homework?sig=<%= sig %>'>Try a sample homework</a></div>
</div>

<div class='seg2'>
<br/><b>SmartText Reading Assignments</b><br/><br/>
Finding it hard to get students to read the textbook? ChemVantage reading assignments incorporate a sequence of question items 
on key concepts related to the assigned chapter. If a student misses 2 questions on any key concept, they are sent back to review 
the chapter before proceeding with the assignment. After correctly answering 2 questions on each key concept, the assignment gets 
full credit but is still available to students who wish to practice their skills. SmartText assignments are available for the 
following textbooks:<ul>
<li>Chemistry 2e (OpenStax)</li>
<li>Chemistry: Atoms First 2e (OpenStax)</li>
<li>Chemistry: A Molecular Approach (Pearson)</li>
</ul>
The keys to success for this type of assignment are 1) colocating the reading assignment with a formative assessment, and 
2) assigning at least a small number of points for completing the task.
<br/><br/>

<div style='text-align: center;'><a href='/SmartText?sig=<%= sig %>'>See a sample SmartText assignment</a></div>
</div>

<div class='seg1'>
<!--  <img class='float' src='images/img05.jpg' alt='student taking notes'/> -->
<br/><b>Video lectures</b><br/><br/>
Some of the more important lessons in the text have been captured as 10-minute instructional videos. Most of the videos have short 
(two-question) quizzes embedded in the video to ensure that students are watching and comprehending. You can assign these to students 
for 3-5 points. Even if the video has no embedded quizzes, ChemVantage will only award the points if the student finishes the video 
to the end.<br/><br/>

<div style='text-align: center;'><a href='/VideoQuiz?sig=<%= sig %>'>Try a sample video</a></div>
</div>

<div class='seg2'>
<img class='float' src='images/img05.jpg' alt='student taking notes'/>
<br/><b>Practice Exams</b><br/><br/>
Students can take timed practice exams that draw questions from any group of 3 or more topics.  Each exam is designed to take 60 minutes 
or less to complete, and includes a selection of 10 quiz questions worth 2 points each, 5 of the easier homework problems at 10 points each, 
and 2 more challenging homework problems worth 15 points each. The scores on each exam are disaggregated by topic to reveal the student's 
areas of strength and weakness.  Practice exams may be repeated as often as desired. Questions are drawn at random from the database, 
and the numeric questions are parameterized, making it extremely unlikely that a student will ever get two identical practice exams. 
Instructors may (optionally) review the exam results and award partial credit.<br/><br/>

<div style='text-align: center;'><a href='/PracticeExam?sig=<%= sig %>'>Try a sample practice exam</a></div>
</div>

<div class='seg1'>
<br/><b>In-class polls</b><br/><br/>
Clickers have become popular in recent years, but they increase the cost of the course for students and faculty. A ChemVantage poll 
can be set up in a few minutes from inside your LMS. You may select any of the thousands of available quiz, homework or exam questions, 
or you can create your own question. Students log into the LMS using their phone or laptop in class to respond to the question items. 
The responses are then displayed on the classroom computer so students can see how their response compared with the responses of others. 
<br/>

<div style='text-align: center;'><a href='mailto:admin@chemvantage.org'>Schedule a free demo in-class poll</a></div>
</div>

<div class='seg2'>
<br/><b>Placement Exams</b><br/><br/>
Each placement exam consists of 40 question items (30 two-point items plus 10 four-point items). Question items are randomly selected 
from three different topic ares in order to assess the student's<ul>
<li>knowledge of basic chemistry</li>
<li>essential math skills</li> 
<li>ability to interpret word problems</li> 
</ul>
The results are returned to the LMS to use as a tool for advising the student whether to take General Chemistry or perhaps a lower-level 
course to get the proper preparation. As with the other assignment types, students may be permitted to repeat the placement exam, depending 
on the settings in the LMS. However, ChemVantage does not charge for repeat exams. The advantage of allowing students to 
repeat the placement exam is to get a sense of how determined the student is to get into the course. After all, if a student is willing to 
work really hard to be admitted to the course and ultimately scores well on the exam, wouldn't you want to have that student in your 
course anyway?

<div style='text-align: center;'><a href='mailto:admin@chemvantage.org'>Schedule a free demo placement exam</a></div>
</div>

</main>

<footer><hr/><img style='padding-left: 5px; vertical-align: middle;width:30px' src=/images/logo_sq.png alt='ChemVantage logo' />&nbsp;
<a href=/index.html style='text-decoration: none;'><span style='color: navy;font-weight: bold;'>ChemVantage</span></a> |  
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a></footer>

</body>
</html>