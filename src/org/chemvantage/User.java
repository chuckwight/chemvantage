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
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.cmd.Query;


public class User {
	@Id 	String id;
	String 	token;
	int 	roles;
	
	User() {}

	User(String id) {
		this.id = id;
		this.roles = 0; // student
	}

	User(String id, int roles) {
		this.id = id;
		this.roles = roles;
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
  
	void setToken() throws Exception {
		setToken(0L);	
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