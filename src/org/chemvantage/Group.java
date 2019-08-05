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
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Group implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String instructorId;
	@Index 	String domain;
	@Index	String context_id;
			String description;	
			Date created;
			String lis_outcome_service_url;
			String lis_outcome_service_format;
			boolean isUsingLisOutcomeService;
			List<String> memberIds = new ArrayList<String>();
			
    Group() {}
    
    Group(String instructorId, String description) {
    	this.created = new Date();
        this.instructorId = instructorId;
        this.description = description;
    }

    Group(String type,String context_id,String description) {
    	if ("BLTI".equals(type)) {
    		this.created = new Date();
    		this.context_id = context_id;
    		this.description = description;
    		this.instructorId = "unknown";
    	}
    }
    
    public boolean isMember(String id) {
    	return memberIds.contains(id);
    }
        
    boolean reviseScores(Assignment assignment) {
    	boolean changed = false;
    	for (String uId : this.memberIds) {
    		Score revised = Score.getInstance(uId, assignment);
    		try {
    			Score previous = ofy().load().key(Key.create(Key.create(User.class,uId),Score.class,assignment.id)).safe();
    			if (!revised.equals(previous)) {
    				ofy().save().entity(revised).now();
    				changed = true;
    			}
    		} catch (Exception e) {
    			ofy().save().entity(revised).now();
    		}
    	}
    	return changed;
    }

    void setUsingLisOutcomeService(String url) {
    	if (url==null) return;  // no useful information
    	if (this.isUsingLisOutcomeService && url.equals(this.lis_outcome_service_url)) return;  // no changes needed
    	this.isUsingLisOutcomeService = true;
    	this.lis_outcome_service_url = url;
    	ofy().save().entity(this);
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
    	try {
    		if (this.validatedMemberCount()==0) ofy().load().type(User.class).id(instructorId).safe();
    		return true;
    	} catch (Exception e) {
    		return false;
    	}
    }
}