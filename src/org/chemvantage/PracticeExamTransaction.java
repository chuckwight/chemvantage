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
import java.util.Date;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Cache
public class PracticeExamTransaction implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index	String userId;
	@Index	Date downloaded;
	@Index	Date graded;
			List<Long> topicIds;
			int[] scores;
			int[] possibleScores;
			String lis_result_sourcedid;
			String IPNumber;
			List<Key<Response>> responseKeys;

	PracticeExamTransaction() {}

	PracticeExamTransaction(List<Long> topicIds,String userId,Date downloaded,Date graded,int[] scores,int[] possibleScores,String lis_result_sourcedid,String IPNumber) {
		this.topicIds = topicIds;
		this.userId = userId;
		this.downloaded = downloaded;
		this.graded = graded;
		this.scores = scores;
		this.possibleScores = possibleScores;
		this.lis_result_sourcedid = lis_result_sourcedid;
		this.IPNumber = IPNumber;
	}

	public List<Response> getResponses() {
		return (List<Response>) ofy().load().type(Response.class).ids(responseKeys).values();
	}

    boolean topicsMatch(List<Long> topicIds) {  // matches if both Lists have identical members but not necessarily in the same order
    	if (this.topicIds.size() != topicIds.size()) return false;
    	for (Long tId : this.topicIds) if (!topicIds.contains(tId)) return false;
    	return true;
    }

}