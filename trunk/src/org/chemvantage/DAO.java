package org.chemvantage;

import java.util.ArrayList;
import java.util.List;

import com.google.appengine.api.datastore.QueryResultIterable;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;

public class DAO extends com.googlecode.objectify.util.DAOBase {
	static { 
		ObjectifyService.register(Subject.class);
		ObjectifyService.register(User.class);
		ObjectifyService.register(Video.class);
		ObjectifyService.register(Text.class);
		ObjectifyService.register(Topic.class);
		ObjectifyService.register(Question.class);
		ObjectifyService.register(Group.class);
		ObjectifyService.register(Assignment.class);
		ObjectifyService.register(Response.class);
		ObjectifyService.register(UserReport.class);
		ObjectifyService.register(QuizTransaction.class);
		ObjectifyService.register(HWTransaction.class);
		ObjectifyService.register(Score.class);
		ObjectifyService.register(PayPalIPN.class);
		ObjectifyService.register(PracticeExamTransaction.class);
		ObjectifyService.register(VideoTransaction.class);
		ObjectifyService.register(BLTIConsumer.class);
	}
	
	public Subject getSubject() {
		Subject genChem = ofy().query(Subject.class).get();
		if (genChem==null) {
			genChem = new Subject("General Chemistry");
			ofy().put(genChem);
		}
		return genChem;
	}
	
	public List<Long> getVideos() {
		List<Long> videoIds = new ArrayList<Long>();
		QueryResultIterable<Key<Video>> videoKeys = ofy().query(Video.class).fetchKeys();
		for (Key<Video> k : videoKeys) videoIds.add(k.getId());
		return videoIds;
	}

	public List<Long> getTextIds() {
		List<Long> textIds = new ArrayList<Long>();
		QueryResultIterable<Key<Text>> textKeys = ofy().query(Text.class).fetchKeys();
		for (Key<Text> k : textKeys) textIds.add(k.getId());
		return textIds;
	}

	public List<Long> getTopicIds() {
		List<Long> topicIds = new ArrayList<Long>();
		QueryResultIterable<Key<Topic>> topicKeys = ofy().query(Topic.class).order("orderBy").fetchKeys();
		for (Key<Topic> k : topicKeys) topicIds.add(k.getId());
		return topicIds;
	}
}