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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public class ExamAssignment implements Comparable<Assignment> {
	@Id Long id;
	long groupId;
	long[] topicIds;
	String assignmentType;
	Date deadline;
	List<Key<Question>> questionKeys_2pt = new ArrayList<Key<Question>>();
	List<Key<Question>> questionKeys_10pt = new ArrayList<Key<Question>>();
	List<Key<Question>> questionKeys_15pt = new ArrayList<Key<Question>>();

    ExamAssignment() {}

    ExamAssignment(long groupId,long[] topicIds,String assignmentType,Date deadline) {
    	this.groupId = groupId;
    	this.topicIds = topicIds;
    	this.assignmentType = assignmentType;
    	this.deadline = deadline;
    	Objectify ofy = ObjectifyService.begin();
    	questionKeys_2pt = ofy.query(Question.class).filter("assignmentType",assignmentType).filter("pointValue",2).filter("topicId in",topicIds).listKeys();
    	questionKeys_10pt = ofy.query(Question.class).filter("assignmentType",assignmentType).filter("pointValue",10).filter("topicId in",topicIds).listKeys();
    	questionKeys_15pt = ofy.query(Question.class).filter("assignmentType",assignmentType).filter("pointValue",15).filter("topicId in",topicIds).listKeys();
    }
    
    public int compareTo(Assignment other) {
    	return (int)(this.deadline.getTime() - other.deadline.getTime());
    }
}