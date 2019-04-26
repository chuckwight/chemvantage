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

import java.util.ArrayList;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Cache @Entity
public class Assignment {
	@Id 	Long id;
	@Index 	long groupId;
	@Index	String domain;
	@Index	String assignmentType;
	@Index	long topicId;
	@Index	String resourceLinkId;
			List<Long> topicIds; // used for practice exams which have multiple topicIds
			List<String> resourceLinkIds = new ArrayList<String>();
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

    Assignment() {}

   Assignment(long groupId,String domain,String resourceLinkId,long topicId,String assignmentType) {  // specific to Quiz and Homework assignments with a single topicId
	   	this.groupId = groupId;
   		this.domain = domain;
    	this.resourceLinkId = resourceLinkId;
    	this.topicId = topicId;
    	this.assignmentType = assignmentType;
    	questionKeys = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).keys().list();
    }
    
    Assignment(long groupId,String domain,String resourceLinkId, List<Long> topicIds,String assignmentType) {   // specific to Practice Exam assignments with multiple topicIds
    	this.groupId = groupId;
    	this.domain = domain;
    	this.resourceLinkId = resourceLinkId;
    	this.topicIds = topicIds;
    	this.assignmentType = assignmentType;
    	for (Long topicId : topicIds) questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",topicId).keys().list());
    }

    public void setResourceLinkId (String r) {
    	this.resourceLinkId = r;
    	if (resourceLinkIds.contains(r)) resourceLinkIds.remove(r);
    }
    
    public String getresourceLinkId() {
    	return this.resourceLinkId;
    }
/*    
    public boolean matches(String assignmentType,List<Long> topicIds) { // this method applies only to PracticeExam assignments
    	if ("PracticeExam".equals(this.assignmentType) && "PracticeExam".equals(assignmentType) && this.topicIds!=null && this.topicIds.equals(topicIds)) return true;
    	else return false;
    }
    
    public boolean matches(String assignmentType,long topicId) {
    	if (!("Quiz".equals(assignmentType) || "Homework".equals(assignmentType))) return false; // this method only applies to Quiz or Homework assignments 
    	return (assignmentType.equals(this.assignmentType) && this.topicId==topicId);
    }
    */
}