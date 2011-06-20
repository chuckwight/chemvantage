package org.chemvantage;

import java.util.Date;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class HWTransaction {
    @Id Long id;
    long questionId;
    long topicId;
    String topicTitle;
    String userId;
    Date graded;
    long responseId;
    int score;
    int possibleScore;
    String IPNumber;

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

}
