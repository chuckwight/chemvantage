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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.QueryResultList;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Objectify;

public class DataStoreCleaner extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Date now;
	Date oneMonthAgo;
	Date sixMonthsAgo;
	Date oneYearAgo;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	int querySizeLimit = 1000;
	Subject subject = dao.getSubject();
	
	public String getServletInfo() {
		return "ChemVantage servlet that performs daily maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each day.
		
		if (request.getParameter("Task")!=null) {  //specify Task to run manually
			doPost(request,response);
			return;
		}
		
		try {
			Queue queue = QueueFactory.getDefaultQueue(); 
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanSessions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanGroups"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDomains"));
		} catch (Exception e) {
		}
	} 

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each day.
		now = new Date();
		oneMonthAgo = new Date(now.getTime()-2678400000L);
		sixMonthsAgo = new Date(now.getTime()-15768000000L);
		oneYearAgo = new Date(now.getTime()-31536000000L);
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		String cursor = request.getParameter("Cursor");
		
		String task = request.getParameter("Task");
		if (task==null) return;
		
		switch (task) {
		case "CleanUsers": out.println(cleanUsers(cursor,0)); break;
		case "CleanSessions": out.println(cleanSessions(cursor,0)); break;
		case "CleanResponses": out.println(cleanResponses(cursor,0)); break;
		case "CleanQuizTransactions": out.println(cleanQuizTransactions(cursor,0)); break;
		case "CleanHWTransactions": out.println(cleanHWTransactions(cursor,0)); break;
		case "CleanPracticeExamTransactions": out.println(cleanPracticeExamTransactions(cursor,0)); break;
		case "CleanGroups": out.println(cleanGroups(cursor,0)); break;
		case "CleanAssignments": out.println(cleanAssignments(cursor,0)); break;
		case "CleanDomains": out.println(cleanDomains(cursor,0)); break;
		case "CleanAll": 
			out.println(cleanUsers(cursor,0));
			out.println(cleanSessions(cursor,0));
			out.println(cleanResponses(cursor,0));
			out.println(cleanQuizTransactions(cursor,0));
			out.println(cleanHWTransactions(cursor,0));
			out.println(cleanPracticeExamTransactions(cursor,0));
			out.println(cleanGroups(cursor,0));
			out.println(cleanAssignments(cursor,0));
		default: return;
		}
	}

	private String cleanUsers(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("User");
			q.setFilter(new FilterPredicate("lastLogin",FilterOperator.LESS_THAN,oneYearAgo));
			
			PreparedQuery pq = datastore.prepare(q);

			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Users</h2>");
			else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		    
		    QueryResultList<Entity> users = pq.asQueryResultList(fetchOptions);
		    
			ArrayList<Key> keys = new ArrayList<Key>();  // list of user entity keys for batch delete
			
			for (Entity u : users) if (deleteUser((String)u.getProperty("id"))) keys.add(u.getKey());  // tests to see if user should be deleted
			
			 if (keys.size() > 0) datastore.delete(keys);
			    
			 buf.append(users.size() + " entities examined, " + keys.size() + " deleted.<br/>");
			   
		    if (users.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanUsers(users.getCursor().toWebSafeString(),retries+1));		
		    else {  // launch a new task for the next 1000 objects in the datastore
				cursor = users.getCursor().toWebSafeString();
				Queue queue = QueueFactory.getDefaultQueue();
    			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("Cursor", cursor));
    			buf.append("Launching a new DataStoreCleaner task.");
    		}

		} catch (Exception e) {
		}
		return buf.toString();
	}
		
	private boolean deleteUser(String userId) {   // recursive user deletion function that follows the alias tree to the end
		try {
			User u = ofy.get(User.class,userId);
			if (u.alias != null && !u.alias.equals(u.id)) {  // follow the alias chain to the end
				if (deleteUser(u.alias)) return true;
				else {  // this alias is to be retained; advance lastLogin to prevent inspection every day
					u.lastLogin = sixMonthsAgo;
					ofy.put(u);
					return false;
				}
			} else { // found the user at the end of the alias chain
				if (u.lastLogin.after(oneYearAgo)) return false;  // if any alias has a recent login, preserve the entire chain
				if (u.isAdministrator()) return false;  // preserve all admin accounts
				if (u.lastLogin.getTime()==0L) {  // user never logged in; perhaps this is a brand new account
					u.lastLogin = new Date(1000L);   // so mark the account by advancing the lastLogin by 1 second
					ofy.put(u);                   // so it will be deleted tomorrow instead if the user does not login
					return false;
				} 
			}
		} catch (Exception e) {  // in case of error, delete the user because the alias points to a null user
		}	
		return true; // signal that this user object should be deleted
	}

	private String cleanSessions(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		long now = new Date().getTime();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("_ah_SESSION");
			q.setFilter(new FilterPredicate("_expires",FilterOperator.LESS_THAN,now));
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean Sessions</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> sessions = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of session entity keys for batch delete
		    
		    for (Entity session : sessions) keys.add(session.getKey()); 
		    
		    if (keys.size() > 0) datastore.delete(keys);
		    
		    buf.append(sessions.size() + " entities examined, " + keys.size() + " deleted.<br/>");
		   
		    if (sessions.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanSessions(sessions.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = sessions.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanSessions").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanResponses(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("Response");
			q.setFilter(new FilterPredicate("submitted",FilterOperator.LESS_THAN,oneYearAgo));
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean Responses</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));
		    
		    QueryResultList<Entity> responses = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of session entity keys for batch delete

		    for (Entity r : responses) {
		    	Date submitted = (Date)r.getProperty("submitted");
		    	if (submitted.before(oneYearAgo)) keys.add(r.getKey()); 
		    }

		    if (keys.size() > 0) datastore.delete(keys);
		    
		    buf.append(responses.size() + " entities examined, " + keys.size() + " deleted.<br/>");
		   
		    if (responses.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanResponses(responses.getCursor().toWebSafeString(),retries+1));			
		    else {
				cursor = responses.getCursor().toWebSafeString();
				Queue queue = QueueFactory.getDefaultQueue();
    			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses").param("Cursor", cursor));
    			buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanQuizTransactions(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("QuizTransaction");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean QuizTransactions</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> quizTransactions = pq.asQueryResultList(fetchOptions);
		    
		    ArrayList<Key> keys = new ArrayList<Key>();  // list of session entity keys for batch delete
		    
		    for (Entity qt : quizTransactions) {
		    	try {
		    		ofy.get(User.class,(String)qt.getProperty("userId"));		    	
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(qt.getKey());
		    	}
		    }
		    
		    if (keys.size() > 0) datastore.delete(keys);
		    
		    buf.append(quizTransactions.size() + " entities examined, " + keys.size() + " deleted.<br/>");
		    
		    if (quizTransactions.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanQuizTransactions(quizTransactions.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = quizTransactions.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}
	
	private String cleanHWTransactions(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("HWTransaction");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean HWTransactions</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> hwTransactions = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of HWTransaction entity keys for batch delete
		    
		    for (Entity ht : hwTransactions) {
		    	try {
		    		ofy.get(User.class,(String)ht.getProperty("userId"));		    	
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(ht.getKey());
		    	}
		    }

		    if (keys.size() > 0) datastore.delete(keys);

		    buf.append(hwTransactions.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (hwTransactions.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanHWTransactions(hwTransactions.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = hwTransactions.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}

	private String cleanPracticeExamTransactions(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("PracticeExamTransaction");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean PracticeExamTransactions</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> peTransactions = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of HWTransaction entity keys for batch delete
		    
		    for (Entity pt : peTransactions) {
		    	try {
		    		ofy.get(User.class,(String)pt.getProperty("userId"));		    	
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(pt.getKey());
		    	}
		    }

		    if (keys.size() > 0) datastore.delete(keys);

		    buf.append(peTransactions.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (peTransactions.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanHWTransactions(peTransactions.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = peTransactions.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}

	private String cleanGroups(String cursor, int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("Group");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean Groups</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> groups = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of Assignment entity keys for batch delete
		    
		    for (Entity g : groups) {
		    	try {
		    		Group group = ofy.get(Group.class,(Long)g.getProperty("id"));
		    		if (group.isActive()) continue;
		    		if (group.validatedMemberCount() > 0) continue;
		    		ofy.get(User.class,group.instructorId);		    	
		    	} catch (Exception e) {  // catches exception if instructor does not exist
		    		keys.add(g.getKey());  // put group on the list to be deleted
		    	}
		    }

		    //if (keys.size() > 0) datastore.delete(keys);

		    buf.append(groups.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (groups.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanAssignments(groups.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = groups.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanGroups").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}
	
	private String cleanAssignments(String cursor, int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("Assignment");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean Assignments</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> assignments = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of Assignment entity keys for batch delete
		    
		    for (Entity a : assignments) {
		    	try {
		    		ofy.get(Group.class,(Long)a.getProperty("groupId"));		    	
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(a.getKey());
		    	}
		    }

		    if (keys.size() > 0) datastore.delete(keys);

		    buf.append(assignments.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (assignments.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanAssignments(assignments.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = assignments.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}

	private String cleanDomains(String cursor, int retries) {
		StringBuffer buf = new StringBuffer();
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService(); 
			Query q = new Query("Domain");
			
			PreparedQuery pq = datastore.prepare(q);
			
			FetchOptions fetchOptions = FetchOptions.Builder.withLimit(querySizeLimit);
		    if (cursor==null) buf.append("<h2>Clean Domains</h2>");
		    else fetchOptions.startCursor(Cursor.fromWebSafeString(cursor));

		    QueryResultList<Entity> domains = pq.asQueryResultList(fetchOptions);

		    ArrayList<Key> keys = new ArrayList<Key>();  // list of Assignment entity keys for batch delete
		   
		    for (Entity d : domains) {
		    	int activeUsers = ofy.query(User.class).filter("domain",d.getProperty("domainName")).count();
		    	Date created = (Date)d.getProperty("created");
		    	if (activeUsers==0 && created.before(oneMonthAgo)) keys.add(d.getKey());
		    }

		    if (keys.size() > 0) datastore.delete(keys);

		    buf.append(domains.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (domains.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanAssignments(domains.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = domains.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDomains").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}

		return buf.toString();
	}
}