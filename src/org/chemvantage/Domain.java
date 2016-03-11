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

import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Cache @Entity
public class Domain {
	@Id 	Long id;
	@Index	String domainName;
	@Index 	double dailyLoginsAvg; 
	Date 	lastLogin;
	Date 	created;
	int 	activeUsers;
	int 	basicAccounts;
	int 	premiumAccounts;
	int 	seatsPurchased;
	int 	seatsAvailable;
	double 	avgInterval = 10.; // Keep a 10-day running average of daily logins
	boolean supportsResultService;
	String 	resultServiceEndpoint;
	String 	resultServiceFormat;
	List<String> capabilities = new ArrayList<String>();
	List<String> domainAdmins = new ArrayList<String>();
			
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
			User u = ofy().load().type(User.class).id(da).now();
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
		this.activeUsers = ofy().load().type(User.class).filter("domain",this.domainName).count();
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
		double interval = (double)(login.getTime() - lastLogin.getTime())/86400000.; // days since previous login
		this.lastLogin = login; // set new value
		this.dailyLoginsAvg = this.dailyLoginsAvg*Math.exp(-interval/avgInterval) + (1/avgInterval);
		ofy().save().entity(this).now();
	}

	public double getDailyLoginsAvg() {
		Date now = new Date();
		double interval = (double)(now.getTime() - lastLogin.getTime())/86400000.; // days since last login
		return Math.round(1000*this.dailyLoginsAvg*Math.exp(-interval/avgInterval))/1000.;
	}
}
