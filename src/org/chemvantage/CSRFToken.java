package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.List;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class CSRFToken {
	// this cryptographically signed token is used to discourage a
	// cross-site request forgery threat (e.g., a state-changing link
	// created by a third-party that takes advantage of the fact that 
	// a user is authenticated in ChemVantage. The token is included 
	// as a hidden parameter in state-changing POST submissions such
	// as quiz submissions. It should be generated at every LTI launch
	// request and should expire in 90 minutes or whenever another 
	// launch takes place from the same user.
	
	@Id 	long id;
			String typ = "jwt";
			String alg = "HS256";
	@Index 	String sub;
	@Index	Date exp;
			static Algorithm algorithm = Algorithm.HMAC256(Subject.getSubject().HMAC256Secret);
			static Builder builder = JWT.create().withIssuer("https://www.chemvantage.org");
			static JWTVerifier verifier = JWT.require(algorithm).withIssuer("https://www.chemvantage.org").build();

			
	CSRFToken(String userId) {
		this.sub = userId;
		this.exp = new Date(new Date().getTime() + 5400000L);  // expires 90 minutes from now
	}
	
	static CSRFToken getInstance(String userId) {
		try {
			User u = ofy().load().type(User.class).id(userId).safe();  // ensures that the user exists
			CSRFToken t = new CSRFToken(u.id);
			// before saving the new token, collect keys of any existing tokens for this user for deletion
			List<Key<CSRFToken>> old_keys = ofy().load().type(CSRFToken.class).filter("sub",u.id).keys().list();
			// also collect keys for any expired tokens for any user 
			old_keys.addAll(ofy().load().type(CSRFToken.class).filter("exp<",new Date()).keys().list());
			// delete the old and expired tokens from the datastore
			if (!old_keys.isEmpty()) ofy().delete().keys(old_keys);  // in the background, unscheduled
			// save the new CSRFToken
			ofy().save().entity(t).now();  // do this right away because we may need it in a second
			return t;
		} catch (Exception e) {
			return null;
		}
	}
	
	static String getJWT(String userId) {
		try {
			User u = ofy().load().type(User.class).id(userId).safe();  // ensures that the user exists
			CSRFToken t = ofy().load().type(CSRFToken.class).filter("sub",u.id).first().safe();
			return builder.withClaim("sub", t.sub).withExpiresAt(t.exp).sign(algorithm);			
		} catch (Exception e) {
			return null;
		}
	}
	
	static String getNewJWT(String userId) {
		try {
			CSRFToken t = CSRFToken.getInstance(userId);
			return builder.withClaim("sub", t.sub).withExpiresAt(t.exp).sign(algorithm);
		} catch (Exception e) {
			return null;
		}
	}
	
	static User getUser(String jwt) {
		try {
			String userId = verifier.verify(jwt).getSubject();
			return ofy().load().type(User.class).id(userId).safe();
		} catch (Exception e) {
			return null;
		}
	}

}
