/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2021 ChemVantage LLC
*   
*	This program is free software: you can redistribute it and/or modify
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.objectify.Key;

public class QuestionCache {
	private Map<Long,Topic> topics = new HashMap<Long,Topic>();
	private Map<Long,Assignment> assignments = new HashMap<Long,Assignment>();
	private Map<Key<Question>,Question> questions = new HashMap<Key<Question>,Question>();
	private Map<Long,List<Key<Question>>> quizQuestionKeys = new HashMap<Long,List<Key<Question>>>();
	private Map<Long,List<Key<Question>>> hwQuestionKeys = new HashMap<Long,List<Key<Question>>>();
	private Map<Long,List<Key<Question>>> examQuestionKeys = new HashMap<Long,List<Key<Question>>>();
	private Map<Key<Question>,Integer> successPct = new HashMap<Key<Question>,Integer>();
	
	Topic getTopic(long topicId) {
		Topic t = topics.get(topicId);
		if (t==null) {
			try {
				t = ofy().load().type(Topic.class).id(topicId).safe();
				topics.put(topicId,t);
			} catch (Exception e) {}
		}
		return t;
	}
	
	Assignment getAssignment(long assignmentId) {
		Assignment a = assignments.get(assignmentId);
		if (a==null) {
			try {
				a = ofy().load().type(Assignment.class).id(assignmentId).safe();
				assignments.put(assignmentId,a);
			} catch (Exception e) {}
		}
		return a;
	}
	
	void putAssignment(Assignment a) {
		assignments.put(a.id, a);
	}
	
	Map<Key<Question>,Question> getQuestions(List<Key<Question>> list) {
		Map<Key<Question>,Question> qs = new HashMap<Key<Question>,Question>();
		List<Key<Question>> needed = new ArrayList<Key<Question>>();
		for (Key<Question> k : list) {
			Question q = getQuestion(k);
			if (q == null) needed.add(k);
			else qs.put(k,q);
		}
		if (needed.size() > 0) {
			Map<Key<Question>,Question> gotem = ofy().load().keys(needed);
			questions.putAll(gotem);
			qs.putAll(gotem);
		}
		return qs;
	}
	
	Question getQuestion(Key<Question> k) {
		Question q = questions.get(k);
		if (q==null) {
			try {
				q = ofy().load().key(k).safe();
				questions.put(k, q);
				return q.clone();
			}
			catch (Exception e) {
				return null;
			}
		}
		return q.clone();
	}
	
	void putQuestion(Question q) {
		try {
			questions.put(Key.create(q), q);
		} catch (Exception e) {}
	}
	
	void removeQuestion(Key<Question> k) {
		questions.remove(k);
	}
	
	List<Key<Question>> getQuizQuestionKeys(long topicId) {
		List<Key<Question>> keys = quizQuestionKeys.get(topicId);
		if (keys == null) {
			keys = ofy().load().type(Question.class).filter("assignmentType", "Quiz").filter("topicId", topicId).filter("isActive", true).keys().list();
			quizQuestionKeys.put(topicId, keys);
		}
		return keys;
	}

	void loadQuizQuestions(long topicId) {
		List<Key<Question>> keys = getQuizQuestionKeys(topicId);
		List<Key<Question>> needed = new ArrayList<Key<Question>>();
		for (Key<Question> k : keys) {
			if (!questions.containsKey(k)) needed.add(k);
		}
		if (needed.size() > 0) questions.putAll(ofy().load().keys(needed));
	}
	
	List<Question> getSortedHWQuestions(long topicId) {
		List<Key<Question>> keys = hwQuestionKeys.get(topicId);
		if (keys == null) {
			keys = ofy().load().type(Question.class).filter("assignmentType", "Homework").filter("topicId", topicId).filter("isActive", true).keys().list();
			if (keys.size() > 0) {
				Collections.sort(keys, new SortBySuccessPct());
				hwQuestionKeys.put(topicId, keys);
			}
		}
		List<Question> hwQuestions = new ArrayList<Question>();
		for (Key<Question> k : keys) hwQuestions.add(getQuestion(k));
		return hwQuestions;
	}

	List<Key<Question>> getExamQuestionKeys(long topicId) {
		List<Key<Question>> keys = examQuestionKeys.get(topicId);
		if (keys.isEmpty()) {
			keys = ofy().load().type(Question.class).filter("assignmentType", "Exam").filter("topicId", topicId).filter("isActive", true).keys().list();
			examQuestionKeys.put(topicId, keys);
		}
		return keys;
	}
	
	int getSuccessPct(Key<Question> k) {
		Integer s = successPct.get(k);
		return s==null?0:s;
	}
	
	class SortBySuccessPct implements Comparator<Key<Question>> {
		
		SortBySuccessPct() {}
		
		public int compare(Key<Question> o1, Key<Question> o2) {
			Integer success1 = successPct.get(o1);
			if (success1==null) {
				int totalResponses = ofy().load().type(Response.class).filter("questionId",o1.getId()).count();
				if (totalResponses==0) success1 = 100;
				else {
					int successResponses = ofy().load().type(Response.class).filter("questionId",o1.getId()).filter("score >",0).count();
					success1 = successResponses*100/totalResponses;
				}
				successPct.put(o1,success1);
			}
			Integer success2 = successPct.get(o2);
			if (success2==null) {
				int totalResponses = ofy().load().type(Response.class).filter("questionId",o2.getId()).count();
				if (totalResponses==0) success2 = 100;
				else {
					int successResponses = ofy().load().type(Response.class).filter("questionId",o2.getId()).filter("score >",0).count();
					success2 = successResponses*100/totalResponses;
				}
				successPct.put(o2,success2);
			}
			int rank = success2-success1; // this reverses the normal Comparator to give higher rank to lower successPct
			if (rank==0) rank = o1.compareTo(o2); // tie breaker required
			return rank;  
		}
	}

}
