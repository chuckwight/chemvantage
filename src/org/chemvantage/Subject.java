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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.cmd.Query;

@Entity
public class Subject {
	@Id Long id;
	String title;
	String HMAC256Secret;
	int nStarReports;
	double avgStars;
	
	Subject() {}
	
	static public Subject getSubject() {
		try {
			ObjectifyService.begin();
			return ofy().load().type(Subject.class).first().safe();	
		} catch (Exception e) { // this should be run only once at setup
			Subject s = new Subject();
			s.id = 1L;
			s.title = "General Chemistry";
			s.HMAC256Secret = "ChangeMeInTheDataStoreManuallyForYourProtection";
			ofy().save().entity(s).now();
			return s;
		}
	}

	public String getTopicSelectBox() {
		return getTopicSelectBox(0);
	}
	
	public String getTopicSelectBox(long id) {
		StringBuffer buf = new StringBuffer();
		Query<Topic> topics = ofy().load().type(Topic.class);
		buf.append("Topic: <SELECT NAME=TopicId>");
		if (id == 0) buf.append("<OPTION VALUE=0>Select a topic:</OPTION>");
		for (Topic t : topics) {
			buf.append("<OPTION " + ((t.id == id)?"SELECTED ":"")
					+ "VALUE='" + t.id + "'>" + t.title + "</OPTION>");
		}
		buf.append("</SELECT><br>");
		return buf.toString();
	}

	public List<Long> getVideos() {
		List<Long> videoIds = new ArrayList<Long>();
		List<Key<Video>> videoKeys = ofy().load().type(Video.class).keys().list();
		for (Key<Video> k : videoKeys) videoIds.add(k.getId());
		return videoIds;
	}

	public List<Long> getTextIds() {
		List<Long> textIds = new ArrayList<Long>();
		List<Key<Text>> textKeys = ofy().load().type(Text.class).keys().list();
		for (Key<Text> k : textKeys) textIds.add(k.getId());
		return textIds;
	}

	public List<Long> getTopicIds() {
		List<Long> topicIds = new ArrayList<Long>();
		List<Key<Topic>> topicKeys = ofy().load().type(Topic.class).order("orderBy").keys().list();
		for (Key<Topic> k : topicKeys) topicIds.add(k.getId());
		return topicIds;
	}

	public void addStarReport(int stars) {
		avgStars = (avgStars*nStarReports + stars)/(nStarReports+1);
		nStarReports++;
		ofy().save().entity(this);
	}

	public double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(avgStars));
	}
}
