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
		+ "ChemVantage is Chuck Wight, who has taught General Chemistry since 1984 at the University of Utah, Weber State "
		+ "University and now Salisbury University."
		
		+ "<h3>Subject material</h3>"
		+ "The database is currently populated with more than 2000 quiz, homework and exam questions in General Chemistry. "
		
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
		+ "problems worth 15 points each.  The scores on each exam are disaggregated by topic to "
		+ "reveal the student's areas of strength and weakness.  Practice exams may be repeated as often as desired. "
		+ "The numerical questions are parameterized, making it extremely unlikely that a student will ever get two "
		+ "identical practice exams.";
		
	public static String premium = "<a NAME=accounts></a>"
		+ "<h3>ChemVantage is FREE</h3>"
		+ "Anyone can use ChemVantage anonymously by simply browsing to <a href=https://www.chemvantage.org>"
		+ "href=https://www.chemvantage.org</a>. Chemistry instructors can also make "
		+ "ChemVantage assignments available to their classes by connecting their class learning management "
		+ "system (LMS) to ChemVantage using <a href=/lti/registration>our LTI interface</a>. Student scores "
		+ "are automatically returned to the LMS grade book.<p>"
		+ "All ChemVantage LTI accounts expire automatically after a 6 month period of inactivity."
		
		+ "<h3>How Can ChemVantage Afford To Be Free?</h3>"
		+ "None of the question items in ChemVantage are owned by textbook publishers, so we pay no "
		+ "royalties or license fees. Also, ChemVantage is powered by Google App Engine, so we are "
		+ "able to provide high quality, reliable service at minimal cost.";
		
	public static String accounts = "<h3>Instructors</h3>"
		+ "ChemVantage is a great resource for teachers and professors.  The BEST way to manage a class using "
		+ "this site is to connect from your institution's learning management system (e.g., Canvas, Moodle, "
		+ "Blackboard Learn, Desire2Learn or Sakai) using the LTI standard. This method allows your LMS to handle "
		+ "all passwords, role assignments and security.  For details of how to get a free set of LTI "
		+ "credentials from ChemVantage, please visit our <a href=/lti/registration/>LTI registration page</a>. "
		+ "All LTI connections and ChemVantages services are provided free of charge.<p>"
		+ "Instructors can customize quiz and homework assignments for their classes simply using the instructor "
		+ "tools in each assignment."
			
		+ "<h3>Teaching Assistants</h3>"
		+ "An instructor may assign any LMS user to be a teaching assistant. This role is recognized by ChemVantage. "
		+ "Teaching assistants may view solutions to homework exercises to provide guidance to students on some "
		+ "of the more challenging problems."
		
		+ "<h3>Authors</h3>"
		+ "Any instructor wishing to contribute question items for quiz or homework assignments may do this using "
		+ "the instructor tools accessible in the assignments. A ChemVantage editor will review and edit them for clarity.  "
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
		+ "LTI v1.0, v1.1 and v2.0 standards for learning tools interoperability. The IMS conformance registration "
		+ "number for ChemVantage is <a href=http://www.imsglobal.org/cc/detail.cfm?ID=259>IMSB2C2ce2014W1</a>.<p>"
		+ "This means that you can configure most learning management systems such as Blackboard, "
		+ "Canvas, Moodle, Sakai or Desire2Learn with an LTI link to ChemVantage.  This allows your LMS to establish "
		+ "and maintain ChemVantage accounts automatically, without having to maintain separate usernames and "
		+ "passwords. If the LMS supports LTI v1.1 or higher, then ChemVantage will report the assignment scores back to the "
		+ "LMS gradebook. The administrator of your LMS can set this up by generating a set of ChemVantage LTI "
		+ "credentials at <a href=/lti/registration/>our LTI registration page</a>.";
			
	public static String copyright = "<a NAME=copyright></a>"
		+ "<h3>Copyright &copy; 2007-2019 ChemVantage LLC</h3>"
		+ "<a rel='license' href=http://creativecommons.org/licenses/by/3.0/us/>"
		+ "<img alt='Creative Commons License' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='http://i.creativecommons.org/l/by/3.0/us/88x31.png'/></a>"
		+ "Except where otherwise noted, the copyright to all content displayed on this site is owned by ChemVantage LLC, "
		+ "and is licensed freely under a "
		+ "<a href='http://creativecommons.org/licenses/by/3.0/us/'>Creative Commons Attribution 3.0 License</a>. "
		+ "Any use of this content must acknowledge ownership by ChemVantage LLC and must contain appropriate "
		+ "notice of this CC-BY licence. Permissions beyond the scope of this license may be available by contacting "
		+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><p>"
		+ "<h3>DMCA Notice</h3>"
		+ "ChemVantage respects the intellectual property rights of others. If you believe that your copyrighted work "
		+ "has been copied in a way that constitutes copyright infringement and is accessible on this site, you may "
		+ "notify our copyright agent, as set forth in the Digital Millennium Copyright Act of 1998 (DMCA). For your "
		+ "complaint to be valid under the DMCA, you must provide the following information when providing notice of "
		+ "the claimed copyright infringement:<ul>"
		+ "<li>A physical or electronic signature of a person authorized to act on behalf of the copyright owner"
		+ "<li>Identification of the copyrighted work claimed to have been infringed"
		+ "<li>Identification of the material that is claimed to be infringing or to be the subject of the infringing "
		+ "activity and that is to be removed"
		+ "<li>Information reasonably sufficient to permit the service provider to contact the complaining party, such as "
		+ "an address, telephone number, and, if available, an electronic mail address"
		+ "<li>A statement that the complaining party believes in good faith that use of the material in the manner "
		+ "complained of is not authorized by the copyright owner, its agent, or law"
		+ "<li>A statement that the information in the notification is accurate, and that under penalty of perjury, "
		+ "the complaining party is authorized to act on behalf of the owner of an exclusive right that is allegedly infringed"
		+ "</ul>"
		+ "The information above must be sent as a written postal mail or email notification to "
		+ "DMCA Office, ChemVantage LLC, 606 Tony Tank Ln, Salisbury, MD 21801 USA, admin@chemvantage.org<p>"
		+ "WE CAUTION YOU THAT UNDER FEDERAL LAW, IF YOU KNOWINGLY MISREPRESENT THAT ONLINE MATERIAL IS INFRINGING, "
		+ "YOU MAY BE SUBJECT TO HEAVY CIVIL PENALTIES. THESE INCLUDE MONETARY DAMAGES, COURT COSTS, AND ATTORNEYS' "
		+ "FEES INCURRED BY US, BY ANY COPYRIGHT OWNER, OR BY ANY COPYRIGHT OWNER'S LICENSEE THAT IS INJURED AS A "
		+ "RESULT OF OUR RELYING UPON YOUR MISREPRESENTATION. YOU MAY ALSO BE SUBJECT TO CRIMINAL PROSECUTION FOR PERJURY.";

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
		+ "ChemVantage does not collect ANY personally identifiable information (PII), and we do not share "
		+ "scores or other user information with anyone except instructors through the LTI interface.</LI>"
		+ "<LI>100% SATISFACTION GUARANTEE<br>"
		+ "We are committed to providing the best possible online learning environment for General Chemistry. "
		+ "If you have a question or problem using the site, please let us know immediately, and we will do our "
		+ "best to correct any errors or software bugs. Please remember that ChemVantage is offered as a free "
		+ "service, and as such it is provided WITHOUT ANY WARRANTEE WHATSOEVER."
		+ "<LI>YOUR ACCEPTANCE OF THESE TERMS<br>"
		+ "By using this Site, you signify your acceptance of this policy and terms of service. "
		+ "If you do not agree to this policy, please do not use our Site. "
		+ "Your continued use of the Site following the posting of changes to this policy will be deemed your acceptance of those changes."
		+ "<LI>CONTACTING US<br>"
		+ "If you have any questions about these Terms and Conditions, our Privacy Policy, the practices of this site, or your dealings with this site, please contact us at:<br>"
		+ "Phone: +1 (801)243-8242<br>"
		+ "Email: <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>"
		+ "<LI>These terms and conditions were last updated on March 24, 2019"
		+ "</UL>";

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Login.header + about + premium + accounts + certification + copyright + terms + Home.footer);
	}
}
