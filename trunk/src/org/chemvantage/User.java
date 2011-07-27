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

import com.google.appengine.api.datastore.QueryResultIterator;
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
		this.myGroupId = 0;
		this.smsMessageDevice = "";
		this.notifyDeadlines = false;
		this.verifiedEmail = false;
		this.authDomain = "google.com";
		this.alias = null;
	}

	static User getInstance(HttpSession session) {
		UserService userService = UserServiceFactory.getUserService();
		String userId = (String)session.getAttribute("UserId");
		if (userId==null) {
			try {  // Google authentication
				userId = userService.getCurrentUser().getUserId();
			} catch (Exception e) {  // invalid user or lost BLTI session
				return null;
			}
		}
		
		//  from here on, userId should be valid
		Objectify ofy = ObjectifyService.begin();
		User user = null;
		try {
			do {
				user = ofy.get(User.class,userId); // retrieve User attributes from datastore if it exists
				userId = user.alias==null?userId:user.alias;
			} while (user.alias!=null);
		} catch (Exception e) {  // falls to here when datastore call fails or user is at the end of hte alias chain
		}
		
		// this section converts nickname userIds to permanent Google userId strings
		if (user==null) {
			try {
				String oldUserId = userService.getCurrentUser().getNickname();
				User oldUser = ofy.get(User.class,oldUserId);
				user = ofy.get(User.class,oldUserId);
				user.id = userId;
				Admin.mergeAccounts(user,oldUser);
				ofy.delete(oldUser);
			} catch (Exception e) {
				user = new User(userId);  // user is not in datastore; create a new one
			}
		}
		
		try {  // try to clean up some unverified accounts
			if (!user.verifiedEmail && userService.isUserLoggedIn() && !userService.isUserAdmin()) {
				String email = userService.getCurrentUser().getEmail();
				if (!email.isEmpty()) {
					user.email = email;
					user.verifiedEmail = true;
					user.authDomain = "google.com";
				}
			}
			InternetAddress addr = new InternetAddress(user.email);
			addr.validate();
		} catch (Exception e) {
			user.setEmail("");
			user.verifiedEmail = false;
		}
		
		try { // determine if this person is an administrator (Google login required)
			if (userId.equals(userService.getCurrentUser().getUserId())) user.setIsAdministrator(userService.isUserAdmin());
		} catch (Exception e) {
			user.setIsAdministrator(false);
		}
		
		// update the lastLogin date only if everything is OK; otherwise the Verification page will stop the user
		if (!user.lastName.isEmpty() && !user.firstName.isEmpty() && user.verifiedEmail) user.lastLogin = new Date();
		
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
		
		if (user.verifiedEmail) {  // this section seeks to merge accounts with the same verified email address
			try {
				Query<User> dupUsers = ofy.query(User.class).filter("email",user.email).filter("verifiedEmail",true);
				if (dupUsers.count() > 1) {
					// find the Google account (keeper) and merge all other accounts with it, leaving alias for redirection
					User keeper = null;
					QueryResultIterator<User> it = dupUsers.iterator();
					do {keeper = it.next();} while (!keeper.authDomain.equals("google.com"));
					for (User u : dupUsers) {
						if (u.id!=keeper.id && u.verifiedEmail) Admin.mergeAccounts(keeper,u);
					}
				}
			} catch (Exception e) {
				// don't worry
			}
		}
		
		return user;
	}
	
	static User createNew(HttpServletRequest request) {
		// this method provisions a new account for a BLTI user
		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		String user_id = request.getParameter("user_id");
		String userId = oauth_consumer_key + (user_id==null?"":":"+user_id);
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
		return ObjectifyService.begin().find(User.class,id).getBothNames();	
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
		authDomain = id.contains(":")?"BLTI":"google.com";
		alias = (alias==null || alias.isEmpty())?null:alias;
		if (alias != null) myGroupId=0;
		ofy.put(this);
	}
	
	void setEmail(String em) {
		try {
			new InternetAddress(em).validate();
			this.email = em;
		} catch (Exception e) {
			this.email = "";
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
		else if (!isInstructor() && makeInstructor) roles += 8;
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
		try {
			Date OneHourAgo = new Date(new Date().getTime()-3600000);
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",this.id).filter("questionId",questionId).filter("graded <",OneHourAgo);
			return (hwTransactions.count() > 2?true:false);
		} catch (Exception e) {
			return false;
		}
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
				g.deleteScores(this.id);
				ofy.put(g);
			}
		}
	}

	public int compareTo(User other) {
		return this.lowercaseName.compareTo(other.lowercaseName);
	}
}