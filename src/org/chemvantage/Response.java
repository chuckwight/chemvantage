package org.chemvantage;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Id;

public class Response implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	String assignmentType;
	long topicId;
	long questionId;
	String studentResponse;
	String correctAnswer;
	int score;
	int possibleScore;
	String userId;
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