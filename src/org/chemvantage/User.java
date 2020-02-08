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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.hash.Hashing;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;
import com.googlecode.objectify.cmd.QueryKeys;

@Entity
public class User {
	@Id 	String id;
	@Index	String domain;
	@Index	Date lastLogin;
	String token;
	int roles;
	String alias;
	String authDomain;

	User() {}

	User(String id) {
		this.id = id;
		this.roles = 0; // student
		this.lastLogin = new Date(0L);
		this.authDomain = "";
		this.alias = null;
	}

	User(String id, int roles) {
		this.id = id;
		this.roles = roles;
	}
	
	static User getInstance(String userId) {
		try {
			User user = ofy().load().type(User.class).id(userId).safe();
			return user;
		} catch (Exception e) {
		}
		return null;
	}
/*	
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
*/
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
	
	static String getRawId(String userId) {
		try {  // v1p3: strip the platform_id and "/" from the front of the userId
			return new URI(userId).getRawPath().substring(1);
		} catch (Exception e) {  // v1p1: strip the domain name and ":" from the front of the userId
			User u = ofy().load().type(User.class).id(userId).now();
			return userId.substring(u.domain.length()+1);
		}
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
/*
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
*/
	public void setLastLogin() {
		this.lastLogin = new Date();
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

	boolean isChemVantageAdmin() {
		return ((roles%64)/32 == 1);
	}
	
	boolean isAdministrator() {
		return ((roles%32)/16 == 1) || this.isChemVantageAdmin();
	}

	boolean isInstructor() {
		return ((roles%16)/8 == 1) || this.isAdministrator();
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

	boolean setIsChemVantageAdmin(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isChemVantageAdmin() ^ makeAdmin) {
			roles += makeAdmin?+32:-32;
			return true;
		}
		else return false;
	}

	boolean setIsAdministrator(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isAdministrator() ^ makeAdmin) {
			roles += makeAdmin?+16:-16;
			return true;
		}
		else return false;
	}

	boolean setIsInstructor(boolean makeInstructor) {  // returns true if state is changed; otherwise returns false
		if (isInstructor() ^ makeInstructor) {
			roles += makeInstructor?+8:-8;
			return true;
		}
		else return false;		
	}

	boolean setIsTeachingAssistant(boolean makeTeachingAssistant) { // returns true if the state is changed; else false
		if (isTeachingAssistant() ^ makeTeachingAssistant) {
			roles += makeTeachingAssistant?+4:-4;
			return true;
		}
		else return false;
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
		QueryKeys<Score> myScores = ofy().load().type(Score.class).ancestor(this).keys();
		ofy().delete().keys(myScores);
	}

	static User getUser(String token) {
    	if (token==null) return null;
    	try {
    		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
    		JWT.require(algorithm).build().verify(token);  // checks validity of token
    		DecodedJWT t = JWT.decode(token);
    		User u = new User(t.getSubject(),t.getClaim("roles").asInt());
    		Date in15Min = new Date(new Date().getTime()+900000L);  
    		if (t.getExpiresAt().before(in15Min)) { // refresh the token if it will expire within 15 minutes
    			if (t.getClaim("lrs")!=null) u.setToken(t.getClaim("aId").asLong(),t.getClaim("lrs").asString());
    			else u.setToken(t.getClaim("aId").asLong()); 
    		}
    		else u.token = token;  // no refresh required
    		return u;
    	} catch (Exception e) {
    	}
    	return null;
    }
  
    void setToken(long assignmentId) throws Exception {  // stores a CRSF JWT token in the field User.token
    	Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
    	Date now = new Date();
    	Date exp = new Date(now.getTime() + 5400000L);  // 90 minutes from now
    	this.token =  JWT.create()
    			.withSubject(this.id)
    			.withExpiresAt(exp)
    			.withClaim("roles", this.roles)
    			.withClaim("aId", assignmentId)
    			.sign(algorithm); 				
    }

    void setToken(long assignmentId,String lis_result_sourcedid) throws Exception {  // stores a CRSF JWT token in the field User.token
    	if (lis_result_sourcedid == null) setToken(assignmentId);
    	else {
    		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
    		Date now = new Date();
    		Date exp = new Date(now.getTime() + 5400000L);  // 90 minutes from now
    		this.token =  JWT.create()
    				.withSubject(this.id)
    				.withExpiresAt(exp)
    				.withClaim("roles", this.roles)
    				.withClaim("aId", assignmentId)
    				.withClaim("lrs", lis_result_sourcedid)
    				.sign(algorithm); 				
    	}
    }

    Long getAssignmentId() {
     	try {
    		return JWT.decode(this.token).getClaim("aId").asLong();  // assignmentId
    	} catch (Exception e) {    		
    		return null;
    	}
    }
    
    String getLisResultSourcedid() {
    	try {
    		String lis_result_sourcedid = JWT.decode(this.token).getClaim("lrs").asString();
    		if (lis_result_sourcedid == null || lis_result_sourcedid.isEmpty()) return null;
    		return lis_result_sourcedid;
    	} catch (Exception e) {
    		return null;
    	}
    }
}