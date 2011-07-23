package org.chemvantage;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class QuizTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
    long topicId;
    String topicTitle;
    String userId;
    Date downloaded;
    Date graded;
    int score;
    int possibleScore;
    String IPNumber;

    QuizTransaction() {}
    
    QuizTransaction(long topicId,String topicTitle,String userId,Date downloaded,Date graded,int score,int possibleScore,String IPNumber) {
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.userId = userId;
        this.downloaded = downloaded;
        this.graded = graded;
        this.score = score;
        this.possibleScore = possibleScore;
        this.IPNumber = IPNumber;
    }

}