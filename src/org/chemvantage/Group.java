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
import java.util.TimeZone;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.cmd.Query;

@Cache @Entity
public class Group implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	@Index String instructorId;
	@Index String context_id;
	@Index String domain;
			 String description;
			 String timeZone;
			 Date created;
			 Date nextDeadline;
			 boolean sendRescueMessages;
			 int rescueThresholdScore = 50;
			 String defaultRescueSubject;
			 String defaultRescueMessage;
			 String lis_outcome_service_url;
			 String lis_outcome_service_format;
			 boolean isUsingLisOutcomeService;
			 List<String> rescueCcIds = new ArrayList<String>();
			 List<String> memberIds = new ArrayList<String>();
			 List<String> tAIds = new ArrayList<String>();
			 List<Long> topicIds = new ArrayList<Long>();
			 List<Long> quizAssignmentIds = new ArrayList<Long>();
			 List<Long> hwAssignmentIds = new ArrayList<Long>();

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
    		return ofy().load().type(User.class).id(this.instructorId).safe().getEmail();
    	} catch (Exception e) {
    		return "";
    	}
    }
    
    public String getInstructorFullName() {
    	try {
    		return ofy().load().type(User.class).id(this.instructorId).safe().getFullName();
    	} catch (Exception e) {
    		return "";
    	}
    }
    
    public String getInstructorBothNames() {
    	try {
    		return ofy().load().type(User.class).id(this.instructorId).safe().getBothNames();
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
    
    boolean addTA(String userId) {
    	if (!this.tAIds.contains(userId)) {
    		tAIds.add(userId);
    		return true;      // signals an addition to the list so Group entity should be saved
    	} else return false;  // the TA was already assigned, so no need to save Group entity
    }
    
    void removeTA(String userId) {
    	this.tAIds.remove(userId);
    }
    
    public void setNextDeadline() {
    	this.nextDeadline = null;
    	try {
    		Query<Assignment> assignments = ofy().load().type(Assignment.class).filter("groupId",this.id).filter("deadline >",new Date());
    		for (Assignment a : assignments) if (this.nextDeadline == null || a.getDeadline().before(nextDeadline)) this.nextDeadline = a.getDeadline();
    		ofy().save().entity(this).now();
    	} catch (Exception e) {}
    }
    
    public Date getNextDeadline() {
    	setNextDeadline();
    	if (isUsingLisOutcomeService) return null;  // don't report deadline to UserInfo box; use the LMS instead
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

    public boolean setGroupTopicIds() {  // this routine only applies to Quiz and Homework assignments
    	this.topicIds.clear();
    	this.quizAssignmentIds.clear();
    	this.hwAssignmentIds.clear();
    	Query<Assignment> assignments = ofy().load().type(Assignment.class).filter("groupId",this.id).order("deadline");
    	for (Assignment a : assignments) {
    		if (!(a.assignmentType.equals("Quiz") || a.assignmentType.equals("Homework"))) continue;
    		if (a.topicId==0L || ofy().load().type(Topic.class).id(a.topicId)==null) {
    			ofy().delete().entity(a);
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

    void reviseScores(Assignment assignment) {
    	for (String uId : this.memberIds) {
    		Score revised = Score.getInstance(uId, assignment);
    		try {
    			Score previous = ofy().load().key(Key.create(Key.create(User.class,uId),Score.class,assignment.id)).safe();
    			if (!revised.equals(previous)) ofy().save().entity(revised).now();
    		} catch (Exception e) {
    			ofy().save().entity(revised).now();
    		}
    	}
    }

    void deleteScores() {
    	Iterable<Key<Score>> scoreKeys = ofy().load().type(Score.class).filter("groupId",this.id).keys();
    	ofy().delete().entities(scoreKeys);
    }

    void setUsingLisOutcomeService(String url) {
    	if (url==null) return;  // no useful information
    	if (this.isUsingLisOutcomeService && url.equals(this.lis_outcome_service_url)) return;  // no changes needed
    	this.isUsingLisOutcomeService = true;
    	this.lis_outcome_service_url = url;
    	ofy().save().entity(this);
    }
    
    void deleteAssignments() {
    	List<Key<Assignment>> assignmentKeys = new ArrayList<Key<Assignment>>();
    	for (Long i : quizAssignmentIds) assignmentKeys.add(Key.create(Assignment.class,i));
    	for (Long i : hwAssignmentIds) assignmentKeys.add(Key.create(Assignment.class,i));
    	ofy().delete().keys(assignmentKeys);
    }
    
    void delete() {
    	this.deleteAssignments();
    	this.deleteScores();
    	ofy().delete().entity(this);
    }
    
    boolean getUsingLisOutcomeService() { 
    	return this.isUsingLisOutcomeService;
    }
    
    String getLisOutcomeFormat() {
    	return lis_outcome_service_format==null?"application/xml":lis_outcome_service_format;
    }
    
	int validatedMemberCount() {
    	// This method retrieves memberIds from the datastore, omitting any users that 
    	// have been deleted from the datastore without being deleted from this group
    	// and updates the memberIds List.
    	// The method then returns the integer number of current group members.
    	int count = this.memberIds.size();
		try {
    		this.memberIds = new ArrayList<String>(ofy().load().type(User.class).ids(memberIds).keySet());
    		if (memberIds.size()!=count) ofy().save().entity(this);
    		return memberIds.size();
    	} catch (Exception e) {}
		return count;
    }
    
    boolean isActive() {
    	// group is active if it has at least one member or a valid instructor
    	return (validatedMemberCount()>0 || ofy().load().type(User.class).id(instructorId).now()!=null);
    }
}