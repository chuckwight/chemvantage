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
	private String title;
	private String HMAC256Secret;
	private String salt;
	private String announcement;
	private int nStarReports;
	private double avgStars;
	
	Subject() {}
	static { refresh(); }
	
	static void refresh() {
		try {
		subject = ofy().load().type(Subject.class).first().safe();
		} catch (Exception e) {
			subject = new Subject();
			subject.id = 1L;
			subject.title = "General Chemistry";
			subject.HMAC256Secret = "ChangeMeInTheDataStoreManuallyForYourProtection";
			subject.salt = "ChangeMeInTheDataStoreManuallyToKeepStoredUsedIdValuesSecure";
			ofy().save().entity(subject);
		}
	}
	
	static String getTitle() { return subject.title; }
	static String getHMAC256Secret() { return subject.HMAC256Secret; }
	static String getSalt() { return subject.salt; }
	static String getAnnouncement() { return subject.announcement; }
	static int getNStarReports() { return subject.nStarReports; }
	
	static void setAnnouncement(String msg) {
		refresh();
		subject.announcement = msg;
		ofy().save().entity(subject);
	}
	
	static double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(subject.avgStars));
	}
	
	static void addStarReport(int stars) {
		refresh();
		subject.avgStars = (subject.avgStars*subject.nStarReports + stars)/(subject.nStarReports+1);
		subject.nStarReports++;
		ofy().save().entity(subject);
	
	}
		
	static String hashId(String userId) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] bytes = md.digest((userId + subject.salt).getBytes(StandardCharsets.UTF_8));
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
