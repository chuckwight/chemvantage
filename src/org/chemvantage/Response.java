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
public class Response implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String assignmentType;
	@Index 	long topicId;
	@Index 	long questionId;
	@Index 	String userId;
	@Index 	Date submitted;
			String studentResponse;
			String correctAnswer;
	@Index	int score;
			int possibleScore;
	
	Response() {}
    
	Response(String assignmentType, long topicId, long questionId, String studentResponse, String correctAnswer, int score, int possibleScore, String userId, Date submitted) {
        this.assignmentType = assignmentType;
        this.topicId = topicId;
        this.questionId = questionId;
        this.studentResponse = studentResponse;
        this.correctAnswer = correctAnswer;
        this.score = score;
        this.possibleScore = possibleScore;
        this.userId = userId;
        this.submitted = submitted;
    }
}