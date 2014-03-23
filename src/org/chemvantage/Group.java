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
import java.util.TimeZone;

import javax.persistence.Id;
import javax.persistence.Transient;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Indexed;
import com.googlecode.objectify.annotation.Unindexed;

@Cached @Unindexed
public class Group implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	@Indexed String instructorId;
	@Indexed String context_id;
	@Indexed String domain;
			 String description;
			 String timeZone;
			 Date created;
			 Date nextDeadline;
			 boolean sendRescueMessages;
			 int rescueThresholdScore = 50;
			 String defaultRescueSubject;
			 String defaultRescueMessage;
			 String lis_outcome_service_url;
			 boolean isUsingLisOutcomeService;
			 List<String> rescueCcIds = new ArrayList<String>();
			 List<String> memberIds = new ArrayList<String>();
			 List<String> tAIds = new ArrayList<String>();
			 List<Long> topicIds = new ArrayList<Long>();
			 List<Long> quizAssignmentIds = new ArrayList<Long>();
			 List<Long> hwAssignmentIds = new ArrayList<Long>();

	@Transient transient Objectify ofy = ObjectifyService.begin();
     
    Group() {}
    
    Group(String instructorId, String description) {
    	this.created = new Date();
        this.instructorId = instructorId;
        this.description = description;
        this.timeZone = TimeZone.getDefault().getID();
    }

    Group(String type,String context_id,String description) {
    	if ("BLTI".equals(type)) {
    		this.created = new Date();
    		this.context_id = context_id;
    		this.description = description;
    		this.timeZone = TimeZone.getDefault().getID();
    		this.instructorId = "unknown";
    	}
    }
    
    public String getInstructorEmail() {
    	try {
    		return ofy.get(User.class,this.instructorId).email;
    	} catch (Exception e) {
    		return "";
    	}
    }
    
    public String getInstructorFullName() {
    	try {
    		return ofy.get(User.class,this.instructorId).getFullName();
    	} catch (Exception e) {
    		return "";
    	}
    }
    
    public String getInstructorBothNames() {
    	try {
    		return ofy.get(User.class,this.instructorId).getBothNames();
    	} catch (Exception e) {
    		return "TBA";
    	}
    }

    public TimeZone getTimeZone() {
        try {
        	return TimeZone.getTimeZone(timeZone);
        } catch (Exception e) {
        	return TimeZone.getDefault();
        }
    }
 
    public boolean isMember(String id) {
    	return memberIds.contains(id);
    }
    
    public boolean isTA(String id) {
    	return tAIds.contains(id);
    }
    
    public void setNextDeadline() {
    	this.nextDeadline = null;
    	Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",this.id).filter("deadline >",new Date());
    	for (Assignment a : assignments) if (this.nextDeadline == null || a.deadline.before(nextDeadline)) this.nextDeadline = a.deadline;
    }
    
    public Date getNextDeadline() {
    	if (isUsingLisOutcomeService) return null;  // don't report deadline to UserInfo box; use the LMS instead
    	if (nextDeadline==null || nextDeadline.before(new Date())) setNextDeadline();
    	return this.nextDeadline;
    }

    public Long getAssignmentId(String assignmentType,long topicId) {
    	if (topicIds.indexOf(topicId)<0) return 0L;
    	if (assignmentType.equals("Quiz")) return this.quizAssignmentIds.get(topicIds.indexOf(topicId));
    	if (assignmentType.equals("Homework")) return this.hwAssignmentIds.get(topicIds.indexOf(topicId));
    	return 0L;
    }
    
    public List<Long> getGroupTopicIds() {
    	if (this.topicIds.isEmpty()) setGroupTopicIds();
    	return this.topicIds;
    }

    public boolean setGroupTopicIds() {
    	this.topicIds.clear();
    	this.quizAssignmentIds.clear();
    	this.hwAssignmentIds.clear();
    	Query<Assignment> assignments = ofy.query(Assignment.class).filter("groupId",this.id).order("deadline");
    	for (Assignment a : assignments) {
    		if (a.topicId==0L || ofy.find(Topic.class,a.topicId)==null) {
    			ofy.delete(a);
    			continue;
    		}
    		if (!this.topicIds.contains(a.topicId)) {  // new entry for this topic
    			this.topicIds.add(a.topicId);
    			this.quizAssignmentIds.add(a.assignmentType.equals("Quiz")?a.id:0L);
    			this.hwAssignmentIds.add(a.assignmentType.equals("Homework")?a.id:0L);    			
    		} else { // second assignment for this topic
    			int i = topicIds.indexOf(a.topicId);
    			if (a.assignmentType.equals("Quiz")) quizAssignmentIds.set(i,a.id);
    			if (a.assignmentType.equals("Homework")) hwAssignmentIds.set(i,a.id);
    		}
    	}
    	return topicIds.isEmpty()?false:true;
    }
    
    Score getScore(String userId,Assignment assignment) {
    	try {
    		Key<Score> k = new Key<Score>(new Key<User>(User.class, userId),Score.class,assignment.id);
    		Score s = ofy.find(k);
    		if (s==null) {
    			s = Score.getInstance(userId,assignment);
    			ofy.put(s);
    		}
    		return s;
    	} catch (Exception e) {
    		return null;
    	}
    }
    
    void deleteScores(Assignment assignment) {
    	List <Key<Score>> scoreKeys = new ArrayList<Key<Score>>();
    	for (String u : this.memberIds) scoreKeys.add(new Key<Score>(new Key<User>(User.class, u),Score.class,assignment.id));
    	ofy.delete(scoreKeys);
    }

    void calculateScores(Assignment assignment) {
    	List<Score> scores = new ArrayList<Score>();
    	for (String u : this.memberIds) scores.add(Score.getInstance(u,assignment));
    	ofy.put(scores);
    }

    void deleteScores() {
    	Iterable<Key<Score>> scoreKeys = ofy.query(Score.class).filter("groupId",this.id).fetchKeys();
    	ofy.delete(scoreKeys);
    }

    void setUsingLisOutcomeService(String url) {
    	if (url==null) return;  // no useful information
    	if (this.isUsingLisOutcomeService && url.equals(this.lis_outcome_service_url)) return;  // no changes needed
    	this.isUsingLisOutcomeService = true;
    	this.lis_outcome_service_url = url;
    	ofy.put(this);
    }
    
    void deleteAssignments() {
    	List<Key<Assignment>> assignmentKeys = new ArrayList<Key<Assignment>>();
    	for (Long i : quizAssignmentIds) assignmentKeys.add(new Key<Assignment>(Assignment.class,i));
    	for (Long i : hwAssignmentIds) assignmentKeys.add(new Key<Assignment>(Assignment.class,i));
    	ofy.delete(assignmentKeys);
    }
    
    void delete() {
    	this.deleteAssignments();
    	this.deleteScores();
    	ofy.delete(this);
    }
    
    boolean getUsingLisOutcomeService() { 
    	return this.isUsingLisOutcomeService;
    }
    
    boolean isActive() {
    	Date oneMonthAgo = new Date(new Date().getTime()-2592000000L);
    	if (this.created==null) {  // update this new field for older groups
    		this.created = new Date();
    		ofy.put(this);
    	}
    	// group is active if an upcoming deadline exists or if the group is less than month old
    	return this.nextDeadline!=null || this.created.after(oneMonthAgo);
    }
 }