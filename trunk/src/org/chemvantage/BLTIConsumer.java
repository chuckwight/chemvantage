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

import java.util.Date;
import java.util.Random;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Unindexed;

@Cached @Unindexed
public class BLTIConsumer {
	@Id String oauth_consumer_key;
	String secret;
	String lti_version;
	String tool_consumer_guid;
	String toolProxyURL;  // Tool Consumer URL containing the tool proxy contract for LTI v2.0
	String resultServiceEndpoint;
	String resultServiceFormat;
	String email;
	Date created;
	
	BLTIConsumer() {}

	BLTIConsumer(String oauth_consumer_key) {
		this.oauth_consumer_key = oauth_consumer_key;
		this.secret = generateSecret();
        this.lti_version = "LTI-1p0";
    }
	
	BLTIConsumer(String key,String email) {
		this.oauth_consumer_key = key;
		this.secret = generateSecret();
		this.email = email;
		this.created = new Date();
	}
	
	BLTIConsumer(String key,String secret,String tool_consumer_guid,String version) {
		this.oauth_consumer_key = key;
		this.secret = secret;
		this.tool_consumer_guid = tool_consumer_guid;
		this.lti_version = version;	
	}
	
	static void create(String oauth_consumer_key) {
		ObjectifyService.begin().put(new BLTIConsumer(oauth_consumer_key));
	}
	
	static void delete(String oauth_consumer_key) {
		ObjectifyService.begin().delete(new Key<BLTIConsumer>(BLTIConsumer.class,oauth_consumer_key));
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
		Objectify ofy = ObjectifyService.begin();
		BLTIConsumer c = ofy.find(BLTIConsumer.class,oauth_consumer_key);
		if (c==null) return null;
		if (c.lti_version==null || c.lti_version.isEmpty()) {
			c.lti_version = "LTI-1p0";
			ofy.put(c);
		}
		return c.secret;
	}
	
	void putToolProxyURL(String url) {
		this.toolProxyURL = url;
	}
	
	String getToolProxyURL() {
		return this.toolProxyURL;
	}
}
