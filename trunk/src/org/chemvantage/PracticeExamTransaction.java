package org.chemvantage;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Cached;

@Cached
public class PracticeExamTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id Long id;
	long[] topicIds;
	String userId;
	Date downloaded;
	Date graded;
	int[] scores;
	int[] possibleScores;
	String IPNumber;
	List<Key<Response>> responseKeys;

	PracticeExamTransaction() {}

	PracticeExamTransaction(long[] topicIds,String userId,Date downloaded,Date graded,int[] scores,int[] possibleScores,String IPNumber) {
		this.topicIds = topicIds;
		this.userId = userId;
		this.downloaded = downloaded;
		this.graded = graded;
		this.scores = scores;
		this.possibleScores = possibleScores;
		this.IPNumber = IPNumber;
	}

	public List<Response> getResponses() {
		Objectify ofy = ObjectifyService.begin();
		return (List<Response>) ofy.get(Response.class,responseKeys).values();
	}

}