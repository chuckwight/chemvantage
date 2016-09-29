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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;


public class Verification extends HttpServlet {

	private static final long serialVersionUID = 137L;
	Subject subject = Subject.getSubject();
	private static final String alpha = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	
	public String getServletInfo() {
		return "This servlet verifies a user-supplied email address.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {

		// This method receives a coded URL to verify a user's email address
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		try {
			String userId = request.getParameter("UserId");
			String code = request.getParameter("Code");
			if (userId.isEmpty() || code.isEmpty()) throw new Exception();
			User user = ofy().load().type(User.class).id(userId).safe();
			user.verifiedEmail = (Integer.parseInt(code) == Key.create(User.class,userId).hashCode());
			ofy().save().entity(user);
			if (user.verifiedEmail) Admin.autoMergeAccounts(user.getEmail());
			out.println(Login.header + verifiedEmail(user.verifiedEmail) + Login.footer);
		} catch (Exception e) {	
			doPost(request,response);  // view current information if logged in; else go to Login page
		}	
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();
			User user = User.getInstance(session);
			if (user==null || (Login.lockedDown && !user.isAdministrator())) {
				response.sendRedirect("/");
				return;
			}

			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			if (userRequest.equals("Save My Information")) {
				String firstName = request.getParameter("FirstName");
				String lastName = request.getParameter("LastName");
				String email = request.getParameter("Email");
				if (firstName != null && !firstName.isEmpty()) user.setFirstName(firstName);
				if (lastName != null && !lastName.isEmpty()) user.setLastName(lastName);
				if (email != null && !email.isEmpty()) {
					user.setEmail(email);
					user.verifiedEmail = false;
				}
				user.lastLogin = new Date();
				ofy().save().entity(user);
				boolean requiresVerification = !user.verifiedEmail && !user.getEmail().isEmpty();
				out.println(Home.getHeader(user) + personalInfoForm(user,requiresVerification?verificationEmailSent(user,request):false,request) + Home.footer);
			} else if (userRequest.equals("Verify My Email Address")) {
				try {
					user.verifiedEmail = user.getEmail().equals(UserServiceFactory.getUserService().getCurrentUser().getEmail());
				} catch(Exception e){
				}
				user.lastLogin = new Date();
				ofy().save().entity(user);
				out.println(Home.getHeader(user) + (user.verifiedEmail?personalInfoForm(user,false,request):personalInfoForm(user,verificationEmailSent(user,request),request)) + Home.footer);
			} else if (userRequest.equals("JoinGroup")) {
				// This section verifies eligibility to join groups and adjusts available seats if necessary				
				try {
					long newGroupId = Long.parseLong(request.getParameter("GroupId"));
					Group newGroup = null;
					if (newGroupId > 0) newGroup = ofy().load().type(Group.class).id(newGroupId).now();
					if (user.processPremiumUpgrade(newGroup)) user.changeGroups(newGroupId);
				} catch (Exception e2) {
				}
				if (user.myGroupId == 0) {
					response.sendRedirect("/Home");
					return;
				}
				out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
			} else if (userRequest.equals("Register")) {
				try {
					String cellNumber = request.getParameter("CellNumber");
					String carrier = request.getParameter("Carrier");
					String code = "";
					while (code.length()<5) {
						code += alpha.charAt(new Random().nextInt(alpha.length()));
					}
					if (cellNumber.length()==10 && Long.parseLong(cellNumber) > 0 && !carrier.isEmpty()) { // 10-digit number
						String address = cellNumber + "@" + carrier;
						user.smsMessageDevice = code + address;
						user.use2FactorAuth = true;
						if (sendSMSConfirmationCode(address,code)) ofy().save().entity(user).now();
					}
					out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
				} catch (Exception e2) {
					out.println(e2.toString());
				}
			} else if (userRequest.equals("Confirm")) {
				if (request.getParameter("Code").toUpperCase().equals(user.smsMessageDevice.substring(0,5))) {
					user.smsMessageDevice = user.smsMessageDevice.substring(5);
					session.setAttribute("Code",999999);
					ofy().save().entity(user).now();
				}
				out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
			}
			else if (userRequest.equals("Cancel")) {
				user.use2FactorAuth = false;
				ofy().save().entity(user).now();
				out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
			} else if (userRequest.equals("Get Authorization Code")) {
				if (mergeAuthCodeSent(user,request)) out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
				else out.println("Sorry, the authorization code could not be sent. Please try again later.");
			} else if (userRequest.equals("Merge This Account With Mine")) {
				try {
					String fromUserId = request.getParameter("FromAccount");
					String toUserId = request.getParameter("ToAccount");
					int code = Integer.parseInt(request.getParameter("Code"));
					if (code==Math.abs((Key.create(User.class,fromUserId).toString() + Key.create(User.class,toUserId)).toString().hashCode())) {
						User fromUser = ofy().load().type(User.class).id(fromUserId).safe();
						User toUser = ofy().load().type(User.class).id(toUserId).safe();
						Admin.mergeAccounts(toUser,fromUser);
						out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
					} else out.println("The authorization code was not valid.");
				} catch (Exception e) {
					out.println("The authorization code was not valid.");
				}
			} else out.println(Home.getHeader(user) + personalInfoForm(user,false,request) + Home.footer);
		} catch (Exception e) {
		}
	}

	private boolean verificationEmailSent(User user,HttpServletRequest request) {
		if (user.verifiedEmail) return false;  // no verification email is necessary
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		String msgBody = "<h3>ChemVantage Email Verification</h3>"
			+ "To confirm that this is the email address associated with your ChemVantage account, "
			+ "please click the link below or copy/paste it into your web browser address bar.<p>"
			+ request.getRequestURL().toString() + "?UserId=" + user.id + "&Code="
			+ Key.create(User.class,user.id).hashCode() + "  <p>"
			+ "If you did not request this verification, please do not click the link.<br>" 
			+ "If you need assistance, please reply to admin@chemvantage.org.<p>"
			+ "Thank you.";

		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(user.getEmail(), user.getBothNames()));
			msg.setSubject("ChemVantage Email Verification");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	String verifiedEmail(boolean verified) {
		StringBuffer buf = new StringBuffer();
		if (verified) buf.append("<h3>Email Verification Complete</h3>" 
				+ "Thank you for verifying your email address with ChemVantage.<p>"
				+ "<a href=http://www.chemvantage.org>Go to the ChemVantage login page now</a>.");
		else buf.append("<h3>Verification Failed</h3>Sorry, this action was unsuccessful. "
				+ "Please be sure that the complete URL was included in the request by copying " 
				+ "and pasting it into the URL address bar in your browser. If you need assistance, "
				+ "send email to admin@chemvantage.org");
		return buf.toString();
	}

	String personalInfoForm(User user,boolean verificationEmailSent,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		//boolean nameRequired = user.firstName.isEmpty() || user.lastName.isEmpty();
		boolean nameRequired = user.getFirstName().isEmpty();  // no longer requiring a last name from users because identities are managed externally through LTI and Google Apps
		boolean emailRequired = user.getEmail().isEmpty();
		boolean groupRequired = user.myGroupId < 0;
		
		try {
			buf.append("<h2>Your ChemVantage Account Profile</h2>"
					+ "ChemVantage protects your personal information. For details, see our <a href=/w3c/privacy.html>Privacy Policy</a>.<br>"
					+ "In order for ChemVantage to function properly as a learning resource, we need to associate your name and email address with your account.<br>"
					+ "This is important for protecting <i>you</i> by making it difficult for someone else to impersonate you or tamper with your account.<p>");

			buf.append("<FORM NAME=Info ACTION=Verification METHOD=POST>");
			buf.append("<TABLE>");
			buf.append("<TR><TD ALIGN=RIGHT>First Name:</TD><TD>" + (nameRequired?"<span style=color:red>*</span><INPUT NAME=FirstName SIZE=50>":user.getFirstName()) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Last Name:</TD><TD>" + (user.getLastName().isEmpty()?"<INPUT NAME=LastName SIZE=50> (optional)":user.getLastName()) + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT VALIGN=TOP>Email:</TD><TD>" + (emailRequired?"<span style=color:red>*</span><INPUT NAME=Email SIZE=50>":user.getEmail()));
			if (!emailRequired && !user.verifiedEmail){
				buf.append(" <span style='color:red'>(unverified)</span> ");
				if (verificationEmailSent) buf.append("<br><span style='color:red'>A verification email has been sent to your address.</span><br>");
				else if (!nameRequired) buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Verify My Email Address'><br>");
			}
			buf.append("</TD></TR>");
			if (user.smsMessageDevice!=null && !user.smsMessageDevice.isEmpty()) {
				String smsAddress = "";
				try {
					Long.parseLong(user.smsMessageDevice.substring(0,10));
					smsAddress = user.smsMessageDevice;
				} catch (Exception e2) {
					smsAddress = user.smsMessageDevice.substring(5) + " (unverified)";
				}
				buf.append("<TR><TD ALIGN=RIGHT>SMS address:</TD><TD>" + smsAddress + "</TD></TR>");	
			}
			buf.append("<TR><TD ALIGN=RIGHT>AuthDomain: </TD><TD>" + user.authDomain + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Domain: </TD><TD>" + user.domain + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>UserID: </TD><TD>" + user.id + "</TD></TR>");
			buf.append("<TR><TD ALIGN=RIGHT>Roles: </TD><TD>Learner " + (user.isInstructor()?"Instructor ":"") + (user.isContributor()?"Author ":"") + (user.isEditor()?"Editor ":"") + (user.isAdministrator()?"Administrator ":"") +"</TD></TR>");			
			if (user.myGroupId>0L) { // user already belongs to a group
				Group myGroup = ofy().load().type(Group.class).id(user.myGroupId).now();
				if (!user.hasPremiumAccount() || myGroup==null) user.changeGroups(0L);
				else buf.append("<TR><TD ALIGN=RIGHT>Group:</TD><TD>" + myGroup.description + " (" + myGroup.getInstructorBothNames() + ")</TD></TR>");
			}

			if (nameRequired || emailRequired || user.getLastName().isEmpty()) {
				buf.append("<TR><TD>&nbsp;</TD>"
						+ "<TD>" + ((nameRequired || emailRequired)?"<span style=color:red>* <span style=font-size:smaller>required field</span></span><p>":"") 
						+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save My Information'></TD></TR>");
			}
			if (nameRequired || emailRequired) buf.append("</TABLE></FORM>");  // finish table
			else {  // all information is current; continue table
				buf.append("</FORM>");
				boolean eligibleToJoin = eligibleToJoin(user);
				if (user.myGroupId<=0L && eligibleToJoin) { // give the user an opportunity to join a group
					buf.append("<TR><TD ALIGN=RIGHT VALIGN=TOP>ChemVantage Group:</TD><TD>");
					buf.append("<FORM NAME=JoinGroup METHOD=POST ACTION=Verification>");
					buf.append("<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=JoinGroup>");

					List<Group> allGroups = null;
					if (user.domain==null || user.domain.isEmpty()) allGroups=ofy().load().type(Group.class).list();
					else allGroups=ofy().load().type(Group.class).filter("domain",user.domain).list();
					//for (Group g : allGroups) if (!g.isActive()) allGroups.remove(g);
					
					if (allGroups.size() == 0) {  // there are not yet any groups for this domain
						user.changeGroups(0L);
						buf.append("No groups have been created in this domain yet.");
					} else {					
						buf.append("<SELECT NAME=GroupId "
								+ "onChange=\"if(confirm('Are you sure that you  want to join this group? "
								+ "This action can only be reversed by an instructor.'))document.JoinGroup.submit();"
								+ "else document.JoinGroup.GroupId[0].selected=true;\">"
								+ (groupRequired?"<OPTION VALUE=-1>Please select a ChemVantage group to join:</OPTION>\n":"")
								+ "<OPTION VALUE=0>I'm not a member of any group or class using ChemVantage</OPTION>\n");
						for (Group g : allGroups) {
							try {
								buf.append("<OPTION VALUE=" + g.id + ">" + g.description + " (" + g.getInstructorBothNames() + ")</OPTION>\n");
							} catch (Exception e2) {
								continue;
							}
						}
						buf.append("</SELECT>");
					}
					buf.append("</FORM>");
					if (groupRequired) {
						buf.append("</TD></TR>");
						if (allGroups.size() > 0) buf.append("<TR><TD COLSPAN=2><span id=instructions style='color:red'><br>"
								+ "Please select a ChemVantage group. This will give you access to assignments and deadlines.<br>"
								+ "It will also give your instructor and teaching assistant access to your scores.</span></TD></TR>");
						else if (user.isInstructor()) buf.append("<TR><TD COLSPAN=2>"
								+ "<span id=instructions style='color:red'><br>Use the 'Instructor' link at the top "
								+ "of the page to create a new ChemVantage group for your chemistry class.</span></TD></TR>");
					}
				}
				buf.append("<TR><TD ALIGN=RIGHT VALIGN=TOP>Account Security: </TD><FORM METHOD=POST><TD>");
				if (user.use2FactorAuth) {
					try {
						Long.parseLong(user.smsMessageDevice.substring(0,10)); // throws exception if not 10 digits
						buf.append("Two-factor authentication. <INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Cancel'>");
					} catch (Exception e) {
						buf.append("A confirmation code was sent to your wireless device. If the message is not received, please<br>"
								+ "cancel and try again, or send email to admin@chemvantage.org for assistance.<br>"
								+ "To complete the SMS registration, enter the code here: "
								+ "<INPUT TYPE=TEXT SIZE=6 NAME=Code><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Confirm'><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Cancel'>");
					}
				} else if (user.authDomain.equals("Google")){
					buf.append("<div id=link>Password only. <a href=# onClick=javascript:getElementById('register').style.display='';getElementById('link').style.display='none';>Click here to activate two-factor authentication.</a>.</div>"
							+ "<div id=register style='display: none'>Enter your 10-digit cellular device number: "							
							+ "<INPUT TYPE=TEXT NAME=CellNumber VALUE='10 digits only' onfocus=\"if (this.value == '10 digits only') {this.value = '';}\">"
							+ "<SELECT NAME=Carrier>"
							+ "<OPTION VALUE=''>Select your carrier</OPTION>"
							+ "<OPTION VALUE='txt.att.net'>AT&T</OPTION>"
							+ "<OPTION VALUE='message.alltel.com'>Alltel</OPTION>"
							+ "<OPTION VALUE='myboostmobile.com'>Boost</OPTION>"
							+ "<OPTION VALUE='mobile.celloneusa.com'>CellularOne</OPTION>"
							+ "<OPTION VALUE='csouth1.com'>Cellular South</OPTION>"
							+ "<OPTION VALUE='cingularme.com'>Cingular (GSM)</OPTION>"
							+ "<OPTION VALUE='mmode.com'>Cingular (TDMA)</OPTION>"
							+ "<OPTION VALUE='sms.mycricket.com'>Cricket</OPTION>"
							+ "<OPTION VALUE='mymetropcs.com'>Metro PCS</OPTION>"
							+ "<OPTION VALUE='clearlydigital.com'>Midwest Wireless</OPTION>"
							+ "<OPTION VALUE='messaging.nextel.com'>Nextel</OPTION>"
							+ "<OPTION VALUE='messaging.sprintpcs.com'>Sprint PCS</OPTION>"
							+ "<OPTION VALUE='tmomail.net'>T-Mobile</OPTION>"
							+ "<OPTION VALUE='email.uscc.net'>US Cellular</OPTION>"  // revised
							+ "<OPTION VALUE='vtext.com'>Verizon</OPTION>"
							+ "<OPTION VALUE='vmobile.com'>Virgin Mobile</OPTION></SELECT>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Register'></div>");
				} else if (user.authDomain.equals("BLTI")) buf.append("Trusted LTI connection.");
				else buf.append("Password only.");
				
				buf.append("</TD></FORM></TR>");

				buf.append("</TABLE>\n");

				buf.append("<h3>Any Corrections Needed?</h3>"
						+ "If your name and/or email shown above is not correct, please send a message to "
						+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><br>giving detailed "
						+ "instructions for any changes that are needed.<p>"); 
				if (!(user.getFirstName().isEmpty() || user.getEmail().isEmpty())) {
					buf.append("<style type='text/css'>a.nav, a.nav:link, a.nav:visited {display:block; width:250px; height:35px; "
							+ "background:red; border:1px solid #000; margin-top:2px; text-align:center; text-decoration:none; "
							+ "font-family:verdana, arial, sans-serif; font-size:15px; color:white; line-height:35px; overflow:hidden;}"
							+ "a.nav:hover {color:#fff; background:#800;}</style>");
					buf.append("<a class='nav' href='/Home'>Continue</a>");
				}
			}
			boolean showDuplicateAccounts = "Get Authorization Code".equals(request.getParameter("UserRequest")) || "Merge This Account With Mine".equals(request.getParameter("UserRequest"));
			buf.append("<p><a href=# style='font-size:smaller' onClick=\"javascript: document.getElementById('multi').style.display=''\">I can't find my stuff</a>"
					+ "<div id='multi'" + (showDuplicateAccounts?">":" style='display:none'>"));

			buf.append("<h3>Do You Have Multiple ChemVantage Accounts?</h3>"
					+ "This is fairly common because there are multiple ways of creating ChemVantage accounts.<br>"
					+ "If you think you have more than one account and you don't see options below for merging<br>"
					+ "them below, first check each account to make sure that the email address has been verified<br>"
					+ "by ChemVantage. If that doesn't work, send a detailed account merge request to "
					+ "<a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<p>");
			
			if (user.verifiedEmail && !user.requiresUpdates()) {
				// This section finds accounts with duplicate verified email addresses for immediate account merging
				List<User> duplicateEmails = ofy().load().type(User.class).filter("email",user.getEmail()).list();
				buf.append("<TABLE>");
				for (User u : duplicateEmails) {
					if (u.id.equals(user.id) || !u.verifiedEmail || u.alias!=null) continue;
					//otherUserIds.add(u.id);
					int code = Math.abs((Key.create(User.class,u.id).toString() + Key.create(User.class,user.id).toString()).hashCode());
					int i = duplicateEmails.indexOf(u);
					String consumerKey = u.authDomain.equals("BLTI")?u.id.substring(0, u.id.indexOf(":")):u.authDomain;
					buf.append("<TR><TD>" + u.getFullName() + " (" + u.getEmail() + ") - authorization domain=" + consumerKey + "</TD>"
							+ "<TD><FORM NAME=DuplicateEmail" + i + " ACTION=Verification METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=FromAccount VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=ToAccount VALUE='" + user.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=Code VALUE='" + code + "'>"
							+ "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Merge This Account With Mine' "
							+ "onClick=\"javascript: document.DuplicateEmail" + i + ".UserRequest.style='display:none';return confirm('Are you sure? This action cannot be undone.')\">"
							+ "</FORM></TD></TR>");
				}
				// This section finds duplicate names, sends a code to the user's email address and accepts it to initiate an account merge.
				List<User> duplicateNames = ofy().load().type(User.class).filter("lowercaseName",user.lowercaseName).list();
				for (User u : duplicateNames) {
					if (u.id.equals(user.id) || duplicateEmails.contains(u.id) || !u.verifiedEmail || u.alias!=null) continue;
					int i = duplicateNames.indexOf(u);
					String consumerKey = u.authDomain.equals("BLTI")?u.id.substring(0, u.id.indexOf(":")):u.authDomain;
					buf.append("<TR><TD>" + u.getFullName() + " (" + u.getEmail() + ") - authorization domain=" + consumerKey + "</TD>"
							+ "<TD><FORM NAME=DuplicateName" + i + " ACTION=Verification METHOD=POST>"
							+ "<INPUT TYPE=HIDDEN NAME=FromAccount VALUE='" + u.id + "'>"
							+ "<INPUT TYPE=HIDDEN NAME=ToAccount VALUE='" + user.id + "'>");
					if ("Get Authorization Code".equals(request.getParameter("UserRequest")) && u.id.equals(request.getParameter("FromAccount"))) {
						buf.append("</TD></TR><TR><TD COLSPAN=2>"
								+ "<span style=color:red>An email has been sent to " + u.getEmail() + " with an authorization code. Please enter it here: </span>"
								+ "<INPUT TYPE=TEXT NAME=Code><INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Merge This Account With Mine'>");
					} else {
						buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Get Authorization Code'>");
					}
					buf.append("</FORM></TD></TR>");
				}
				buf.append("</TABLE>");
			}
			buf.append("</div>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}

	boolean eligibleToJoin(User user) {
		// This method checks to see if the user is eligible to join a new group
		if (user.hasPremiumAccount()) return true;
		Domain domain = ofy().load().type(Domain.class).filter("domainName", user.domain).first().now();
		if (domain == null) return false;
		return true;   // ******* THIS STATEMENT CREATES A FREE ACCOUNT FOR ANYONE IN A DOMAIN **********
//		if (domain.seatsAvailable>0 || domain.freeTrialExpires.after(new Date())) return true;
//		return false;
	}
	
	boolean processPremiumUpgrade(User user,long newGroupId) {
		// this routine converts the user account to premium, if applicable
		try {
			// check out the following line that returns true of domin == null
			// does this allow anyone not in a domain (UserService entry) to join any group for free?
			// test the effect of eliminating this one check and returning false instead to
			// force these users to pay $4.99  Keeps people in line with the LMS and reduces account proliferation
			/*
			if (user.hasPremiumAccount() || newGroupId <= 0) return true;
			else if (user.domain == null) return false;
			*/
			if (user.hasPremiumAccount() || user.domain==null || newGroupId <= 0) return true;
			Domain domain = ofy().load().type(Domain.class).filter("domainName", user.domain).first().now();
			Group newGroup = ofy().load().type(Group.class).id(newGroupId).now();
			if (domain == null || newGroup==null || !newGroup.domain.equals(user.domain)) return false;
			if (domain.seatsAvailable > 0) {
				user.setPremium(true);
				domain.seatsAvailable--;
				ofy().save().entity(domain);
			} else return false;
			ofy().save().entity(user);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	boolean mergeAuthCodeSent(User user,HttpServletRequest request) {
		if (!user.verifiedEmail) return false;
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			User fromAccountUser = ofy().load().type(User.class).id(request.getParameter("FromAccount")).safe();
			int code = Math.abs((Key.create(User.class,fromAccountUser.id).toString() + Key.create(User.class,user.id)).toString().hashCode());
			String msgBody = "<h3>ChemVantage Account Merge Request</h3>"
				+ "To complete the requested account merge request, please copy/paste the following "
				+ "authorization number into the request form:<p>"
				+ code + "<p>"
				+ "If you did not request this authorization code, please ignore this message.<br>" 
				+ "For assistance, please reply to admin@chemvantage.org.<p>"
				+ "Thank you.";
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.addRecipient(Message.RecipientType.TO,new InternetAddress(fromAccountUser.getEmail(), fromAccountUser.getBothNames()));
			msg.setSubject("ChemVantage Authorization Number");
			msg.setContent(msgBody,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	boolean sendSMSConfirmationCode(String address,String code) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		Message msg = new MimeMessage(session);
		try {
			msg.setRecipient(Message.RecipientType.TO,new InternetAddress(address));
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ChemVantage"));
			msg.setSubject("ChemVantage");
			String messageText = "Your confirmation code is: " + code;
			msg.setContent(messageText,"text/html");
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}