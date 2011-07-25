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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.QueryResultIteratorWrapper;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;



public class DataHealer extends HttpServlet {
	private static final long serialVersionUID = 137L;

	public String getServletInfo() {
		return "PZone servlet presents user's detailed scores in the Practice Zone site.";
	}

	public static final long LIMIT_MILLIS = 1000 * 25; // provide a little leeway

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    long startTime = System.currentTimeMillis();

	    Objectify ofy = ObjectifyService.begin();
	    
	    String className = request.getParameter("ClassName");
	    if (className==null) className = "Assignment";
	    String cursorStr = request.getParameter("Cursor");

	    if (className.equals("Assignment")) {
	    	Query<Assignment> query = ofy.query(Assignment.class);
	    	QueryResultIteratorWrapper<Assignment> iterator = (QueryResultIteratorWrapper<Assignment>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		Assignment a = iterator.next();
	    		Group g = ofy.find(Group.class,a.groupId);
	    		if (g == null) ofy.delete(a);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","Assignment").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "Question";
	    	cursorStr = null;
	    }
	    
	    if (className.equals("Question")) {
	    	Query<Question> query = ofy.query(Question.class);
	    	QueryResultIteratorWrapper<Question> iterator = (QueryResultIteratorWrapper<Question>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		Question q = iterator.next();
	    		Topic t = ofy.find(Topic.class,q.topicId);
	    		if (t == null) ofy.delete(q);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","Question").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "QuizTransaction";
	    	cursorStr = null;
	    }
	    
	    if (className.equals("QuizTransaction")) {
	    	Query<QuizTransaction> query = ofy.query(QuizTransaction.class);
	    	QueryResultIteratorWrapper<QuizTransaction> iterator = (QueryResultIteratorWrapper<QuizTransaction>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		QuizTransaction t = iterator.next();
	    		User u = ofy.find(User.class,t.userId);
	    		if (u == null) ofy.delete(t);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","QuizTransaction").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "HWTransaction";
	    	cursorStr = null;
	    }
	    
	    if (className.equals("HWTransaction")) {
	    	Query<HWTransaction> query = ofy.query(HWTransaction.class);
	    	QueryResultIteratorWrapper<HWTransaction> iterator = (QueryResultIteratorWrapper<HWTransaction>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		HWTransaction t = iterator.next();
	    		User u = ofy.find(User.class,t.userId);
	    		if (u == null) ofy.delete(t);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","HWTransaction").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "PracticeExamTransaction";
	    	cursorStr = null;
	    }
	    
	    if (className.equals("PracticeExamTransaction")) {
	    	Query<PracticeExamTransaction> query = ofy.query(PracticeExamTransaction.class);
	    	QueryResultIteratorWrapper<PracticeExamTransaction> iterator = (QueryResultIteratorWrapper<PracticeExamTransaction>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		PracticeExamTransaction t = iterator.next();
	    		User u = ofy.find(User.class,t.userId);
	    		if (u == null) ofy.delete(t);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","PracticeExamTransaction").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "VideoTransaction";
	    	cursorStr = null;
	    }
	    
	    if (className.equals("VideoTransaction")) {
	    	Query<VideoTransaction> query = ofy.query(VideoTransaction.class);
	    	QueryResultIteratorWrapper<VideoTransaction> iterator = (QueryResultIteratorWrapper<VideoTransaction>) query.iterator();
	    	if (cursorStr != null)
	    		query.startCursor(Cursor.fromWebSafeString(cursorStr));
	    	while (iterator.hasNext()) {
	    		VideoTransaction t = iterator.next();
	    		User u = ofy.find(User.class,t.userId);
	    		if (u == null) ofy.delete(t);

	    		if (System.currentTimeMillis() - startTime > LIMIT_MILLIS) {
	    			Cursor cursor = iterator.getCursor();
	    			Queue queue = QueueFactory.getDefaultQueue();
	    			queue.add(withUrl("/DataHealer").param("ClassName","VideoTransaction").param("Cursor", cursor.toWebSafeString()));
	    			break;
	    		}
	    	}
	    	className = "";
	    	cursorStr = null;
	    }    
	}
}