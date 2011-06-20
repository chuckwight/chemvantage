/*  PracticeZone - A Java web application for online learning
    Copyright (C) 2009 PracticeZone.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.chemvantage;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Parent;


@Cached
public class HomeworkScore {

	@Id Long assignmentId;
	@Parent Key<User> owner;
	int numberOfAttempts;
	int score;

	HomeworkScore() {}
	
	public static HomeworkScore getInstance(Assignment assignment,String userId) {
		Objectify ofy = ObjectifyService.begin();
		try {
			Key<User> owner = new Key<User>(User.class,userId);
			return ofy.get(new Key<HomeworkScore>(owner,HomeworkScore.class,assignment.id));
		} catch (Exception e) {
			HomeworkScore s = new HomeworkScore();
			s.owner = new Key<User>(User.class,userId);
			s.assignmentId = assignment.id;
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",userId).filter("topicId",assignment.topicId).filter("graded <",assignment.deadline);
			s.numberOfAttempts = hwTransactions.count();
			s.score = 0;
			List<Long> assignedQuestionIds = new ArrayList<Long>();
			for (Key<Question> k : assignment.questionKeys) assignedQuestionIds.add(k.getId());
			for (HWTransaction h : hwTransactions) if (h.score > 0 && assignedQuestionIds.remove(h.questionId)) s.score ++; 
			ofy.put(s);
			return s;
		}
	}
	
	public static void remove(long assignmentId,String userId) {
		try {
			Key<User> owner = new Key<User>(User.class,userId);
			ObjectifyService.begin().delete(new Key<HomeworkScore>(owner,HomeworkScore.class,assignmentId));
		} catch (Exception e) {
		}
	}
	
	public static void removeAll(String userId) {
		try {
			Key<User> owner = new Key<User>(User.class,userId);
			Objectify ofy = ObjectifyService.begin();
			Query<HomeworkScore> allScores = ofy.query(HomeworkScore.class).ancestor(owner);
			ofy.delete(allScores);
		} catch (Exception e) {
		}
	}
	
	public String getScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score);
	}
	
	public String getEnhancedScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score) + "&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>";
	}
	
	public void update(int newScore) {
		numberOfAttempts++;
		if (newScore > score) score = newScore;
		ObjectifyService.begin().put(this);
	}
}