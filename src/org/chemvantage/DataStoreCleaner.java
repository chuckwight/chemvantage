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
import java.util.Date;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;
import com.googlecode.objectify.Objectify;
//import com.googlecode.objectify.Objectify;
//import com.googlecode.objectify.Query;

public class DataStoreCleaner extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

	public String getServletInfo() {
		return "ChemVantage servlet that performs daily maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each day.
		cleanUsers();
		cleanSessions();
		cleanResponses();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println("Done.");
}
	
	private void cleanUsers() {
		final Date now = new Date();
		final Date sixMonthsAgo = new Date(now.getTime()-15768000000L); 
		final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("User"); 
		for (final Entity user : datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) {
			Date lastLogin = (Date) user.getProperty("lastLogin");
			String aliasId = (String) user.getProperty("alias");
			if (lastLogin.before(sixMonthsAgo) && aliasExpired(aliasId)) datastore.delete(user.getKey());
		}
	}

	private boolean aliasExpired(String userId) {
		if (userId==null || userId.isEmpty()) return true;
		try {
			User u = ofy.get(User.class,userId);
			final Date now = new Date();
			final Date sixMonthsAgo = new Date(now.getTime()-15768000000L); 
			if (u.lastLogin.before(sixMonthsAgo) && aliasExpired(u.alias)) {
				ofy.delete(u);
				return true;
			}
			else return false;
		} catch (Exception e) {
			return true;
		}		
	}

	private void cleanSessions() {
		final long now = new Date().getTime(); 
		final DatastoreService datastore = 
			DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("_ah_SESSION"); 
		for (final Entity session : 
			datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) 
		{ 
			Long expires = (Long) session.getProperty("_expires"); 
			if (expires < now) { 
				final Key key = session.getKey(); 
				datastore.delete(key); 
			}
		}
	}

	private void cleanResponses() {
		final DatastoreService datastore = 
			DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("Response"); 
		for (final Entity response : 
			datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) 
		{ 
			String userId = (String) response.getProperty("userId");
			User user = ofy.find(User.class,userId);
			if (user==null) { 
				final Key key = response.getKey(); 
				datastore.delete(key); 
			}
		}
	}
}