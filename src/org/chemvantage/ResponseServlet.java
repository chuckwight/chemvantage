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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.cmd.Query;

public class ResponseServlet extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This admin servlet creates and stores a single instance of a Response object (normally called from a Task queue).";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		PrintWriter out = response.getWriter();
		try {
			if ("Go".equals(request.getParameter("UserRequest"))) {
				response.setContentType("text/csv");
				long topicId = 0L;
				try {
					topicId = Long.parseLong(request.getParameter("TopicId"));
				} catch (Exception e2) {
					topicId = 0L;
				} // leaves topicId=0L if all topics are desired
				
				List<Long> questionIds = new ArrayList<Long>();
				List<String> userIds1 = new ArrayList<String>();
				List<String> userIds2 = new ArrayList<String>();
				List<String> userIds3 = new ArrayList<String>();
				List<String> userIds4 = new ArrayList<String>();
				List<String> userIds5 = new ArrayList<String>();
				List<HashMap<Long,Integer>> results1 = new ArrayList<HashMap<Long,Integer>>();
				List<HashMap<Long,Integer>> results2 = new ArrayList<HashMap<Long,Integer>>();
				List<HashMap<Long,Integer>> results3 = new ArrayList<HashMap<Long,Integer>>();
				List<HashMap<Long,Integer>> results4 = new ArrayList<HashMap<Long,Integer>>();
				List<HashMap<Long,Integer>> results5 = new ArrayList<HashMap<Long,Integer>>();
				
				Query<Response> responses;
				if (topicId==0L) responses = ofy().load().type(Response.class).order("submitted");
				else responses = ofy().load().type(Response.class).filter("topicId", topicId).order("submitted");
				
				// process and organize the collection of responses
				int totalResponses = responses.count();
				//out.println("UserId,AssignmentType,TopicId,QuestionId,Score,Submitted");
				int nResponsesUsed = 0;
				for (Response r : responses) {
					if (nResponsesUsed > 75000) break;
					if (r.possibleScore>1) continue;
					if (r.userId.contains("wight")) continue;
					//out.println(r.userId + "," + r.assignmentType + "," + r.topicId + "," + r.questionId + "," + r.score + "," + r.submitted);
					if (topicId > 0L && r.topicId != topicId) continue;
					if (!questionIds.contains(r.questionId)) questionIds.add(r.questionId);
					nResponsesUsed++;
					if (!userIds1.contains(r.userId)) {
						userIds1.add(r.userId);
						HashMap<Long,Integer> userResult = new HashMap<Long,Integer>();
						userResult.put(r.questionId, r.score);
						results1.add(userIds1.indexOf(r.userId),userResult);
					} else if (results1.get(userIds1.indexOf(r.userId)).get(r.questionId)==null) {
						results1.get(userIds1.indexOf(r.userId)).put(r.questionId, r.score);
					} else if (!userIds2.contains(r.userId)) {
						userIds2.add(r.userId);
						HashMap<Long,Integer> userResult = new HashMap<Long,Integer>();
						userResult.put(r.questionId, r.score);
						results2.add(userIds2.indexOf(r.userId),userResult);
					} else if (results2.get(userIds2.indexOf(r.userId)).get(r.questionId)==null) {
						results2.get(userIds2.indexOf(r.userId)).put(r.questionId, r.score);
					} else if (!userIds3.contains(r.userId)) {
						userIds3.add(r.userId);
						HashMap<Long,Integer> userResult = new HashMap<Long,Integer>();
						userResult.put(r.questionId, r.score);
						results3.add(userIds3.indexOf(r.userId),userResult);
					} else if (results3.get(userIds3.indexOf(r.userId)).get(r.questionId)==null) {
						results3.get(userIds3.indexOf(r.userId)).put(r.questionId, r.score);
					} else if (!userIds4.contains(r.userId)) {
						userIds4.add(r.userId);
						HashMap<Long,Integer> userResult = new HashMap<Long,Integer>();
						userResult.put(r.questionId, r.score);
						results4.add(userIds4.indexOf(r.userId),userResult);
					} else if (results4.get(userIds4.indexOf(r.userId)).get(r.questionId)==null) {
						results4.get(userIds4.indexOf(r.userId)).put(r.questionId, r.score);
					} else if (!userIds5.contains(r.userId)) {
						userIds5.add(r.userId);
						HashMap<Long,Integer> userResult = new HashMap<Long,Integer>();
						userResult.put(r.questionId, r.score);
						results5.add(userIds5.indexOf(r.userId),userResult);
					} else if (results5.get(userIds5.indexOf(r.userId)).get(r.questionId)==null) {
						results5.get(userIds5.indexOf(r.userId)).put(r.questionId, r.score);
					}						
				}
				out.println("Total responses," + totalResponses + ",Responses Used," + nResponsesUsed);
				out.println("");
				
//				construct a header row
				StringBuffer header = new StringBuffer();
				header.append("UserId");
				for (Long qid : questionIds) header.append("," + qid);
				// now print out the csv file:
				
				out.println(header.toString());
				for (HashMap<Long,Integer> userResult : results1) {
					StringBuffer line = new StringBuffer();
					line.append(userIds1.get(results1.indexOf(userResult)));
					for (Long quid : questionIds) line.append("," + (userResult.get(quid)==null?"9":userResult.get(quid)));					
					out.println(line);
				}
				
				out.println(""); // insert a blank line between sections
				out.println(header.toString());
				for (HashMap<Long,Integer> userResult : results2) {
					StringBuffer line = new StringBuffer();
					line.append(userIds2.get(results2.indexOf(userResult)));
					for (Long quid : questionIds) line.append("," + (userResult.get(quid)==null?"9":userResult.get(quid)));					
					out.println(line);
				}
				
				out.println(""); // insert a blank line between sections
				out.println(header.toString());
				for (HashMap<Long,Integer> userResult : results3) {
					StringBuffer line = new StringBuffer();
					line.append(userIds3.get(results3.indexOf(userResult)));
					for (Long quid : questionIds) line.append("," + (userResult.get(quid)==null?"9":userResult.get(quid)));					
					out.println(line);
				}
				
				out.println(""); // insert a blank line between sections
				out.println(header.toString());
				for (HashMap<Long,Integer> userResult : results4) {
					StringBuffer line = new StringBuffer();
					line.append(userIds4.get(results4.indexOf(userResult)));
					for (Long quid : questionIds) line.append("," + (userResult.get(quid)==null?"9":userResult.get(quid)));					
					out.println(line);
				}
				
				out.println(""); // insert a blank line between sections
				out.println(header.toString());
				for (HashMap<Long,Integer> userResult : results5) {
					StringBuffer line = new StringBuffer();
					line.append(userIds5.get(results5.indexOf(userResult)));
					for (Long quid : questionIds) line.append("," + (userResult.get(quid)==null?"9":userResult.get(quid)));					
					out.println(line);
				}
				
			} else {
				response.setContentType("text/html");
				out.println(Home.getHeader(user) + responseForm(request) + Home.footer);
			}
		} catch (Exception e) {
			response.setContentType("text/html");
			out.println("Error: " + e.getMessage());
		}
	}
	
	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			Response r = new Response(
					request.getParameter("AssignmentType"),
					Long.parseLong(request.getParameter("TopicId")),
					Long.parseLong(request.getParameter("QuestionId")),
					request.getParameter("StudentResponse"),
					request.getParameter("CorrectAnswer"),
					Integer.parseInt(request.getParameter("Score")),
					Integer.parseInt(request.getParameter("PossibleScore")),
					request.getParameter("UserId"),
					new Date());
			ofy().save().entity(r);		
		} catch (Exception e) {
		}
	}
	
	String topicSelectBox() {
		StringBuffer buf = new StringBuffer();
		buf.append("<SELECT NAME=TopicId><OPTION VALUE=all>Include all topics</OPTION>");
		List<Topic> topics = ofy().load().type(Topic.class).list();
		for (Topic t : topics) buf.append("<OPTION VALUE=" + t.id + ">" + t.title + "</OPTION>");
		buf.append("</SELECT>");
		return buf.toString();
	}
	
	String responseForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h3>Question Item Response Analysis</h3>");
		buf.append("Select one of the topic areas for analysis.  The output will be a CSV file.<p>"
				+ "<FORM ACTION=ResponseServlet METHOD=GET>"
				+ topicSelectBox() + "<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Go>"
				+ "</FORM>");
		return buf.toString();
	}
	
}
