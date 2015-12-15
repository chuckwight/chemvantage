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
	@Id 		Long id;
	@Indexed	String domainName;
	@Indexed	Date lastLogin;
	@Indexed	double dailyLoginsAvg; 
				Date created;
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
		this.activeUsers = 1;
		this.basicAccounts = 0;
		this.premiumAccounts = 1;
		this.seatsPurchased = 0;
		this.seatsAvailable = 0;
	}
	
	public List<String> getDomainAdmins() {
		List<String> admins = new ArrayList<String>();
		List<String> remove = new ArrayList<String>();
		for (String da : domainAdmins) {
			User u = ofy.find(User.class,da);
			if (u!=null && u.domain.equals(this.domainName)) admins.add(da);
			else remove.add(da);
		}
		if (remove.size()>0) domainAdmins.removeAll(remove);
		return admins;
	}
	
	public boolean addAdmin(String adminId) {
		if (this.domainAdmins==null) this.domainAdmins = new ArrayList<String>();
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
		if (domainAdmins==null || domainAdmins.size()==0) return false;
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
	
	public void setLastLogin(Date login) {
		this.lastLogin = login;
		this.dailyLoginsAvg = this.dailyLoginsAvg<0.1?1.0:getDailyLoginsAvg();
		ofy.put(this);
	}

	public double getDailyLoginsAvg() {
		try {
			Date now = new Date();
			double interval = (double)(now.getTime() - lastLogin.getTime())/86400000.; // days since last login
			long w = Math.round(interval) + 1;   // adds extra weighting factor for more than 1 day
			return Math.floor(100*this.dailyLoginsAvg*(7+w)/(6 + this.dailyLoginsAvg*interval*w))/100.;  // exponential 7-day moving average 
		} catch (Exception e) {
			return this.dailyLoginsAvg;
		}
	}
}
