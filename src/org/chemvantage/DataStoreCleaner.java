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
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet("/DataStoreCleaner")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
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
		// For GET request, use /DataStoreCleaner?Task=CleanUsers&TestOnly=true (or similar).
		// Default value of TestOnly is false;
		// For POST request, no parameters runs all methods
		
		now = new Date();
		oneMonthAgo = new Date(now.getTime()-2592000000L);
		fiveMonthsAgo = new Date(now.getTime()-13140000000L);
		sixMonthsAgo = new Date(now.getTime()-15768000000L);
		oneYearAgo = new Date(now.getTime()-31536000000L);
		
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		out.println(Home.header);
		
		String task = request.getParameter("Task");
		boolean testOnly = true;
		try {
			testOnly = Boolean.parseBoolean(request.getParameter("TestOnly"));
		} catch (Exception e) {}

		if (task!=null) {  // must specify Task parameter
			switch (task) {
			case "CleanResponses": out.println(cleanResponses(testOnly)); break;
			case "CleanQuizTransactions": out.println(cleanQuizTransactions(testOnly)); break;
			case "CleanHWTransactions": out.println(cleanHWTransactions(testOnly)); break;
			case "CleanPracticeExamTransactions": out.println(cleanPracticeExamTransactions(testOnly)); break;
			case "CleanScores": out.println(cleanScores(testOnly)); break;
			case "CleanAssignments": out.println(cleanAssignments(testOnly)); break;
			case "CleanBLTIConsumers": out.println(cleanBLTIConsumers(testOnly)); break;
			case "CleanDeployments": out.println(cleanDeployments(testOnly)); break;
			case "CleanAll": doPost(request,response); return;
			} 
		} else if (Boolean.parseBoolean(request.getParameter("TestAll"))) {
			out.println(cleanResponses(true)
					+ cleanQuizTransactions(true)
					+ cleanHWTransactions(true)
					+ cleanPracticeExamTransactions(true)
					+ cleanScores(true)
					+ cleanAssignments(true)
					+ cleanDeployments(true)
					+ cleanBLTIConsumers(true));
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

		// This section handles individual tasks executed from the Task Queue
		String task = request.getParameter("Task");
		if (task==null) return;
		
		boolean testOnly = true;
		try {
			testOnly = Boolean.parseBoolean(request.getParameter("TestOnly"));
		} catch (Exception e) {}

		switch (task) {
		case "CleanResponses": cleanResponses(testOnly); break;
		case "CleanQuizTransactions": cleanQuizTransactions(testOnly); break;
		case "CleanHWTransactions": cleanHWTransactions(testOnly); break;
		case "CleanPracticeExamTransactions": cleanPracticeExamTransactions(testOnly); break;
		case "CleanScores": cleanScores(testOnly); break;
		case "CleanAssignments": cleanAssignments(testOnly); break;
		case "CleanBLTIConsumers": cleanBLTIConsumers(testOnly); break;
		case "CleanDeployments": cleanDeployments(testOnly); break;
		case "CleanAll":
			// This section handles the parent case from the cron job
			Queue queue = QueueFactory.getDefaultQueue();

			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanQuizTransactions").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanHWTransactions").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanPracticeExamTransactions").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDeployments").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanBLTIConsumers").param("TestOnly", testOnly?"true":"false"));
			break;
		}
	}
	
	String interactiveMenu() {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Data Store Cleaner</h2>");
		
		buf.append("<a href=DataStoreCleaner?Task=CleanResponses&TestOnly=true>Test CleanResponses</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanQuizTransactions&TestOnly=true>Test CleanQuizTransactions</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanHWTransactions&TestOnly=true>Test CleanHWTransactions</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanPracticeExamTransactions&TestOnly=true>Test CleanPracticeExamTransactions</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanScores&TestOnly=true>Test CleanScores</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanAssignments&TestOnly=true>Test CleanAssignments</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanDeployments&TestOnly=true>Test CleanDeployments</a> (with no deletions)<br>");
		buf.append("<a href=DataStoreCleaner?Task=CleanBLTIConsumers&TestOnly=true>Test CleanBLTIConsumers</a> (with no deletions)<br>");
		
		buf.append("<a href=DataStoreCleaner?TestAll=true>Test all cleaners</a> (with no deletions)<p>");
	
		return buf.toString();
	}

	private String cleanResponses(boolean testOnly) {
		// This method deletes all Response entities older than one year

		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Quiz Transactions</h2>");
		try {
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted<",oneYearAgo).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanQuizTransactions(boolean testOnly) {
		// This method deletes all QuizTransactions older than one year
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Quiz Transactions</h2>");
		try {
			List<Key<QuizTransaction>> keys = ofy().load().type(QuizTransaction.class).filter("downloaded<",oneYearAgo).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
		    buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanHWTransactions(boolean testOnly) {
	// This method deletes all HWTransactions older than one year
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Homework Transactions</h2>");
		try {
			List<Key<HWTransaction>> keys = ofy().load().type(HWTransaction.class).filter("graded<",oneYearAgo).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
		    buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanPracticeExamTransactions(boolean testOnly) {
	// This method deletes all PracticeExamTransactions older than one year
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Practice Exam Transactions</h2>");
		try {
			List<Key<PracticeExamTransaction>> keys = ofy().load().type(PracticeExamTransaction.class).filter("downloaded<",oneYearAgo).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
		    buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanScores(boolean testOnly) {
		// This method deletes all Score entities older than one year

		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Scores</h2>");
		try {
			List<Key<Score>> keys = ofy().load().type(Score.class).filter("mostRecentAttempt<",oneYearAgo).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanAssignments(boolean testOnly) {
		// This method searches for BLTIConsumers at least 6 months old with no Assignment entities

		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean BLTIConsumers</h2>");
		try {
			List<Key<Assignment>> keys = new ArrayList<Key<Assignment>>();
			List<Assignment> assignments = ofy().load().type(Assignment.class).filter("created<",sixMonthsAgo).list();
			for (Assignment a : assignments) {
				if ("Quiz".equals(a.assignmentType) && ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
				else if ("Homework".equals(a.assignmentType) && ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
				else if ("PracticeExam".equals(a.assignmentType) && ofy().load().type(PracticeExamTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
			}
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanBLTIConsumers(boolean testOnly) {
		// This method searches for BLTIConsumers at least 6 months old with no Assignment entities
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean BLTIConsumers</h2>");
		try {
			List<Key<BLTIConsumer>> keys = new ArrayList<Key<BLTIConsumer>>();
			List<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class).filter("created<",sixMonthsAgo).list();					
			for (BLTIConsumer c : consumers) {
				int n = ofy().load().type(Assignment.class).filter("domain",c.oauth_consumer_key).chunk(Integer.MAX_VALUE).count();
				if (n==0) keys.add(Key.create(c));
			}
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");

		}catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanDeployments(boolean testOnly) {
		// This method searches for BLTIConsumers at least 6 months old with no Assignment entities
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Deployments</h2>");
		try {
			List<Key<Deployment>> keys = new ArrayList<Key<Deployment>>();
			List<Deployment> deployments = ofy().load().type(Deployment.class).filter("created<",sixMonthsAgo).list();					
			for (Deployment d : deployments) {
				int n = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).chunk(Integer.MAX_VALUE).count();
				if (n==0) keys.add(Key.create(d));
			}

			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " entities, " + keys.size() + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");

		}catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
}