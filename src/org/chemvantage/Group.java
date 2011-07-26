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
import com.googlecode.objectify.annotation.Unindexed;

@Cached
public class Group implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
    String instructorId;
    String context_id;
    @Unindexed String description;
    @Unindexed String timeZone;
    @Unindexed Date nextDeadline;
    @Unindexed boolean sendRescueMessages;
    @Unindexed int thresholdScorePct = 50;
    @Unindexed String defaultRescueSubject;
    @Unindexed String defaultRescueMessage;
    List<String> rescueCcIds = new ArrayList<String>();
    List<String> memberIds = new ArrayList<String>();
    List<String> tAIds = new ArrayList<String>();
    List<Long> topicIds = new ArrayList<Long>();
    List<Long> quizAssignmentIds = new ArrayList<Long>();
    List<Long> hwAssignmentIds = new ArrayList<Long>();
    
	@Transient transient Objectify ofy = ObjectifyService.begin();
     
    Group() {}
    
    Group(String instructorId, String description) {
        this.instructorId = instructorId;
        this.description = description;
        this.timeZone = TimeZone.getDefault().getID();
    }

    Group(String type,String context_id,String description) {
    	if ("BLTI".equals(type)) {
    		this.context_id = context_id;
    		this.description = description;
    		this.timeZone = TimeZone.getDefault().getID();
    		this.instructorId = "admin@chemvantage.org";
    	}
    }
    
    public String getInstructorEmail() {
    	return ofy.get(User.class,this.instructorId).email;
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
    		if (this.instructorId.equals("admin@chemvantage.org")) return "TBA";
    		return ofy.get(User.class,this.instructorId).getBothNames();
    	} catch (Exception e) {
    		return "";
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
    		if (ofy.find(Topic.class,a.topicId)==null) {
    			ofy.delete(a);
    			continue;
    		}
    		if (!this.topicIds.contains(a.topicId)) {  // new entry for this topic
    			this.topicIds.add(a.topicId);
    			this.quizAssignmentIds.add(a.assignmentType.equals("Quiz")?a.id:0L);
    			this.hwAssignmentIds.add(a.assignmentType.equals("Homework")?a.id:0L);    			
    		} else { // duplicate entry for this topic
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
    
    void deleteScores(long assignmentId) {
    	List<Score> scores = ofy.query(Score.class).filter("assignmentId",assignmentId).list();
    	ofy.delete(scores);
    }

    void deleteScores(String userId) {
    	List<Score> scores = ofy.query(Score.class).filter("userId",userId).list();
    	ofy.delete(scores);
    }
    
    void deleteScores() {
    	List<Score> scores = ofy.query(Score.class).filter("groupId",this.id).list();
    	ofy.delete(scores);
   }

}