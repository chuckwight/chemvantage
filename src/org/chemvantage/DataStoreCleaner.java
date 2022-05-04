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

import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.QueryResults;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;


@WebServlet("/DataStoreCleaner")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class DataStoreCleaner extends HttpServlet {
	private static final long serialVersionUID = 137L;
	Date sixMonthsAgo;
	Date oneYearAgo;
	Date threeYearsAgo;
	int querySizeLimit = 100;
	
	public String getServletInfo() {
		return "ChemVantage servlet that performs monthly maintenance of the datastore.";
	}
	
	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		String task = request.getParameter("Task");		
		if (task != null) doPost(request,response);
		else out.println(Subject.header("Datastore Cleaner") + interactiveMenu() + Subject.footer);		
	} 

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each month, or manually by this servlet.
		Date now = new Date();
		sixMonthsAgo = new Date(now.getTime()- 15768000000L);
		oneYearAgo = new Date(now.getTime()- 31536000000L);
		threeYearsAgo = new Date(now.getTime()- 94608000000L);

		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		StringBuffer buf = new StringBuffer(Subject.header("Datastore Cleaner"));
		buf.append("<h2>Data Store Cleaner</h2>");
		
		// This section handles individual tasks executed from the Task Queue
		String task = request.getParameter("Task");
		if (task==null) return;
		
		boolean testOnly = Boolean.parseBoolean(request.getParameter("TestOnly"));
		
		switch (task) {
		case "CleanResponses": buf.append(cleanResponses(testOnly)); break;
		case "CleanQuizTransactions": buf.append(cleanQuizTransactions(testOnly)); break;
		case "CleanHWTransactions": buf.append(cleanHWTransactions(testOnly)); break;
		case "CleanPracticeExamTransactions": buf.append(cleanPracticeExamTransactions(testOnly)); break;
		case "CleanScores": buf.append(cleanScores(testOnly)); break;
		case "CleanAssignments": buf.append(cleanAssignments(testOnly,request)); break;
		case "CleanBLTIConsumers": buf.append(cleanBLTIConsumers(testOnly)); break;
		case "CleanDeployments": buf.append(cleanDeployments(testOnly)); break;
		case "CleanUsers": buf.append(cleanUsers(testOnly)); break;
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
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("TestOnly", testOnly?"true":"false"));
			
			buf.append("8 background tasks launched to scrub all obsolete entity types from the datastore.");
			break;
		}
		buf.append("<br>" + Subject.footer);
		
		out.println(buf.toString());
	}
	
	String interactiveMenu() {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Data Store Cleaner</h2>");
		
		buf.append("<form method=post action=/DataStoreCleaner>");
		
		buf.append("<label><input type=radio name=TestOnly value=true checked> Test only (no deletions)</label><br>");
		buf.append("<label><input type=radio name=TestOnly value=false> Delete entities (cannot be undone)</label><p>");
		
		buf.append("Entities to be scrubbed from the datastore:<br>");
		buf.append("<label><input type=radio name=Task value=CleanResponses> Responses older than 3 years</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanQuizTransactions> Quiz transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanHWTransactions> Homework transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanPracticeExamTransactions> Practice Exam transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanScores> Scores older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanAssignments> Assignments with no Deployment</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanDeployments> Deployments with no logins for more than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanBLTIConsumers> BLTIConsumers older than 6 months with no assignments</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanUsers> Users whose tokens have expired</label><p>");
		buf.append("<label><input type=radio name=Task value=CleanAll>All of the entities above (launches background job)</label><p>");
		buf.append("<input type=submit><br>");
		buf.append("</form>");

		return buf.toString();
	}

	private String cleanResponses(boolean testOnly) {
		// This method deletes all Response entities older than 3 years

		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Responses</h2>");
		try {
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted <",threeYearsAgo).keys().list();			
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
			buf.append(keys.size() + " Responses more than 3 years old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<QuizTransaction>> keys = ofy().load().type(QuizTransaction.class).filter("downloaded <",oneYearAgo).keys().list();
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
		    buf.append(keys.size() + " QuizTransactions more than one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<HWTransaction>> keys = ofy().load().type(HWTransaction.class).filter("graded <",oneYearAgo).keys().list();
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
		    buf.append(keys.size() + " HWTransactions more than one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<PracticeExamTransaction>> keys = ofy().load().type(PracticeExamTransaction.class).filter("downloaded <",oneYearAgo).keys().list();
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
		    buf.append(keys.size() + " PracticeExamTransactions more than one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<Score>> keys = ofy().load().type(Score.class).filter("mostRecentAttempt <",oneYearAgo).keys().list();
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
			buf.append(keys.size() + " Scores more than one year old" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanAssignments(boolean testOnly, HttpServletRequest request) {
		// This method searches for assignments that do not belong to a Deployment and deletes them
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Assignments</h2>");
		try {
			Query<Assignment> query = ofy().load().type(Assignment.class).limit(1000);
			String cursorStr = request.getParameter("cursor");
			if (cursorStr != null)
				query = query.startAt(Cursor.fromUrlSafe(cursorStr));

			boolean continu = false;
			List<Key<Assignment>> assignmentKeys = new ArrayList<Key<Assignment>>();
			QueryResults<Assignment> iterator = query.iterator();

			while (iterator.hasNext()) {
				Assignment a = iterator.next();
				if (a.domain==null) assignmentKeys.add(Key.create(Assignment.class,a.id));
				else if (a.domain.contains("https://")) {
					if (ofy().load().filterKey(Key.create(Deployment.class,a.domain)).count()==0) assignmentKeys.add(Key.create(Assignment.class,a.id));
				}
				else {
					if (ofy().load().filterKey(Key.create(BLTIConsumer.class,a.domain)).count()==0) assignmentKeys.add(Key.create(Assignment.class,a.id));	
				}
				continu = true;
			}

			if (continu) {
				Cursor cursor = iterator.getCursorAfter();
				Queue queue = QueueFactory.getDefaultQueue();
				queue.add(withUrl("/DataStoreCleaner").param("cursor", cursor.toUrlSafe()));
			}

			if (assignmentKeys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = assignmentKeys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(assignmentKeys.subList(i*500, (i+1)*500));
				ofy().delete().keys(assignmentKeys.subList(nBatches*500, assignmentKeys.size()));
			}
			
			buf.append(assignmentKeys.size() + " orphan Assignments" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");

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
			List<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class).filter("created <",sixMonthsAgo).list();					
			for (BLTIConsumer c : consumers) {
				int n = ofy().load().type(Assignment.class).filter("domain",c.oauth_consumer_key).chunk(Integer.MAX_VALUE).count();
				if (n==0) keys.add(Key.create(c));
				else if (c.lastLogin==null || c.lastLogin.before(oneYearAgo)) {
					c.status = "suspended";
					ofy().save().entity(c);
				}
			}
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
			buf.append(keys.size() + " BLTIConsumers at least 6 months old with no Assignments" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<Deployment>> deploymentKeys = ofy().load().type(Deployment.class).filter("lastLogin <",oneYearAgo).keys().list();					
			if (deploymentKeys.size() > 0 && !testOnly) {  // delete all the old deployments in batches of 500 (max allowed by ofy().delete)
				int nBatches = deploymentKeys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(deploymentKeys.subList(i*500, (i+1)*500));
				ofy().delete().keys(deploymentKeys.subList(nBatches*500, deploymentKeys.size()));
			}
			
			buf.append(deploymentKeys.size() + " Deployments unused for more than one year" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");

		}catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
	
	private String cleanUsers(boolean testOnly) {
		// This method clears all User entity tokens that have expired
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Users</h2>");
		try {
			Date now = new Date();
			List<Key<User>> keys = ofy().load().type(User.class).filter("exp <", now).keys().list();
			if (keys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				int nBatches = keys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(keys.subList(i*500, (i+1)*500));
				ofy().delete().keys(keys.subList(nBatches*500, keys.size()));
			}
			
			buf.append(keys.size() + " Expired user tokens" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
}