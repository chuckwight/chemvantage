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
import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.googlecode.objectify.cmd.Query;


public class User {
	String 	id;                    // stored with transactions, responses, userReports
	String 	email;                 // optional
	String 	lis_result_sourcedid;  // used only by LTIv1p1 users
	long	assignmentId = 0L;     // used only for LTI users
	int 	roles = 0;             // student
	public String 	token = null;          // cross-site request fraud (CSRF) token
	
	User() {}
	
	User(String id) {
		this.id = id;
	}

	User(String id, String email, String lis_result_sourcedid, long assignmentId, int roles) {
		this.id = id;
		this.email = email;
		this.lis_result_sourcedid = lis_result_sourcedid;
		this.assignmentId = assignmentId;
		this.roles = roles;
		try {
			setToken();
		} catch (Exception e) {
		}		
	}
	
	static String getRawId(String userId) {
		try {  // v1p3: strip the platform_id and "/" from the front of the userId
			return new URI(userId).getRawPath().substring(1);
		} catch (Exception e) {
			return null;
		}
	}
	
	boolean isAnonymous() {
		try {
			return id.startsWith("anonymous");
		} catch (Exception e) {
			return false;
		} 
	}

	boolean isChemVantageAdmin() {
		return ((roles%64)/32 == 1);
	}
	
	boolean isAdministrator() {
		return ((roles%32)/16 == 1 || this.isChemVantageAdmin());
	}

	boolean isInstructor() {
		return ((roles%16)/8 == 1 || this.isAdministrator());
	}

	boolean isTeachingAssistant() {    
		return ((roles%8)/4 == 1);
	}

	boolean isEditor() {
		return ((roles%4)/2 == 1 || this.isAdministrator());
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

	/*
	 * Every user is identified by a Java Web Token (JWT), which is cryptographically signed but not stored, and must be passed from page to page
	 * in order to authenticate and authorize the user. The token stores the following information:
	 * 1) userId - combination of the oauth_consumer_key (or platform_id) and the ID provided by the LMS; or anonymousXXXXXX issued by ChemVantage
	 * 2) assignmentId - only for LTI users (requires new launch to migrate to a different assignment)
	 * 3) roles - used to identify LTI instructors, teaching assistants and administrators
	 * 4) lis_result_sourcedid - LTIv1.1 pointer to a LMS grade book cell
	 * 5) email - not implemented yet, used to provide response to feedback
	 */
	
	public static User getUser(String token) {
    	if (token==null) return null;
    	try {
    		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
    		JWT.require(algorithm).build().verify(token);  // checks validity of token
    		DecodedJWT t = JWT.decode(token);
    		User u = new User();
    		u.id = t.getSubject();
    		if (!t.getClaim("email").isNull()) u.email = t.getClaim("email").asString();    		
    		if (!t.getClaim("roles").isNull()) u.roles = t.getClaim("roles").asInt();
    		if (!t.getClaim("aid").isNull()) u.assignmentId = t.getClaim("aid").asLong();
    		if (!t.getClaim("lrs").isNull()) u.lis_result_sourcedid = t.getClaim("lrs").asString();
    		/*
    		Date in15Min = new Date(new Date().getTime()+900000L);  
    		if (t.getExpiresAt().before(in15Min)) u.setToken();  // refresh the token for 90 min expiration
    		else u.token = token;  // no refresh required
    		*/
    		u.token = token;
    		return u;
    	} catch (Exception e) {
    	}
    	return null;
    }
  
	void setToken() {
		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
		Date now = new Date();
    	Date exp = new Date(now.getTime() + 5400000L);  // 90 minutes from now
    	
    	Builder b = JWT.create()
    			.withSubject(this.id)   // required
    			.withExpiresAt(exp);    // required
    			
    	if (this.roles>0) b = b.withClaim("roles", this.roles);
    	if (this.assignmentId>0L) b = b.withClaim("aid", this.assignmentId);
    	if (this.email != null) b = b.withClaim("email", this.email);
    	if (this.lis_result_sourcedid != null) b = b.withClaim("lrs", this.lis_result_sourcedid);
    	
    	this.token = b.sign(algorithm);
	}

	void setAssignment(long assignmentId,String lis_result_sourcedid) {  // LTI v1.1
		this.assignmentId = assignmentId;
		this.lis_result_sourcedid = lis_result_sourcedid;
		setToken();
	}
	
	void setAssignment(long assignmentId) {  // LTI Advantage
		this.assignmentId = assignmentId;
		setToken();
	}
	
    public long getAssignmentId() {
     	try {
    		return JWT.decode(this.token).getClaim("aid").asLong();  // assignmentId
    	} catch (Exception e) {    		
    		return 0L;
    	}
    }

    String getLisResultSourcedid() {
    	try {
    		String lis_result_sourcedid = JWT.decode(this.token).getClaim("lrs").asString();
    		if (lis_result_sourcedid == null || lis_result_sourcedid.isEmpty()) return null;
    		return lis_result_sourcedid;
    	} catch (Exception e) {
    	}
    	return null;
    }
    
    boolean validToken() {
    	if (this.token== null) return false;
    	try {
    		Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
    		JWT.require(algorithm).build().verify(this.token);
    		return true;
    	} catch (Exception e) {}
    	return false;
    }
    
    String getTokenSignature() {
    	if (this.token == null) return "unavailable";
    	DecodedJWT t = JWT.decode(token);
		return t.getSignature();
     }
    
    boolean signatureIsValid(String sig) {
    	if (this.token==null || sig==null) return false;
    	return validToken() && token.contains(sig);
    }
}