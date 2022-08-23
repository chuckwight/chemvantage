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
	@Index	Date start;
			int paid;
			String org;
	
	public PremiumUser() {}
	
	public PremiumUser(String id) {
		hashedId = id;
		exp = new Date(new Date().getTime() + 26265600000L); // default 10-month subscription
		ofy().save().entity(this).now();
	}
	
	public PremiumUser(String id, int months, int paid, String org) {
		hashedId = id;
		exp = new Date(new Date().getTime() + 2678400000L * months); // months-long subscription
		start = new Date();
		this.paid = paid;
		this.org = org;
		ofy().save().entity(this).now();
	}
}
