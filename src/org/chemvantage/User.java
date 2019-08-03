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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.google.common.hash.Hashing;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;

@Entity
public class User {
	@Id 	String id;
	@Index	String domain;
	@Index	Date lastLogin;
	@Index	String cvsToken;
			Date cvsTokenExpires;
			int roles;
			long myGroupId;
			String alias;
			String authDomain;

	User() {}

	User(String id) {
		this.id = id;
		this.roles = 0; // student
		this.lastLogin = new Date(0L);
		this.myGroupId = -1L;
		this.authDomain = "";
		this.alias = null;
	}

	static User getInstance(HttpSession session) {
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
			return user;
		} catch (Exception e) {
			if (User.isAnonymous(session)) {
				user = new User(userId);
				user.lastLogin = new Date(); // now
				ofy().save().entity(user).now();
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

	static boolean isChemVantageAdministrator(HttpSession session) {
		try {
			return User.getInstance(session).isChemVantageAdmin();
		} catch (Exception e) {
			return false;
		}
	}
	
	static User createUserServiceUser(com.google.appengine.api.users.User u) {
		if (u==null) return null;
		User user = null;
		try {
			user = new User(u.getUserId());
			user.authDomain = "Google";
			ofy().save().entity(user);
		} catch (Exception e) {
			return null;
		}
		return user;
	}

	static User createBLTIUser(HttpServletRequest request) {
		// this method provisions a new account for a BLTI user
		String userId = request.getParameter("user_id");
		userId = request.getParameter("oauth_consumer_key") + ":" + (userId==null?"":userId);
		User user = new User(userId);
		user.authDomain = "BLTI";
		user.domain = request.getParameter("oauth_consumer_key");
		user.alias = null;

		String roles = request.getParameter("roles");
		if (roles!=null) {
			roles = roles.toLowerCase();
			if (roles.contains("instructor")) user.setIsInstructor(true);
			if (roles.contains("administrator")) user.setIsAdministrator(true);
		}

		user.setLastLogin();
		ofy().save().entity(user).now();
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
		try {
			return id.startsWith("anonymous");
		} catch (Exception e) {
			return false;
		} 
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
	
	public String getIdHash() {
		return Hashing.sha256().hashString(this.id, StandardCharsets.UTF_8).toString().substring(0,15);
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

	public void setLastLogin() {
		this.lastLogin = new Date();
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
		try {
			return ((roles%16)/8 == 1) || this.isAdministrator();
		} catch (Exception e) {
			return false;
		}
	}

	boolean setIsInstructor(boolean makeInstructor) {  // returns true if state is changed; otherwise returns false
		if (isInstructor() && !makeInstructor) {
			roles -= 8;
			return true;
		} else if (!isInstructor() && makeInstructor) {
			roles += 8;
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
    
    boolean setCvsToken(Date exp) {
    	// this sets a temporary CvsToken user credential
    	if (exp==null) return false;
    	try {
    		cvsToken = Long.toHexString(new Random().nextLong());
    		cvsTokenExpires = exp;
    		ofy().save().entity(this).now();
    		return true;
    	} catch (Exception e) {
    		return false;
    	}
    }
    
    String getCvsToken() {   // ChemVantage session token to track users when HttpSession not persisted and to prevent CSRF attacks
    	Date now = new Date();
    	Date exp = new Date(now.getTime() + 5400000L);  // 90 minutes from now
    	try {
    		if (cvsToken==null || cvsTokenExpires==null || cvsTokenExpires.before(now)) { // create a new token
    			if (setCvsToken(exp)) return cvsToken;
    			else return null;
    		} else {  // token is valid; return as is or update the exp time:
    			long millisRemaining = cvsTokenExpires.getTime() - now.getTime();
    			if (millisRemaining>3600000L) return cvsToken;  // token is still valid for >60 min
    			else {  // update the expiration Date
    				cvsTokenExpires = exp;  
    				ofy().save().entity(this);
    			}
    		}
    	} catch (Exception e) {
    		return null;
    	}
    	return cvsToken;
    }
    
    boolean destroyCvsToken() { // returns true if save() operation succeeds
    	cvsToken = null;
    	cvsTokenExpires = null;
    	return Key.create(this).equals(ofy().save().entity(this).now());
   }
    
    static User getUser(String cvs) {
    	if (cvs==null) return null;
    	try {
    		User u = ofy().load().type(User.class).filter("cvsToken",cvs).first().now();
    		Date now = new Date();
    		if (u.cvsTokenExpires.after(now)) {
    			u.getCvsToken();  // freshens the token, if needed
    			return u;
    		}
    	} catch (Exception e) {
    	}
    	return null;
    }
}