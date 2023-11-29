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

@Entity
public class QuizTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index	long topicId;
	@Index	String userId;
	@Index	Date downloaded;
	@Index	Date graded;
	@Index	int score;
	@Index  long assignmentId;
			int possibleScore;
			String topicTitle;
			String studentAnswer;
			String correctAnswer;
			
	QuizTransaction() {}

	public QuizTransaction(long topicId,String topicTitle,String userId,Date downloaded,Date graded,int score,long assignmentId,int possibleScore) {
		this.topicId = topicId;
		this.topicTitle = topicTitle;
		this.userId = Subject.hashId(userId);
		this.downloaded = downloaded;
		this.graded = graded;
		this.score = score;
		this.assignmentId = assignmentId;
		this.possibleScore = possibleScore;
	}

	public QuizTransaction(long assignmentId,String hashedId) {
		this.assignmentId = assignmentId;
		this.userId = hashedId;
		this.downloaded = new Date();
	}
	
	public Long getId() {
		return this.id;
	}

	public Date getGraded() {
		return graded;
	}

	public void putPossibleScore(int ps) {
		this.possibleScore = ps;
	}

	public Date getDownloaded() {
		return this.downloaded;
	}

	public String tableRow() {
		return "<tr><td>" + topicTitle + "</td><td>" + downloaded + "</td><td>" + graded + "</td><td>" + score + "/" + possibleScore + "</td></tr>";
	}

}