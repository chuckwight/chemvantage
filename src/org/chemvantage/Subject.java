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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class Subject {
	static Subject subject;
	@Id Long id;
	String title;
	String HMAC256Secret;
	String salt;
	String announcement;
	int nStarReports;
	double avgStars;
	
	Subject() {}
	
	static public Subject getSubject() {
		try {
			if (subject == null) {
				subject = ofy().load().type(Subject.class).first().safe();
				return subject;
			}
			else return subject;
		} catch (Exception e) { // this should be run only once at setup
			Subject s = new Subject();
			s.id = 1L;
			s.title = "General Chemistry";
			s.HMAC256Secret = "ChangeMeInTheDataStoreManuallyForYourProtection";
			s.salt = "ChangeMeInTheDataStoreManuallyToKeepStoredUsedIdValuesSecure";
			ofy().save().entity(s).now();
			return s;
		}
	}

	public void addStarReport(int stars) {
		avgStars = (avgStars*nStarReports + stars)/(nStarReports+1);
		nStarReports++;
		ofy().save().entity(this);
	}

	public double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(avgStars));
	}
	
	public String getAnnouncement() {
		return this.announcement;
	}
	
	static String hashId(String userId) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] bytes = md.digest((userId + Subject.getSubject().salt).getBytes(StandardCharsets.UTF_8));
        	StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
		} catch (Exception e) {
        	return null;
        }
	}
}
