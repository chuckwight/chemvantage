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
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="images/logo_sq.png">
    <!-- Fav-Icon -->
    <title>ChemVantage | Examples</title>
    <!-- Title -->
    <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;800;900&family=Shantell+Sans:wght@300;400;500;600;700;800&display=swap" rel="stylesheet"/>
    <!-- Font Family -->
    <link rel="stylesheet" href="css/style.css">
    <!-- Main Style Sheet -->
    <link rel="stylesheet" href="css/responsive.css">
    <!-- Responsive Style Sheet -->
</head>

<body>
    <a href=#main class="skip-to-main-content-link">Skip to main content</a>
    <!-- Hidden elements for accessibility compliance -->
    <h1 style="height:1px;width:1px;">&nbsp;</h1><h2 style="height:1px;width:1px;">&nbsp;</h2>
    <!-- Webpage Header Start -->
    <header class="webpage-header">
        <div class="container">
            <div class="d-flex justify-between align-center">
                <div class="w-0">
                    <a href="./index.html">
                        <img src="images/logo.png" class="webpage-logo" alt="website logo"/>
                    </a>
                </div>
                <div class="w-80">
                    <nav class="header-nav d-flex flex-end align-center gap-3">
                        <div class="nav-list">
                            <ul>
                                <li>
                                    <a href="./index.html" class="nav-link">Home</a>
                                </li>
                                <li>
                                    <a href="./about.html" class="nav-link">About Us</a>
                                </li>
                                <li>
                                    <a href="./index.html#assignments" class="nav-link">Assignments</a>
                                </li>
                                <li>
                                    <a href="./index.html#itembank" class="nav-link">Items</a>
                                </li>
                                <li>
                                    <a href="./index.html#pricing" class="nav-link">Pricing</a>
                                </li>
                            </ul>
                            <button class="close">&times;</button>
                        </div>
                        <a href="https://www.chemvantage.org/install.html" target="_blank" aria-label="Opens new tab" class="header-action">
                            Get Started
                        </a>
                        <button class="solid-bar">&#9776;</button>
                    </nav>
                </div>
            </div>
        </div>
    </header>
    <!-- Webpage Header End -->

    <!-- Webpage BTT Start -->
    <div class="btt">
        <img src="images/up-arrow.png" alt="up-arrow"/>
    </div>
    <!-- Webpage BTT End -->

    <main id="main">
    <!-- Examples Content Start -->
    <section>
        <div class="section-padding example-content">
            <div class="container">
                <div class="section-heading">
                    <h3>Examples</h3>
                </div>
                <div class="section-heading-top-gap">
                    <h4>ChemVantage Assignments</h4>
                    <p>
                        Students don't do optional assignments, but most students will actually work hard on them if
                    </p>
                    <ul>
                        <li>
                            they receive immediate feedback on the assignment,
                        </li>
                        <li>
                            they feel that they are progressing in their learning, and
                        </li>
                        <li>
                            the assignment counts for even a small portion of the course grade,
                        </li>
                    </ul>
                    <p>
                        Immediate feedback helps them to correct mistakes and guides their learning. That's where ChemVantage fits in. Our assignments are designed to challenge and engage students for extended periods of time, allowing the correction of errors and repeated submissions to improve their score. ChemVantage assignments are designed to be learning tools, not assessment tools, so a good practice is to make each quiz or homework assignment worth no more than about 1-2% of the overall course grade.
                    </p>
                </div>
            </div>
        </div>
        <div class="section-padding bg-off-white">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/1.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            Quizzes
                        </h5>
                        <p>
                            Each ChemVantage quiz consists of 10 short-answer question items randomly selected from a bank of 60-80 items. Question types include multiple choice, true/false, checkbox, fill-in-the-blank, and numeric answers. The quizzes are designed to ensure that students have completed the reading assignment before coming to class. Students are permitted to repeat the quizzes (in the spirit of being a learning tool rather than an assessment). However, it is unlikely that any two quizzes will contain exactly the same set of questions. Of course, you can configure your LMS to restrict the number of retries. In my classes, I encourage students to collaborate on quizzes as a way of encouraging them to form study groups. A group of three students will have to take at least 3 quizzes together for everyone to get credit for completing the assignment.
                        </p>
                        <a href="/Quiz?sig=<%= sig %>" target="_blank" aria-label="Opens new tab" class="btn btn-two">
                            Try a Sample Quiz
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/2.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            Homework
                        </h5>
                        <p>
                            Homework assignments give students practice in solving quantitative problems having numeric answers. Answers are scored correct if the solution agrees with the solution found in the database to within a specified tolerance (indicated for each problem). For many problems, answers must be expressed with the appropriate number of significant figures. Students are allowed to rework incorrectly solved problems and resubmit the answers. Nearly all of the problems are parameterized, so the correct answer is different for each student. This makes them suitable for collaboration and working in teams, because even though the group may work to find a correct algorithm for solving a problem, each student will have to use the algorithm to find the detailed solution to his or her own problem. If students get stuck and needs help, they can email a link to an instructor or teaching assistant that shows their work and previous submissions.
                        </p>
                        <a href="/Homework?sig=<%= sig %>" target="_blank" aria-label="Opens new tab" class="btn btn-two">
                            Try a Sample Homework
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding bg-off-white">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/3.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            SmartText Reading Assignments
                        </h5>
                        <p>
                            Finding it hard to get students to read the textbook? ChemVantage reading assignments incorporate a sequence of question items on key concepts related to the assigned chapter. If a student misses 2 questions on any key concept, they are sent back to review the chapter before proceeding with the assignment. After correctly answering 2 questions on each key concept, the assignment gets full credit but is still available to students who wish to practice their skills. SmartText assignments are available for the following textbooks:
                        </p>
                        <ul>
                            <li>Chemistry 2e (OpenStax)</li>
                            <li>Chemistry: Atoms First 2e (OpenStax)</li>
                            <li>Chemistry: A Molecular Approach (Pearson)</li>
                        </ul>
                        <p>
                            The keys to success for this type of assignment are 1) colocating the reading assignment with a formative assessment, and 2) assigning at least a small number of points for completing the task. 
                        </p>
                        <a href="/SmartText?sig=<%= sig %>" class="btn btn-two">
                            See a sample SmartText assignment
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/4.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            Video lectures
                        </h5>
                        <p>
                            Some of the more important lessons in the text have been captured as 10-minute instructional videos. Most of the videos have short (two-question) quizzes embedded in the video to ensure that students are watching and comprehending. You can assign these to students for 3-5 points. Even if the video has no embedded quizzes, ChemVantage will only award the points if the student finishes the video to the end.
                        </p>
                        <a href="/Video.jsp?VideoId=5691392549453824&sig=<%= sig %>" target="_blank" aria-label="Opens new tab" class="btn btn-two">
                            Try a Sample Video
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding bg-off-white">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/5.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            Practice Exams
                        </h5>
                        <p>
                            Students can take timed practice exams that draw questions from any group of 3 or more topics. Each exam is designed to take 60 minutes or less to complete, and includes a selection of 10 quiz questions worth 2 points each, 5 of the easier homework problems at 10 points each, and 2 more challenging homework problems worth 15 points each. The scores on each exam are disaggregated by topic to reveal the student's areas of strength and weakness. Practice exams may be repeated as often as desired. Questions are drawn at random from the database, and the numeric questions are parameterized, making it extremely unlikely that a student will ever get two identical practice exams. Instructors may (optionally) review the exam results and award partial credit.
                        </p>
                        <a href="/PracticeExam?sig=<%= sig %>" target="_blank" aria-label="Opens new tab" class="btn btn-two">
                            Try a Sample Practice Exam
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/6.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            In-class polls
                        </h5>
                        <p>
                            Clickers have become popular in recent years, but they increase the cost of the course for students and faculty. A ChemVantage poll can be set up in a few minutes from inside your LMS. You may select any of the thousands of available quiz, homework or exam questions, or you can create your own question. Students log into the LMS using their phone or laptop in class to respond to the question items. The responses are then displayed on the classroom computer so students can see how their response compared with the responses of others.
                        </p>
                        <a href="mailto:admin@chemvantage.org" class="btn btn-two">
                            Schedule a Free Demo
                        </a>
                    </div>
                </div>
            </div>
        </div>
        <div class="section-padding bg-off-white">
            <div class="container">
                <div class="d-flex align-center example-content">
                    <div class="w-40">
                        <img src="images/examples/7.jpg" class="w-100 section-image" alt="examples"/>
                    </div>
                    <div class="w-60">
                        <h5>
                            Placement Exams
                        </h5>
                        <p>
                            Each placement exam consists of 40 question items (30 two-point items plus 10 four-point items). Question items are randomly selected from three different topic ares in order to assess the student's
                        </p>
                        <ul>
                            <li>knowledge of basic chemistry</li>
                            <li>essential math skills</li>
                            <li>ability to interpret word problems</li>
                        </ul>
                        <p>
                            The results are returned to the LMS to use as a tool for advising the student whether to take General Chemistry or perhaps a lower-level course to get the proper preparation. As with the other assignment types, students may be permitted to repeat the placement exam, depending on the settings in the LMS. However, ChemVantage does not charge for repeat exams. The advantage of allowing students to repeat the placement exam is to get a sense of how determined the student is to get into the course. After all, if a student is willing to work really hard to be admitted to the course and ultimately scores well on the exam, wouldn't you want to have that student in your course anyway?
                        </p>
                        <a href="mailto:admin@chemvantage.org" class="btn btn-two">
                            Schedule a Free Demo
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </section>
    <!-- Examples Content End -->
  </main>
  
    <!-- Webpage Footer Start -->
    <footer>
        <div class="container">
            <div class="d-flex footer-content justify-between align-center">
                <div class="w-40">
                    <ul class="social">
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/facebook.png" alt="facebook"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/instagram.png" alt="instagram"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/twitterx.png" alt="twitterx"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/linkedin.png" alt="linkedin"/>
                            </a>
                        </li>
                    </ul>
                </div>
                <div class="w-40 text-end">
                    <ul class="contact">
                        <li>
                            <a href="tel:+18012438242" title="phone">
                                +1 (801)243-8242
                            </a>
                        </li>
                        <li>
                            <a href="mailto:admin@chemvantage.org" title="email">
                                admin@chemvantage.org
                            </a>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        <div class="m-container">
            <div class="d-flex align-center footer-btm-content">
                <div class="w-40">
                    <span class="copy">
                        &copy; 2010 - <script>document.write(new Date().getFullYear())</script> ChemVantage LLC
                    </span>
                </div>
                <div class="w-60 text-end">
                    <ul>
                        <li>
                            <a href="./terms_and_conditions.html" target="_blank" aria-label="Opens new tab">
                                Terms and Conditions
                            </a>
                        </li>
                        <li>
                            <a href="./privacy_policy.html" target="_blank" aria-label="Opens new tab">
                                Privacy Policy
                            </a>
                        </li>
                        <li>
                            <a href="./copyright.html" target="_blank" aria-label="Opens new tab">
                                Copyright
                            </a>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
    <!-- Webpage Footer End -->
    
    <script src="js/script.js"></script>
    <!-- Main Script Sheet -->
</body>
</html>
