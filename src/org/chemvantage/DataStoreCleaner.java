/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2014 ChemVantage LLC
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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

public class DataStoreCleaner extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Date now;
	Date oneMonthAgo;
	Date fiveMonthsAgo;
	Date sixMonthsAgo;
	Date oneYearAgo;
	int querySizeLimit = 100;
	
	public String getServletInfo() {
		return "ChemVantage servlet that performs monthly maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each month.
		// web.xml file set to Admin access only; change this to run manually
		// For GET request, use /DataStoreCleaner?Task=CleanUsers&TestOnly=true (or similar).
		// Default value of TestOnly is false;
		// For POST request, no parameters runs all methods
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		out.println(Home.header);
		
		String task = request.getParameter("Task");
		boolean testOnly = Boolean.parseBoolean(request.getParameter("TestOnly"));
		String cursor = request.getParameter("Cursor");
		
		if (task!=null) {  // must specify Task parameter
			switch (task) {
			case "CleanUsers": out.println(cleanUsers(testOnly)); break;
			case "CleanResponses": out.println(cleanResponses(testOnly)); break;
			case "CleanQuizTransactions": out.println(cleanQuizTransactions(cursor,0,testOnly)); break;
			case "CleanHWTransactions": out.println(cleanHWTransactions(cursor,0,testOnly)); break;
			case "CleanPracticeExamTransactions": out.println(cleanPracticeExamTransactions(cursor,0,testOnly)); break;
			case "CleanScores": out.println(cleanScores(cursor,0,testOnly)); break;
			case "CleanGroups": out.println(cleanGroups(cursor,0,testOnly)); break;
			case "CleanAssignments": out.println(cleanAssignments(cursor,0,testOnly)); break;
			case "CleanDomains": out.println(cleanDomains(cursor,0,testOnly)); break;
			} 
		} else if (Boolean.parseBoolean(request.getParameter("TestAll"))) {
			out.println(cleanUsers(true)
					+ cleanResponses(true)
					+ cleanQuizTransactions(null,0,true)
					+ cleanHWTransactions(null,0,true)
					+ cleanPracticeExamTransactions(null,0,true)
					+ cleanScores(null,0,true)
					+ cleanGroups(null,0,true)
					+ cleanAssignments(null,0,true)
					+ cleanDomains(null,0,true));
		}

		out.println(interactiveMenu());
		out.println(Home.footer);

	} 

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each day.
		now = new Date();
		oneMonthAgo = new Date(now.getTime()-2592000000L);
		fiveMonthsAgo = new Date(now.getTime()-13140000000L);
		sixMonthsAgo = new Date(now.getTime()-15768000000L);
		oneYearAgo = new Date(now.getTime()-31536000000L);
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		String cursor = request.getParameter("Cursor");
		boolean testOnly = Boolean.parseBoolean(request.getParameter("TestOnly"));
		
		String task = request.getParameter("Task");
		if (task==null) return;
		
		switch (task) {
		case "CleanUsers": out.println(cleanUsers(testOnly)); break;
		case "CleanResponses": out.println(cleanResponses(testOnly)); break;
		case "CleanQuizTransactions": out.println(cleanQuizTransactions(cursor,0,testOnly)); break;
		case "CleanHWTransactions": out.println(cleanHWTransactions(cursor,0,testOnly)); break;
		case "CleanPracticeExamTransactions": out.println(cleanPracticeExamTransactions(cursor,0,testOnly)); break;
		case "CleanScores": out.println(cleanScores(cursor,0,testOnly)); break;
		case "CleanGroups": out.println(cleanGroups(cursor,0,testOnly)); break;
		case "CleanAssignments": out.println(cleanAssignments(cursor,0,testOnly)); break;
		case "CleanDomains": out.println(cleanDomains(cursor,0,testOnly)); break;
		case "CleanAll": 
			out.println("<h2>Clean All</h2>");
			Queue queue = QueueFactory.getDefaultQueue();
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanUsers as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanResponses as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanQuizTransactions as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanHWTransactions as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanPracticeExamTransactions as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanScores as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanGroups").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanGroups as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanAssignments as a background task" + (testOnly?" (test only)":"") + ".<br>");
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDomains").param("TestOnly", testOnly?"true":"false"));
			out.println("Launched CleanDomains as a background task" + (testOnly?" (test only)":"") + ".<br>");
		default: return;
		}
	}
	
	String interactiveMenu() {
		StringBuffer buf = new StringBuffer("<h2>Data Store Cleaner</h2>Useage: /DataStoreCleaner?Task=CleanAll [&TestOnly=true]<p>");
		buf.append("<a href=DataStoreCleaner?Task=CleanUsers&TestOnly=true>Test CleanUsers (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanResponses&TestOnly=true>Test CleanResponses (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanQuizTransactions&TestOnly=true>Test CleanQuizTransactions (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanHWTransactions&TestOnly=true>Test CleanHWTransactions (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanPracticeExamTransactions&TestOnly=true>Test CleanPracticeExamTransactions (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanScores&TestOnly=true>Test CleanScores (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanGroups&TestOnly=true>Test CleanGroups (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanAssignments&TestOnly=true>Test CleanAssignments (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanDomains&TestOnly=true>Test CleanDomains (with no deletions)<br>");
		
		buf.append("<a href=DataStoreCleaner?TestAll=true>Test all cleaners</a> (with no deletions)<p>");
	
		return buf.toString();
	}

	private String cleanUsers(boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {
			//Query<User> query = ofy().load().type(User.class).limit(querySizeLimit); // temporary
			
			Query<User> query = ofy().load().type(User.class).filter("lastLogin <", sixMonthsAgo).limit(querySizeLimit);
			buf.append("<h2>Clean Users</h2>");
		    
		    QueryResultIterator<User> iterator = query.iterator();
		    ArrayList<Key<User>> keys = new ArrayList<Key<User>>();  // list of User entity keys for batch delete

		    while (iterator.hasNext()) {
		    	User u = iterator.next();
		    	if (deleteUser(u.id)) {  // tests to see if user should be deleted
		    		keys.add(Key.create(u));  // saves key in group to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);
		     
		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
		
	private boolean deleteUser(String userId) {   // recursive user deletion function that follows the alias tree to the end
		try {
			User u = ofy().load().type(User.class).id(userId).now();
			if (u.alias != null && !u.alias.isEmpty() && !u.alias.equals(u.id)) {  // follow the alias chain to the end
				if (deleteUser(u.alias)) return true;
				else {  // this alias is to be retained; advance lastLogin to prevent inspection every day
					u.lastLogin = fiveMonthsAgo;
					ofy().save().entity(u);
					return false;
				}
			} else { // found the user at the end of the alias chain
				if (u.lastLogin.after(sixMonthsAgo)) return false;  // if any alias has a recent login, preserve the entire chain
				if (u.isChemVantageAdmin()) return false; 
			}
		} catch (Exception e) {  // in case of error, delete the user because the alias points to a null user
		}	
		return true; // signal that this user object should be deleted
	}

	private String cleanResponses(boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			buf.append("<h2>Clean Responses</h2>");
		    
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted <", oneYearAgo).limit(querySizeLimit).keys().list();
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanQuizTransactions(String cursor,int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {
			Query<QuizTransaction> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Quiz Transactions</h2>");
				query = ofy().load().type(QuizTransaction.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(QuizTransaction.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<QuizTransaction>> keys = new ArrayList<Key<QuizTransaction>>();  // list of QuizTransaction entity keys for batch delete
		    QueryResultIterator<QuizTransaction> iterator = query.iterator();
		    
		    while (iterator.hasNext()) {
		    	QuizTransaction q = iterator.next();
		    	try {
		    		Key<User> k = Key.create(User.class,q.userId);  // look for the User of the QuizTransaction
		    		ofy().load().type(User.class).filterKey(k).keys().first().safe();
		    	} catch (Exception e) {  // the user does not exist
		    		keys.add(Key.create(q)); // add the QuizTransaction key to the list to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 5) {
		    	buf.append(cleanQuizTransactions(cursor,retries+1,testOnly));
		    }
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanHWTransactions(String cursor,int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<HWTransaction> query;
			 if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Homework Transactions</h2>");
				query = ofy().load().type(HWTransaction.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(HWTransaction.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
			ArrayList<Key<HWTransaction>> keys = new ArrayList<Key<HWTransaction>>();  // list of HWTransaction entity keys for batch delete
		    QueryResultIterator<HWTransaction> iterator = query.iterator();
		    
		    while (iterator.hasNext()) {
		    	HWTransaction h = iterator.next();
		    	try {
		    		Key<User> k = Key.create(User.class,h.userId);
		    		Key<User> key = ofy().load().type(User.class).filterKey(k).keys().first().now();
		    		if (!k.equals(key)) keys.add(Key.create(h)); // if user does not exist, add the QuizTransaction key to the list to be deleted
		    	} catch (Exception e) {  // catches exception if user does not exist
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 5) {
		    	buf.append(cleanHWTransactions(cursor,retries+1,testOnly));
		    }
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanPracticeExamTransactions(String cursor,int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<PracticeExamTransaction> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Practice Exam Transactions</h2>");
				query = ofy().load().type(PracticeExamTransaction.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(PracticeExamTransaction.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<PracticeExamTransaction>> keys = new ArrayList<Key<PracticeExamTransaction>>();  // list of PracticeExamTransaction entity keys for batch delete
		    QueryResultIterator<PracticeExamTransaction> iterator = query.iterator();

		    while (iterator.hasNext()) {
		    	PracticeExamTransaction p = iterator.next();
		    	try {
		    		Key<User> k = Key.create(User.class,p.userId);
		    		Key<User> key = ofy().load().type(User.class).filterKey(k).keys().first().now();
		    		if (!k.equals(key)) keys.add(Key.create(p)); // if user does not exist, add the QuizTransaction key to the list to be deleted
		    	} catch (Exception e) {  // catches exception if user does not exist
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 5) buf.append(cleanPracticeExamTransactions(cursor,retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanScores(String cursor, int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<Score> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean User Scores</h2>");
				query = ofy().load().type(Score.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(Score.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<Score>> keys = new ArrayList<Key<Score>>();  // list of Score entity keys for batch delete		    
		    QueryResultIterator<Score> iterator = query.iterator();

		    while (iterator.hasNext()) {
		    	Score s = iterator.next();
		    	try {
		    		Key<User> k = s.owner;
		    		Key<User> key = ofy().load().type(User.class).filterKey(k).keys().first().now();
		    		if (!k.equals(key)) keys.add(Key.create(s)); // if user does not exist, add the QuizTransaction key to the list to be deleted
		    	} catch (Exception e) {  // catches exception if user does not exist
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 9) buf.append(cleanScores(cursor,retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
		
	}
	
	private String cleanGroups(String cursor, int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<Group> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Groups</h2>");
				query = ofy().load().type(Group.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(Group.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<Group>> keys = new ArrayList<Key<Group>>();  // list of Group entity keys for batch delete
		    QueryResultIterator<Group> iterator = query.iterator();
		    
		    while (iterator.hasNext()) {
		    	Group g = iterator.next();
		    	if (!g.isActive()) keys.add(Key.create(g)); 
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 9) buf.append(cleanGroups(cursor,retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanGroups").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanAssignments(String cursor, int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<Assignment> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Assignments</h2>");
				query = ofy().load().type(Assignment.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(Assignment.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<Assignment>> keys = new ArrayList<Key<Assignment>>();  // list of Assignment entity keys for batch delete
		    QueryResultIterator<Assignment> iterator = query.iterator();
		    
		    while (iterator.hasNext()) {
		    	Assignment a = iterator.next();
		    	try {
		    		Key<Group> k = Key.create(Group.class,a.groupId);
		    		Key<Group> key = ofy().load().type(Group.class).filterKey(k).keys().first().now();
		    		if (!k.equals(key)) keys.add(Key.create(a)); // if user does not exist, add the QuizTransaction key to the list to be deleted
		    	} catch (Exception e) {  // catches exception if Group does not exist
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 9) buf.append(cleanAssignments(cursor,retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanDomains(String cursor, int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<Domain> query;
			if (cursor==null) {  // start new entity search
				buf.append("<h2>Clean Domains</h2>");
				query = ofy().load().type(Domain.class).limit(querySizeLimit);
			} else {  // continue search with the next 1000 entities
				query = ofy().load().type(Domain.class).startAt(Cursor.fromWebSafeString(cursor)).limit(querySizeLimit);
			}
			
		    ArrayList<Key<Domain>> keys = new ArrayList<Key<Domain>>();  // list of Domain entity keys for batch delete
		    QueryResultIterator<Domain> iterator = query.iterator();
		    
		    while (iterator.hasNext()) {
		    	Domain d = iterator.next();
		    	if (d.activeUsers==0 && d.created.before(oneMonthAgo)) keys.add(Key.create(d));  // flags for deletion if domain has been around for a while but has no users
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");
		    cursor = iterator.getCursor().toWebSafeString();
	    	
		    if (query.count()<querySizeLimit) buf.append("Done.<br>");
		    else if (retries < 9) buf.append(cleanDomains(cursor,retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDomains").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
}