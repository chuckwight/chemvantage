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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Unindexed;

@Cached
public class Assignment implements Comparable<Assignment>,Serializable {
	private static final long serialVersionUID = 137L;
	@Unindexed	@Id Long id;
	long groupId;
	long topicId;
	List<Long> topicIds; // used for practice exams which have multiple topicIds
	String assignmentType;
	Date deadline;
	List<String> resourceLinkIds = new ArrayList<String>();
	List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

    Assignment() {}

    Assignment(long groupId,long topicId,String assignmentType,Date deadline) {
    	this.groupId = groupId;
    	this.topicId = topicId;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
    	questionKeys = ObjectifyService.begin().query(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).listKeys();
    }
    
    Assignment(long groupId,List<Long> topicIds,String assignmentType,Date deadline) {   // specific to Practice Exam assignments with multiple topicIds
    	this.groupId = groupId;
    	this.topicIds = topicIds;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
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
}