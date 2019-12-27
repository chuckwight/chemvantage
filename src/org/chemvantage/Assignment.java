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
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Assignment {
	@Id 	Long id;
	@Index 	long groupId;
	@Index	String domain;
	@Index	String assignmentType;
	@Index	long topicId;
	@Index	String resourceLinkId;
			String lti_ags_lineitem_url;
			List<Long> topicIds; // used for practice exams which have multiple topicIds
			List<String> resourceLinkIds = new ArrayList<String>();  // deprecated
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

	Assignment() {}

	Assignment(long groupId,String platformDeploymentId,String resourceLinkId) {  // specific to Quiz and Homework assignments with a single topicId
		this.groupId = groupId;
		this.domain = platformDeploymentId;
		this.resourceLinkId = resourceLinkId;
	}

	Assignment(String assignmentType, long topicId, List<Long> topicIds, String platform_deployment_id, long groupId) {
		this.assignmentType = assignmentType;
		this.topicId = topicId;
		this.topicIds = topicIds;
		this.domain = platform_deployment_id;
		this.groupId = groupId;
		this.questionKeys = ofy().load().type(Question.class).filter("assignmentType",this.assignmentType).filter("topicId",this.topicId).keys().list();
	}
}