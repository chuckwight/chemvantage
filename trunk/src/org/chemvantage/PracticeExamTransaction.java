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