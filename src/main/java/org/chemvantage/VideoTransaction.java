/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2020 ChemVantage LLC
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.condition.IfNotZero;

@Entity
public class VideoTransaction {
	@Id		Long id;
	@Index	long videoId;
	@Index	String userId;
	@Index	Date downloaded;
	@Index	Date graded;
			int score;
	@Index(IfNotZero.class)	long assignmentId;
			List<Integer> quizletScores;
			List<String> missedQuestions;
			int possibleScore;
			String videoTitle;
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			Map<Key<Question>,Integer> questionScores = new HashMap<Key<Question>,Integer>();
			Map<Key<Question>,String> studentAnswers = new HashMap<Key<Question>,String>();
			Map<Key<Question>,String> correctAnswers = new HashMap<Key<Question>,String>();
			
    VideoTransaction() {}
    
    VideoTransaction(long videoId,String videoTitle,int nBreaks, String userId,long assignmentId,int possibleScore) {
    	this.videoId = videoId;
    	this.videoTitle = videoTitle;
    	this.userId = Subject.hashId(userId);
        this.graded = null;
        this.score = 0;
        this.assignmentId = assignmentId;
        this.possibleScore = possibleScore;
        this.downloaded = new Date();
        this.quizletScores = new ArrayList<Integer>();
        this.missedQuestions = new ArrayList<String>();
        for (int i=0;i<nBreaks;i++) {
        	quizletScores.add(0);
        	missedQuestions.add("");
        }
    }
}
