package org.chemvantage;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class PremiumUser {
	@Id 	String 	hashedId;
	@Index	Date 	exp;
}
