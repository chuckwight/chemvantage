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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import com.googlecode.objectify.cmd.Query;

@Cache @Entity
public class Score {    // this object represents a best score achieved by a user on a quiz or homework
	@Id 	Long assignmentId;      // from the datastore.
	@Parent Key<User> owner;
	@Index	long groupId;
			boolean lisReportComplete;
			int score;
			int overallScore;
			int maxPossibleScore;
			int numberOfAttempts;
			Date mostRecentAttempt;
			String lis_result_sourcedid;
	
	Score() {
		lisReportComplete=false;
	}	
	
	public static Score getInstance(String userId,Assignment a) {
		Score s = new Score();
		s.assignmentId = a.id;
		s.owner = Key.create(User.class,userId);
		s.groupId = a.groupId;
		s.score = 0;
		s.overallScore = 0;
		s.numberOfAttempts = 0;
		
		if (a.assignmentType.equals("Quiz")) {
			Query<QuizTransaction> quizTransactions = ofy().load().type(QuizTransaction.class).filter("userId",userId).filter("assignmentId",a.id);
			for (QuizTransaction qt : quizTransactions) {
				s.numberOfAttempts++;  // number of pre-deadline quiz attempts
				s.score = (qt.score>s.score?qt.score:s.score);  // keep the best (max) score
				//if (qt.score > s.overallScore) s.overallScore = qt.score;  // overall student score on this assignment
				if (s.lis_result_sourcedid == null || s.lis_result_sourcedid.isEmpty()) s.lis_result_sourcedid = qt.lis_result_sourcedid;  // record any available sourcedid value for reporting score to the LMS
				if (s.mostRecentAttempt==null || qt.downloaded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
					s.mostRecentAttempt = qt.downloaded;
					s.maxPossibleScore = qt.possibleScore;
					if (qt.lis_result_sourcedid != null && !qt.lis_result_sourcedid.equals(s.lis_result_sourcedid)) s.lis_result_sourcedid = qt.lis_result_sourcedid;
				}				
			}
		} else if (a.assignmentType.equals("Homework")) {
			Query<HWTransaction> hwTransactions = ofy().load().type(HWTransaction.class).filter("userId",userId).filter("assignmentId",a.id);
			//List<Key<Question>> allQuestionKeys = ofy().load().type(Question.class).filter("assignmentType","Homework").filter("topicId", a.topicId).keys().list();
			List<Key<Question>> assignmentQuestionKeys = new ArrayList<Key<Question>>();
			assignmentQuestionKeys.addAll(a.questionKeys);  // clones the assignment List of question keys
			for (HWTransaction ht : hwTransactions) {				
				s.numberOfAttempts++;
				if (ht.score > 0 && assignmentQuestionKeys.remove(Key.create(Question.class,ht.questionId))) s.score++; 
				//if (ht.score>0 && allQuestionKeys.remove(Key.create(Question.class,ht.questionId))) s.overallScore++;
				if (s.lis_result_sourcedid == null || s.lis_result_sourcedid.isEmpty()) s.lis_result_sourcedid = ht.lis_result_sourcedid;  // record any available sourcedid value for reporting score to the LMS				
				if (s.mostRecentAttempt == null || ht.graded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
					s.mostRecentAttempt = ht.graded;
					s.maxPossibleScore = a.questionKeys.size();
					if (ht.lis_result_sourcedid != null && !ht.lis_result_sourcedid.equals(s.lis_result_sourcedid)) s.lis_result_sourcedid = ht.lis_result_sourcedid;
				}					
			}
		} else if (a.assignmentType.equals("PracticeExam")) {
			Query<PracticeExamTransaction> practiceExamTransactions = ofy().load().type(PracticeExamTransaction.class).filter("userId",userId);
			int score = 0;
			int possibleScore = 0;
			for (PracticeExamTransaction pt : practiceExamTransactions) {
				if (pt.graded==null || !pt.topicsMatch(a.topicIds)) continue;
				s.numberOfAttempts++;  // number of pre-deadline quiz attempts
				score = 0;
				for (int i=0;i<pt.scores.length;i++) score += pt.scores[i];
				s.score = (score>s.score?score:s.score);  // keep the best (max) score
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
			String dotUrl = "https://www.chemvantage.org/images/red_dot.gif";
			
			if (now.after(deadline)) {  // only consider the red dot if we're past the assignment deadline
				if (overallScore==0) redDot = true;
				else if (mostRecentAttempt==null) { // recalculate the Score object
					Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
					Score s = Score.getInstance(owner.getName(),a);
					ofy().save().entity(s);
					return s.getDotScore(deadline, thresholdPct);
				}
				else {
					boolean belowThreshold = 100.0*(double)score/(double)maxPossibleScore < thresholdPct;
					boolean rehabilitated = 100.0*(double)overallScore/(double)maxPossibleScore >= thresholdPct;
					redDot = belowThreshold && !rehabilitated;
				}
			}
			return (redDot?"<img src=" + dotUrl + ">&nbsp;":"") + (numberOfAttempts>0?Integer.toString(score):"") + (numberOfAttempts>0?"&nbsp;<FONT COLOR=GRAY>("+numberOfAttempts+"&nbsp;" + (numberOfAttempts>1?"tries":"try") + ")</FONT>":"");
		} catch (Exception e) {
			return "";
		}
	}
	
	public String getEnhancedDotScore(Date deadline,int thresholdPct) {
		return getDotScore(deadline,thresholdPct) + (numberOfAttempts>0?"/"+maxPossibleScore:"");
	}
	
	public boolean needsLisReporting() {
		if (lis_result_sourcedid==null || lis_result_sourcedid.isEmpty() || lisReportComplete) return false;
		return true;
	}
	
    boolean equals(Score s) {
    	// note: this method does not check the value of lisReportComplete because the comparison is generally to a new Score instance
    	return s.score == this.score
    			&& s.assignmentId == this.assignmentId
    			&& s.owner.equals(this.owner)
    			&& s.groupId == this.groupId
    			&& s.score == this.score
    			&& s.overallScore == this.overallScore
    			&& s.maxPossibleScore == this.maxPossibleScore
    			&& s.numberOfAttempts == this.numberOfAttempts
    			&& s.mostRecentAttempt.equals(s.mostRecentAttempt)
    			&& (s.lis_result_sourcedid==null?this.lis_result_sourcedid==null:s.lis_result_sourcedid.equals(this.lis_result_sourcedid));	
    }
}