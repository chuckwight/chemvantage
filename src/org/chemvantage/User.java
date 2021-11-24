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

import java.util.Date;
import java.util.Random;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class User {
	@Id 	Long 	sig;
	@Index 	private String encryptedId;   // stored temporarily in encrypted form
	@Index	Date 	exp;				   // max 90 minutes from now
			String	platformId;			   // URL of the LMS
			String 	lis_result_sourcedid = null;  // used only by LTIv1p1 users
			long	assignmentId = 0L;     // used only for LTI users
			int 	roles = 0;             // student
	
	User() {  // constructor for anonymous user
		Date now = new Date();
		exp = new Date(now.getTime() + 5400000L); // 90 minutes from now
		sig = encode(exp.getTime());
		encryptedId = "anonymous" + String.valueOf(exp.getTime()).hashCode();
	}

	User(String platformId, String id) {  // used for LTI 1.3 launches
		this.platformId = platformId;
		if (id == null) id = "";
		this.sig = ofy().factory().allocateId(User.class).getId();
		this.encryptedId = encryptId(platformId + "/" + id,sig);
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now
	}

	User(String id, String lis_result_sourcedid, long assignmentId, int roles) {
		this.sig = ofy().factory().allocateId(User.class).getId();
		this.encryptedId = encryptId(id,sig);
		this.lis_result_sourcedid = lis_result_sourcedid;
		this.assignmentId = assignmentId;
		this.roles = roles;
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now
	}
	
	public static User getUser(String sig) {  // default token expiration of 90 minutes
    	return getUser(sig,90);
	}
		
	public static User getUser(String sig,int minutesRequired) {   // allows custom expiration for long assignments
		if (sig==null) return null;
    	if (minutesRequired > 500) minutesRequired = 500;
		User user = null;
    	Date now = new Date();
    	Date grace = new Date(now.getTime() + minutesRequired*60000L);  // start of grace period
    	Date expires = new Date(now.getTime() + (minutesRequired+5)*60000L);   // includes 5-minute grace period
			
		try {  // try to find the User entity in the datastore
    		user = ofy().load().type(User.class).id(Long.parseLong(sig)).safe();
    		if (user.exp.before(now)) return null; // entity has expired
    		if (user.exp.before(grace)) { // extend the exp time
    			user.exp = expires;
    			ofy().save().entity(user);
    		}
    		return user;
    	} catch (Exception e) { 
    		return new User(); // retrieve an anonymous User entity
    	}
	}

	static long encode(long encrypt) {
		/*
		 * Weak encoding of the expiration Date takes place in 3 steps using 4 groups of 3 hexdigits (each hexdigit represents 4 bits):
		 * 1 - using the last 3 hex digits as an initialization vector (iv), each group is XORed with the group to its right (after modification). The iv is unmodified.
		 * 2 - the modified long integer is XORed with hexdigits 4-12 of a long resulting from new Random(iv)
		 * 3 - the resulting long is XORed with itself after shifting to the left by 3 hexdigits (12 bits) and masking all but 12 digits
		 * Decoding is done by repeating the exact same operation as encoding
		 */
		try {
			long mask = 0xfffL;
			long iv = encrypt & mask;
			long code;
			for (int i=0;i<3;i++) {  // step 1
				code = (mask & encrypt) << 12;
				encrypt = encrypt ^ code;
				mask = mask << 12;
			}
			mask = 0xfffffffff000L;
			encrypt = encrypt ^ (new Random(iv).nextLong() & mask); // step 2
			mask = 0xfffffffffL;
			code = (encrypt & mask) << 12;
			encrypt = encrypt ^ code;  // step 3
		} catch (Exception e) {
			return 0;
		}
		return encrypt;
	}

	public String getId() {   // public method to support JSP files to receive unhashed userId value
		return decryptId(encryptedId,sig);
	}
	
	public String getHashedId() {  // public method to support JSP files to retrieve hashed userId value
		return Subject.hashId(this.getId());
	}
	
	public boolean isAnonymous() {
		try {
			return encryptedId.startsWith("anonymous");
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
			int nAttempts = ofy().load().type(HWTransaction.class).filter("userId",this.getHashedId()).filter("questionId",questionId).filter("graded <",FifteenMinutesAgo).count();
			return (nAttempts > 2?true:false);
		} catch (Exception e) {
			return false;
		}
	}

	public String getTokenSignature() {
		if (this.isAnonymous()) return Long.toHexString(sig);  // weakly-encrypted hexadecimal exp Date
		else return String.valueOf(sig);     // String version of @Id value of User in the datastore
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
		if (this.isAnonymous()) return;
		else ofy().save().entity(this).now();
	}
	
    public long getAssignmentId() {
    	return assignmentId;
    }

    public String getLisResultSourcedid() {
   		return lis_result_sourcedid;
    }
 
    String decryptId(String id, long sig) {  // encryption and decryption use the same method
    	return encryptId(id,sig);
    }
    
    String encryptId(String id, long sig) {
    	/* This method uses the long integer sig to encrypt the userId value (weak encryption but different for every sig value)
    	 * The original id String can be recovered by using exactly the same encryption method (symmetric and reversible)
    	 */
    	try {
    		byte[] input = id.getBytes("UTF-8");
    		byte[] output = new byte[input.length];
    		Random rand = new Random(sig);
    		for (int i=0;i<input.length;i++) {
    			int a = rand.nextInt(128);
    			int b = (int) input[i];
    			output[i] = (byte)(0xff & (a ^ b));
    		}
    		return new String(output,"UTF-8");
    	} catch (Exception e) {
    		return null;
    	}
    }
}