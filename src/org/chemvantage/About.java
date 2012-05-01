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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class About extends HttpServlet {

	private static final long serialVersionUID = 137L;
	public static String announcement = "";

	public String getServletInfo() {
		return "This servlet provides information, terms and conditions for using ChemVantage.org.";
	}

	public static String about = "<TABLE><TR><TD><img src=/images/CVLogo_thumb.jpg alt='ChemVantage Logo'></TD>"
		+ "<TD>Welcome to<br><b><FONT SIZE=+3>ChemVantage.org</FONT></b></TD></TR></TABLE>"
		+ "<h3>Who we are</h3>"
		+ "ChemVantage LLC was founded in 2010 to provide Open Education " 
		+ "Resources for teaching and learning General Chemistry. The founder and chief software architect of "
		+ "ChemVantage is Chuck Wight, who has taught General Chemistry at the University of Utah since 1984."
		+ "<h3>Subject material</h3>"
		+ "The database is currently populated with more than 2000 quiz and homework questions in General Chemistry. "
		+ "<h3>Why we created this site</h3>"
		+ "Chemistry and many other technical disciplines are taught from a problem-solving approach. Lots of "
		+ "practice is required to build a solid foundation of skills for using knowledge to solve scientific "
		+ "problems. We create web sites that provide immediate feedback so that students can learn "
		+ "problem-solving skills through repeated attempts without having to wait long periods of time for "
		+ "their assignments to be graded by human instructors."
		+ "<h3>Learning versus testing</h3>"
		+ "Usually, web sites with dynamic content like this one are best used as learning tools, not assessment "
		+ "or testing tools.  For this reason, we have configured the software to allow students to submit "
		+ "proposed solutions to the problems as often as they want, in order to improve their scores. The objective "
		+ "is for students to use the feedback to correct their errors prior to the deadline for the assignment."
		+ "<h3>Quizzes</h3>"
		+ "The quizzes presented here are designed to encourage students to read and understand the textbook used "
		+ "for the course. The questions are based on material presented in most introductory General Chemistry "
		+ "textbooks.  The question items have straighforward answers not requiring detailed calculations.  "
		+ "Questions are presented in several different formats including multiple choice, true/false, checkbox, "
		+ "fill-in-the-blank, and numerical answers.  Each quiz consists of 10 questions drawn at random from a "
		+ "database containing many more questions.  In the process of taking several quizzes to improve their "
		+ "scores, students will likely encounter some questions repeated from earlier quizzes; however, it is "
		+ "unlikely that any two quizzes will contain exactly the same set of questions in the same order.  By "
		+ "assigning quizzes to be completed prior to the lecture, instructors can be assured that their students "
		+ "have some exposure to the material.  This allows for more discussion, dialog and engagement in the "
		+ "classroom, and it makes learning more fun (really!)."
		+ "<h3>Video Lectures</h3>"
		+ "A series of 10-minute video lectures on a variety of General Chemistry topics is avialable from the "
		+ "Home Page.  The videos are designed to reinforce the main points presented in many textbooks." 
		+ "<h3>Homework</h3>"
		+ "The homework assignments are designed to give students practice in solving quantitative problems having "
		+ "numerical answers. Each student can download the assignment, work the problems and submit the answers "
		+ "online. Answers are judged to be correct if the numerical solution agrees with the solution found in "
		+ "the database to within a specified tolerance (indicated for each problem).  The site informs students "
		+ "which answers are correct, so that they can rework incorrectly solved problems and resubmit the answers. "
		+ "Each problem is based on a template that draws parameters at random, so the correct answer is different "
		+ "for each student. This makes them suitable for collaboration and working in groups, because even though "
		+ "the group may work to find a correct algorithm for solving a problem, each student will have to use the "
		+ "algorithm to find the detailed solution to his or her own problem."
		+ "<h3>Practice Exams</h3>"
		+ "Students can take timed practice exams that draw questions from any group of 3 or more topics.  Each "
		+ "exam is designed to take 60 minutes or less to complete, and includes a selection of 10 quiz questions "
		+ "worth 2 points each, 5 of the easier homework problems at 10 points each, and 2 more challenging homework "
		+ "problems worth 15 points each.  On the student's Portfolio Page, the scores are disaggregated by topic to "
		+ "reveal the student's areas of strength and weakness.  Practice exams may be repeated as often as desired. "
		+ "The numerical questions are parameterized, making it extremely unlikely that a student will ever get two "
		+ "identical practice exams."
		+ "<h3>Portfolio Page</h3>"
		+ "Each student can access a page that gives the score on each quiz and homework assignment.  Students "
		+ "who are members of a group have a scores page associated with group assignments. There is a separate "
		+ "portfolio page that provides a summary of all activities completed in ChemVantage, including videos "
		+ "watched, quizzes completed, homework problems solved, and practice exams completed.  Scores on the "
		+ "practice exams are disaggregated to reveal students' strengths and weakness by topic.<p>"
		+ "Instructors can view the group scores of each student in his/her class or group.</p>";

	public static String premium = "<a NAME=accounts></a>"
		+ "<h3>ChemVantage Basic and Premium Accounts</h3>"
		+ "Anyone can obtain an individual account at ChemVantage.org at no cost. "
		+ "All of the functionality required for completing quiz and homework assignments "
		+ "is included in the free basic account.<p>In order to use ChemVantage as part of a "
		+ "group or class, you must first upgrade to a premium account. Account upgrades may be "
		+ "purchased by individual users for $4.99, or institutions may purchase premium account "
		+ "seats on behalf of their students for $2.00/seat in quantities of 50 seats or more. "
		+ "Upgrades for instructors and institutional domain admins are always free.<p>"
		+ "<a href=Upgrade>Click here to upgrade your account</a><p>"
		+ "All ChemVantage accounts expire automatically after a 6 month period of inactivity."
		+ "<h3>Why Does ChemVantage Cost So Little?</h3>"
		+ "None of the question items in ChemVantage are owned by textbook publishers, so we pay no "
		+ "royalties or license fees. Also, ChemVantage is powered by Google App Engine, so we are "
		+ "able to provide high quality, reliable service at minimal cost.";
		
	public static String accounts = "<h3>Instructors</h3>"
		+ "If you are a teacher or professor at an accredited educational institution, you may obtain "
		+ "a free instructor account. This allows you to create/edit/delete groups for your students, manage group "
		+ "enrollments, assign teaching assistants, set quiz and homework deadlines, select question items for the "
		+ "quiz and homework assignments, and view the scores for members of your groups. To obtain an instructor "
		+ "account, please create a free basic account, then send email to "
		+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> "
		+ "from your institutional email address requesting an account upgrade to instructor status. "
		+ "ChemVantage provides two free ways to make it easy for students to join the group for your class:<ol>"
		+ "<li>You can integrate ChemVantage directly into your class learning management system using LTI (see below)."
		+ "<li>If your school uses GMail for student email accounts, you can add ChemVantage into your Google Apps domain."
		+ "</ol>Either method creates a private domain in ChemVantage for your college or school and gives you unlimited "
		+ "free premium account upgrades for the first four months."
		+ "<h3>Teaching Assistants</h3>"
		+ "An instructor may assign any user to be a teaching assistant for a group managed by the instructor.  "
		+ "Teaching assistants may view the scores of group members and may view the solutions to homework exercises."
		+ "<h3>Editors</h3>"
		+ "Any instructor wishing to contribute question items for quiz or homework assignments may request an "
		+ "editor account by sending email to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.  "
		+ "The copyrights to all question items at this site are owned entirely by <b>ChemVantage LLC</b>. The "
		+ "only way that we can keep costs low enough to offer free accounts to both students and instructors is "
		+ "to avoid paying royalties to textbook publishers.  Therefore, all contributors of ChemVantage question "
		+ "items are asked to certify that all contributed items are original works, and that the author assigns the "
		+ "copyrights to ChemVantage LLC to be shared freely under the terms of a "
		+ "<a href='http://creativecommons.org/licenses/by/3.0/us/'>Creative Commons Attribution 3.0 License.</a>";

	public static String certification = "<a NAME=certification></a>"
		+ "<h3>Learning Management System Integration</h3>"
		+ "<a href=http://imscert.org><img alt='IMS Global Certified' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='/images/imscertifiedfinalsmall.png'/></a> ChemVantage is certified by the "
		+ "<a href=http://imsglobal.org>IMS Global Learning Consortium</a> to be conformant with the "
		+ "LTI v1.1 standard for learning tools interoperability. The IMS conformance registration "
		+ "number for ChemVantage v2.0 is <a href=http://www.imsglobal.org/cc/detail.cfm?ID=102>IMSC2ce2012W1</a>.<p>"
		+ "This means that you can configure most learning management systems such as Blackboard, "
		+ "Canvas, Moodle or Desire2Learn with a Basic LTI link to allow your students to establish and use their "
		+ "accounts automatically in ChemVantage without having to maintain a separate user ID and "
		+ "password. If the LMS supports LTI 1.1, then ChemVantage will report the assignment scores back to the "
		+ "LMS gradebook. The administrator of your LMS can set this up by contacting "
		+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> to obtain the required LTI credentials. "
		+ "All LTI premium account upgrades are free for the first four months."
		+ "<h3>Google Apps Integration</h3>"
		+ "<a href=https://www.google.com/enterprise/marketplace/viewListing?productListingId=9006+12752972024151964645>"
		+ "<img alt='Add To Google Apps' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='/images/marketplace-addtogoogleapps-shadow.png'/></a>ChemVantage is also certified as an educational "
		+ "services provider in the Google Apps EDU Marketplace. If your institution uses Google/GMail for student "
		+ "email accounts, your Google Apps administrator can use this button to install ChemVantage in your Google Apps "
		+ "domain at no cost. All Google Apps premium account upgrades are free for the first 4 months.";
		
	public static String copyright = "<a NAME=copyright></a>"
		+ "<h3>Copyright &copy; 2007-2012 ChemVantage LLC</h3>"
		+ "<a rel='license' href=http://creativecommons.org/licenses/by/3.0/us/>"
		+ "<img alt='Creative Commons License' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='http://i.creativecommons.org/l/by/3.0/us/88x31.png'/></a>"
		+ "Except where otherwise noted, the copyright to all content displayed on this site is owned by ChemVantage LLC, "
		+ "and is licensed freely under a "
		+ "<a href='http://creativecommons.org/licenses/by/3.0/us/'>Creative Commons Attribution 3.0 License</a>. "
		+ "Any use of this content must acknowledge ownership by ChemVantage LLC and must contain appropriate "
		+ "notice of this CC-BY licence. Permissions beyond the scope of this license may be available by contacting "
		+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>";

	public static String terms = "<a NAME=terms></a>"
		+ "<h3>Terms and Conditions of Use</h3>"
		+ "<UL>"
		+ "<LI>LIMITED LIABILITY." 
		+ "<UL>"
		+ "<LI>CHEMVANTAGE LLC IS WITHOUT LIABILITY FOR DAMAGES CAUSED OR ALLEGEDLY CAUSED BY ANY FAILURE "
		+ "OF PERFORMANCE, ERROR, OMISSION, INTERRUPTION, DELETION, DEFECT, VIRUS, DELAY IN OPERATION OR "
		+ "TRANSMISSION, INACCURATE INFORMATION, COMMUNICATIONS LINE FAILURE, THEFT OR DESTRUCTION OF OR "
		+ "UNAUTHORIZED ACCESS TO, ALTERATION OF, OR USE OF RECORDS, WHETHER FOR BREACH OF CONTRACT, "
		+ "TORTUOUS BEHAVIOR, NEGLIGENCE, OR UNDER ANY OTHER CAUSE OF ACTION.</LI>"
		+ "</UL>"
		+ "<LI>PRIVACY POLICY.<br>"
		+ "We protect your privacy to the maximum extent that allows the site to function as designed.<br>"
		+ "For details, see our official <a href=/w3c/privacy.html>Privacy Policy</a>"
		+ "<LI>100% SATISFACTION GUARANTEE<br>"
		+ "We are committed to providing the best possible online learning environment for General Chemistry. "
		+ "If you have a question or problem using the site, please let us know immediately. "
		+ "If we are unable to resolve any issue to your complete satisfaction, we will cheerfully refund your payment in full."
		+ "<LI>YOUR ACCEPTANCE OF THESE TERMS<br>"
		+ "By using this Site, you signify your acceptance of this policy and terms of service. "
		+ "If you do not agree to this policy, please do not use our Site. "
		+ "Your continued use of the Site following the posting of changes to this policy will be deemed your acceptance of those changes."
		+ "<LI>CONTACTING US<br>"
		+ "If you have any questions about this Privacy Policy, the practices of this site, or your dealings with this site, please contact us at:<br>"
		+ "ChemVantage, 2568 Redondo Ave, Salt Lake City, UT 84108 USA<br>"
		+ "Phone: +1 (801)810-4401<br>"
		+ "Email: <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>"
		+ "<LI>These terms and conditions were last updated on December 19, 2011"
		+ "</UL>";

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Login.header + about + premium + accounts + certification + copyright + terms + Home.footer);
	}
}
