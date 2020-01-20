/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2012 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.List;
import java.util.Random;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Nonce {
	@Id String id;
	@Index	Date created;
			
	Nonce() {}
	
	Nonce(String id) {
		this.id = id;
		this.created = new Date();
	}
	static long interval = 5400000L;  // 90 minutes in milliseconds
	
	static String generateNonce() {
		Random random =  new Random(new Date().getTime());
        long r1 = random.nextLong();
        long r2 = random.nextLong();
        String hash1 = Long.toHexString(r1);
        String hash2 = Long.toHexString(r2);
        return hash1 + hash2;
	}

	public static boolean isUnique(String nonce, String timestamp) {
		// This method provides a level of security for OAuth launches for LTI
		// by verifying that oauth_nonce strings are submitted only once
		// This protects against eavesdropping and copycat login attacks
		
		Date now = new Date();
		Date oldest = new Date(now.getTime()-interval); // converts seconds to millis

		try {
			//check the timestamp to ensure this is a new launch (within half of the interval)
			Date stamped = new Date(Long.parseLong(timestamp)*1000);  // millis since Jan 1, 1970 00:00 UTC
			if (Math.abs(stamped.getTime()-now.getTime()) > interval/2) throw new Exception();  // out of submission interval
			
			// delete all Nonce objects older than the interval
			List<Key<Nonce>> expired = ofy().load().type(Nonce.class).filter("created <",oldest).keys().list();
			if (expired.size() > 0) ofy().delete().keys(expired);
			
			// check to see if a Nonce with the specified id already exists in the database
			if (ofy().load().type(Nonce.class).id(nonce).now() != null) throw new Exception(); // if nonce exists
			
			// store a new Nonce object in the datastore with the unique nonce string
			ofy().save().entity(new Nonce(nonce)).now();
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}