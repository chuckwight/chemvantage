/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
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
import java.util.Map;
import java.util.Random;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class BLTIConsumer {
	@Id String oauth_consumer_key;
	String secret;
	String lti_version;
	String email;
	String contact_name;
	String organization;
	String org_url;
	String lms;
	Date created;
	Map<String,String[]> launchParameters;
	@Index Date lastLogin;

	BLTIConsumer() {}

	BLTIConsumer(String oauth_consumer_key) {
		this.oauth_consumer_key = oauth_consumer_key;
		this.secret = generateSecret();
        this.lti_version = "LTI-1p0";
        this.created = new Date();
    }
	
	BLTIConsumer(String key,String email) {
		this.oauth_consumer_key = key;
		this.secret = generateSecret();
		this.email = email;
		this.created = new Date();
	}
	
	static void delete(String oauth_consumer_key) {
		ofy().delete().type(BLTIConsumer.class).id(oauth_consumer_key);
	}

	static String generateSecret() {
		Random random =  new Random();
        long r1 = random.nextLong();
        long r2 = random.nextLong();
        String hash1 = Long.toHexString(r1);
        String hash2 = Long.toHexString(r2);
        return hash1 + hash2;
	}
	
	static String getSecret(String oauth_consumer_key) {
		BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).now();
		if (c==null) return null;
		if (c.lti_version==null || c.lti_version.isEmpty()) {
			c.lti_version = "LTI-1p0";
			ofy().save().entity(c);
		}
		return c.secret;
	}
}
