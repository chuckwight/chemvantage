/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
		+ "ChemVantage.org was founded in 2010 " 
		+ "as a non-profit organization providing Open Education Resources for teaching and learning General Chemistry. "
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
		+ "Anyone can obtain a basic account at ChemVantage.org at no cost.  All of the functionality "
		+ "required for completing quiz and homework assignments is included in the basic account. For a $20.00 USD "
		+ "nonrefundable fee, users may upgrade to a premium account type. There are two features that distinguish "
		+ "premium accounts:<OL>"
		+ "<LI>After each quiz or practice exam, premium account holders are shown the correct answers to all of "
		+ "the problems that were answered incorrectly. A basic account shows a maximum of two correct answers."
		+ "<LI>Whenever the correct answer to a homework exercise is submitted, a premium acccount holder is "
		+ "shown the detailed step-by-step solution to the problem in the database. A basic account simply "
		+ "indicates that the problem was solved correctly."
		+ "</OL>"
		+ "All ChemVantage accounts expire automatically after a 6 month period of inactivity.<p>"
		+ "<b>During a limited time of site development, all new user accounts are free premium accounts.</b>";

	public static String accounts = "<h3>Instructors</h3>"
		+ "If you are a teacher or professor at an accredited non-profit educational institution, you may obtain "
		+ "a free instructor account. This allows you to create/edit/delete groups for your students, manage group "
		+ "enrollments, assign teaching assistants, set quiz and homework deadlines, select question items for the "
		+ "quiz and homework assignments, and view the scores for members of your groups. To obtain an instructor "
		+ "account, please create a free basic account, then send email to "
		+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a> "
		+ "from your institutional email address requesting an account upgrade to instructor status."
		+ "<h3>Teaching Assistants</h3>"
		+ "An instructor may assign any user to be a teaching assistant for a group managed by the instructor.  "
		+ "Teaching assistants may view the scores of group members and may view the solutions to homework exercises."
		+ "<h3>Editors</h3>"
		+ "Any instructor wishing to contribute question items for quiz or homework assignments may request an "
		+ "editor account by sending email to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.  "
		+ "The copyrights to all question items at this site are owned entirely by <b>PracticeZone.org</b>. The "
		+ "only way that we can keep costs low enough to offer free accounts to both students and instructors is "
		+ "to avoid paying royalties to textbook publishers.  Therefore, all contributors of ChemVantage question "
		+ "items are asked to certify that all contributed items are original works, and that the author grants the "
		+ "copyrights to PracticeZone ORG LLC to be shared freely under the terms of a "
		+ "<a href='http://creativecommons.org/licenses/by/3.0/us/'>Creative Commons Attribution 3.0 License.</a>";

	public static String copyright = "<a NAME=copyright></a>"
		+ "<h3>Copyright &copy; 2007-2010 PracticeZone.org</h3>"
		+ "<TABLE><TR><TD VALIGN=TOP>"
		+ "<a rel='license' href=http://creativecommons.org/licenses/by/3.0/us/>"
		+ "<img alt='Creative Commons License' style='border-width:0' "
		+ "src='http://i.creativecommons.org/l/by/3.0/us/88x31.png'/></a>"
		+ "</TD><TD VALIGN=MIDDLE>"
		+ "Except where otherwise noted, the copyright to all content on this site is owned by PracticeZone ORG LLC, "
		+ "and is licensed freely under a "
		+ "<a href='http://creativecommons.org/licenses/by/3.0/us/'>Creative Commons Attribution 3.0 License</a>. "
		+ "Any use of this content must acknowledge ownership by PracticeZone ORG LLC and must contain appropriate "
		+ "notice of this CC-BY licence. Permissions beyond the scope of this license may be available by contacting "
		+ "<a href=mailto:admin@practicezone.org>admin@practicezone.org</a>"
		+ "</TD></TR></TABLE>";

	public static String terms = "<a NAME=terms></a>"
		+ "<h3>Terms and Conditions of Use</h3>"
		+ "<UL>"
		+ "<LI>LIMITED LIABILITY." 
		+ "<UL>"
		+ "<LI>CHEMVANTAGE.ORG IS WITHOUT LIABILITY FOR DAMAGES CAUSED OR ALLEGEDLY CAUSED BY ANY FAILURE "
		+ "OF PERFORMANCE, ERROR, OMISSION, INTERRUPTION, DELETION, DEFECT, VIRUS, DELAY IN OPERATION OR "
		+ "TRANSMISSION, INACCURATE INFORMATION, COMMUNICATIONS LINE FAILURE, THEFT OR DESTRUCTION OF OR "
		+ "UNAUTHORIZED ACCESS TO, ALTERATION OF, OR USE OF RECORDS, WHETHER FOR BREACH OF CONTRACT, "
		+ "TORTUOUS BEHAVIOR, NEGLIGENCE, OR UNDER ANY OTHER CAUSE OF ACTION.</LI>"
		+ "</UL>"
		+ "<LI>PRIVACY POLICY.<br>"
		+ "This Privacy Policy governs the manner in which ChemVantage collects, uses, maintains and discloses information collected from users (each, a \"User\") of the http://www.chemvantage.org website (\"Site\"). This privacy policy applies to the Site and all products and services offered by ChemVantage."
		+ "<UL><LI>Personal identification information<br>"
		+ "We may collect personally identification information from Users in a variety of ways, including, but not limited to, when Users visit our site, register on the site, respond to a survey, fill out a form, and in connection with other activities, services, features or resources we make available on our Site. Users may be asked for, as appropriate, name, email address. We will collect personal identification information from Users only if they voluntarily submit such information to us. Users can always refuse to supply personally identification information, except that it may prevent them from engaging in certain Site related activities."
		+ "<LI>Non-personal identification information<br>"
		+ "We may collect non-personal identification information about Users whenever they interact with our Site. Non-personal identification information may include the browser name, the type of computer and technical information about Users means of connection to our Site, such as the operating system and the Internet service providers utilized and other similar information."
		+ "<LI>Web browser cookies<br>"
		+ "Our Site may use \"cookies\" to enhance User experience. User's web browser places cookies on their hard drive for record-keeping purposes and sometimes to track information about them. User may choose to set their web browser to refuse cookies, or to alert you when cookies are being sent. If they do so, note that some parts of the Site may not function properly."
		+ "<LI>How we use collected information<br>"
		+ "ChemVantage collects and uses Users personal information for the following purposes:"
		+ "<UL><LI>To personalize user experience<br>"
		+ "We may use information in the aggregate to understand how our Users as a group use the services and resources provided on our Site."
		+ "<LI>To improve our Site<br>"
		+ "We continually strive to improve our website offerings based on the information and feedback we receive from you."
		+ "<LI>To improve customer service<br>"
		+ "Your information helps us to more effectively respond to your customer service requests and support needs."
		+ "<LI>To send periodic emails<br>"
		+ "The email address Users provide for order processing, will only be used to send them information and updates pertaining to their order. It may also be used to respond to their inquiries, and/or other requests or questions."
		+ "</UL>"
		+ "<LI>How we protect your information<br>"
		+ "We adopt appropriate data collection, storage and processing practices and security measures to protect against unauthorized access, alteration, disclosure or destruction of your personal information, username, password, transaction information and data stored on our Site. Sensitive and private data exchange between the Site and its Users happens over a SSL secured communication channel and is encrypted and protected with digital signatures."
		+ "<LI>Sharing your personal information<br>"
		+ "We do not sell, trade, or rent Users personal identification information to others. If you are a member of a ChemVantage group, your User information is visible to your group instructor and teaching assistants. We may share generic aggregated demographic information not linked to any personal identification information regarding visitors and users with our business partners, trusted affiliates and advertisers for the purposes outlined above."
		+ "<LI>Third party websites<br>"
		+ "Users may find advertising or other content on our Site that link to the sites and services of our partners, suppliers, advertisers, sponsors, licensors and other third parties. We do not control the content or links that appear on these sites and are not responsible for the practices employed by websites linked to or from our Site. In addition, these sites or services, including their content and links, may be constantly changing. These sites and services may have their own privacy policies and customer service policies. Browsing and interaction on any other website, including websites which have a link to our Site, is subject to that website's own terms and policies."
		+ "<LI>Changes to this privacy policy<br>"
		+ "ChemVantage has the discretion to update this privacy policy at any time. When we do, we will post a notification on the main page of our Site, revise the updated date at the bottom of this page. We encourage Users to frequently check this page for any changes to stay informed about how we are helping to protect the personal information we collect. You acknowledge and agree that it is your responsibility to review this privacy policy periodically and become aware of modifications."
		+ "</UL>"
		+ "<LI>Should any provision of this agreement be held to be illegal, invalid, or unenforceable by a "
		+ "court of law, the legality, validity and enforceability of the remaining provisions of this agreement "
		+ "shall remain unaffected thereby unless otherwise stated.</LI>"
		+ "<LI>These Terms and Conditions supersede all previous representations, understandings or agreements "
		+ "and shall prevail notwithstanding any variance with terms and conditions of any order submitted. "
		+ "<LI>YOUR ACCEPTANCE OF THESE TERMS<br>"
		+ "By using this Site, you signify your acceptance of this policy and terms of service. If you do not agree to this policy, please do not use our Site. Your continued use of the Site following the posting of changes to this policy will be deemed your acceptance of those changes."
		+ "<LI>CONTACTING US<br>"
		+ "If you have any questions about this Privacy Policy, the practices of this site, or your dealings with this site, please contact us at:<br>"
		+ "ChemVantage, 2568 Redondo Ave, Salt Lake City, UT 84108 USA<br>"
		+ "Phone: +1 (801) 810-4401<br>"
		+ "Email: <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>"
		+ "<LI>This document was last updated on April 09, 2011"
		+ "</UL>";

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Login.header + about + premium + accounts + copyright + terms + Home.footer);
	}
}
