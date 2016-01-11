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

import com.google.appengine.api.datastore.QueryResultIterable;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.cmd.Query;

@Cache @Entity
public class Topic implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	String title;
	String orderBy;
	
	Topic() {}
	
	Topic(String title,String orderBy) {
		this.title = title;
		this.orderBy = orderBy;
	}

	public QueryResultIterable<Key<Question>> getQuestionKeys(String assignmentType) {
		return  ofy().load().type(Question.class).filter("topicId",this.id).filter("assignmentType",assignmentType).keys();
	}
	
	public Query<Question> getQuestions(String assignmentType) {
		return ofy().load().type(Question.class).filter("topicId", this.id).filter("assignmentType",assignmentType).order("pointValue");
	}
	
	public int getQuestionCount(String assignmentType) {
		return this.getQuestions(assignmentType).count();
	}
	
	public Question getQuestion(Key<Question> key) {
		return ofy().load().key(key).now();
	}

	public Question getQuestion(long id) {
		return ofy().load().type(Question.class).id(id).now();
	}
}