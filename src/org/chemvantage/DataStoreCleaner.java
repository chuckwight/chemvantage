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
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlecode.objectify.Key;


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
		case "CleanTransactions": buf.append(cleanTransactions(testOnly)); break;
		case "CleanScores": buf.append(cleanScores(testOnly)); break;
		case "CleanAssignments": buf.append(cleanAssignments(testOnly,request)); break;
		case "CleanDeployments": buf.append(cleanDeployments(testOnly)); break;
		case "CleanUsers": buf.append(cleanUsers(testOnly)); break;
		case "CleanAll":
			// This section handles the parent case from the cron job
			Queue queue = QueueFactory.getDefaultQueue();

			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanResponses").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanTransactions").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanScores").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanAssignments").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanDeployments").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanBLTIConsumers").param("TestOnly", testOnly?"true":"false"));
			queue.add(withUrl("/DataStoreCleaner").param("Task","CleanUsers").param("TestOnly", testOnly?"true":"false"));
			
			buf.append("7 background tasks launched to scrub all obsolete entity types from the datastore.");
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
		buf.append("<label><input type=radio name=Task value=CleanTransactions> Transactions with no existing Assignment entity</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanScores> Scores older than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanAssignments> Assignments older than 6 months with no lineitem_url</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanDeployments> Deployments with no logins for more than 1 year</label><br>");
		buf.append("<label><input type=radio name=Task value=CleanBLTIConsumers> BLTIConsumers with no logins for more than 1 year</label><br>");
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

	private String cleanTransactions(boolean testOnly) {
		// this method deletes all transactions that have no corresponding assignment and were not deleted by Clean Assignments
		
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Transactions</h2>");
		
		// Clean QuizTransactions
		List<QuizTransaction> qts = ofy().load().type(QuizTransaction.class).limit(1000).list();
		List<Key<QuizTransaction>> qtKeys = new ArrayList<Key<QuizTransaction>>();
		for (QuizTransaction qt :qts) {
			if (qt.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,qt.assignmentId)).count()==0) qtKeys.add(Key.create(qt));
		}
		if (!testOnly) {
			int nBatches = qtKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(qtKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(qtKeys.subList(nBatches*500, qtKeys.size()));
		}
		buf.append(qtKeys.size() + " orphan QuizTransactions" + (testOnly?" identified":" deleted") + ".<br>");
		
		// Clean HWTransactions
		List<HWTransaction> hwts = ofy().load().type(HWTransaction.class).limit(1000).list();
		List<Key<HWTransaction>> hwtKeys = new ArrayList<Key<HWTransaction>>();
		for (HWTransaction hwt :hwts) {
			if (hwt.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,hwt.assignmentId)).count()==0) hwtKeys.add(Key.create(hwt));
		}
		if (!testOnly) {
			int nBatches = hwtKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(hwtKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(hwtKeys.subList(nBatches*500, hwtKeys.size()));
		}
		buf.append(hwtKeys.size() + " orphan HWTransactions" + (testOnly?" identified":" deleted") + ".<br>");
		
		// Clean PracticeExamTransactions
		List<PracticeExamTransaction> pets = ofy().load().type(PracticeExamTransaction.class).limit(1000).list();
		List<Key<PracticeExamTransaction>> petKeys = new ArrayList<Key<PracticeExamTransaction>>();
		for (PracticeExamTransaction pet :pets) {
			if (pet.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,pet.assignmentId)).count()==0) petKeys.add(Key.create(pet));
		}
		if (!testOnly) {
			int nBatches = petKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(petKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(petKeys.subList(nBatches*500, petKeys.size()));
		}
		buf.append(petKeys.size() + " orphan PracticeExamTransactions" + (testOnly?" identified":" deleted") + ".<br>");

		// Clean VideoTransactions
		List<VideoTransaction> vts = ofy().load().type(VideoTransaction.class).limit(1000).list();
		List<Key<VideoTransaction>> vtKeys = new ArrayList<Key<VideoTransaction>>();
		for (VideoTransaction vt :vts) {
			if (vt.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,vt.assignmentId)).count()==0) vtKeys.add(Key.create(vt));
		}
		if (!testOnly) {
			int nBatches = vtKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(vtKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(vtKeys.subList(nBatches*500, vtKeys.size()));
		}
		buf.append(vtKeys.size() + " orphan VideoTransactions" + (testOnly?" identified":" deleted") + ".<br>");

		// Clean PollTransactions
		List<PollTransaction> pts = ofy().load().type(PollTransaction.class).limit(1000).list();
		List<Key<PollTransaction>> ptKeys = new ArrayList<Key<PollTransaction>>();
		for (PollTransaction pt :pts) {
			if (pt.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,pt.assignmentId)).count()==0) ptKeys.add(Key.create(pt));
		}
		if (!testOnly) {
			int nBatches = ptKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(ptKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(ptKeys.subList(nBatches*500, ptKeys.size()));
		}
		buf.append(ptKeys.size() + " orphan PollTransactions" + (testOnly?" identified":" deleted") + ".<br>");

		// Clean PlacementExamTransactions
		List<PlacementExamTransaction> plts = ofy().load().type(PlacementExamTransaction.class).limit(1000).list();
		List<Key<PlacementExamTransaction>> pltKeys = new ArrayList<Key<PlacementExamTransaction>>();
		for (PlacementExamTransaction plt :plts) {
			if (plt.assignmentId==0 || ofy().load().filterKey(Key.create(Assignment.class,plt.assignmentId)).count()==0) pltKeys.add(Key.create(plt));
		}
		if (!testOnly) {
			int nBatches = pltKeys.size()/500;
			for (int i=0;i<nBatches;i++) ofy().delete().keys(pltKeys.subList(i*500, (i+1)*500));
			ofy().delete().keys(pltKeys.subList(nBatches*500, pltKeys.size()));
		}
		buf.append(pltKeys.size() + " orphan PlacementExamTransactions" + (testOnly?" identified":" deleted") + ".<br>");
		buf.append("Done.<br>");

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
		// This method searches for assignments having a specified lineitems_url and validates them
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Clean Assignments</h2>");
		String lti_ags_lineitems_url = request.getParameter("lti_ags_lineitems_url");
		String platform_deployment_id = request.getParameter("platform_deployment_id");
		if (lti_ags_lineitems_url==null || platform_deployment_id==null) return "Error: Must specify lti_ags_lineitems_url and platform_deployment_id.";
		Date now = new Date();
		Date oneMonthAgo = new Date(now.getTime()-2635200000L);
		try {
			Deployment d = Deployment.getInstance(platform_deployment_id);
			JsonArray lineitem_container = LTIMessage.getLineItemContainer(d, lti_ags_lineitems_url);
			buf.append("Retrieved lineitem container with " + lineitem_container.size() + " lineitems:<br/>");
			Iterator<JsonElement> iterator = lineitem_container.iterator();
			List<Assignment> assignmentsToBeSaved = new ArrayList<Assignment>();
			while (iterator.hasNext()) {
				JsonObject lineitem = iterator.next().getAsJsonObject();
				String lineitem_id = lineitem.get("id").getAsString();
				Assignment a = ofy().load().type(Assignment.class).filter("lti_ags_lineitem_url",lineitem_id).first().now();
				if (a==null) continue;
				if (a.valid==null || a.valid.before(oneMonthAgo)) {
					a.lti_ags_lineitems_url = lti_ags_lineitems_url;
					a.valid = now;
					assignmentsToBeSaved.add(a);
				}
				buf.append(a.lti_ags_lineitem_url + "<br/>");
			}
			if (!assignmentsToBeSaved.isEmpty()) ofy().save().entities(assignmentsToBeSaved);
		} catch (Exception e) {
			buf.append("Error: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
/*		
		try {
			Query<Assignment> query = ofy().load().type(Assignment.class).limit(1000);
			String cursorStr = request.getParameter("cursor");
			if (cursorStr != null)
				query = query.startAt(Cursor.fromUrlSafe(cursorStr));

			boolean continu = false;
			List<Key<Assignment>> assignmentKeys = new ArrayList<Key<Assignment>>();
			QueryResults<Assignment> iterator = query.iterator();
			int nBatches = 0;
			
			while (iterator.hasNext()) {
				Assignment a = iterator.next();
				if (a.domain==null && a.created.before(sixMonthsAgo)) assignmentKeys.add(Key.create(Assignment.class,a.id));
				else if (a.domain.contains("https://")) {
					if (ofy().load().filterKey(Key.create(Deployment.class,a.domain)).count()==0) assignmentKeys.add(Key.create(Assignment.class,a.id));
				}
				continu = true;
				
				if (testOnly || a.assignmentType==null) continue;
				
				switch (a.assignmentType) {  // delete all associated transactions
				case "Quiz":
					List<Key<QuizTransaction>> qtKeys = ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = qtKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(qtKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(qtKeys.subList(nBatches*500, qtKeys.size()));
					break;
				case "Homework":
					List<Key<HWTransaction>> hwtKeys = ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = hwtKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(hwtKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(hwtKeys.subList(nBatches*500, hwtKeys.size()));
					break;
				case "PracticeExam":
					List<Key<PracticeExamTransaction>> peKeys = ofy().load().type(PracticeExamTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = peKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(peKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(peKeys.subList(nBatches*500, peKeys.size()));
					break;
				case "Poll":
					List<Key<PollTransaction>> ptKeys = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = ptKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(ptKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(ptKeys.subList(nBatches*500, ptKeys.size()));
					break;
				case "PlacementExam":
					List<Key<PlacementExamTransaction>> plKeys = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = plKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(plKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(plKeys.subList(nBatches*500, plKeys.size()));
					break;
				case "Video":
					List<Key<VideoTransaction>> vtKeys = ofy().load().type(VideoTransaction.class).filter("assignmentId",a.id).limit(1000).keys().list();
					nBatches = vtKeys.size()/500;
					for (int i=0;i<nBatches;i++) ofy().delete().keys(vtKeys.subList(i*500, (i+1)*500));
					ofy().delete().keys(vtKeys.subList(nBatches*500, vtKeys.size()));
					break;
				default:  // it is possible that assignmentType is empty - no transactions need to be deleted
				}
			}

			if (continu) {
				Cursor cursor = iterator.getCursorAfter();
				Queue queue = QueueFactory.getDefaultQueue();
				queue.add(withUrl("/DataStoreCleaner").param("cursor", cursor.toUrlSafe()));
			}

			if (assignmentKeys.size() > 0 && !testOnly) {  // delete all the expired keys in batches of 500 (max allowed by ofy().delete)
				nBatches = assignmentKeys.size()/500;
				for (int i=0;i<nBatches;i++) ofy().delete().keys(assignmentKeys.subList(i*500, (i+1)*500));
				ofy().delete().keys(assignmentKeys.subList(nBatches*500, assignmentKeys.size()));
			}

			buf.append(assignmentKeys.size() + " orphan Assignments" + (testOnly?" identified":" deleted") + ".<br>");
			buf.append("Done.<br>");

		} catch (Exception e) {
			buf.append("Error: " + e.toString());
		}
		return buf.toString();
*/
	}

	String getLineitemsUrl(String lineitem_url, String platformDeploymentId) {
		try {
			URL lineitemUrl = new URL(lineitem_url);
			Deployment d = Deployment.getInstance(platformDeploymentId);
			String query = lineitemUrl.getQuery();
			if ("moodle".equals(d.lms_type)) lineitem_url = lineitem_url.substring(0,lineitem_url.lastIndexOf("/lineitem")); /// strips "/lineitem"
			lineitem_url = lineitem_url.substring(0,lineitem_url.lastIndexOf("/"));  // strips lineitem identifier
			return lineitem_url + (query==null?"":"?"+query);
		} catch (Exception e) {
			return null;
		}
	}
	private String cleanDeployments(boolean testOnly) {
		// This method searches for Deployment entities with no logins for at least 1 year
		
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