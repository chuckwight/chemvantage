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

import java.util.ArrayList;
import java.util.Date;
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
	int overallScore;
	int numberOfAttempts;
	
	Score() {}
	
	public static Score getInstance(String userId,Assignment a) {
		Objectify ofy = ObjectifyService.begin();
		Score s = new Score();
		s.assignmentId = a.id;
		s.owner = new Key<User>(User.class,userId);
		s.groupId = a.groupId;
		s.score = 0;
		s.overallScore = 0;
		s.numberOfAttempts = 0;
		if (a.assignmentType.equals("Quiz")) {
			Query<QuizTransaction> quizTransactions = ofy.query(QuizTransaction.class).filter("userId",userId).filter("topicId",a.topicId);
			for (QuizTransaction qt : quizTransactions) {
				if (qt.downloaded.before(a.deadline)) {  // pre-deadline group score
					s.numberOfAttempts++;  // number of pre-deadline quiz attempts
					s.score = (qt.score>s.score?qt.score:s.score);  // keep the best (max) score
				}
				if (qt.score > s.overallScore) s.overallScore = qt.score;  // overall student score on this assignment
			}
		} else if (a.assignmentType.equals("Homework")) {
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",userId).filter("topicId",a.topicId);
			List<Key<Question>> allQuestionKeys = ofy.query(Question.class).filter("assignmentType","Homework").filter("topicId", a.topicId).listKeys();
			List<Key<Question>> assignmentQuestionKeys = new ArrayList<Key<Question>>();
			for (Key<Question> k : a.questionKeys) assignmentQuestionKeys.add(k);
			for (HWTransaction h : hwTransactions) {
				if (h.graded.before(a.deadline)) {
					s.numberOfAttempts++;
					// Warning: the following line removes Keys from a.questionKeys to avoid counting duplicate scores
					// on homework assignments.  Do not "put" the assignment to the database in this method!
					if (h.score > 0 && assignmentQuestionKeys.remove(new Key<Question>(Question.class,h.questionId))) s.score ++; 
				}
				if (h.score>0 && allQuestionKeys.remove(new Key<Question>(Question.class,h.questionId))) s.overallScore++;
			}
		}
		return s;
	}
		
	public String getScore() {
		return numberOfAttempts>0?Integer.toString(score):"";
	}
	
	public String getEnhancedScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score) + "&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>";
	}
	
	public String getDotScore(Date deadline,int rescueScore) {
		try {
			Date now = new Date();
			// a red dot indicates a low score that has not been rehabilitated
			// show the red dot only if the deadline has passed and both the group score and total score are at/below threshold
			boolean redDot = now.after(deadline) && score<=rescueScore && overallScore<=rescueScore;
			return (redDot?"<img src=images/red_dot.gif>&nbsp;":"") + (numberOfAttempts>0?Integer.toString(score):"");
		} catch (Exception e) {
			return "";
		}
	}
	
	public String getEnhancedDotScore(Date deadline,int rescueScore) {
		Date now = new Date();
		return getDotScore(deadline,rescueScore) + (deadline.after(now) && numberOfAttempts==0?"":"&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>");
	}
	
	public Score update(Date deadline,int newScore) {
		Date now = new Date();
		if (now.before(deadline)) {
			numberOfAttempts++;
			if (newScore > score) score = newScore;
		}
		if (newScore > overallScore) overallScore = newScore;
		return this;
	}
}