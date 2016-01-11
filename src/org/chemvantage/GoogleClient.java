package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Cache @Entity
public class GoogleClient {
	
	@Id Long id;
	String client_id;
	String client_secret;
	
	GoogleClient() {}
	
	static GoogleClient getInstance() {
		try {
			return ofy().load().type(GoogleClient.class).first().safe();
		} catch (Exception e) {  // this section runs only once
			GoogleClient gc = new GoogleClient();
			// NOTE: Placeholder values must be replaced in the database manually!
			gc.client_id = "GoogleIdPlaceholder";
			gc.client_secret = "GoogleSecretPlaceholder";
			ofy().save().entity(gc);
			return gc;
		}
	}
}
