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
	//Subject subject = Subject.getSubject();
	
	public String getServletInfo() {
		return "ChemVantage servlet that performs daily maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each month.
		// To run manually, use the URL /DataStoreCleaner?Task=CleanUsers or similar. 
		// No parameter runs all methods in background. 
		// Task=CleanAll runs all interactively.
		if (request.getParameter("Task")!=null) {  //specify Task to run manually
			doPost(request,response);
			return;
		}
		
		try {
			Queue queue = QueueFactory.getDefaultQueue(); 
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanGroups"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDomains"));
		} catch (Exception e) {
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
		
		String task = request.getParameter("Task");
		if (task==null) return;
		
		switch (task) {
		case "CleanUsers": out.println(cleanUsers(cursor,0)); break;
		case "CleanResponses": out.println(cleanResponses(cursor,0)); break;
		case "CleanQuizTransactions": out.println(cleanQuizTransactions(cursor,0)); break;
		case "CleanHWTransactions": out.println(cleanHWTransactions(cursor,0)); break;
		case "CleanPracticeExamTransactions": out.println(cleanPracticeExamTransactions(cursor,0)); break;
		case "CleanScores": out.println(cleanScores(cursor,0)); break;
		case "CleanGroups": out.println(cleanGroups(cursor,0)); break;
		case "CleanAssignments": out.println(cleanAssignments(cursor,0)); break;
		case "CleanDomains": out.println(cleanDomains(cursor,0)); break;
		case "CleanAll": 
			out.println(cleanUsers(cursor,0));
			out.println(cleanResponses(cursor,0));
			out.println(cleanQuizTransactions(cursor,0));
			out.println(cleanHWTransactions(cursor,0));
			out.println(cleanPracticeExamTransactions(cursor,0));
			out.println(cleanScores(cursor,0));
			out.println(cleanGroups(cursor,0));
			out.println(cleanAssignments(cursor,0));
			out.println(cleanDomains(cursor,0));
		default: return;
		}
	}

	private String cleanUsers(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<User> query = ofy().load().type(User.class).filter("lastLogin <", sixMonthsAgo).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Users</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<User> qri = query.iterator();
		    ArrayList<Key<User>> keys = new ArrayList<Key<User>>();  // list of User entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	User u = qri.next();
		    	if (deleteUser(u.id)) keys.add(Key.create(u));  // tests to see if user should be deleted
		    }

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanUsers(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("Cursor", cursor));
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
				if (u.isInstructor() && u.lastLogin.after(oneYearAgo)) return false; // save instructor accounts for one year
				if (u.isAdministrator() && u.lastLogin.after(oneYearAgo)) return false;  // preserve all admin accounts for one year
				if (u.lastLogin.getTime()==0L) {  // user never logged in; perhaps this is a brand new account
					u.lastLogin = new Date(1000L);   // so mark the account by advancing the lastLogin by 1 second
					ofy().save().entity(u);                   // so it will be deleted tomorrow instead if the user does not login
					return false;
				} 
			}
		} catch (Exception e) {  // in case of error, delete the user because the alias points to a null user
		}	
		return true; // signal that this user object should be deleted
	}

	private String cleanResponses(String cursor,int retries) {
		StringBuffer buf = new StringBuffer();
		try {			
			if (cursor==null) buf.append("<h2>Clean Responses</h2>");
		    
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted <", oneYearAgo).limit(querySizeLimit).keys().list();
			if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (keys.size()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanResponses("continue",retries+1));
		    else {
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses"));
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
			Query<QuizTransaction> query = ofy().load().type(QuizTransaction.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Quiz Transactions</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<QuizTransaction> qri = query.iterator();
		    ArrayList<Key<QuizTransaction>> keys = new ArrayList<Key<QuizTransaction>>();  // list of QuizTransaction entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	QuizTransaction q = qri.next();
	    		try {
		    		ofy().load().type(User.class).id(q.userId).safe();  // throws Exception if owner (User) does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(q));  // collection of keys for QuizTransactions to be deleted
		    	}
		    }

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanQuizTransactions(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
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
			Query<HWTransaction> query = ofy().load().type(HWTransaction.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Homework Transactions</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<HWTransaction> qri = query.iterator();
		    ArrayList<Key<HWTransaction>> keys = new ArrayList<Key<HWTransaction>>();  // list of HWTransaction entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	HWTransaction h = qri.next();
	    		try {
		    		ofy().load().type(User.class).id(h.userId).safe();  // throws Exception if user does not exist
		    	} catch (Exception e) {  // catches exception if user does not exist
		    		keys.add(Key.create(h));  // collection of keys for HWTransactions to be deleted
		    	}
		    }

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanHWTransactions(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
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
			Query<PracticeExamTransaction> query = ofy().load().type(PracticeExamTransaction.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean PracticeExam Transactions</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

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

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanPracticeExamTransactions(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions").param("Cursor", cursor));
		    	buf.append("Launching a new DataStoreCleaner task.");
		    }

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanScores(String cursor, int retries) {
		StringBuffer buf = new StringBuffer();
		try {			
			Query<Score> query = ofy().load().type(Score.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean User Scores</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

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

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanScores(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
		    	Queue queue = QueueFactory.getDefaultQueue();
		    	queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores").param("Cursor", cursor));
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
			Query<Group> query = ofy().load().type(Group.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Groups</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<Group> qri = query.iterator();
		    ArrayList<Key<Group>> keys = new ArrayList<Key<Group>>();  // list of Group entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Group g = qri.next();
		    	if (!g.isActive()) keys.add(Key.create(g)); 
		    }

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanGroups(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
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
			Query<Assignment> query = ofy().load().type(Assignment.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Assignments</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

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

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanAssignments(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
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
			Query<Domain> query = ofy().load().type(Domain.class).limit(querySizeLimit);
			if (cursor==null) buf.append("<h2>Clean Domains</h2>");
		    else query.startAt(Cursor.fromWebSafeString(cursor));

		    QueryResultIterator<Domain> qri = query.iterator();
		    ArrayList<Key<Domain>> keys = new ArrayList<Key<Domain>>();  // list of Domain entity keys for batch delete
		    
		    while (qri.hasNext()) {
		    	Domain d = qri.next();
	    		if (d.activeUsers==0 && d.created.before(oneMonthAgo)) keys.add(Key.create(d));  // flags for deletion if domain has been around for a while but has no users
		    }

		    if (keys.size() > 0) ofy().delete().keys(keys);

		    buf.append(query.count() + " entities examined, " + keys.size() + " deleted.<br/>");

		    if (query.count()<querySizeLimit) buf.append("Done.");
		    else if (retries < 9) buf.append(cleanDomains(qri.getCursor().toWebSafeString(),retries+1));
		    else {
		    	cursor = qri.getCursor().toWebSafeString();
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