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
