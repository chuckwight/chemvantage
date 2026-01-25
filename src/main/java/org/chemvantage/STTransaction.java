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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class STTransaction implements Serializable {
	@Serial
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index	String userId;
	@Index	Date created;
	@Index	Date graded;
	@Index	Long assignmentId;
			boolean armed = false;  // signifies that a question has been posed; discourages using backspace/corrected answer
			List<Long> conceptIds;
			int[] scores;
			int[] possibleScores;
			int[] missedQuestions;
			List<Key<Question>> answeredKeys = new ArrayList<Key<Question>>();
			Map<Key<Question>,Integer> questionScores = new HashMap<Key<Question>,Integer>();
			Map<Key<Question>,String> studentAnswers = new HashMap<Key<Question>,String>();
			Map<Key<Question>,String> correctAnswers = new HashMap<Key<Question>,String>();
			
	STTransaction() {}

	STTransaction(String userId, Assignment a) {
		this.userId = userId;
		this.created = new Date();
		this.graded = null;
		this.assignmentId = a.id;
		this.conceptIds = a.conceptIds;
		scores = new int[conceptIds.size()];
		possibleScores = new int[conceptIds.size()];
		Arrays.fill(possibleScores, 2);
		missedQuestions = new int[conceptIds.size()];
	}

}