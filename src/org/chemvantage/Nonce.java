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

import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class Nonce {
	@Id String id;
		Date created;
	
	Nonce() {}
	
	Nonce(String id) {
		this.id = id;
		this.created = new Date();
	}
	
	public static boolean isUnique(String nonce, String timestamp) {
		// This method provides a level of security for OAuth launches for LTI
		// by verifying that oauth_nonce strings are submitted only once
		// This protects against eavesdropping and copycat login attacks
		
		long interval = 5400000L;  // 90 minutes in milliseconds
		Date now = new Date();
		Date oldest = new Date(now.getTime()-interval); // converts seconds to millis

		try {
			//check the timestamp to ensure this is a new launch (within half of the interval)
			Date stamped = new Date(Long.parseLong(timestamp)*1000);  // millis since Jan 1, 1970 00:00 UTC
			if (Math.abs(stamped.getTime()-now.getTime()) > interval/2) throw new Exception();  // out of submission interval
			
			// delete all Nonce objects older than the interval
			Objectify ofy = ObjectifyService.begin();
			List<Key<Nonce>> expired = ofy.query(Nonce.class).filter("created <",oldest).listKeys();
			if (expired.size() > 0) ofy.delete(expired);
			
			// check to see if a Nonce with the specified id already exists in the database
			if (ofy.find(Nonce.class,nonce) != null) throw new Exception();
			
			// store a new Nonce object in the datastore with the unique nonce string
			ofy.put(new Nonce(nonce));
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}