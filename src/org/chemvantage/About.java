/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2020 ChemVantage LLC
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
*   along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/About")
public class About extends HttpServlet {

	private static final long serialVersionUID = 137L;
	public static String announcement = "";

	public String getServletInfo() {
		return "This servlet provides information, terms and conditions for using ChemVantage.";
	}

	public static String about = Home.banner
		+ "<h3>Who we are</h3>"
		+ "ChemVantage LLC was founded in 2010 to provide Open Education " 
		+ "Resources for teaching and learning General Chemistry. The founder and chief software architect of "
		+ "ChemVantage is Chuck Wight, who has taught General Chemistry since 1984 at the University of Utah, Weber State "
		+ "University and now Salisbury University."
		
		+ "<h3>Subject material</h3>"
		+ "The database is currently populated with about 5000 quiz, homework, exam and video quiz questions in General Chemistry. "
		
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
		+ "fill-in-the-blank, and numeric answers.  Each quiz consists of 10 questions drawn at random from a "
		+ "database containing many more questions.  In the process of taking several quizzes to improve their "
		+ "scores, students will likely encounter some questions repeated from earlier quizzes; however, it is "
		+ "unlikely that any two quizzes will contain exactly the same set of questions in the same order.  By "
		+ "assigning quizzes to be completed prior to the lecture, instructors can be assured that their students "
		+ "have some exposure to the material.  This allows for more discussion, dialog and engagement in the "
		+ "classroom, and it makes learning more fun (really!)."
		
		+ "<h3>Video Lectures</h3>"
		+ "A series of 10-minute video lectures on a variety of General Chemistry topics is avialable from the "
		+ "<a href=/>Home Page</a>.  The videos are designed to reinforce the main points presented in many textbooks. "
		+ "Instructors can create LTI video assignments in the LMS; many of the videos used in this way contain embedded "
		+ "quizzes, and these scores are returned to the LMS grade book. Videos that do not have embedded quizzes "
		+ "will award full credit for watching to the end."
		
		+ "<h3>Homework</h3>"
		+ "The homework assignments are designed to give students practice in solving quantitative problems having "
		+ "numeric answers. Each student can download the assignment, work the problems and submit the answers "
		+ "online. Answers are judged to be correct if the solution agrees with the solution found in the database "
		+ "to within a specified tolerance (indicated for each problem). For many problems, answers must be expressed "
		+ "with the appropriate number of significant figures. The site informs students which answers are correct, "
		+ "so that they can rework incorrectly solved problems and resubmit the answers. "
		+ "Each problem is based on a template that draws parameters at random, so the correct answer is different "
		+ "for each student. This makes them suitable for collaboration and working in groups, because even though "
		+ "the group may work to find a correct algorithm for solving a problem, each student will have to use the "
		+ "algorithm to find the detailed solution to his or her own problem."
		
		+ "<h3>Practice Exams</h3>"
		+ "Students can take timed practice exams that draw questions from any group of 3 or more topics.  Each "
		+ "exam is designed to take 60 minutes or less to complete, and includes a selection of 10 quiz questions "
		+ "worth 2 points each, 5 of the easier homework problems at 10 points each, and 2 more challenging homework "
		+ "problems worth 15 points each. The scores on each exam are disaggregated by topic to "
		+ "reveal the student's areas of strength and weakness.  Practice exams may be repeated as often as desired. "
		+ "Questions are drawn at random from the database, and the numeric questions are parameterized, making it "
		+ "extremely unlikely that a student will ever get two identical practice exams.";
		
	public static String premium = "<a NAME=accounts></a>"
		+ "<h3>ChemVantage is FREE</h3>"
		+ "Anyone can use ChemVantage anonymously by simply browsing to <a href=https://www.chemvantage.org>"
		+ "www.chemvantage.org</a>. Chemistry instructors can also make ChemVantage assignments available to their "
		+ "classes by connecting their class learning management system (LMS) to ChemVantage using "
		+ "<a href=/lti/registration>our LTI interface</a>. Student scores are automatically returned to the LMS grade book.<p>"
		+ "All ChemVantage LTI accounts expire automatically after one year of inactivity, and all data associated "
		+ "with expired accounts are deleted irrevocably to protect user privacy.<p>"
		+ "The first 1000 LTI user accounts for each non-profit institution are free of charge. For more than 1000 users "
		+ "or for commercial use, <a href=mailto:admin@chemvantage.org>contact us</a> for pricing. ChemVantage can "
		+ "afford to be free for most users because none of the content in ChemVantage is owned by textbook publishers, "
		+ "so we pay no royalties or license fees."
		
