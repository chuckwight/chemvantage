package org.chemvantage;

import java.io.Serializable;

import javax.persistence.Id;

import com.google.appengine.api.datastore.QueryResultIterable;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Cached;

@Cached
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
		Objectify ofy = ObjectifyService.begin();
		return  ofy.query(Question.class).filter("topicId",this.id).filter("assignmentType",assignmentType).fetchKeys();
	}
	
	public Query<Question> getQuestions(String assignmentType) {
		Objectify ofy = ObjectifyService.begin();
		return ofy.query(Question.class).filter("topicId", this.id).filter("assignmentType",assignmentType);
	}
	
	public int getQuestionCount(String assignmentType) {
		return this.getQuestions(assignmentType).count();
	}
	
	public Question getQuestion(Key<Question> key) {
		Objectify ofy = ObjectifyService.begin();
		return ofy.get(key);
	}

	public Question getQuestion(long id) {
		Objectify ofy = ObjectifyService.begin();
		return ofy.get(Question.class,id);
	}
}