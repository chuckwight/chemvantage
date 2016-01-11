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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Cache @Entity
public class Assignment implements Comparable<Assignment>,Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	long groupId;
	long topicId;
	List<Long> topicIds; // used for practice exams which have multiple topicIds
	String assignmentType;
	Date deadline;
	List<String> resourceLinkIds = new ArrayList<String>();
	List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

    Assignment() {}

    @SuppressWarnings("unchecked")
	Assignment(long groupId,long topicId,String assignmentType,Date deadline) {
    	this.groupId = groupId;
    	this.topicId = topicId;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
    	questionKeys = (List<Key<Question>>)ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).keys();
    }
    
    @SuppressWarnings("unchecked")
	Assignment(long groupId,List<Long> topicIds,String assignmentType,Date deadline) {   // specific to Practice Exam assignments with multiple topicIds
    	this.groupId = groupId;
    	this.topicIds = topicIds;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
    	for (Long topicId : topicIds) questionKeys.addAll((List<Key<Question>>)ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",topicId).keys());
    }
    
    Assignment(long groupId,String assignmentType) {
    	this.groupId = groupId;
    	this.assignmentType = assignmentType;
    }

    public int compareTo(Assignment other) {
    	return (int)(this.deadline.getTime() - other.deadline.getTime());
    }
    
    public void addResourceLinkId(String r) {
    	if (!resourceLinkIds.contains(r)) resourceLinkIds.add(r);
    }
    
    public boolean matches(String assignmentType,List<Long> topicIds) { // this method applies only to PracticeExam assignments
    	if ("PracticeExam".equals(this.assignmentType) && "PracticeExam".equals(assignmentType) && this.topicIds!=null && this.topicIds.equals(topicIds)) return true;
    	else return false;
    }
    
    public boolean matches(String assignmentType,long topicId) {
    	if (!("Quiz".equals(assignmentType) || "Homework".equals(assignmentType))) return false; // this method only applies to Quiz or Homework assignments 
    	return (assignmentType.equals(this.assignmentType) && this.topicId==topicId);
    }
}