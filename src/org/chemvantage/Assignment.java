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
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Assignment implements java.lang.Cloneable {
	@Id 	Long id;
	@Index	String domain;
	@Index	String assignmentType;
	@Index	long topicId;
	@Index	String resourceLinkId;
	@Index 	Date created;
	public	long videoId;    // used only for video assignments
	public	Integer timeAllowed; // to complete assignment, in seconds default = 900 for Quiz, 3600 for PracticeExam
	public	String lis_outcome_service_url;
	@Index	public	String lti_ags_lineitem_url;
	public	String lti_nrps_context_memberships_url;
			List<Long> topicIds; // used for practice exams which have multiple topicIds
			List<String> resourceLinkIds = new ArrayList<String>();  // deprecated
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

	Assignment() {}

	Assignment(String consumer_key,String resourceLinkId,String lisOutcomeServiceUrl,boolean isLTIv1p1) {  // specific to Quiz and Homework assignments with a single topicId
		this.domain = consumer_key;
		this.resourceLinkId = resourceLinkId;
		this.lis_outcome_service_url = lisOutcomeServiceUrl;
		this.created = new Date();
	}

	Assignment(String platformDeploymentId,String resourceLinkId,String lti_nrps_context_memberships_url) {
		this.domain = platformDeploymentId;
		this.resourceLinkId = resourceLinkId;
		this.lti_nrps_context_memberships_url = lti_nrps_context_memberships_url;
		this.created = new Date();
	}
	
	Assignment(String assignmentType, long topicId, List<Long> topicIds, String platform_deployment_id) {
		this.assignmentType = assignmentType;
		this.topicId = topicId;
		this.topicIds = topicIds;
		this.domain = platform_deployment_id;
		this.questionKeys = ofy().load().type(Question.class).filter("assignmentType",this.assignmentType).filter("topicId",this.topicId).keys().list();
		this.created = new Date();
	}
	
	public long getTopicId() {
		return topicId;
	}
	
	public List<Key<Question>> getQuestionKeys() {
		return questionKeys==null?new ArrayList<Key<Question>>():this.questionKeys;
	}

	void updateQuestions(HttpServletRequest request) {
		try {
			String[] questionIds = request.getParameterValues("QuestionId");
			this.questionKeys.clear();
			if (questionIds != null) for (String id : questionIds) this.questionKeys.add(Key.create(Question.class,Long.parseLong(id)));
			ofy().save().entity(this).now();	
		} catch (Exception e) {}
	}

	boolean equivalentTo(Assignment a) {
		// This method compares another Assignment to this one and ensures that all fields are either the same or both null
		// except for primitive (long) fields, which may not be null and must be equal (possibly 0L).
		if (a==null) return false;
		
		return	((this.id != null && this.id.equals(a.id)) 																								|| (a.id == null && this.id == null)) &&
				((this.domain != null && this.domain.equals(a.domain)) 																					|| (a.domain == null && this.domain == null)) &&
				((this.assignmentType != null && this.assignmentType.equals(a.assignmentType)) 															|| (a.assignmentType == null && this.assignmentType == null)) &&
				(this.topicId == a.topicId) &&
				(this.videoId == a.videoId) &&
				((this.resourceLinkId != null && this.resourceLinkId.equals(a.resourceLinkId)) 															|| (a.resourceLinkId == null && this.resourceLinkId == null)) &&
				((this.lis_outcome_service_url != null && this.lis_outcome_service_url.equals(a.lis_outcome_service_url)) 								|| (a.lis_outcome_service_url == null && this.lis_outcome_service_url == null)) &&
				((this.lti_ags_lineitem_url != null && this.lti_ags_lineitem_url.equals(a.lti_ags_lineitem_url)) 										|| (a.lti_ags_lineitem_url == null && this.lti_ags_lineitem_url == null)) &&
				((this.lti_nrps_context_memberships_url != null && this.lti_nrps_context_memberships_url.equals(a.lti_nrps_context_memberships_url)) 	|| (a.lti_nrps_context_memberships_url == null && this.lti_nrps_context_memberships_url == null)) &&
				((this.topicIds != null && this.topicIds.equals(a.topicIds)) 																			|| (a.topicIds == null && this.topicIds == null)) &&
				((this.questionKeys != null && this.questionKeys.equals(a.questionKeys)) 																|| (a.questionKeys == null && this.questionKeys == null));				
	}
	
	boolean isValid() {
		// this method verifies that the Assignment has a valid assignmentType and appropriate topicId(s)
		if (this.assignmentType==null) return false;
		
		switch (this.assignmentType) {
		case "Quiz": return this.topicId>0L;
		case "Homework": return this.topicId>0L;
		case "PracticeExam": return topicIds.size()>2;
		case "VideoQuiz": return this.videoId>0;
		case "Poll": return true;
		default: return false;
		}
	}
	
	protected Assignment clone() throws CloneNotSupportedException {
		return (Assignment) super.clone();
	}

}