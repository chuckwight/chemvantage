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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.chemvantage.samples.apps.marketplace.UserInfo;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;

import net.sf.json.JSONObject;

@Cache @Entity
public class User implements Comparable<User>,Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	String id;
	@Index	String email;
	@Index	String domain;
	@Index 	String lastName;
	@Index	String firstName;
	@Index	String lowercaseName;
			String smsMessageDevice;
			Date lastLogin;
			int roles;
			boolean premium;
			long myGroupId;
			boolean notifyDeadlines;
			boolean verifiedEmail;
			boolean use2FactorAuth;
			String alias;
			String authDomain;

	User() {}

	User(String id) {
		this.id = id;
		this.firstName = "";
		this.lastName = "";
		this.lowercaseName = "";
		this.email = "";
		this.roles = 0; // student
		this.premium = false;
		this.lastLogin = new Date(0L);
		this.myGroupId = -1L;
		this.smsMessageDevice = "";
		this.notifyDeadlines = false;
		this.verifiedEmail = false;
		this.use2FactorAuth  = false;
		this.authDomain = "";
		this.alias = null;
	}

	static User getInstance(HttpSession session) {
		return getInstance(session,true);  // default action is to verify 2FactorAuth
	}
	
	static User getInstance(HttpSession session, boolean verify) {
		User user = null;
		String userId = null;
		try {
			userId = (String)session.getAttribute("UserId");
			user = ofy().load().type(User.class).id(userId).safe();
			if (user.alias != null) { // follow the alias chain to the end
				List<String> userIds = new ArrayList<String>();
				userIds.add(userId);
				userIds.add(0,user.alias);
				user = User.getInstance(userIds);
				session.setAttribute("UserId",user.id);
				Domain d = ofy().load().type(Domain.class).filter("domainName", user.domain).first().now();
				if (d!=null) {
					d.setLastLogin(new Date());
					ofy().save().entity(d);
				}
			}
			Date now = new Date();
			Date eightHoursAgo = new Date(now.getTime()-28800000L);
			if (user.lastLogin.before(eightHoursAgo)) {
				user.lastLogin = now;
				user.alias = null;  // in case alias is set to "" or to invalid userId
				ofy().save().entity(user);
			}
			if (!verify || !user.use2FactorAuth || session.getAttribute("Code")!=null || Long.parseLong(user.smsMessageDevice.substring(5,15))>0L) return user;
		} catch (Exception e) {
			if (User.isAnonymous(session)) {
				user = new User(userId);
				ofy().save().entity(user);
				return user;
			}
		}
		return null;
	}

	static User getInstance(List<String> userIds) {
		try {
			User user = ofy().load().type(User.class).id(userIds.get(0)).safe();			
			if (user.alias != null && !user.alias.isEmpty() && !userIds.contains(user.alias)) {
				userIds.add(0,user.alias);
				user = User.getInstance(userIds);  // follow the alias chain one more link
			}
			return user;
		} catch (Exception e) {
			return ofy().load().type(User.class).id(userIds.get(1)).now();
		}
	}

	static User createUserServiceUser(com.google.appengine.api.users.User u) {
		if (u == null) return null;
		User user = null;
		try {
			user = new User(u.getUserId());
			user.authDomain = "Google";
			user.setEmail(u.getEmail());
			user.verifiedEmail = !(user.email==null || user.email.isEmpty());
			if (user.verifiedEmail) { // search for any accounts with the same email address and alias the new one to it
				User twin = ofy().load().type(User.class).filter("email", user.email).first().now();
				if (twin != null && twin.verifiedEmail) user.alias = twin.id;
				String myDomainName = extractDomain(user.email);
				do { // try to assign a user to an existing ChemVantage domain by checking the authDomain or super domain
					try { 
						Domain d = ofy().load().type(Domain.class).filter("domainName",myDomainName).first().now();
						user.domain = d.domainName;
					} catch (Exception e) {
						user.domain = null;  // user is a free agent and can join any ChemVantage group
					}	
					myDomainName = myDomainName.substring(myDomainName.indexOf('.')+1);  // removes first subdomain name and period
				} while (user.domain==null && myDomainName.indexOf('.') >= 0);
			}
			ofy().save().entity(user);
		} catch (Exception e) {
			return null;
		}
		return user;
	}

	static User createBLTIUser(HttpServletRequest request) {
		// this method provisions a new account for a BLTI user
		String user_id = request.getParameter("user_id");
		String userId = request.getParameter("oauth_consumer_key") + (user_id==null?"":":"+user_id);
		User user = new User(userId);
		user.authDomain = "BLTI";
		user.domain = request.getParameter("oauth_consumer_key");
		user.alias = null;
		
		String lis_person_name_given = request.getParameter("lis_person_name_given");
		if (lis_person_name_given==null) lis_person_name_given = request.getParameter("custom_lis_person_name_given");
		if (lis_person_name_given==null) lis_person_name_given = request.getParameter("lis_person_name_full");
		if (lis_person_name_given==null) lis_person_name_given = request.getParameter("custom_lis_person_name_full");
		user.setFirstName(lis_person_name_given);
		
		String roles = request.getParameter("roles");
		if (roles!=null) {
			roles = roles.toLowerCase();
			if (roles.contains("instructor")) user.setIsInstructor(true);
			if (roles.contains("administrator")) user.setIsAdministrator(true);
			if (user.isInstructor() || user.isAdministrator()) {
				String lis_person_name_family = request.getParameter("lis_person_name_family");
				if (lis_person_name_family==null) lis_person_name_family = request.getParameter("custom_lis_person_name_family");
				user.setLastName(lis_person_name_family);
			}
		}

		String lis_person_contact_email_primary = request.getParameter("lis_person_contact_email_primary");
		if (lis_person_contact_email_primary==null) lis_person_contact_email_primary = request.getParameter("custom_lis_person_contact_email_primary");
		user.setEmail(lis_person_contact_email_primary);

		if (!user.email.isEmpty()) user.verifiedEmail = true; // value supplied by institution
		
		user.setPremium(true);  // all LTI users have premium accounts by default
		ofy().save().entity(user);
		return user;
	}

	static public User createOpenIdUser(UserInfo userInfo) {
		User user;
		try {
			String userId = userInfo.getClaimedId();
			user = ofy().load().type(User.class).id(userId).now();
			if (user != null) return user;
			user = new User(userId);
			user.authDomain = extractDomain(userInfo.getClaimedId());
			if (user.authDomain.contains("google.com")) user.authDomain="gmail.com";
			String myDomainName = user.authDomain;
			do { // try to assign a user to an existing ChemVantage domain by checking the authDomain or super domain
				try { 
					Domain d = ofy().load().type(Domain.class).filter("domainName",myDomainName).first().now();
					user.domain = d.domainName;
				} catch (Exception e) {
					user.domain = null;  // user is a free agent and can join any ChemVantage group
				}	
				myDomainName = myDomainName.substring(myDomainName.indexOf('.')+1);  // removes first subdomain name and period
			} while (user.domain==null && myDomainName.indexOf('.') >= 0);
			user.setEmail(userInfo.getEmail());
			user.verifiedEmail = !(user.email==null || user.email.isEmpty());
			user.setFirstName(userInfo.getFirstName());
			user.setLastName(userInfo.getLastName());
			ofy().save().entity(user);
			if (user.verifiedEmail) {
				Query<User> twins = ofy().load().type(User.class).filter("email",user.email);
				for (User t : twins) if (!t.id.equals(user.id)) Admin.mergeAccounts(user, t);
			}
		} catch (Exception e) {
			return null;
		}		
		return user;
	}

	static public User createGooglePlusUser(JSONObject payload,String firstName) {
		User user = null;
		try {
			String userId = payload.getString("sub");
			if (userId != null) user = ofy().load().type(User.class).id(userId).now();
			if (user != null) return user;
			user = new User(userId);
			String email = payload.getString("email");
			if (email!=null) {
				user.setEmail(email);
				user.verifiedEmail = payload.getBoolean("email_verified");
			}
			user.setFirstName(firstName);
			
			user.authDomain = "google.com";
		
			if (payload.containsKey("hd")) user.authDomain = payload.getString("hd");
			else if (payload.containsKey("iss")) user.authDomain = payload.getString("iss");
			else user.authDomain = "accounts.google.com";
			
			String myDomainName = User.extractDomain(email);
			do { // try to assign a user to an existing ChemVantage domain by checking the authDomain or super domain
				try { 
					Domain d = ofy().load().type(Domain.class).filter("domainName",myDomainName).first().now();
					user.domain = d.domainName;
				} catch (Exception e) {
					user.domain = null;  // user is a free agent and can join any ChemVantage group
				}	
				myDomainName = myDomainName.substring(myDomainName.indexOf('.')+1);  // removes first subdomain name and period
			} while (user.domain==null && myDomainName.indexOf('.') >= 0);
	
			ofy().save().entity(user);
			
			if (user.verifiedEmail) {
				Query<User> twins = ofy().load().type(User.class).filter("email",user.email);
				for (User t : twins) if (!t.id.equals(user.id)) Admin.mergeAccounts(user, t);
			}
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
			return null;
		}		
		return user;
	}
	
	static public User createCASUser(String userId,String email,String domain) {
		if (userId == null) return null;
		User user;
		try {
			user = new User(userId);
			user.authDomain = "CAS";
			user.domain = domain;
			user.setEmail(email);
			user.verifiedEmail = !user.email.isEmpty();
			ofy().save().entity(user);
		} catch (Exception e) {
			return null;
		}		
		return user;
	}

	static boolean isAnonymous(HttpSession session) {
		try {
			String userId = session.getAttribute("UserId").toString();
			if (userId.startsWith("anonymous")) return true;
		} catch (Exception e) {}
		return false;
	}
	
	boolean isAnonymous() {
		if (this.id.startsWith("anonymous")) return true;
		else return false;
	}
	
	static String extractDomain(String claimedId) {
		if (claimedId==null || claimedId.isEmpty()) return "";
		StringBuffer domain = new StringBuffer(claimedId.toLowerCase().trim());
		domain = domain.delete(0, domain.indexOf("@")+1);    // strips username from email address
		if (domain.indexOf("//")>=0) domain = domain.delete(0, domain.indexOf("//")+2);   // strips http:// or https:// from beginning
		if (domain.indexOf("/")>=0) domain = domain.delete(domain.indexOf("/"),domain.length());      // strips URI from end
		return domain.toString();
	}

	public String getId() {
		return this.id;
	}

	static String getBothNames(String id) {
		if (id==null || id.isEmpty()) return "missing user id";
		try {
			return ofy().load().type(User.class).id(id).now().getBothNames();
		} catch (Exception e) {
			return "User " + id + " not found";
		}
	}

	void clean() {
		if (firstName==null) firstName = "";
		if (lastName==null) lastName = "";
		setLowerCaseName();
		try {
			if (email==null || email.isEmpty() || !email.contains("@")) throw new Exception();
			new InternetAddress(email).validate();
			email = email.toLowerCase();
		} catch (Exception e) {
			verifiedEmail = false;
			email = "";
		}
		if (lastLogin==null) lastLogin = new Date();
		if (smsMessageDevice==null) smsMessageDevice = "";
		//authDomain = id.contains(":")?"BLTI":"gmail.com";
		alias = (alias==null || alias.isEmpty())?null:alias;
		//if (alias != null) myGroupId=0;
		ofy().save().entity(this);
	}


	boolean needsEmail() {
		if (email==null || email.isEmpty()) return true;
		else return false;
	}

	void setDomain(String d) {
		this.domain = null;
		try {
			if (d!=null && !d.isEmpty()) {
				Domain newDomain = ofy().load().type(Domain.class).filter("domainName",d).first().now();
				this.domain = newDomain.domainName;
			}
		}catch (Exception e) {
		}
	}

	String getEmail() {
		return this.email==null?"":this.email;
	}
		
	static String getEmail(String id) {
		try {
			return ofy().load().type(User.class).id(id).now().email;
		} catch (Exception e) {
			return "";
		}
	}

	void setEmail(String em) {
		try {
			new InternetAddress(em).validate();
			if (em.indexOf("@")<0) throw new Exception();
			this.email = em.toLowerCase();
		} catch (Exception e) {
			this.email = "";
		}
	}

	boolean requiresUpdatesNow() {
		try {
			Group g = ofy().load().type(Group.class).id(this.myGroupId).safe();
			if (g.isUsingLisOutcomeService) return false;
		} catch (Exception e) {
		}
		Date now = new Date();
		Date eightHoursAgo = new Date(now.getTime()-28800000L);
		return this.lastLogin.before(eightHoursAgo) && this.requiresUpdates();
	}

	boolean requiresUpdates() {
		try {
			if (firstName.isEmpty() || email.isEmpty() || !verifiedEmail) return true;  // note: lastName no longer required
			if (this.hasPremiumAccount() && myGroupId < 0) {  // new user has not joined a group yet
				Query<Group> allGroups = null;  // count the available groups to join
				if (this.domain != null && !this.domain.isEmpty()) allGroups = ofy().load().type(Group.class).filter("domain",this.domain);
				else allGroups = ofy().load().type(Group.class);
				if (allGroups.count() > 0) return true;  // needs update if there is at least one group available
				else return false;  // no groups yet; don't bother
			} else return false;    // user has already joined a group or chosen not to join
		} catch (Exception e) {
			return true;  // unexpected error; please check the profile just in case
		}
	}

	String getFullName() {
		setLowerCaseName();
		return this.lastName + (this.lastName.isEmpty()?"":", ") + this.firstName;
	}

	String getBothNames() {
		setLowerCaseName();
		String bothNames = firstName + (lastName.isEmpty()?"":" " + lastName);
		return bothNames.isEmpty()?"anonymous":bothNames;
	}

	public void setLastLogin() {
		this.lastLogin = new Date();
	}

	boolean needsFirstName() {
		if (firstName==null || firstName.isEmpty()) return true;
		else return false;
	}

	boolean needsLastName() {
		if (lastName==null || lastName.isEmpty()) return true;
		else return false;
	}

	String getFirstName() {
		return firstName==null?"":this.firstName;
	}
	
	void setFirstName(String fn) {
		if (fn == null) this.firstName = "";
		else this.firstName = fn.trim();
		setLowerCaseName();
	}

	String getLastName() {
		return this.lastName==null?"":this.lastName;
	}
	
	void setLastName(String ln) {
		if (ln == null) this.lastName = "";
		else this.lastName = ln.trim();
		setLowerCaseName();
	}

	void setLowerCaseName() {
		String tmp = this.lowercaseName;
		if (this.lastName==null || this.lastName.isEmpty()) this.lastName = "";
		if (this.firstName==null || this.firstName.isEmpty()) this.firstName = "";
		this.lowercaseName = this.lastName + (!this.lastName.isEmpty()&&!this.firstName.isEmpty()?", ":"") + this.firstName;
		this.lowercaseName = this.lowercaseName.toLowerCase().trim();
		if (!this.lowercaseName.equals(tmp)) ofy().save().entity(this);  // save User object if lowercaseName changed
	}

	void setAlias(String newId) {
		this.alias = newId;
	}

	List<QuizTransaction> getQuizTransactions() {
		return ofy().load().type(QuizTransaction.class).filter("userId",this.id).order("-score").list();
	}

	List<Key<QuizTransaction>> getQuizTransactionKeys() {
		return ofy().load().type(QuizTransaction.class).filter("userId",this.id).keys().list();
	}

	QuizTransaction getQuizTransaction(Key<QuizTransaction> key) {
		return ofy().load().key(key).now();
	}

	List<HWTransaction> getHWTransactions() {
		return ofy().load().type(HWTransaction.class).filter("userId",this.id).list();
	}

	List<Key<HWTransaction>> getHWTransactionKeys() {
		return ofy().load().type(HWTransaction.class).filter("userId",this.id).keys().list();
	}

	HWTransaction getHWTransaction(Key<HWTransaction> key) {
		return ofy().load().key(key).now();
	}

	List<ExamTransaction> getExamTransactions() {
		return ofy().load().type(ExamTransaction.class).filter("userId",this.id).list();
	}

	List<Key<ExamTransaction>> getExamTransactionKeys() {
		return ofy().load().type(ExamTransaction.class).filter("userId",this.id).keys().list();
	}

	ExamTransaction getExamTransaction(Key<ExamTransaction> key) {
		return ofy().load().key(key).now();
	}

	List<PracticeExamTransaction> getPracticeExamTransactions() {
		return ofy().load().type(PracticeExamTransaction.class).filter("userId",this.id).list();
	}

	List<Key<PracticeExamTransaction>> getPracticeExamTransactionKeys() {
		return ofy().load().type(PracticeExamTransaction.class).filter("userId",this.id).keys().list();
	}

	PracticeExamTransaction getPracticeExamTransaction(Key<PracticeExamTransaction> key) {
		return ofy().load().key(key).now();
	}

	public String getPrincipalRole() {
		String principalRole;		
		if (roles < 1) principalRole = "Student";
		else if (roles < 2) principalRole = "Contributor";
		else if (roles < 4) principalRole = "Editor";
		else if (roles < 8) principalRole = "Teaching Assistant";
		else if (roles < 16) principalRole = "Instructor";
		else if (roles < 32) principalRole = "Administrator";
		else if (roles < 64) principalRole = "ChemVantageAdmin";
		else principalRole = ""; // unknown role
		principalRole += " (" + (premium?"premium":"basic") + ")";
		return principalRole;
	}

	public String getDecoratedRole() {
		String principalRole;		
		if (roles < 1) principalRole = "Student";
		else if (roles < 2) principalRole = "Contributor";
		else if (roles < 4) principalRole = "Editor";
		else if (roles < 8) principalRole = "Teaching Assistant";
		else if (roles < 16) principalRole = "Instructor";
		else if (roles < 32) principalRole = "Administrator";
		else if (roles < 64) principalRole = "ChemVantageAdmin";
		else principalRole = ""; // unknown role

		int level = 0;
		List<QuizTransaction> quizTransactions = ofy().load().type(QuizTransaction.class).filter("userId",this.id).list();
		HashSet<Long> topicIds = new HashSet<Long>();  // HashSet is like a List, but does not allow duplicates
		for (QuizTransaction qt : quizTransactions) if (qt.graded!=null) topicIds.add(qt.topicId);  // collects unique topicIds
		level += topicIds.size();  // number of unique quiz topics graded
		topicIds.clear();
		List<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",this.id).list();
		for (HWTransaction ht : hwTransactions) topicIds.add(ht.topicId);
		level += topicIds.size();
		principalRole += " - Level " + level;
		switch (level) {
		case (0): principalRole += " (sparrow)"; break;
		case (1): principalRole += " (sea gull)"; break;
		case (2): principalRole += " (dove)"; break;
		case (3): principalRole += " (kestrel)"; break;
		case (4): principalRole += " (sandpiper)"; break;
		case (5): principalRole += " (owl)"; break;
		case (6): principalRole += " (osprey)"; break;
		case (7): principalRole += " (falcon)"; break;
		case (8): principalRole += " (harrier)"; break;
		case (9): principalRole += " (hawk)"; break;
		case (10): principalRole += " (red kite)"; break;
		case (11): principalRole += " (egret)"; break;
		case (12): principalRole += " (bobcat)"; break;
		case (13): principalRole += " (puma)"; break;
		case (14): principalRole += " (lynx)"; break;
		case (15): principalRole += " (janguarundi)"; break;
		case (16): principalRole += " (kodkod)"; break;
		case (17): principalRole += " (ocelot)"; break;
		case (18): principalRole += " (cougar)"; break;
		case (19): principalRole += " (panther)"; break;
		case (20): principalRole += " (cheetah)"; break;
		case (21): principalRole += " (leopard)"; break;
		case (22): principalRole += " (tiger)"; break;
		default: principalRole += " (lion)"; level=23; break;
		}
		principalRole = "<img alt='animal' src=images/animals/" + level + ".jpg><br>" + principalRole;	

		principalRole += " - <a href=/Verification>view account profile</a>";
		return principalRole;
	}

	boolean hasPremiumAccount() {
		return (premium);
	}

	void setPremium(boolean newValue) {
		premium = newValue;
	}

	boolean isAdministrator() {
		return ((roles%32)/16 == 1) || this.isChemVantageAdmin();
	}

	boolean isChemVantageAdmin() {
		return ((roles%64)/32 == 1);
	}
	
	boolean setIsAdministrator(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isAdministrator() && !makeAdmin) {
			roles -= 16;
			return true;
		}
		else if (!isAdministrator() && makeAdmin) {
			roles += 16;
			setPremium(makeAdmin);
			return true;
		}
		else return false; // user already had the requested status; no changes made
	}

	boolean setIsChemVantageAdmin(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isChemVantageAdmin() && !makeAdmin) {
			roles -= 32;
			ofy().save().entity(this);
			return true;
		}
		else if (!isChemVantageAdmin() && makeAdmin) {
			roles += 32;
			ofy().save().entity(this);
			return true;
		}
		else return false; // user already had the requested status; no changes made
	}

	boolean isInstructor() {
		return ((roles%16)/8 == 1) || this.isAdministrator();
	}

	boolean setIsInstructor(boolean makeInstructor) {  // returns true if state is changed; otherwise returns false
		if (isInstructor() && !makeInstructor) {
			roles -= 8;
			return true;
		} else if (!isInstructor() && makeInstructor) {
			roles += 8;
			this.setPremium(true);
			return true;
		} else return false; // user already had the requested status; no changes made

	}

	boolean setIsTeachingAssistant(boolean makeTeachingAssistant) { // returns true if the state is changed; else false
		if (isTeachingAssistant() && !makeTeachingAssistant) {
			roles -= 4;
			return true;
		} else if (!isTeachingAssistant() && makeTeachingAssistant) {
			roles += 4;
			return true;
		} else return false; // user already has the requested status; no changes made		
	}
	
	boolean isTeachingAssistant() {    
		return ((roles%8)/4 == 1);
	}

	boolean isEditor() {
		return ((roles%4)/2 == 1);
	}

	boolean isContributor() {
		return ((roles%2)/1 == 1);
	}
