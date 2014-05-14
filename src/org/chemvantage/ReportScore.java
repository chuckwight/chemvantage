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

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class ReportScore extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();

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
		// required parameters: AssignmentId,UserId
		
		long assignmentId = 0L;
		String userId = "";
		try {
			userId = URLDecoder.decode(request.getParameter("UserId"),"UTF-8");
			assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment a = ofy.get(Assignment.class,assignmentId);
			Key<Score> k = new Key<Score>(new Key<User>(User.class, userId),Score.class,a.id);
    		Score s = ofy.find(k);
    		if (s == null) return;
    		
    		// compute a scaled score in the range 0.0-1.0 for LIS services specification
    		double score = (double)s.score / (double)s.maxPossibleScore;
			if (score < 0.0 || score > 1.0) throw new Exception();
			
			if (!s.needsLisReporting()) return;
			Group g = ofy.get(Group.class,a.groupId);
			String oauth_consumer_key = g.domain;
			String body = xmlReplaceResult(s.lis_result_sourcedid,Double.toString(score));
			
			String replyBody = new LTIMessage("application/xml",body,g.lis_outcome_service_url,oauth_consumer_key).send();
			
			if (replyBody.toLowerCase().contains("success")) {
				s.lisReportComplete = true;
				ofy.put(s);
			}
			else throw new Exception();  // try again later
		} catch (Exception e) {
			try {
				String delay = request.getParameter("Delay");
				int n = 0;
				if (delay != null) n = Integer.parseInt(delay);
				if (assignmentId<=0 || n>10) return;  // will attempt to record up to 11 times over 17 hours
				long countdownMillis = (long) Math.pow(2,n)*60000;
				Queue queue = QueueFactory.getDefaultQueue();  // used for storing individual responses by Task queue
				queue.add(withUrl("/ReportScore").param("AssignmentId",Long.toString(assignmentId)).param("UserId",userId).param("Delay",Integer.toString(n+1)).param("Error",e.getMessage()).countdownMillis(countdownMillis));
			} catch (Exception e2) {}
		}
	}
	
	String xmlReplaceResult(String lis_result_sourcedid, String score) {		
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
		+ "<imsx_POXEnvelopeRequest xmlns = \"http://www.imsglobal.org/services/ltiv1p1/xsd/imsoms_v1p0\">"
		+ "<imsx_POXHeader>"
		+ "<imsx_POXRequestHeaderInfo>"
		+ "<imsx_version>V1.0</imsx_version>"
		+ "<imsx_messageIdentifier>1</imsx_messageIdentifier>"
		+ "</imsx_POXRequestHeaderInfo>"
		+ "</imsx_POXHeader>"
		+ "<imsx_POXBody>"
		+ "<replaceResultRequest>"
		+ "<resultRecord>"
		+ "<sourcedGUID>"
		+ "<sourcedId>" + lis_result_sourcedid + "</sourcedId>"
		+ "</sourcedGUID>"
		+ "<result>"
		+ "<resultScore>"
		+ "<language>en</language>"
		+ "<textString>" + score + "</textString>"
		+ "</resultScore>"
		+ "</result>"
		+ "</resultRecord>"
		+ "</replaceResultRequest>"
		+ "</imsx_POXBody>"
		+ "</imsx_POXEnvelopeRequest>";		
	}
}