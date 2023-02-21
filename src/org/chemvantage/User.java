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
	@Index 	String  hashedId;   		   // used to check for duplicate stored values
	@Index	Date 	exp;				   // max 90 minutes from now
			String  encryptedId;		   // stored temporarily in encrypted form
			String	platformId;			   // URL of the LMS
	@Index	long	assignmentId = 0L;     // used only for LTI users
			int 	roles = 0;             // student

/*
 * 		A User entity represents either a qualified LTI User or an anonymous ChemVantage user entering through the web site.
 * 		LTI User:
 * 		We store the plain text platformId from the iss claim made by the LMS, plus
 * 			- The entity's @Id (sig) value (autogenerated key from the datastore)
 * 			- A hashed Id value stored with Transaction, Score and Response entities for later retrieval
 * 			- An encrypted userId value used to send scores back tot he user's LMS
 * 			- expiration Date object (typically 90 minutes after launch
 * 			- user's assignmentId, if known
 * 
 * 		Anonymous User:
 * 		For a new anonymous user, the constructor uses an expiration Date 90 minutes from now to create the values
 * 		For a returning user (getUser()) the constructor uses the prior expiration Date to reconstruct the values
 * 			- sig: a hexadecimal encoded value representing the original expiration Date
 * 			- encryptedId = "anonymous" + hashCode of millis of original exp Date
 * 			- hashedId value to be stored with Transaction, Score and Response entities
 */
			
	User() {  // constructor for new anonymous user; expires in 90 minutes
		this(new Date().getTime() + 5400000L);
	}
	
	User(long millis) {  // constructor for continuing anonymous user; expires at new Date(millis)
		sig = encode(millis);
		encryptedId = "anonymous" + String.valueOf(millis).hashCode();
		hashedId = Subject.hashId(encryptedId);
	}

	User(String platformId, String id) {  // used for LTI 1.3 and LTIDeepLinks launches
		// First look for this user in the database to avoid creating a duplicate entry
		// If not found, create a new one.
		this.platformId = platformId;
		if (id == null) id = "";
		String user_id = platformId==null?id:platformId + "/" + id;
		this.hashedId = Subject.hashId(user_id);
		this.exp = new Date(new Date().getTime() + 5400000L);  // value expires 90 minutes from now
		this.encryptedId = encryptId(user_id,0L);  // temporary until sig is assigned in setToken()
	}

	public static User getUser(String sig) {  // default token expiration of 90 minutes
    	return getUser(sig,90);
	}
		
	public static User getUser(String sig,int minutesRequired) {   // allows custom expiration for long assignments
		if (sig==null) return null;
    	if (minutesRequired > 500) minutesRequired = 500;
		Date now = new Date();
    	Date expires = new Date(now.getTime() + (minutesRequired+5)*60000L);   // includes 5-minute grace period
		
    	try {  // try to find the LTI User entity in the datastore
    		User user = ofy().load().type(User.class).id(Long.parseLong(sig)).safe();
    		if (user.exp.before(now)) return null; // entity has expired
    		else { // extend the exp time
    			user.exp = expires;
    			ofy().save().entity(user);
    		}
    		return user;
    	} catch (Exception e) {}
    	
    	try {  // try to validate an anonymous user
    		Date exp = new Date(encode(Long.parseLong(sig,16)));
    		Date aMonthFromNow = new Date(now.getTime() + 2678400000L);
    		if (exp.after(now) && exp.before(aMonthFromNow)) return new User(expires.getTime());
    	} catch (Exception e) {}

    	return null;   	
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
		if (this.isAnonymous()) return encryptedId;
		else return decryptId(encryptedId,sig);
	}
	
	public String getHashedId() {  // public method to support JSP files to retrieve hashed userId value
		return (isAnonymous()?Subject.hashId(encryptedId):hashedId);
	}
	
	boolean isAdministrator() {
		return (roles & 16)>0 || isChemVantageAdmin();		
	}

	public boolean isAnonymous() {
		return encryptedId.startsWith("anonymous");
	}

	boolean isChemVantageAdmin() {
		return (roles & 32)>0;
		//return ((roles%64)/32 == 1);
	}
	
	public boolean isInstructor() {
		return (roles & 8)>0 || isAdministrator();
		//return ((roles%16)/8 == 1 || this.isAdministrator());
	}

	boolean isTeachingAssistant() {    
		return (roles & 4)>0 || isInstructor();
		//return ((roles%8)/4 == 1) || this.isInstructor();
	}

	boolean isEditor() {
		return (roles & 2)>0 || isChemVantageAdmin();
		//return ((roles%4)/2 == 1 || this.isChemVantageAdmin());
	}

	boolean isContributor() {
		return (roles & 1)>0;
		//return ((roles%2)/1 == 1);
	}

	boolean setIsChemVantageAdmin(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isChemVantageAdmin() ^ makeAdmin) {
			roles = roles | (makeAdmin?32:63^32);  // bitwise operation to set or unset the proper roles bit
			//roles += makeAdmin?+32:-32;
			setToken();
			return true;
		}
		else return false;
	}

	boolean setIsAdministrator(boolean makeAdmin) {  // returns true if state is changed; otherwise returns false
		if (isAdministrator() ^ makeAdmin) {
			roles = roles | (makeAdmin?16:63^16);
			//roles += makeAdmin?+16:-16;
			return true;
		}
		else return false;
	}

	boolean setIsInstructor(boolean makeInstructor) {  // returns true if state is changed; otherwise returns false
		if (isInstructor() ^ makeInstructor) {
			roles = roles | (makeInstructor?8:63^8);
			//roles += makeInstructor?+8:-8;
			return true;
		}
		else return false;		
	}

	boolean setIsTeachingAssistant(boolean makeTeachingAssistant) { // returns true if the state is changed; else false
		if (isTeachingAssistant() ^ makeTeachingAssistant) {
			roles = roles | (makeTeachingAssistant?4:63^4);
			//roles += makeTeachingAssistant?+4:-4;
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
		if (this.isAnonymous()) return Long.toHexString(sig);
		else {
			if (sig==null) setToken();
			return String.valueOf(sig);     // String version of @Id value of User in the datastore
		}
	}
	
	void setAssignment(long assignmentId) {  // LTI Advantage
		this.assignmentId = assignmentId;
		setToken();
	}
	
	void setToken() {
		if (this.isAnonymous()) return;  // never save anonymous users to the database
		else { // check to see ig this user/assignment combination already exists
			try {  // check to see if there is a current user in the database
				User u = ofy().load().type(User.class).filter("hashedId",this.hashedId).filter("assignmentId",this.assignmentId).first().safe();
				this.sig = u.sig;  // this is a current user; just use it
				this.encryptedId = u.encryptedId;
				ofy().save().entity(this);
			} catch (Exception e) {  // store a new user
				this.sig = ofy().factory().allocateId(User.class).getId();
				this.encryptedId = encryptId(decryptId(this.encryptedId,0L),sig);  // change the temporary encrypted value to one matching sig
				ofy().save().entity(this).now();	
			}
		}
	}
	
    public long getAssignmentId() {
    	return assignmentId;
    }

   static String encryptId(String id, long sig) {
    	/* This method uses a simple one-time pad to encrypt a userId value prior to storing in the database.
    	 * The uses sig as a seed for Random. Each 8-bit random integer (0 to 127) is XORed with one byte
    	 * of the input String and converted to a two-character hexadecimal number for storage as a printable value.
    	 * The final output should have 2 characters for every byte of input, so the encryption is weak.
    	 * 
    	 * NOTE: the sig valus are generated randomly, so decryption is only possible while the User entity is persisted in the database (about 1 day)
    	 */
    	try {
    		byte[] input = id.getBytes("UTF-8");
    		String output = "";
    		Random rand = new Random(sig);
    		for (int i=0;i<input.length;i++) {
    			int a = rand.nextInt(128);
    			int b = (int) input[i];
    			int xor = a^b;
    			output += (xor<16?"0":"") + Integer.toHexString(a^b);  // retains leading zero, if present
    		}
    		return output;
    	} catch (Exception e) {
    		return null;
    	}
    }
    
    String decryptId(String enc, long sig) { 
    	/* This method reverses the encrypt method above by converting each pair of hexadecimal characters in the input
    	 * to an integer, XORing that with a pseudo-random integer based on sig, converting to a single byte and 
    	 * finally to a String character, which is appended to the output for each pair of hexadecimal input characters.
    	 */
    	try {
    		int length = enc.length()/2;
    		byte[] output = new byte[length];
    		Random rand = new Random(sig);
    		for (int i=0;i<length;i++) {
    			int a = rand.nextInt(128);
    			int b = Integer.parseInt(enc.substring(2*i, 2*i+2),16);
    			Integer xor = a^b;
    			output[i] = xor.byteValue(); 
    		}
    		return new String(output,"UTF-8");	
    	} catch (Exception e) {
    		return e.toString() + " " + e.getMessage();
    	}
    }
    
    public boolean isPremium() {
    	try {
    		if (isInstructor() || isTeachingAssistant()) return true;
    		PremiumUser u = ofy().load().type(PremiumUser.class).id(hashedId).safe();
    		return u.exp.after(new Date());
    	} catch (Exception e) {
    		return false; // not found
    	}
    }
}