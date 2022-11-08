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

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.condition.IfNotZero;

@Entity
public class Response implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String assignmentType;
	@Index	long assignmentId;
	@Index 	long topicId;
	@Index 	long questionId;
	@Index 	String userId;
	@Index 	Date submitted;
			String studentResponse;
			String correctAnswer;
	@Index	int score;
			int possibleScore;
	@Index(IfNotZero.class)  long transactionId;
	
	/*
	 * We can store and retrieve the userId as a SHA-256 hash using the following scheme:
	 * hashedId = new String(MessageDigest.getInstance("SHA-256").digest(userId.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
	 * To store, calculate the hashedId and include it as the userId in the ofy().save() command
	 * To retrieve, calculate the hashedId and use it in the ofy().load() filter
	 */
			
	Response() {}
    
	Response(String assignmentType, long assignmentId, long questionId, String studentResponse, String correctAnswer, int score, int possibleScore, String userId, Date submitted) {
        this.assignmentType = assignmentType;
        this.assignmentId = assignmentId;
        this.questionId = questionId;
        this.studentResponse = studentResponse;
        this.correctAnswer = correctAnswer;
        this.score = score;
        this.possibleScore = possibleScore;
        this.userId = Subject.hashId(userId);
        this.submitted = submitted;
    }
}