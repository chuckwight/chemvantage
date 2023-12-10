package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Calendar;
import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class PremiumUser {
	@Id 	String 	hashedId;
	@Index	public Date exp;
	@Index	Date start;
	@Index	int paid;
			String org;
	
	public PremiumUser() {}
	
	public PremiumUser(String id, int months, int paid, String org) {
		hashedId = id;
		start = new Date();
		Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        calendar.add(Calendar.MONTH, months);
        exp = calendar.getTime();
        this.paid = paid;
		this.org = org;
		ofy().save().entity(this).now();
	}
}
