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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.googlecode.objectify.Objectify;
//import com.googlecode.objectify.Objectify;
//import com.googlecode.objectify.Query;

public class DataStoreCleaner extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Date now;
	Date sixMonthsAgo;
	Date oneYearAgo;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "ChemVantage servlet that performs daily maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each day.
		now = new Date();
		sixMonthsAgo = new Date(now.getTime()-15768000000L);
		oneYearAgo = new Date(now.getTime()-31536000000L);
		
		cleanUsers();
		cleanSessions();
		cleanResponses();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("Done.");
}
	private void cleanUsers() {
		Iterable<User> oldUsers = ofy.query(User.class).filter("lastLogin<",sixMonthsAgo.getTime()).limit(1000).list();
		for (User u : oldUsers) {
			if (u.lastLogin.getTime() > 0) {  // user account is at least 1 day old
				User user = u;
				if (user.alias != null) { // follow the alias chain to the end
					List<String> userIds = new ArrayList<String>();
					userIds.add(user.id);
					userIds.add(0,user.alias);
					user = getUserInstance(userIds);					
				}
				if (user.lastLogin.before(oneYearAgo) || !user.hasPremiumAccount()) {
//					/===========NEEDS MORE CODE HERE ===============/
					// delete the user and all aliases in the chain back to u
				}
			} else {  // advance the lastLogin value 1 millisecond to provide 1 day grace period for new user
				u.lastLogin = new Date(1L);
			}
		}
	}
	
	User getUserInstance(List<String> userIds) {
		try {
			User user = ofy.get(User.class,userIds.get(0));			
			if (user.alias != null && !user.alias.isEmpty() && !userIds.contains(user.alias)) {
				userIds.add(0,user.alias);
				user = getUserInstance(userIds);  // recursion: follow the alias chain one more link
			}
			return user;  // found the user at the end of the alias chain
		} catch (Exception e) {  // this happens if the last alias does not point to a valid user
			return ofy.find(User.class,userIds.get(1));  // return the previous user in the alias chain
		}
	}
/*
	private void cleanUsers() {
		final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("User"); 
		for (final Entity user : datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) {
			Date lastLogin = (Date) user.getProperty("lastLogin");
			String aliasId = (String) user.getProperty("alias");
			if (lastLogin.before(sixMonthsAgo) && aliasExpired(aliasId)) datastore.delete(user.getKey());
		}
	}
*/
	private void cleanSessions() {
		final long now = new Date().getTime(); 
		final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("_ah_SESSION"); 
		for (final Entity session : datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) { 
			Long expires = (Long) session.getProperty("_expires"); 
			if (expires < now) datastore.delete(session.getKey());
		}
	}

	private void cleanResponses() {
		final DatastoreService datastore = 
			DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("Response"); 
		for (final Entity response : datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) {
			if ((Long)response.getProperty("submitted") < now.getTime()) datastore.delete(response.getKey());
		}
	}
}