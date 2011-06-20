package org.chemvantage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.persistence.Id;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Unindexed;

@Cached
public class Group {
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
    	Objectify ofy = ObjectifyService.begin();
    	return ofy.get(User.class,this.instructorId).email;
    }
    
    public String getInstructorFullName() {
    	Objectify ofy = ObjectifyService.begin();
    	try {
    		return ofy.get(User.class,this.instructorId).getFullName();
    	} catch (Exception e) {
    		return "";
    	}
    }
    
    public String getInstructorBothNames() {
    	Objectify ofy = ObjectifyService.begin();
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
    	Objectify ofy = ObjectifyService.begin();
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
    	Objectify ofy = ObjectifyService.begin();
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
}