package org.chemvantage;

import javax.persistence.Id;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Unindexed;

@Unindexed
public class GoogleClient {
	
	@Id Long id;
	String client_id;
	String client_secret;
	
	GoogleClient() {}
	
	static GoogleClient getInstance() {
		Objectify ofy = ObjectifyService.begin();
		GoogleClient gc = ofy.query(GoogleClient.class).get();
		if (gc==null) {  // this section runs only once; placeholder values must be replaced manually in the database
			gc = new GoogleClient();
			gc.client_id = "GoogleIdPlaceholder";
			gc.client_secret = "GoogleSecretPlaceholder";
			ofy.put(gc);
		}
		return gc;
	}
}
