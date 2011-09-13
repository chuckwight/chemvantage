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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.internet.InternetAddress;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class User implements Comparable<User>,Serializable {

	private static final long serialVersionUID = 137L;
	@Id String id;
	String email;
	String lastName;
	String lowercaseName;
	String firstName;
	int roles;
	boolean premium;
	Date lastLogin;
	long myGroupId;
	String smsMessageDevice;
	boolean notifyDeadlines;
	boolean verifiedEmail;
	String alias;
	String authDomain;
	
	@Transient transient Objectify ofy = ObjectifyService.begin();

	User() {}

	User(String id) {
		this.id = id;
		this.firstName = "";
		this.lastName = "";
		setLowerCaseName();
		this.email = "";
		this.roles = 0; // student
		this.premium = false;
		this.lastLogin = new Date(0);
		this.myGroupId = 0L;
		this.smsMessageDevice = "";
		this.notifyDeadlines = false;
		this.verifiedEmail = false;
		this.authDomain = "";
		this.alias = null;
	}

	static User getInstance(HttpSession session) {
		UserService userService = null;
		com.google.appengine.api.users.User googleUser = null;
		String userId = (String)session.getAttribute("UserId");
		boolean freshLogin = userId==null?true:false;;
		
		if (freshLogin) {
			try {  // Google authentication
				googleUser = UserServiceFactory.getUserService().getCurrentUser();
				userId = googleUser.getUserId();
			} catch (Exception e) {  // invalid user or lost BLTI session
				return null;
			}
		}
		
		//  from here on, userId should be valid
		Objectify ofy = ObjectifyService.begin();
		User user = null;
		try {
			List<String> aliasChain = new ArrayList<String>();
			do {  // this loop finds the end of the alias chain without going into an infinite loop
				user = ofy.get(User.class,userId); // retrieve User attributes from datastore if it exists
				if (user.alias != null) {
					aliasChain.add(user.id);
					userId = user.alias;
				}
			} while (user.alias!=null && !aliasChain.contains(userId));
		} catch (Exception e) {  // falls to here when datastore call fails
			user = new User(userId);
			userService = UserServiceFactory.getUserService();
			googleUser = userService.getCurrentUser();
			user.authDomain = googleUser.getAuthDomain();
			user.email = googleUser.getEmail();
			user.setIsAdministrator(userService.isUserAdmin());
			ofy.put(user);
		}
		session.setAttribute("UserId", userId);

		// update the lastLogin date only if everything is OK; otherwise the Verification page will stop the user
		if (freshLogin && !user.requiresUpdates()) {
			user.lastLogin = new Date();
			try {  // this tests the availability of the datastore and locks the site if necessary
				ofy.put(user); 
				if (Home.announcement.equals(Home.maintenanceAnnouncement)) {
					Home.announcement = "";
					Login.lockedDown = false;
				}
			} catch (com.google.apphosting.api.ApiProxy.CapabilityDisabledException e) {
				Home.announcement = Home.maintenanceAnnouncement;
				Login.lockedDown = true;
			}
		}
		return user;
	}
	
	static User createNew(HttpServletRequest request) {
		// this method provisions a new account for a BLTI user
		String user_id = request.getParameter("user_id");
		String userId = request.getParameter("oauth_consumer_key") + (user_id==null?"":":"+user_id);
		User user = new User(userId);
		user.authDomain = "BLTI";
		user.alias = null;
		user.setFirstName(request.getParameter("lis_person_name_given"));
		user.setLastName(request.getParameter("lis_person_name_family"));
		user.setEmail(request.getParameter("lis_person_contact_email_primary"));
		if (!user.email.isEmpty()) user.verifiedEmail = true; // value supplied by institution
		String userRole = request.getParameter("roles");
		if (userRole!=null) user.setIsInstructor(userRole.toLowerCase().contains("instructor"));
		ObjectifyService.begin().put(user);
		return user;
	}
	
	static String getEmail(String id) {
		User user = ObjectifyService.begin().find(User.class,id);
		return (user==null?"":user.email);
	}
	
	static String getBothNames(String id) {
		try {
			return ObjectifyService.begin().find(User.class,id).getBothNames();
		} catch (Exception e) {
			return "unknown";
		}
			
	}
	
	void clean() {
		if (firstName==null) firstName = "";
		if (lastName==null) lastName = "";
		setLowerCaseName();
		try {
			if (email==null || email.isEmpty() || !email.contains("@")) throw new Exception();
			new InternetAddress(email).validate();
			verifiedEmail = true;
		} catch (Exception e) {
			verifiedEmail = false;
			email = "";
		}
		if (lastLogin==null) lastLogin = new Date();
		if (smsMessageDevice==null) smsMessageDevice = "";
		//authDomain = id.contains(":")?"BLTI":"gmail.com";
		alias = (alias==null || alias.isEmpty())?null:alias;
		if (alias != null) myGroupId=0;
		ofy.put(this);
	}
	
	void setEmail(String em) {
		try {
			new InternetAddress(em).validate();
			if (em.indexOf("@")<0) throw new Exception();
			this.email = em;
		} catch (Exception e) {
			this.email = "";
		}
	}
	
	boolean requiresUpdates() {
		try {
			if (!firstName.isEmpty() && !lastName.isEmpty() && !email.isEmpty()) return false;
			return true;
		} catch (Exception e) {
			return true;
		}
	}
	
	String getFullName() {
		setLowerCaseName();
		return (this.lastName.isEmpty()?" ":this.lastName) + (this.firstName.isEmpty()?" ":", " + firstName);
	}

	String getBothNames() {
		return firstName + " " + lastName;
	}
	
	public void setLastLogin() {
		this.lastLogin = new Date();
	}
	
	void setFirstName(String fn) {
		if (fn==null) return;
		this.firstName = fn;
		setLowerCaseName();
	}

	void setLastName(String ln) {
		if (ln==null) return;
		this.lastName = ln;
		setLowerCaseName();
	}

	void setLowerCaseName() {
		this.lowercaseName = (lastName.length()>0?lastName.toLowerCase():" ") 
		+ (firstName.length()>0?", " + firstName.toLowerCase():" ");
	}
	
	void setAlias(String newId) {
		this.alias = newId;
	}
	
	@SuppressWarnings("unchecked")
	List<QuizTransaction> getQuizTransactions() {
		return (List<QuizTransaction>) ofy.query(QuizTransaction.class).filter("userId",this.id).order("-score");
	}

	@SuppressWarnings("unchecked")
	List<Key<QuizTransaction>> getQuizTransactionKeys() {
		return (List<Key<QuizTransaction>>) ofy.query(QuizTransaction.class).filter("userId",this.id).fetchKeys();
	}
	
	QuizTransaction getQuizTransaction(Key<QuizTransaction> key) {
		return ofy.get(key);
	}

	@SuppressWarnings("unchecked")
	List<HWTransaction> getHWTransactions() {
		return (List<HWTransaction>) ofy.query(HWTransaction.class).filter("userId",this.id);
	}

	@SuppressWarnings("unchecked")
	List<Key<HWTransaction>> getHWTransactionKeys() {
		return (List<Key<HWTransaction>>) ofy.query(HWTransaction.class).filter("userId",this.id).fetchKeys();
	}
	
	HWTransaction getHWTransaction(Key<HWTransaction> key) {
		return ofy.get(key);
	}

	@SuppressWarnings("unchecked")
	List<ExamTransaction> getExamTransactions() {
		return (List<ExamTransaction>) ofy.query(ExamTransaction.class).filter("userId",this.id);
	}

	@SuppressWarnings("unchecked")
	List<Key<ExamTransaction>> getExamTransactionKeys() {
		return (List<Key<ExamTransaction>>) ofy.query(ExamTransaction.class).filter("userId",this.id).fetchKeys();
	}
	
	ExamTransaction getExamTransaction(Key<ExamTransaction> key) {
		return ofy.get(key);
	}

	@SuppressWarnings("unchecked")
	List<PracticeExamTransaction> getPracticeExamTransactions() {
		return (List<PracticeExamTransaction>) ofy.query(PracticeExamTransaction.class).filter("userId",this.id);
	}

	@SuppressWarnings("unchecked")
	List<Key<PracticeExamTransaction>> getPracticeExamTransactionKeys() {
		return (List<Key<PracticeExamTransaction>>) ofy.query(PracticeExamTransaction.class).filter("userId",this.id).fetchKeys();
	}
	
	PracticeExamTransaction getPracticeExamTransaction(Key<PracticeExamTransaction> key) {
		return ofy.get(key);
	}

	public String getPrincipalRole() {
		String principalRole;		
		if (roles < 1) principalRole = "Student";
		else if (roles < 2) principalRole = "Contributor";
		else if (roles < 4) principalRole = "Editor";
		else if (roles < 8) principalRole = "Teaching Assistant";
		else if (roles < 16) principalRole = "Instructor";
		else if (roles < 32) principalRole = "Administrator";
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
		else principalRole = ""; // unknown role
		
		if (myGroupId>0) { //user is a student member of a group
			List<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",myGroupId).list();
			List<Key<Score>> keys = new ArrayList<Key<Score>>();
			for(Assignment a : assignments) keys.add(new Key<Score>(new Key<User>(User.class,id),Score.class,a.id));
			List<Score> scores = new ArrayList<Score>(ofy.get(keys).values());
			int level = 0;  // user level is set to be the number of non-zero assignment scores
			for (Score s : scores) level += (s.score>0?1:0);
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
		}
		principalRole += " - <a href=Upgrade>" + (premium?"premium":"upgrade me") + "</a>";
		return principalRole;
	}

	void setPremium(boolean newValue) {
		premium = newValue;
	}

	boolean isAdministrator() {
		return ((roles%32)/16 == 1);
	}

	void setIsAdministrator(boolean makeAdmin) {
		if (isAdministrator() && !makeAdmin) roles -= 16;
		else if (!isAdministrator() && makeAdmin) roles += 16;
	}
	
	boolean isInstructor() {
		return ((roles%16)/8 == 1);
	}

	void setIsInstructor(boolean makeInstructor) {
		if (isInstructor() && !makeInstructor) roles -= 8;
		else if (!isInstructor() && makeInstructor) {
			roles += 8;
			this.setPremium(true);
		}
		
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

	boolean hasPremiumAccount() {
		return (premium);
	}

	public int getHWQuestionScore(long questionId) {
		return (ofy.query(HWTransaction.class).filter("userId",this.id).filter("questionId",questionId).filter("score >",0).getKey() == null?0:1);
	}

	public boolean moreThan1RecentAttempts(long questionId,int minutes) { // for Homework question grading
		try {
			Date minutesAgo = new Date(new Date().getTime()-minutes*60000);
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("graded >",minutesAgo).filter("userId",this.id).filter("questionId",questionId);
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
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",this.id).filter("questionId",questionId).filter("graded <",FifteenMinutesAgo);
			return (hwTransactions.count() > 2?true:false);
		} catch (Exception e) {
			return false;
		}
	}

	void recalculateScores() {
		Query<Score> myScores = ofy.query(Score.class).ancestor(this);
		ofy.delete(myScores);
	}
	
	public void changeGroups(long newGroupId) {
		if (newGroupId==0 || ofy.find(Group.class,newGroupId) != null) this.myGroupId = newGroupId;
		else this.myGroupId = 0;
		ofy.put(this);
		List<Group> allGroups = ofy.query(Group.class).list();
		for (Group g : allGroups) {
			if (g.id == newGroupId && !g.isMember(this.id)) {
				g.memberIds.add(this.id);
				ofy.put(g);
			} else if (g.id != newGroupId && g.isMember(this.id)) {
				g.memberIds.remove(this.id);
				ofy.put(g);
			}
		}
		recalculateScores();
	}

	public int compareTo(User other) {
		return this.lowercaseName.compareTo(other.lowercaseName);
	}
}