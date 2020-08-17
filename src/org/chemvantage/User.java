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
import java.util.Random;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;

@Entity
public class User {
	@Id 	Long 	sig;
	@Index 	String 	id;                    // stored with transactions, responses, userReports
	@Index	Date 	exp;				   // max 90 minutes from now
			String 	lis_result_sourcedid = null;  // used only by LTIv1p1 users
			long	assignmentId = 0L;     // used only for LTI users
			int 	roles = 0;             // student
	
	User() {
		do {
			sig = Long.valueOf(Math.abs(new Random().nextInt()));
		} while (ofy().load().type(User.class).id(sig).now() != null);  // guarantees that sig is unique
		id = "anonymous" + sig;
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now			
	}
	
	User(String id) {
		this.id = id;
		if (this.isAnonymous()) this.sig = Long.parseLong(id.substring(9));
		
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now
	}

	User(String id, String email, String lis_result_sourcedid, long assignmentId, int roles) {
		this.id = id;
		this.lis_result_sourcedid = lis_result_sourcedid;
		this.assignmentId = assignmentId;
		this.roles = roles;
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now
	}
	
	public static User getUser(String sig) {
    	if (sig==null) return null;
    	User user = null;
    		
		try {  // try to find the User entity in the datastore
    		user = ofy().load().type(User.class).id(Long.parseLong(sig)).safe();
    		Date now = new Date();
        	Date in15min = new Date(now.getTime() + 900000L);
    		if (user.exp.before(now)) return null; // entity has expired
    		if (user.exp.before(in15min)) { // extend the exp time
    			Date in90min = new Date(now.getTime() + 5400000L);
    			user.exp = in90min;
    			ofy().save().entity(user);
    		}
    		return user;
    	} catch (Exception e) { // retrieve an anonymous User entity
    		if (Long.parseLong(sig) <= Integer.MAX_VALUE) { // all legitimate anonymous users have a random Integer sig value
    			user = new User("anonymous" + sig);
    			return user;
    		} else return null;
    	}
    }
  
static String getRawId(String userId) {
		try {  // v1p3: strip the platform_id and "/" from the front of the userId
			return new URI(userId).getRawPath().substring(1);
		} catch (Exception e) {
			return null;
		}
	}
	
	public String getId() {
		return id;
	}
	
	public boolean isAnonymous() {
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

	public boolean isInstructor() {
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

	public String getTokenSignature() {
		return String.valueOf(sig);
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
	
	void setToken() {
		try {
			User u = ofy().load().type(User.class).filter("id",this.id).first().safe();
			if (u.exp.after(new Date())) this.sig = u.sig;
			else ofy().delete().entity(u);
		} catch (Exception e) {			
		}
		ofy().save().entity(this).now();
	}
	
    public long getAssignmentId() {
    	return assignmentId;
    }

    public String getLisResultSourcedid() {
   		return lis_result_sourcedid;
    }
}