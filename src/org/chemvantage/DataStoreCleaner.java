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
	int querySizeLimit = 1000;
	
	public String getServletInfo() {
		return "ChemVantage servlet that performs daily maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each month.
		// web.xml file set to Admin access only; change this to run manually
		// For GET request, use /DataStoreCleaner?Task=CleanUsers&TestOnly=true (or similar).
		// Default value of TestOnly is false;
		// For POST request, no parameters runs all methods
		if (request.getParameter("Task")!=null) {  // must specify Task parameter
			doPost(request,response);
			return;
		} else {
			PrintWriter out = response.getWriter();
			response.setContentType("text/html");
			out.println("<h2>Data Store Cleaner</h2>Useage: /DataStoreCleaner?Task=CleanAll [&TestOnly=true]");
		}
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
		case "CleanUsers": out.println(cleanUsers(cursor,0,testOnly)); break;
		case "CleanResponses": out.println(cleanResponses(cursor,0,testOnly)); break;
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

	private String cleanUsers(String cursor,int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {
			//Query<User> query = ofy().load().type(User.class).limit(querySizeLimit); // temporary
			
			Query<User> query = ofy().load().type(User.class).filter("lastLogin <", sixMonthsAgo).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Users</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<User> qri = query.iterator();
		    ArrayList<Key<User>> keys = new ArrayList<Key<User>>();  // list of User entity keys for batch delete

		    while (qri.hasNext()) {
		    	User u = qri.next();
/*
  		    	u.firstName = "";
		    	u.lastName = "";
		    	u.lowercaseName = "";
		    	u.notifyDeadlines = false;
		    	if (!u.isChemVantageAdmin()) {
		    		u.email = "";
		    		u.smsMessageDevice = "";
		    	}
*/		    
		    	if (deleteUser(u.id)) {  // tests to see if user should be deleted
		    		keys.add(Key.create(u));  // saves key in group to be deleted
		    	} else ofy().save().entity(u);
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys).now();
		     
		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanUsers(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("Cursor", cursor).param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

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

	private String cleanResponses(String cursor,int retries,boolean testOnly) {
		StringBuffer buf = new StringBuffer();
		try {			
			if (cursor==null) buf.append("<h2>Clean Responses</h2>");
		    
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted <", oneYearAgo).limit(querySizeLimit).keys().list();
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (keys.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanResponses("continue",retries+1,testOnly));
		    else if (!testOnly) {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses").param("TestOnly", testOnly?"true":"false"));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

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
		    
		    for (QuizTransaction q : query) {
		    	try {
		    		ofy().load().type(User.class).id(q.userId).safe();  // throws Exception if owner (User) does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(q));  // collection of keys for QuizTransactions to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanQuizTransactions(query.iterator().getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = query.iterator().getCursor().toWebSafeString();
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
			
			QueryResultIterator<HWTransaction> qri = query.iterator();			   
		    ArrayList<Key<HWTransaction>> keys = new ArrayList<Key<HWTransaction>>();  // list of HWTransaction entity keys for batch delete
		    for (HWTransaction h : query) {
		    	try {
		    		ofy().load().type(User.class).id(h.userId).safe();  // throws Exception if user does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(h));  // collection of keys for HWTransactions to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanHWTransactions(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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
			
		    QueryResultIterator<PracticeExamTransaction> qri = query.iterator();
		    ArrayList<Key<PracticeExamTransaction>> keys = new ArrayList<Key<PracticeExamTransaction>>();  // list of PracticeExamTransaction entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	PracticeExamTransaction p = qri.next();
	    		try {
		    		ofy().load().type(User.class).id(p.userId).safe();  // throws Exception if user does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(p));  // collection of keys for PracticeExamTransactions to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanPracticeExamTransactions(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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
			
		    QueryResultIterator<Score> qri = query.iterator();
		    ArrayList<Key<Score>> keys = new ArrayList<Key<Score>>();  // list of Score entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Score s = qri.next();
	    		try {
		    		ofy().load().key(s.owner).safe();  // throws Exception if owner (User) does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(s));  // collection of keys for Scores to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanScores(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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
			
		    QueryResultIterator<Group> qri = query.iterator();
		    ArrayList<Key<Group>> keys = new ArrayList<Key<Group>>();  // list of Group entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Group g = qri.next();
		    	if (!g.isActive()) keys.add(Key.create(g)); 
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanGroups(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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
			
		    QueryResultIterator<Assignment> qri = query.iterator();
		    ArrayList<Key<Assignment>> keys = new ArrayList<Key<Assignment>>();  // list of Assignment entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Assignment a = qri.next();
	    		try {
		    		ofy().load().type(Group.class).id(a.groupId).safe();  // throws Exception if the corresponding Group does not exist
		    	} catch (Exception e) {  // catches exception if Group does not exist
		    		keys.add(Key.create(a));  // collection of keys for Assignments to be deleted
		    	}
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanAssignments(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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
			
		    QueryResultIterator<Domain> qri = query.iterator();
		    ArrayList<Key<Domain>> keys = new ArrayList<Key<Domain>>();  // list of Domain entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Domain d = qri.next();
	    		if (d.activeUsers==0 && d.created.before(oneMonthAgo)) keys.add(Key.create(d));  // flags for deletion if domain has been around for a while but has no users
		    }

		    if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + (testOnly?" identified":" deleted") + ".<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanDomains(qri.getCursor().toWebSafeString(),retries+1,testOnly));
		    else if (!testOnly) {
		    	cursor = qri.getCursor().toWebSafeString();
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