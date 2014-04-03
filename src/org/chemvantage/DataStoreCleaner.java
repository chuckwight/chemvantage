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
import java.util.List;
import java.util.Random;

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
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		out.println(cleanUsers());
		cleanSessions();
		cleanResponses();
		out.println("Done.");
}
	private String cleanUsers() {
		StringBuffer buf = new StringBuffer();
		final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
		final Query query = new Query("User");
		String userId = "";
		for (final Entity user : datastore.prepare(query).asIterable(FetchOptions.Builder.withLimit(1000))) {
			try {
				Date lastLogin = (Date)user.getProperty("lastLogin");
				if (lastLogin.before(oneYearAgo)) {
					userId = user.getKey().getName();
					deleteUser(ofy.get(User.class,userId));
					buf.append("Deleting: " + userId + "<br/>");
				}
			} catch (Exception e) {
				buf.append("Failed on: " + userId + "<br/>");
			}
		}
		return buf.toString();
	}

	boolean deleteUser(User u) {   // recursive user deletion function that follows the alias tree to the end
		Date expires = u.hasPremiumAccount()?oneYearAgo:sixMonthsAgo;
		if (u.lastLogin.after(expires)) return false;  // if any alias has a recent login, preserve the entire chain
		if (u.isAdministrator() || u.isInstructor()) return false;
		if (u.alias==null) {  // found the end of the expired alias chain
			if (u.lastLogin.getTime()==0L) {  // user never logged in; perhaps this is a brand new account
				u.lastLogin = new Date(1000L);   // so mark the account by advancing the lastLogin by 1 second
				ofy.put(u);                   // so it will be deleted tomorrow instead if the user does not login
				return false;
			} else ofy.delete(u);    // delete this user
			return true;      // and signal to delete all users that alias this user
		} else {  // follow the alias chain to the end
			try {
				if (deleteUser(ofy.get(User.class,u.alias))) {  // recursion to end; if returns true then delete everything
					deleteUserData(u);
					ofy.delete(u);
					return true;
				} else {  // set this alias account to expire sometime in the next 10 days so I don't encounter it every day
					int upToTenDaysRandom = new Random().nextInt(10);
					Date almostExpired = new Date(expires.getTime() + upToTenDaysRandom*86400000L);
					u.lastLogin = almostExpired;
					ofy.put(u);
					return false;
				}
			} catch (Exception e) {
				return true;        // this alias chain has no valid user at the end point
			}
		}
	}
	
	void deleteUserData(User u) {
		ofy.delete(ofy.query(QuizTransaction.class).filter("userId", u.id).listKeys());
		ofy.delete(ofy.query(HWTransaction.class).filter("userId", u.id).listKeys());
		ofy.delete(ofy.query(ExamTransaction.class).filter("userId", u.id).listKeys());
		ofy.delete(ofy.query(PracticeExamTransaction.class).filter("userId", u.id).listKeys());
		ofy.delete(ofy.query(VideoTransaction.class).filter("userId", u.id).listKeys());	
	}

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
			if (((Date)response.getProperty("submitted")).before(oneYearAgo)) datastore.delete(response.getKey());
		}
	}
}