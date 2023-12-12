package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.Random;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Voucher {
	@Id 	String 	code;
	@Index	Date purchased;
	@Index	Date activated;
			int months;
			int paid;
			String org;
	
	public Voucher() {}
	
	public Voucher(String org,int price) {
		this.code = Integer.toHexString(1048576 + new Random().nextInt(15728640)).toUpperCase(); // 6-character HEX
		this.purchased = new Date();
		this.months = 12;
		this.paid = price;
		this.org = org;
	}
	
	public boolean activate() {
		if (this.activated == null) {
			this.activated = new Date();
			ofy().save().entity(this);
			return true;
		} else return false;
	}
}
