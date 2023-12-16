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

import static com.googlecode.objectify.ObjectifyService.ofy;
import static com.googlecode.objectify.ObjectifyService.key;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

/* 
 * Access to this servlet is restricted to ChemVantage admin users and the project service account
 * by specifying login: admin in a url handler of the project app.yaml file
 */
@WebServlet("/EraseEntity")
public class EraseEntity extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		String domain = request.getParameter("Domain");
		
		if (domain == null) out.println(Subject.header("Erase ChemVantage Entity") + menu() + Subject.footer);
		else out.println(Subject.header("Erase ChemVantage Entity") + options(domain) + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// This method gets an array of assignmentId values (initially as a Sting[] array
		// and deletes the assignments along all of the associated transactions
		
		// Create containers for all Assignments and their Keys (to be deleted)
		List<Assignment> assignments = new ArrayList<Assignment>();
		List<Key<Assignment>> assignmentKeys = new ArrayList<Key<Assignment>>();
		
		// If selected, erase the domain:		
		String domain = request.getParameter("Domain");
		if (Boolean.parseBoolean(request.getParameter("EraseDomain"))) {
			try {
				new URL(domain);  // throws Exception if this is a BLTIConsumer
				ofy().delete().key(key(Deployment.class,domain));
			} catch (Exception e) {
			}
			// Load all of this domain's assignments Keys into the List of assignmentKeys:
			assignmentKeys = ofy().load().type(Assignment.class).filter("domain",domain).keys().list();
		} else { // otherwise, delete only selected assignments and all associated transactions
			String[] assignmentIds = request.getParameterValues("AssignmentId");
			if (assignmentIds != null) { 
				for (String id : assignmentIds) assignmentKeys.add(key(Assignment.class,Long.parseLong(id)));
				assignments = new ArrayList<Assignment>(ofy().load().keys(assignmentKeys).values());
			}
		}
		
		// Delete all of the transactions associated with each assignment
		for (Assignment a : assignments) {
			if (a.assignmentType != null) {
				switch (a.assignmentType) {
					case "Quiz": ofy().delete().keys(ofy().load().type(QuizTransaction.class).filter("assignmentId", a.id).keys().iterable()); break;				
					case "Homework": ofy().delete().keys(ofy().load().type(HWTransaction.class).filter("assignmentId", a.id).keys().iterable()); break;
					case "PracticeExam": ofy().delete().keys(ofy().load().type(PracticeExamTransaction.class).filter("assignmentId", a.id).keys().iterable()); break;
					case "VideoQuiz": ofy().delete().keys(ofy().load().type(VideoTransaction.class).filter("assignmentId", a.id).keys().iterable()); break;
					case "Poll": ofy().delete().keys(ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).keys().iterable());break;
					case "PlacementExam": ofy().delete().keys(ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",a.id).keys().iterable());break;
				}
			}
		}
		
		// Now delete the assignments
		if (assignmentKeys.size()>0) ofy().delete().keys(assignmentKeys);
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Subject.header("Erase ChemVantage Entity") + menu(domain) + Subject.footer);
	}

	String menu() {
		return menu(null);
	}
	
	String menu(String domain) {
		StringBuffer buf = new StringBuffer("<h3>Database Utility for Permanent Erasure</h3>");
		buf.append("The ChemVantage administrator can use this utility to permanently delete the following from the datastore:"
				+ "<ul>"
				+ "<li>Selected assignments and associated transactions"
				+ "<li>All assignments and transactions associated with a domain (complete reset)"
				+ "<li>A deployment and all associated assignments and transactions (complete delete)"
				+ "</ul>");
		List<Deployment> deployments = ofy().load().type(Deployment.class).list();
		
		buf.append("Select the domain of interest by its Consumer Key or Deployment ID:<br>"
				+ "<form method=get action=/EraseEntity>"
				+ "<select name=Domain>"
				+ "<option value=''" + (domain==null?" SELECTED":"") + ">Select a domain</option>");
		for (Deployment d : deployments) buf.append("<option value='" + d.platform_deployment_id + "'" + (d.platform_deployment_id.equals(domain)?" SELECTED>":">") + d.platform_deployment_id + "</option>");		
		
		buf.append("</select>&nbsp;&nbsp;");
		buf.append("<input type=submit value='Select' onClick=this.value='working...'></form><p>");
		
		return buf.toString();
	}
	
	String options(String domain) {
		StringBuffer buf = new StringBuffer("<h3>Database Utility for Permanent Erasure</h3>");
		
		buf.append(("Domain: ") + "<b>" + domain + "</b><p>");
		
		boolean domainExists = ofy().load().type(Deployment.class).id(domain).now() != null;
		if (!domainExists) {
			buf.append("This domain is not currently registered in ChemVantage.");
			return buf.toString();
		}
		
		buf.append("<form method=post action=/EraseEntity>");
		buf.append("<input type=hidden name=Domain value='" + domain + "'>");
		buf.append("<label><input type=radio name=EraseDomain value=true>Delete everything including the domain entity</label><br>");
		
		List<Assignment> assignments = ofy().load().type(Assignment.class).filter("domain",domain).list();
		if (assignments.size()==0) buf.append("This domain does not have any associated assignments.<p>");
		else {
			buf.append("<label><input type=radio name=EraseDomain value=false checked>Delete only the assignments selected below and their associated transactions and scores</label><p>");
			Map<Long,String> titles = new HashMap<Long,String>();
			List<Topic> topics = ofy().load().type(Topic.class).list();
			for (Topic t : topics) titles.put(t.id, t.title);
			
			// Create a checkbox for selecting all assignments
			if (assignments.size()>1) buf.append("<label><input type=checkbox name=SelectAll value=true onClick='for(var i=0;i<this.form.AssignmentId.length;i++)this.form.AssignmentId[i].checked=this.form.SelectAll.checked;'>Select/Unselect All Assignments</label><p>");
			
			// Make a list of assignments with checkboxes, assignment titles and created dates
			for (Assignment a : assignments) {
				buf.append("<label><input type=checkbox name=AssignmentId value=" + a.id + " onClick=this.form.SelectAll.indeterminate=true>" + a.assignmentType + " - ");
				if ("PracticeExam".equals(a.assignmentType)) {
					String topicTitles = "";
					for (Long id : a.topicIds) topicTitles += titles.get(id) + ", ";
					buf.append(topicTitles.substring(0,topicTitles.length()-2));
				} else buf.append(titles.get(a.topicId));
					
				buf.append(a.created==null?"":" created " + a.created);
				buf.append("</label><br>");
				// Count the number of transactions associated with each assignment (to be deleted, if selected)
				int nTransactions = 0;
				if (a.assignmentType != null) {
					switch (a.assignmentType) {
						case "Quiz": nTransactions = ofy().load().type(QuizTransaction.class).filter("assignmentId",a.id).count(); break;
						case "Homework": nTransactions = ofy().load().type(HWTransaction.class).filter("assignmentId",a.id).count(); break;
						case "PracticeExam": nTransactions = ofy().load().type(PracticeExamTransaction.class).filter("assignmentId",a.id).count(); break;
						case "VideoQuiz": nTransactions = ofy().load().type(VideoTransaction.class).filter("assignmentId",a.id).count(); break;
						case "PlacementExam": nTransactions = ofy().load().type(PlacementExamTransaction.class).filter("assignmentId",a.id).count(); break;
						case "Poll": nTransactions = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).count(); break;
					}
				}
				buf.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + nTransactions + " transactions are associated with this assignment<br>");
			}
		}
		buf.append("<br><input type=submit value='Permanently delete the selected entities (cannot be undone)'></form>");
		return buf.toString();
	}
}
