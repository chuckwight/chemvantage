package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class PremiumUser {
	@Id 	String 	hashedId;
	@Index	public Date exp;
	
	public PremiumUser() {}
	
	public PremiumUser(String id) {
		hashedId = id;
		exp = new Date(new Date().getTime() + 26265600000L); // default 10-month subscription
		ofy().save().entity(this).now();
	}
	
	public PremiumUser(String id, int months) {
		hashedId = id;
		exp = new Date(new Date().getTime() + 2628000000L * months); // months-long subscription
		ofy().save().entity(this).now();
	}
}
