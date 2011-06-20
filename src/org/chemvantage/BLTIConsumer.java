package org.chemvantage;

import java.util.Random;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class BLTIConsumer {
	@Id String oauth_consumer_key;
	String secret;

	BLTIConsumer() {}

	BLTIConsumer(String oauth_consumer_key) {
		Random random =  new Random();
        long r1 = random.nextLong();
        long r2 = random.nextLong();
        String hash1 = Long.toHexString(r1);
        String hash2 = Long.toHexString(r2);
        this.secret = hash1 + hash2;
        this.oauth_consumer_key = oauth_consumer_key;
    }
	
	static void create(String oauth_consumer_key) {
		ObjectifyService.begin().put(new BLTIConsumer(oauth_consumer_key));
	}
	
	static void delete(String oauth_consumer_key) {
		ObjectifyService.begin().delete(new Key<BLTIConsumer>(BLTIConsumer.class,oauth_consumer_key));
	}
	
	static String getSecret(String oauth_consumer_key) {
		BLTIConsumer c = ObjectifyService.begin().find(BLTIConsumer.class,oauth_consumer_key);
		return (c==null?null:c.secret);
	}
}