/*
	public int getHWQuestionScore(long questionId) {
		return (ofy().load().type(HWTransaction.class).filter("userId",this.id).filter("questionId",questionId).filter("score >",0).count() == 0?0:1);
	}
*/
	public boolean moreThan1RecentAttempts(long questionId,int minutes) { // for Homework question grading
		try {
			Date minutesAgo = new Date(new Date().getTime()-minutes*60000);
			Query<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("graded >",minutesAgo).filter("userId",this.id).filter("questionId",questionId);
			return (hwTransactions.count() > 1);
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isEligibleForHints(long questionId) {
		// users are eligible for hints on homework questions if they have submitted
		// more than 2 answers more than 15 minutes ago
		try {
			Date FifteenMinutesAgo = new Date(new Date().getTime()-900000);
			Query<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",this.id).filter("questionId",questionId).filter("graded <",FifteenMinutesAgo);
			return (hwTransactions.count() > 2?true:false);
		} catch (Exception e) {
			return false;
		}
	}

	void deleteScores() {
		Query<Score> myScores = ofy().load().type(Score.class).ancestor(this);
		ofy().delete().entities(myScores);
	}

	boolean processPremiumUpgrade(Group newGroup) {
		// this routine converts the user account to premium, if applicable
		try {
			// check out the following line that returns true of domain == null
			// does this allow anyone not in a domain (UserService entry) to join any group for free?
			// test the effect of eliminating this one check and returning false instead to
			// force these users to pay $4.99  Keeps people in line with the LMS and reduces account proliferation

			if (this.hasPremiumAccount() || newGroup == null) return true;
			else if (this.domain == null) return false;

			Domain domain = ofy().load().type(Domain.class).filter("domainName", this.domain).first().now();
			if (domain == null || newGroup==null || !newGroup.domain.equals(this.domain)) return false;
			if (domain.seatsAvailable > 0) {
				this.setPremium(true);
				domain.seatsAvailable--;
				ofy().save().entity(domain);
			} else return false;
			ofy().save().entity(this);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void changeGroups(long newGroupId) {
		if (newGroupId == this.myGroupId) {  // no change needed; just verify that user is listed as a group member
			if (this.myGroupId == 0) return;
			Group g = ofy().load().type(Group.class).id(myGroupId).now();
			if (!g.memberIds.contains(this.id)) {
				g.memberIds.add(this.id);
				ofy().save().entity(g).now();
			}
			return;
		}
		// User is attempting to change groups:
		try {
			Group oldGroup = myGroupId>0?ofy().load().type(Group.class).id(myGroupId).now():null;
			if (oldGroup != null) {
				oldGroup.memberIds.remove(this.id);
				ofy().save().entity(oldGroup).now();
			}
			Group newGroup = newGroupId>0?ofy().load().type(Group.class).id(newGroupId).now():null;
			if (newGroup != null) {
				if (!newGroup.memberIds.contains(this.id)) {
					newGroup.memberIds.add(this.id);
					ofy().save().entity(newGroup).now();
				}
			}
			this.myGroupId = newGroup==null?0:newGroupId;
			ofy().save().entity(this).now();
		} catch (Exception e) {
		}
		deleteScores();
	}

    Score getScore(Assignment assignment) {
    	try {
    		Key<Score> k = Key.create(Key.create(User.class,this.id),Score.class,assignment.id);
    		Score s = ofy().load().key(k).now();
    		if (s==null) {
    			s = Score.getInstance(this.id,assignment);
    			ofy().save().entity(s).now();
    		}
    		return s;
    	} catch (Exception e) {
    		return null;
    	}
    }

	public int compareTo(User other) {
		return this.lowercaseName.compareTo(other.lowercaseName);
	}
}