		+ "<h3>Powered by Google Cloud</h3>"
		+ "<a href=https://cloud.google.com target=_blank><img alt='Google Cloud Logo' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='/images/GCP_logo.png'/></a> ChemVantage is hosted by Google App Engine and Google Cloud Firestore, "
		+ "so we are able to provide high quality, reliable and massively scalable service at minimal cost.";
	
		
	public static String accounts = "<h3>Instructors</h3>"
		+ "ChemVantage is a great resource for teachers and professors. The BEST way to manage a class using "
		+ "this site is to connect from your institution's learning management system (e.g., Canvas, Moodle, "
		+ "Blackboard, Desire2Learn or Sakai) using the LTI standard. This method allows your LMS to handle "
		+ "all passwords, role assignments and security.  To obtain a set of LTI "
		+ "credentials from ChemVantage, please visit our <a href=/lti/registration target=_blank>LTI registration page</a>. "
		+ "After registration, instructors can customize quiz and homework assignments for their classes using the instructor "
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
		+ "<a href='https://creativecommons.org/licenses/by/3.0/us' target=_blank>Creative Commons Attribution 3.0 License.</a>";

	public static String certification = "<a NAME=certification></a>"
		+ "<h3>Learning Management System Integration</h3>"
		+ "<a href=https://site.imsglobal.org/certifications target=_blank><img alt='IMS Global Certified' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='/images/imscertifiedfinalsmall.png'/></a> ChemVantage is certified by the "
		+ "<a href=https://imsglobal.org target=_blank>IMS Global Learning Consortium</a> to be conformant with the "
		+ "LTI 1.0, LTI 1.1, Basic Outcomes 1.0 and LTI Advantage Complete standards for learning tools interoperability. The IMS registration "
		+ "number for ChemVantage is <a href=https://site.imsglobal.org/certifications/chemvantage/chemvantage target=_blank>IMSD2C6C25C26C27ce2020W1</a>.<p>"
		+ "This means that you can configure <a href=https://site.imsglobal.org/certifications?refinementList%5Bproduct_category%5D%5B0%5D=Learning%20Platform target=_blank>"
		+ "compatible learning management systems</a> with an LTI link to ChemVantage.  This allows your LMS to establish "
		+ "and maintain ChemVantage accounts automatically, without having to maintain separate usernames and "
		+ "passwords. If your LMS supports the LTI Outcomes Service, ChemVantage will report the assignment scores back to the "
		+ "LMS grade book. You can obtain a set of ChemVantage LTI credentials at <a href=/lti/registration target=_blank>our LTI registration page</a>.";
			
	public static String accessibility = "<a NAME=accessibility></a>"
		+ "<h3>Web Site Accessibility</h3>"
		+ "ChemVantage is committed to providing accessible content for its users. The site is in substantial compliance with "
		+ "The Americans with Disabilities Act (ADA) as measured by Level AA of the "
		+ "<a href=https://www.w3.org/TR/WCAG20 target=_blank>Web Content Accessibility Guidelines (WCAG) 2.0</a>, which is our target level of "
		+ "compliance. If you experience difficulty accessing any ChemVantage resource or if you have questions or suggestions regarding "
		+ "accessibility of our site, please let us know through the <a href=/Feedback>ChemVantage Feedback Page</a> or by using the "
		+ "contact information at the bottom of this page.";
			
	public static String copyright = "<a NAME=copyright></a>"
		+ "<h3>Copyright &copy; 2007-2020 ChemVantage LLC</h3>"
		+ "<a rel='license' href=https://creativecommons.org/licenses/by/3.0/us target=_blank>"
		+ "<img alt='Creative Commons License' style='border-width:0' align=left hspace=10 vspace=5 "
		+ "src='https://i.creativecommons.org/l/by/3.0/us/88x31.png'/></a>"
		+ "Except where otherwise noted, the copyright to all content displayed on this site is owned by ChemVantage LLC, "
		+ "and is licensed freely under a "
		+ "<a href='https://creativecommons.org/licenses/by/3.0/us' target=_blank>Creative Commons Attribution 3.0 License</a>. "
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

