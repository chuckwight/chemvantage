package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import com.googlecode.objectify.annotation.Entity;

@Entity
public class Instructor extends User {

	Instructor() {}
	
	Instructor(String platform_id,String userId,int roles) {
		super(platform_id,userId);
		this.roles = roles;
		ofy().save().entity(this);
	}
}
