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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;

@WebServlet("/ResponseServlet")
public class ResponseServlet extends HttpServlet {
	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This admin servlet creates and stores a single instance of a Response object (normally called from a Task queue).";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
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
				if (topicId==0L) responses = ofy().load().type(Response.class).limit(75000).order("submitted");
				else responses = ofy().load().type(Response.class).filter("topicId", topicId).limit(75000).order("submitted");
				
				// process and organize the collection of responses
				int totalResponses = responses.count();
				//out.println("UserId,AssignmentType,TopicId,QuestionId,Score,Submitted");
				int nResponsesUsed = 0;
				for (Response r : responses) {
					if (nResponsesUsed > 75000) break;
					if (r.possibleScore>1) continue;
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
				out.println(Subject.header("ChemVantage User Response Analysis") + responseForm(request) + Subject.footer);
			}
		} catch (Exception e) {
			response.setContentType("text/html");
			out.println("Error: " + e.getMessage());
		}
	}
	
	public void doPost(HttpServletRequest request,HttpServletResponse response)
			throws ServletException, IOException {
		// this method accepts an assignmentId and updates all of the associated transactions with the 
		// studentAnswers and correctAnswers. It then eliminates the Response entities that contained the information
		// At the same time, the Question entities are updated with nTotalAttempts and nCorrectAnswers statistics
		StringBuffer debug = new StringBuffer("Debug:");
		try {
			long assignmentId = Long.parseLong(request.getParameter("AssignmentId"));
			Assignment a = ofy().load().type(Assignment.class).id(assignmentId).safe();
			debug.append("Assignment " + a==null?"is null.":a.id + " has ");
			List<Response> responses = ofy().load().type(Response.class).filter("assignmentId",a.id).list();
			if (responses.size()==0) return;  // this assignment is already updated
			debug.append(responses.size()+ " " + a.assignmentType + " Response entities.");
			// These Maps are for collecting stats for questions answered on this assignment
			Map<Long,Integer> nTotalAttempts = new HashMap<Long,Integer>();
			Map<Long,Integer> nCorrectAnswers = new HashMap<Long,Integer>();

			switch (a.assignmentType) {
			case "Homework":
				// Using the Response items for this assignment, collect all of the relevant HWTransaction keys
				List<Key<HWTransaction>> hwTransactionKeys = new ArrayList<Key<HWTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<HWTransaction> k = key(HWTransaction.class,r.transactionId);
					if (!hwTransactionKeys.contains(k)) hwTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				// Make a Map of HWTransactions for these Response items
				Map<Key<HWTransaction>,HWTransaction> hwTransactions = new HashMap<Key<HWTransaction>,HWTransaction>();
				if (hwTransactionKeys.size()>0) hwTransactions = ofy().load().keys(hwTransactionKeys);
				debug.append(hwTransactions.size() + " Transaction entities ");
				int i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {  // put Response data into the transaction
						HWTransaction hwt = hwTransactions.get(key(HWTransaction.class,r.transactionId));  // get the HWTransaction for this Response
						hwt.studentAnswer = r.studentResponse;					// update the HWTransaction data
						hwt.correctAnswer = r.correctAnswer;
					} catch (Exception e) {}
				}
				debug.append("Stats collected for " + i + " questions. Saving " + hwTransactions.size() + " transactions.");
				if (hwTransactions.size()>0) ofy().save().entities(new ArrayList<HWTransaction>(hwTransactions.values()));
				debug.append("saved.");
				break;
			case "Quiz":
				List<Key<QuizTransaction>> qTransactionKeys = new ArrayList<Key<QuizTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<QuizTransaction> k = key(QuizTransaction.class,r.transactionId);
					if (!qTransactionKeys.contains(k)) qTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				Map<Key<QuizTransaction>,QuizTransaction> qTransactions = new HashMap<Key<QuizTransaction>,QuizTransaction>();
				if (qTransactionKeys.size()>0) qTransactions = ofy().load().keys(qTransactionKeys);
				debug.append(qTransactions.size() + " Transaction entities ");
				i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {
						QuizTransaction qt = qTransactions.get(key(QuizTransaction.class,r.transactionId));
						Key<Question> k = key(Question.class,r.questionId);
						qt.questionKeys.add(k);
						qt.studentAnswers.put(k,r.studentResponse);
						qt.correctAnswers.put(k,r.correctAnswer);
					} catch (Exception e) {}
				}
				debug.append("Stats collected for " + i + " questions. Saving " + qTransactions.size() + " transactions.");
				if (qTransactions.size()>0) ofy().save().entities(new ArrayList<QuizTransaction>(qTransactions.values()));
				break;
			case "Poll":
				List<PollTransaction> pollTransactions = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).list();
				Map<Key<Question>,Question> questions = ofy().load().keys(a.questionKeys);
				for (PollTransaction pt : pollTransactions) {
					pt.questionKeys = a.questionKeys;
					for (Key<Question> k : pt.questionKeys) {
						String studentAnswer = pt.responses.get(k);
						if (studentAnswer != null) {
							pt.studentAnswers.put(k, studentAnswer);
							Question q = questions.get(k);
							if (q.hasACorrectAnswer()) pt.correctAnswers.put(k, q.getCorrectAnswer());
						}
					}
					pt.responses = null;
				}
				if (pollTransactions.size()>0) ofy().save().entities(pollTransactions);
				break;
			case "PracticeExam":
				List<Key<PracticeExamTransaction>> prExTransactionKeys = new ArrayList<Key<PracticeExamTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<PracticeExamTransaction> k = key(PracticeExamTransaction.class,r.transactionId);
					if (!prExTransactionKeys.contains(k)) prExTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				Map<Key<PracticeExamTransaction>,PracticeExamTransaction> prExTransactions = new HashMap<Key<PracticeExamTransaction>,PracticeExamTransaction>();
				if (prExTransactionKeys.size()>0) prExTransactions = ofy().load().keys(prExTransactionKeys);
				debug.append(prExTransactions.size() + " Transaction entities ");
				i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {
						PracticeExamTransaction pt = prExTransactions.get(key(PracticeExamTransaction.class,r.transactionId));
						Key<Question> k = key(Question.class,r.questionId);
						pt.studentAnswers.put(k,r.studentResponse);
						pt.correctAnswers.put(k,r.correctAnswer);
					} catch (Exception e) {}
				}
				debug.append("Stats collected for " + i + " questions. Saving " + prExTransactions.size() + " transactions.");
				if (prExTransactions.size()>0) ofy().save().entities(new ArrayList<PracticeExamTransaction>(prExTransactions.values()));
				break;
			case "PlacementExam":
				List<Key<PlacementExamTransaction>> plExTransactionKeys = new ArrayList<Key<PlacementExamTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<PlacementExamTransaction> k = key(PlacementExamTransaction.class,r.transactionId);
					if (!plExTransactionKeys.contains(k)) plExTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				Map<Key<PlacementExamTransaction>,PlacementExamTransaction> plExTransactions = new HashMap<Key<PlacementExamTransaction>,PlacementExamTransaction>();
				if (plExTransactionKeys.size()>0) plExTransactions = ofy().load().keys(plExTransactionKeys);
				i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {
						PlacementExamTransaction pt = plExTransactions.get(key(PlacementExamTransaction.class,r.transactionId));
						Key<Question> k = key(Question.class,r.questionId);
						pt.studentAnswers.put(k,r.studentResponse);
						pt.correctAnswers.put(k,r.correctAnswer);
					} catch (Exception e) {}
				}
				if (plExTransactions.size()>0) debug.append("Stats collected for " + i + " questions. Saving " + plExTransactions.size() + " transactions.");
				ofy().save().entities(new ArrayList<PlacementExamTransaction>(plExTransactions.values()));
				break;
			case "VideoQuiz":
				List<Key<VideoTransaction>> vTransactionKeys = new ArrayList<Key<VideoTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<VideoTransaction> k = key(VideoTransaction.class,r.transactionId);
					if (!vTransactionKeys.contains(k)) vTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				Map<Key<VideoTransaction>,VideoTransaction> vTransactions = new HashMap<Key<VideoTransaction>,VideoTransaction>();
				if (vTransactionKeys.size()>0) vTransactions = ofy().load().keys(vTransactionKeys);
				i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {
						VideoTransaction vt = vTransactions.get(key(VideoTransaction.class,r.transactionId));
						Key<Question> k = key(Question.class,r.questionId);
						vt.questionKeys.add(k);
						vt.studentAnswers.put(k,r.studentResponse);
						vt.correctAnswers.put(k,r.correctAnswer);
					} catch (Exception e) {}
				}
				debug.append("Stats collected for " + i + " questions. Saving " + vTransactions.size() + " transactions.");
				if (vTransactions.size()>0) ofy().save().entities(new ArrayList<VideoTransaction>(vTransactions.values()));
				break;
			case "SmartText":
				List<Key<STTransaction>> sTransactionKeys = new ArrayList<Key<STTransaction>>();
				for (Response r : responses) {
					if (r.transactionId==0) continue;
					Key<STTransaction> k = key(STTransaction.class,r.transactionId);
					if (!sTransactionKeys.contains(k)) sTransactionKeys.add(k);
				}
				debug.append("Transaction keys loaded.");
				Map<Key<STTransaction>,STTransaction> sTransactions = new HashMap<Key<STTransaction>,STTransaction>();
				if (sTransactionKeys.size()>0) sTransactions = ofy().load().keys(sTransactionKeys);
				debug.append(sTransactions.size() + " Transaction entities ");
				i = 0;
				for (Response r : responses) {
					try {  // update the question stats
						Integer nt = nTotalAttempts.get(r.questionId);		// record the stats for this question
						nTotalAttempts.put(r.questionId,nt==null?1:nt+1);
						Integer nc = nCorrectAnswers.get(r.questionId);
						nCorrectAnswers.put(r.questionId,nc==null?1:nc+r.score>0?1:0);
					} catch (Exception e) {}
					i++;
					try {
						STTransaction st = sTransactions.get(key(STTransaction.class,r.transactionId));
						Key<Question> k = key(Question.class,r.questionId);
						st.answeredKeys.add(k);
						st.studentAnswers.put(k,r.studentResponse);
						st.correctAnswers.put(k,r.correctAnswer);
					} catch (Exception e) {}
				}
				debug.append("Stats collected for " + i + " questions. Saving " + sTransactions.size() + " transactions.");
				if (sTransactions.size()>0) ofy().save().entities(new ArrayList<STTransaction>(sTransactions.values()));
				break;

			default: return;
			}
			if (responses.size()>0) ofy().delete().entities(responses);
			debug.append("Responses deleted.");
			Map<Long,Question> questions = new HashMap<Long,Question>();
			if (nTotalAttempts.size()>0) questions = ofy().load().type(Question.class).ids(new ArrayList<Long>(nTotalAttempts.keySet()));
			for (Entry<Long,Question> e : questions.entrySet()) {
				e.getValue().addBulkAttempts(nTotalAttempts.get(e.getKey()),nCorrectAnswers.get(e.getKey()));
			}
			if (questions.size()>0) ofy().save().entities(new ArrayList<Question>(questions.values()));
			debug.append("Questions updated.Done.");
			//throw new Exception("Finished OK.");
			Utilities.sendEmail("ChemVantage", "admin@chemvantage.org", "ResponseServlet Completed", debug.toString());
		} catch (Exception e) {
			Utilities.sendEmail("ChemVantage", "admin@chemvantage.org", "ResponseServlet Failed", e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString());
		}
	}
/*		
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
					Long.parseLong(request.getParameter("TransactionId")),
					new Date());
			ofy().save().entity(r);		
		} catch (Exception e) {
		}
	}
*/	
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
