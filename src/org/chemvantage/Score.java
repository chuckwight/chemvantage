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
public class Score {    // this object represents a best score achieved by a user on a quiz or homework
	@Id Long assignmentId;      // from the datastore.
	@Parent Key<User> owner;
	long groupId;
	int score;
	int numberOfAttempts;
	
	Score() {}
	
	public static Score getInstance(String userId,Assignment a) {
		Objectify ofy = ObjectifyService.begin();
		Score s = new Score();
		s.assignmentId = a.id;
		s.owner = new Key<User>(User.class,userId);
		s.groupId = a.groupId;
		s.score = 0;
		s.numberOfAttempts = 0;
		if (a.assignmentType.equals("Quiz")) {
			Query<QuizTransaction> quizTransactions = ofy.query(QuizTransaction.class).filter("userId",userId).filter("topicId",a.topicId).filter("downloaded <",a.deadline);
			s.numberOfAttempts = quizTransactions.count();
			for (QuizTransaction qt : quizTransactions) if (qt.score > s.score) s.score = qt.score;
		} else if (a.assignmentType.equals("Homework")) {
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",userId).filter("topicId",a.topicId).filter("graded <",a.deadline);
			s.numberOfAttempts = hwTransactions.count();
			List<Long> assignedQuestionIds = new ArrayList<Long>();
			for (Key<Question> k : a.questionKeys) assignedQuestionIds.add(k.getId());
			for (HWTransaction h : hwTransactions) if (h.score > 0 && assignedQuestionIds.remove(h.questionId)) s.score ++; 
		}
		return s;
	}
		
	public String getScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score);
	}
	
	public String getEnhancedScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score) + "&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>";
	}
	
	public Score update(int newScore) {
		numberOfAttempts++;
		if (newScore > score) score = newScore;
		return this;
	}
}