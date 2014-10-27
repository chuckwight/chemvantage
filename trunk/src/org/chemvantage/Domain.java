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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;
import javax.persistence.Transient;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Indexed;
import com.googlecode.objectify.annotation.Unindexed;

@Cached @Unindexed 
public class Domain {
	@Id Long id;
@Indexed		String domainName;
		Date created;
@Indexed		Date lastLogin;
		Date freeTrialExpires;
		int activeUsers;
		int basicAccounts;
		int premiumAccounts;
		int seatsPurchased;
		int seatsAvailable;
		boolean supportsResultService = false;
		List<String> capabilities = new ArrayList<String>();
		List<String> domainAdmins = new ArrayList<String>();
		
	@Transient transient Objectify ofy = ObjectifyService.begin();
	
	Domain() {}
	
	Domain(String hd) {
		this.domainName = hd;
		this.created = new Date();
		this.lastLogin = new Date();
		this.freeTrialExpires = new Date(created.getTime() + 10368000000L);  // 120 days
		this.activeUsers = 1;
		this.basicAccounts = 0;
		this.premiumAccounts = 1;
		this.seatsPurchased = 0;
		this.seatsAvailable = 0;
	}
	
	public boolean addAdmin(String adminId) {
		try {
			if (!domainAdmins.contains(adminId)) {
				domainAdmins.add(adminId);
				return true;
			}
			else return false;
		} catch (Exception e) {
			return false;
		}
	}
	
	public void removeAdmin(String adminId) {
		domainAdmins.remove(adminId);
	}
	
	public boolean isAdmin(String adminId) {
		try {
			if (domainAdmins.contains(adminId)) return true;
			else return false;
		} catch (Exception e) {
		}
		return false;
	}
	
	public int getActiveUsers() {
		this.activeUsers = ofy.query(User.class).filter("domain",this.domainName).count();
		return activeUsers;
	}
	
	public int getBasicAccounts() {
		return basicAccounts;
	}
	
	public int getPremiumAccounts() {
		return premiumAccounts;
	}
	
	public int getSeatsPurchased() {
		return seatsPurchased;
	}
	
	public int getSeatsAvailable() {
		return seatsAvailable;
	}
	
}
