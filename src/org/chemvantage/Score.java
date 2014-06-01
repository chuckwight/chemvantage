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
import com.googlecode.objectify.annotation.Indexed;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.annotation.Unindexed;

@Cached @Unindexed
public class Score {    // this object represents a best score achieved by a user on a quiz or homework
	@Id Long assignmentId;      // from the datastore.
	@Parent Key<User> owner;
	long groupId;
	int score;
	int overallScore;
	int maxPossibleScore;
	int numberOfAttempts;
	Date mostRecentAttempt;
	String lis_result_sourcedid;
	@Indexed boolean lisReportComplete;
	
	Score() {
		lisReportComplete=false;
	}	
	
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
				if (s.lis_result_sourcedid == null || s.lis_result_sourcedid.isEmpty()) s.lis_result_sourcedid = qt.lis_result_sourcedid;  // record any available sourcedid value for reporting score to the LMS
				if (s.mostRecentAttempt==null || qt.downloaded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
					s.mostRecentAttempt = qt.downloaded;
					s.maxPossibleScore = qt.possibleScore;
					if (qt.lis_result_sourcedid != null && !qt.lis_result_sourcedid.equals(s.lis_result_sourcedid)) s.lis_result_sourcedid = qt.lis_result_sourcedid;
				}				
			}
		} else if (a.assignmentType.equals("Homework")) {
			Query<HWTransaction> hwTransactions = ofy.query(HWTransaction.class).filter("userId",userId).filter("topicId",a.topicId);
			List<Key<Question>> allQuestionKeys = ofy.query(Question.class).filter("assignmentType","Homework").filter("topicId", a.topicId).listKeys();
			List<Key<Question>> assignmentQuestionKeys = new ArrayList<Key<Question>>();
			assignmentQuestionKeys.addAll(a.questionKeys);  // clones the assignment List of question keys
			for (HWTransaction ht : hwTransactions) {
				if (ht.graded.before(a.deadline)) {
					s.numberOfAttempts++;
					if (ht.score > 0 && assignmentQuestionKeys.remove(new Key<Question>(Question.class,ht.questionId))) s.score++; 
				}
				if (ht.score>0 && allQuestionKeys.remove(new Key<Question>(Question.class,ht.questionId))) s.overallScore++;
				if (s.lis_result_sourcedid == null || s.lis_result_sourcedid.isEmpty()) s.lis_result_sourcedid = ht.lis_result_sourcedid;  // record any available sourcedid value for reporting score to the LMS				
				if (s.mostRecentAttempt == null || ht.graded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
					s.mostRecentAttempt = ht.graded;
					s.maxPossibleScore = a.questionKeys.size();
					if (ht.lis_result_sourcedid != null && !ht.lis_result_sourcedid.equals(s.lis_result_sourcedid)) s.lis_result_sourcedid = ht.lis_result_sourcedid;
				}					
			}
		} else if (a.assignmentType.equals("PracticeExam")) {
			Query<PracticeExamTransaction> practiceExamTransactions = ofy.query(PracticeExamTransaction.class).filter("userId",userId);
			int score = 0;
			int possibleScore = 0;
			for (PracticeExamTransaction pt : practiceExamTransactions) {
				if (pt.graded==null || !pt.topicsMatch(a.topicIds)) continue;
				if (pt.downloaded.before(a.deadline)) {  // pre-deadline group score
					s.numberOfAttempts++;  // number of pre-deadline quiz attempts
					score = 0;
					for (int i=0;i<pt.scores.length;i++) score += pt.scores[i];
					s.score = (score>s.score?score:s.score);  // keep the best (max) score
				}
				if (score > s.overallScore) s.overallScore = score;  // overall student score on this assignment including post-deadline scores
				if (s.lis_result_sourcedid == null || s.lis_result_sourcedid.isEmpty()) s.lis_result_sourcedid = pt.lis_result_sourcedid;  // record any available sourcedid value for reporting score to the LMS
				if (s.mostRecentAttempt==null || pt.downloaded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
					s.mostRecentAttempt = pt.downloaded;
					possibleScore = 0;
					for (int i=0;i<pt.possibleScores.length;i++) possibleScore += pt.possibleScores[i];
					s.maxPossibleScore = possibleScore;
					if (pt.lis_result_sourcedid != null && !pt.lis_result_sourcedid.equals(s.lis_result_sourcedid)) s.lis_result_sourcedid = pt.lis_result_sourcedid;
				}				
			}			
		}
		if (s.score > s.maxPossibleScore) s.score = s.maxPossibleScore;  // max really is the limit for LTI reporting
		if (s.overallScore > s.maxPossibleScore) s.overallScore = s.maxPossibleScore;  // max really is the limit for LTI reporting
		return s;
	}
		
	public String getScore() {
		return numberOfAttempts>0?Integer.toString(score):"";
	}
	
	public String getEnhancedScore() {
		if (numberOfAttempts == 0) return "";
		else return Integer.toString(score) + "&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>";
	}
	
	public String getDotScore(Date deadline,int thresholdPct) {
		try {
			Date now = new Date();
			// a red dot indicates a low score that has not been rehabilitated
			// show the red dot only if the deadline has passed and both the group score and total overall score are at/below threshold
			boolean redDot = false;
			
			if (now.after(deadline)) {  // only consider the red dot if we're past the assignment deadline
				if (overallScore==0) redDot = true;
				else if (mostRecentAttempt==null) { // recalculate the Score object
					Assignment a = ObjectifyService.begin().get(Assignment.class,assignmentId);
					Score s = Score.getInstance(owner.getName(),a);
					ObjectifyService.begin().put(s);
					return s.getDotScore(deadline, thresholdPct);
				}
				else {
					boolean belowThreshold = 100.0*(double)score/(double)maxPossibleScore < thresholdPct;
					boolean rehabilitated = 100.0*(double)overallScore/(double)maxPossibleScore >= thresholdPct;
					redDot = belowThreshold && !rehabilitated;
				}
			}
			return (redDot?"<img src=images/red_dot.gif>&nbsp;":"") + (numberOfAttempts>0?Integer.toString(score):"");
		} catch (Exception e) {
			return "";
		}
	}
	
	public String getEnhancedDotScore(Date deadline,int thresholdPct) {
		return getDotScore(deadline,thresholdPct) + (numberOfAttempts>0?"/"+maxPossibleScore:"");
		//return getDotScore(deadline,rescueScore) + (deadline.after(now) && numberOfAttempts==0?"":"&nbsp;&nbsp;&nbsp;&nbsp;<FONT COLOR=GRAY>(" + Integer.toString(numberOfAttempts) + ")</FONT>");
	}
	
	public boolean needsLisReporting() {
		if (lis_result_sourcedid==null || lis_result_sourcedid.isEmpty() || lisReportComplete) return false;
		return true;
	}
}