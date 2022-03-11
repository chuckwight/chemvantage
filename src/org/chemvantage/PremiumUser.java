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
		exp = new Date(new Date().getTime() + 26265600000L); // 10-month subscription
		ofy().save().entity(this).now();
	}
}
