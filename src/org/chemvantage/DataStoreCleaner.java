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
	Date sixMonthsAgo;
	Date oneYearAgo;
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
		else out.println(Home.header("Datastore Cleaner") + interactiveMenu() + Home.footer);		
	} 

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		// This servlet is called by the cron daemon once each month, or manually by this servlet.
		Date now = new Date();
		sixMonthsAgo = new Date(now.getTime()- 15768000000L);
		oneYearAgo = new Date(now.getTime()- 31536000000L);

		PrintWriter out = response.getWriter();
		response.setContentType("text/html");
		
		StringBuffer buf = new StringBuffer(Home.header("Datastore Cleaner"));
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
		case "CleanAssignments": buf.append(cleanAssignments(testOnly)); break;
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
		buf.append("<br>" + Home.footer);
		
		out.println(buf.toString());
	}
	
	String interactiveMenu() {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Data Store Cleaner</h2>");
		
		buf.append("<form method=post action=/DataStoreCleaner>");
		
		buf.append("<label><input type=radio name=TestOnly value=true checked> Test only (no deletions)</label><br>");
		buf.append("<label><input type=radio name=TestOnly value=false> Delete entities (cannot be undone)</label><p>");
		
		buf.append("Entities to be scrubbed from the datastore:<br>");
		buf.append("<label><input type=radio name=Task value=CleanResponses> Responses older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanQuizTransactions> Quiz transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanHWTransactions> Homework transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanPracticeExamTransactions> Practice Exam transactions older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanScores> Scores older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanAssignments> Assignments older than 6 months with no transactions</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanDeployments> Deployments older than 6 months with no assignments</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanBLTIConsumers> BLTIConsumers older than 6 months with no assignments</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanUsers> Users whose tokens have expired</label><p>");
		buf.append("<label><input type=radio name=Task value=CleanAll>All of the entities above (launches background job)</label><p>");
		buf.append("<input type=submit><br>");
		buf.append("</form>");

		return buf.toString();
	}

	private String cleanResponses(boolean testOnly) {
		// This method deletes all Response entities older than one year

		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Responses</h2>");
		try {
			List<Key<Response>> keys = ofy().load().type(Response.class).filter("submitted <",oneYearAgo).limit(500).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " Responses more then one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<QuizTransaction>> keys = ofy().load().type(QuizTransaction.class).filter("downloaded <",oneYearAgo).limit(500).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " QuizTransactions more then one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<HWTransaction>> keys = ofy().load().type(HWTransaction.class).filter("graded <",oneYearAgo).limit(500).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " HWTransactions more then one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<PracticeExamTransaction>> keys = ofy().load().type(PracticeExamTransaction.class).filter("downloaded <",oneYearAgo).limit(500).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

		    buf.append(keys.size() + " PracticeExamTransactions more then one year old" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<Key<Score>> keys = ofy().load().type(Score.class).filter("mostRecentAttempt <",oneYearAgo).limit(500).keys().list();
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " Scores more then one year old" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}

	private String cleanAssignments(boolean testOnly) {
		// This method searches for assignments more than six months old with no remaining transactions (deleted after 1 year)
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Assignments</h2>");
		try {
			List<Key<Assignment>> keys = new ArrayList<Key<Assignment>>();
			List<Assignment> assignments = ofy().load().type(Assignment.class).filter("created <",sixMonthsAgo).limit(500).list();
			for (Assignment a : assignments) {
				if ("Quiz".equals(a.assignmentType) && ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
				else if ("Homework".equals(a.assignmentType) && ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
				else if ("PracticeExam".equals(a.assignmentType) && ofy().load().type(PracticeExamTransaction.class).filter("assignmentId",a.id).count()==0) keys.add(Key.create(a));
			}

			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " Assignments at least 6 months old with no transactions" + (testOnly?" identified":" deleted") + ".<br>");
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
			List<BLTIConsumer> consumers = ofy().load().type(BLTIConsumer.class).filter("created <",sixMonthsAgo).limit(500).list();					
			for (BLTIConsumer c : consumers) {
				int n = ofy().load().type(Assignment.class).filter("domain",c.oauth_consumer_key).chunk(Integer.MAX_VALUE).count();
				if (n==0) keys.add(Key.create(c));
			}
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

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
			List<Key<Deployment>> keys = new ArrayList<Key<Deployment>>();
			List<Deployment> deployments = ofy().load().type(Deployment.class).filter("created <",sixMonthsAgo).limit(500).list();					
			for (Deployment d : deployments) {
				int n = ofy().load().type(Assignment.class).filter("domain",d.platform_deployment_id).chunk(Integer.MAX_VALUE).count();
				if (n==0) keys.add(Key.create(d));
			}

			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " Deployments at least 6 months old with no Assignments" + (testOnly?" identified":" deleted") + ".<br>");
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
			
			if (keys.size() > 0 && !testOnly) ofy().delete().keys(keys);

			buf.append(keys.size() + " Expired user tokens" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");
		
		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
	}
}