package org.chemvantage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Score {
    long topicId = 0;
    String topicTitle = "";
    int score;
    Date deadline = null;
    List<Long> correctQuestions = new ArrayList<Long>();
    
    Score(long topicId,String topicTitle,int score,Date deadline) {
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.score = score;
    	this.deadline = deadline;
    }
    
    Score(long topicId, String topicTitle,int score) {
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.score = score;
    }

    Score(long topicId,String topicTitle,Date deadline) {
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    	this.deadline = deadline;
    }
    
    Score(long topicId, String topicTitle) {
    	this.topicId = topicId;
    	this.topicTitle = topicTitle;
    }

    public int getHWScore() {
    	return correctQuestions.size();
    }
    
    public void addCorrectQuestion(long id) {
    	correctQuestions.add(id);
    }
    
    public boolean correctQuestionExists(long id) {
    	return correctQuestions.contains(id);
    }
    
    public int compareTo(Score s) {
    	try {
    		return (int)(this.deadline.getTime() - s.deadline.getTime());
    	} catch (Exception e) {
    		return this.topicTitle.compareTo(s.topicTitle);
    	}
    }

  }

