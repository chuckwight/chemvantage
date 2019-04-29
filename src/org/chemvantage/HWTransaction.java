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

import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Cache @Entity
public class HWTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id		Long id;
	@Index	long questionId;
	@Index	long topicId;
	@Index	String userId;
	@Index	Date graded;
	@Index	int score;
	@Index	long assignmentId;
			String lis_result_sourcedid;
			int possibleScore;
			long responseId;
			String topicTitle;
	
    HWTransaction() {}
    
    HWTransaction(long questionId,long topicId,String topicTitle,String userID,Date graded,int score,long assignmentId,int possibleScore) {
    	this.questionId = questionId;
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.userId = userID;
        this.graded = graded;
        this.score = score;
        this.assignmentId = assignmentId;
        this.possibleScore = possibleScore;
    }

    public String tableRow() {
    	return "<tr><td>" + topicTitle + "</td><td>" + questionId + "</td><td>" + graded + "</td><td>" + score + "/" + possibleScore
    	    	+ (lis_result_sourcedid==null?"":" reported to LMS.") + "</td></tr>";
    }
    
}
