/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2019 ChemVantage LLC
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/* 
 * Access to this servlet is restricted to ChemVantage admin users and the project service account
 * by specifying login: admin in a url handler of the project app.yaml file
 */
@WebServlet("/ReportScore")
public class ReportScore extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet reports a single Score object back to a user's LMS as a Task "
				+ "using the 1EdTech LTI Advantage Assignment and Grade Services 2.0 Specification.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		if (request.getParameter("AssignmentId") == null || request.getParameter("UserId") == null) {
			PrintWriter out = response.getWriter();
			response.setContentType("text/html");
			out.println(Subject.header("ChemVantage Unreported Scores") + "<h3>Unreported Scores</h3>");
			out.println("For each of the scores below, click the link to report it manually.<p>");
			List<Score> scores = ofy().load().type(Score.class).filter("lisReportComplete",false).list();
			for (Score s : scores) {
				String userId = s.owner.getName();
				String url = "/ReportScore?UserId=" + userId + "&AssignmentId=" + s.assignmentId;
				if (s.lisReportComplete) out.println("Report is complete: " + url + "<br>");
				else if (s.needsLisReporting()) out.println("Score needs reporting: <a href=" + url + ">" + url + "</a><br>");
				else out.println("No reporting URL provided: " + url + "<br>");
			}
			if (scores.size()==0) out.println("None. All scores are up to date.");
			out.println(Subject.footer);
		} else 	doPost(request,response);	
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		StringBuffer debug = new StringBuffer("Debug:");
		try {  // post single user score
			response.setContentType("text/html");
			debug.append("1");
			PrintWriter out = response.getWriter();
			String userId = request.getParameter("UserId");
			Long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			debug.append("2");
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			debug.append("3");
			Score s = Score.getInstance(userId, a);
			ofy().save().entity(s).now();
			debug.append("4");
			
			if (a.lti_ags_lineitem_url != null && !a.lti_ags_lineitem_url.contains("localhost")) {  // use LTIAdvantage reporting specs
				debug.append("5");
				String reply = LTIMessage.postUserScore(s,userId);
				debug.append("6<br/>" + Subject.projectId + "<br/>" + a.lti_ags_lineitem_url + "<br/>");
				
				if (reply.contains("Success") || reply.contains("422")) out.println(reply);
				else {
					User user = ofy().load().type(User.class).filter("hashedId",Subject.hashId(userId)).first().now();
					if (user.isInstructor()) return; // no harm; LMS refused to post instructor score
					debug.append("User " + userId + " earned a score of " + s.getPctScore() + "% on assignment "
							+ a.id + "; however, the score could not be posted to the LMS grade book. "
							+ "The response from the " + a.domain + " LMS was: " + reply);
				}
				debug.append("7");
			}
		} catch (Exception e) {
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Failed ReportScore",(e.getMessage()==null?e.toString():e.getMessage()) + "<br/>" + debug.toString());
			response.sendError(401,"Failed ReportScore");
		}
	}	
}