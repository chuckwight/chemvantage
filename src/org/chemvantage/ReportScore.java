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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

public class ReportScore extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "ChemVantage servlet reports a single Score object back to a user's LMS as a Task using the IMS LTI 1.1 Learning Information Services API.";
	}
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		doPost(request,response);
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {

		try {  // post single user score
			String userId = URLDecoder.decode(request.getParameter("UserId"),"UTF-8");
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			String delay = request.getParameter("Delay");
			postUserScore(userId,assignmentId,delay);
			return;
		} catch (Exception e) {}

		try {  // post group scores for one assignment
			long groupId = Long.parseLong(request.getParameter("GroupId"));
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Group g = ofy().load().type(Group.class).id(groupId).safe();
			for (String uId : g.memberIds) postUserScore(uId,assignmentId,null);			
			return;
		} catch (Exception e) {}
	}
	
	void postUserScore(String userId,long assignmentId,String delay) {
		try {
			Key<Score> k = Key.create(Key.create(User.class, userId),Score.class,assignmentId);
    		Score s = ofy().load().key(k).now();
    		if (s == null || !s.needsLisReporting()) return;
    		
    		// compute a scaled score in the range 0.0-1.0 for LIS services specification
    		double score = (double)s.score / (double)s.maxPossibleScore;
			if (score < 0.0 || score > 1.0) throw new Exception();
			
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			Group g = ofy().load().type(Group.class).id(a.groupId).safe();
			String oauth_consumer_key = g.domain;
			
			String messageFormat = g.getLisOutcomeFormat();			
			String body = (messageFormat.contains("jason")?jsonReplaceResult(Double.toString(score)):LTIMessage.xmlReplaceResult(s.lis_result_sourcedid,Double.toString(score)));
			String replyBody = new LTIMessage(messageFormat,body,g.lis_outcome_service_url,oauth_consumer_key).send();
			
			if (replyBody.toLowerCase().contains("success")) {
				s.lisReportComplete = true;
				ofy().save().entity(s);
			}
			else throw new Exception();  // try again later
		} catch (Exception e) {
			try {
				int n = 0;
				if (delay != null) n = Integer.parseInt(delay);
				if (assignmentId<=0 || n>10) return;  // will attempt to record up to 11 times over 17 hours
				long countdownMillis = (long) Math.pow(2,n)*60000;
				Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
				queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(assignmentId)).param("UserId",userId).param("Delay",Integer.toString(n+1)).param("Error",e.getMessage()).countdownMillis(countdownMillis));
			} catch (Exception e2) {}
		}
	}
	
	String jsonReplaceResult(String score) {
		return "{"
		+ "'@context' : 'http://purl.imsglobal.org/ctx/lis/v2/Result',"
		+ "'@type' : 'Result',"
		+ "'resultScore' : " + score + ","
		+ "}";
	}
}