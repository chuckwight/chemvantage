package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Calendar;
import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
class PremiumUser {
	@Id 	String 	hashedId;
	@Index	Date exp;
	@Index	Date start;
	@Index	int paid;
			String org;
			String order_id;
	
	public PremiumUser() {}

	public PremiumUser(String id, String org) {
		hashedId = id;
		start = new Date();
		exp = new Date(start.getTime()+12L*60*60*1000); // 12-hour free trial
		order_id = "FREE_TRIAL";
		this.org = org;
		ofy().save().entity(this).now();
	}
	
	public PremiumUser(String id, int months, int paid, String org, String order_id) {
		hashedId = id;
		start = new Date();
		Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        calendar.add(Calendar.MONTH, months);
        exp = calendar.getTime();
        this.paid = paid;
		this.org = org;
		this.order_id = order_id;
		ofy().save().entity(this).now();
	}
}
