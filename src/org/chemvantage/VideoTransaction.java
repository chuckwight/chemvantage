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
import java.util.List;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class VideoTransaction {
	@Id		Long id;
	@Index	long videoId;
	@Index	String userId;
	@Index	Date graded;
	@Index	int score;
	@Index	long assignmentId;
			List<Integer> quizletScores;
			List<String> missedQuestions;
			String lis_result_sourcedid;
			int possibleScore;
			String videoTitle;
	
    VideoTransaction() {}
    
    VideoTransaction(long videoId,String videoTitle,int nBreaks, String userID,long assignmentId,int possibleScore,String lis_result_sourcedid) {
    	this.videoId = videoId;
    	this.videoTitle = videoTitle;
    	this.userId = userID;
        this.graded = null;
        this.score = 0;
        this.assignmentId = assignmentId;
        this.possibleScore = possibleScore;
        this.lis_result_sourcedid = lis_result_sourcedid;
        this.quizletScores = new ArrayList<Integer>();
        for (int i=0;i<nBreaks+1;i++) {
        	quizletScores.add(0);
        	missedQuestions.add("");
        }
    }
}
