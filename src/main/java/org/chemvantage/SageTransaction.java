/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2022 ChemVantage LLC
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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class SageTransaction implements Serializable {
	@Serial
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index	String userId; // hashedId
	@Index	Long assignmentId;
			Date created;
			Date graded;
			Long random = new Random().nextLong();
			boolean helped;
			List<Long> conceptIds;
			int[] scores;
			int[] possibleScores;
			Map<Key<Concept>,List<Long>> answeredQuestionIds = new HashMap<Key<Concept>,List<Long>>();
			
	SageTransaction() {}

	SageTransaction(String userId,long assignmentId,List<Long> conceptIds) {
		this.userId = userId;
		this.created = new Date();
		this.graded = null;
		this.assignmentId = assignmentId;
		this.conceptIds = conceptIds;
		scores = new int[conceptIds.size()];
		possibleScores = new int[conceptIds.size()];
		Arrays.fill(possibleScores, 100);
	}

	boolean update(int rawScore, Long conceptId) throws Exception {
		graded = new Date();
		int score = scores[conceptIds.indexOf(conceptId)];
		int oldQuintileRank = score/20 + 1;
		if (oldQuintileRank == 6) return false;
		int proposedScore = 0;  // range 0-100 percent
		/*
		 * Apply this scoring algorithm to update the user's Score on the current Concept:
		 * If the user got help from Sage:
		 *   prior score < 50 - add 5*rawScore (0, 5 or 10 points)
		 *   prior score > 50 - subtract 5*(2-rawScore)
		 * Otherwise, if the user got no help:
		 *   q = user’s quintile (1-5) based on current score
		 *   rawScore (0,1 or 2) - a 1 means partially or almost correct
		 *   n = (17-2q)/3 averaging constant - range 2-5 
		 *   Sn = (60c + nSn-1)/(n+1)  stars (100 max = 83.3% proficient)
		 *   If (c<2) the minimum quintile rank score is a floor for the user (0, 20, 40, 60, 80).
		 */
		if (helped) {
			proposedScore = score + 5*rawScore - (score<50?0:10);
		} else {
			int n = (17-2*oldQuintileRank)/3;
			proposedScore = (60*rawScore + n*score)/(n+1);
			if (proposedScore > 100) proposedScore = 100;
		}
		
		// Check for any changes in quintile rank and apply guardrails, if needed:
		int newQuintileRank = proposedScore/20 + 1;
		if (newQuintileRank < oldQuintileRank) proposedScore = (oldQuintileRank - 1)*20;  // minimum score
		
		scores[conceptIds.indexOf(conceptId)] = proposedScore;
		random =  new Random().nextLong();
		helped =  false;
		
		return newQuintileRank > oldQuintileRank;
	}
	
}