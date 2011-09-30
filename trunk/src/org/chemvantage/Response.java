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

import com.googlecode.objectify.annotation.Indexed;
import com.googlecode.objectify.annotation.Unindexed;

@Unindexed
public class Response implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	String assignmentType;
	long topicId;
	@Indexed long questionId;
	String studentResponse;
	String correctAnswer;
	int score;
	int possibleScore;
	@Indexed String userId;
	Date submitted;

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