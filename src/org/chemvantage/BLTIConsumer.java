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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class BLTIConsumer {
	@Id String oauth_consumer_key;
	String secret;
	String lti_version;
	String tool_consumer_guid;
	String toolProxyURL;  // Tool Consumer URL containing the tool proxy contract for LTI v2.0
	String toolProxy;
	String toolSettingsURL; // Tool Consumer URL to PUT changes to settings in tool proxy
	String resultServiceFormat;
	String resultServiceEndpoint;
	String email;
	Date created;
	boolean suppressEmailNotifications;
	List<String> capabilities_enabled;
	List<String> tool_service;
	
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
	
	BLTIConsumer(String key,String secret,String tool_consumer_guid,String version) {
		this.oauth_consumer_key = key;
		this.secret = secret;
		this.tool_consumer_guid = tool_consumer_guid;
		this.lti_version = version;
		this.created = new Date();
		this.capabilities_enabled = new ArrayList<String>();
		this.tool_service = new ArrayList<String>();
		}
	
	static void create(String oauth_consumer_key) {
		ofy().save().entity(new BLTIConsumer(oauth_consumer_key)).now();
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
	
	void putToolProxyURL(String url) {
		this.toolProxyURL = url;
	}
	
	String getToolProxyURL() {
		return this.toolProxyURL;
	}
	
	void putToolSettingsURL(String url) {
		this.toolSettingsURL = url;
	}

	String getToolSettingsURL() {
		return this.toolSettingsURL;
	}

	void putResultServiceFormat(String format) {
		this.resultServiceFormat = format;
	}
	
	String getResultServiceFormat() {
		return this.resultServiceFormat;
	}

	static String getResultServiceFormat(String oauth_consumer_key) {
		BLTIConsumer c = ofy().load().type(BLTIConsumer.class).id(oauth_consumer_key).now();
		return c==null?null:c.resultServiceFormat;
	}
	
	void putResultServiceEndpoint(String endpoint) {
		this.resultServiceEndpoint= endpoint;
	}
	
	String getResultServiceEndpoint() {
		return this.resultServiceEndpoint;
	}
		
	boolean supportsResultService() {
		if (this.resultServiceEndpoint !=null && this.resultServiceFormat != null) return true;
		return false;
	}
	
	List<String> getToolService() {
		if (this.tool_service == null) tool_service = new ArrayList<String>();
		return this.tool_service;
	}
	
	void putCapabilities(List<String>capabilities_enabled) {
		this.capabilities_enabled = capabilities_enabled;
	}
	
	List<String> getCapabilities() {
		if (this.capabilities_enabled == null) capabilities_enabled = new ArrayList<String>();
		return this.capabilities_enabled;
	}
}
