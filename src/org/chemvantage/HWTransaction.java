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

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Unindexed;

@Cached
public class HWTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Unindexed	@Id Long id;
    			long questionId;
    			long topicId;
    @Unindexed	String topicTitle;
    			String userId;
    			Date graded;
    @Unindexed	long responseId;
    			int score;
    @Unindexed	int possibleScore;
    @Unindexed  String lis_result_sourcedid;
    @Unindexed	String IPNumber;

    HWTransaction() {}
    
    HWTransaction(long questionId,long topicId,String topicTitle,String userID,Date graded,long responseId,int score,int possibleScore,String IPNumber) {
    	this.questionId = questionId;
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.userId = userID;
        this.graded = graded;
        this.responseId = responseId;
        this.score = score;
        this.possibleScore = possibleScore;
        this.IPNumber = IPNumber;
    }

	public String toString() {
    	return "Homework - " + topicTitle + " QuestionId:" + questionId + " Graded:" + graded + " Score:" + score + "/" + possibleScore
    	+ (lis_result_sourcedid==null?"":" reported to LMS.");
	}
}
