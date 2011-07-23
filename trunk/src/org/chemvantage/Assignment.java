package org.chemvantage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class Assignment implements Comparable<Assignment>,Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	long groupId;
	long topicId;
	String assignmentType;
	Date deadline;
	List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

    Assignment() {}

    Assignment(long groupId,long topicId,String assignmentType,Date deadline) {
    	this.groupId = groupId;
    	this.topicId = topicId;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
    	questionKeys = ObjectifyService.begin().query(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).listKeys();
    }
    
    public int compareTo(Assignment other) {
    	return (int)(this.deadline.getTime() - other.deadline.getTime());
    }
}