	public static String privacy = "<a NAME=privacy></a>"
		+ "<h3>Privacy Policy</h3>"
		+ "ChemVantage protects its users' privacy to the maximum extent that allows the site to function as designed."
		+ "<ul>"
		+ "<li><b>Personally identifiable information (PII)</b> - Except for LMS administrators and people who contact ChemVantage "
		+ "directly (e.g., to establish an account or to request a response to feedback), Chemvantage does not " 
		+ "store any user PII. No names, no passwords, no addresses, no phone numbers. ChemVantage " 
		+ "does not process, collect or store any user credit card or financial information whatsoever.</li>"
		+ "<li><b>Digital Tracking</b> - ChemVantage does not use cookies, web analytics, web beacons, pixel tags or any other type "
		+ "of digital tracking devices. We do not store information about IP addresses, locations, or browser information of "
		+ "our users. We do not display any advertisements or enter into any service agreements with third party advertisers.</li>"
		+ "<li><b>User IDs</b> - During each user's session, ChemVantage issues a secure token that is linked to an "
		+ "opaque user ID to ensure both privacy and security. For LTI users, this ID is provided by the learning "
		+ "management system (LMS). LTI is a secure messaging and communications standard developed by the "
		+ "<a href=https://www.imsglobal.org target=_blank>IMS Global Learning Consortium</a>. LTI connects students and instructors with "
		+ "educational service providers. ChemVantage is a "
		+ "<a href=https://site.imsglobal.org/certifications/chemvantage/36981/chemvantage target=_blank>certified LTI provider</a>. "
		+ "Users who enter ChemVantage through our home page are assigned a random ID number and are informed that they are "
		+ "participating as anonymous users.</li>"
		+ "<li><b>Data and Information Collected</b> - ChemVantage collects and stores data and information about the quizzes, homework "
		+ "assignments and practice exams that are downloaded by our users, including customized assignments, individual responses "
		+ "to question items, assignment scores and timestamps. All data and information transmitted to or from ChemVantage are "
		+ "protected by TLS/SSL encryption while in transit, and are stored in encrypted form on Google Cloud Datastore servers while at rest. "
		+ "All user data are solely owned by the user and may be deleted upon request to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.</li>"
		+ "<li><b>How We Use Data and Information that We Collect</b> - For LTI users, individual user scores, data and information are "
		+ "available to the user, the instructor (as identified by the LMS) and to the LMS administrators. User scores may "
		+ "be reported by ChemVantage directly back to the LMS grade book via the LTI Outcomes Service if this service is "
		+ "supported by the LMS.<br>For all users, including anonymous users entering the site through our home page, we use "
		+ "scores, responses and user comments/feedback to improve the quality of individual question items in the site as well "
		+ "as site navigation and user experience. We also use timestamps to automatically delete user data and LMS accounts after "
		+ "one year of inactivity.</li>"
		+ "<li><b>Information That We Share</b> - We will access, use, preserve, and/or disclose user information if we reasonably "
		+ "believe it necessary to satisfy a valid and legally enforceable warrant, subpoena, court order, law or regulation, "
		+ "or other judicial or administrative order. Otherwise, ChemVantage does not and will not share any user data or "
		+ "information with anyone except the user\'s LMS and with instructors and administrators who are identified by the "
		+ "LMS through the LTI interface.</li>"
		+ "<li><b>Google reCAPTCHA</b> - ChemVantage uses the Google reCAPTCHA tool on its LTI registration page to detect bots and "
		+ "prevent spam and abuse. To register your LMS with ChemVantage you must accept the Google reCAPTCHA Terms of Service "
		+ "and the Google API Terms of service. This is not required of users who are accessing ChemVantage resources through the LTI "
		+ "interface (i.e., students and instructors) or users who enter ChemVantage through the home page anonymously.</li>"
		+ "</ul>";				
	
	public static String terms = "<a NAME=terms></a>"
		+ "<h3>ChemVantage Terms and Conditions of Use</h3>"
		+ "<UL>"
		+ "<LI>LIMITED LIABILITY.<br>" 
		+ "CHEMVANTAGE LLC IS WITHOUT LIABILITY FOR DAMAGES CAUSED OR ALLEGEDLY CAUSED BY ANY FAILURE "
		+ "OF PERFORMANCE, ERROR, OMISSION, INTERRUPTION, DELETION, DEFECT, VIRUS, DELAY IN OPERATION OR "
		+ "TRANSMISSION, INACCURATE INFORMATION, COMMUNICATIONS LINE FAILURE, THEFT OR DESTRUCTION OF OR "
		+ "UNAUTHORIZED ACCESS TO, ALTERATION OF, OR USE OF RECORDS, WHETHER FOR BREACH OF CONTRACT, "
		+ "TORTUOUS BEHAVIOR, NEGLIGENCE, OR UNDER ANY OTHER CAUSE OF ACTION."
		+ "<LI>NO WARRANTEE<br>"
		+ "We are committed to providing the best possible online learning environment for General Chemistry. "
		+ "If you have a question or problem using the site, please let us know immediately, and we will do our "
		+ "best to correct any errors or software bugs. However, ChemVantage software as a service is provided "
		+ "without any warrantee whatsoever. All ChemVantage subscription fees are non-refunable.</LI>"
		+ "<LI>GOVERNING LAW<br>"
		+ "These Terms and Conditions of Use and any dispute arising between ChemVantage and its users shall be "
		+ "governed by the laws of the State of Maryland, U.S.A.</LI>"
		+ "<LI>YOUR ACCEPTANCE OF THESE TERMS<br>"
		+ "By using this Site, you signify your acceptance of this policy and terms of service. "
		+ "If you do not agree to this policy, please do not use our Site. Your continued use of the Site "
		+ "following the posting of changes to this policy will be deemed your acceptance of those changes.</LI>"
		+ "<LI>CONTACTING US<br>"
		+ "If you have any questions about these Terms and Conditions, our Privacy Policy, the practices of this site, "
		+ "or your dealings with this site, please contact us at:<br>"
		+ "Phone: +1 (801)243-8242<br>"
		+ "Email: <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a></LI>"
		+ "<LI>These terms and conditions were last updated on September 23, 2020</LI>"
		+ "</UL>";

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Home.header("About ChemVantage") + about + premium + accounts + certification + accessibility + copyright + privacy + terms + Home.footer);
	}
}
