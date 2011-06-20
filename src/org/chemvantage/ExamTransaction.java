package org.chemvantage;

import java.util.Date;

import javax.persistence.Id;

public class ExamTransaction {
    @Id Long id;
    long topicID;
    String userID;
    Date downloaded;
    Date graded;
    int score;
    int possibleScore;
    String IPNumber;

    ExamTransaction() {}
    
    ExamTransaction(long topicID,String userID,Date downloaded,Date graded,int score,int possibleScore,String IPNumber) {
    	this.topicID = topicID;
    	this.userID = userID;
        this.downloaded = downloaded;
        this.graded = graded;
        this.score = score;
        this.possibleScore = possibleScore;
        this.IPNumber = IPNumber;
    }
}