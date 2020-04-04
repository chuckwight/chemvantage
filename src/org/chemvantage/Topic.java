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
/*
 * This entity represents a topic, which is roughly equivalent to a textbook chapter.
 * The topicGroup attribute indicates if a topic is aligned with a particular text, i.
 * The topic is aligned if topicGroup%(i+1)/i==1. Thus, if OpenStax = 1 and LibreTexts = 2,
 * then topicGroup is interpreted as
 * 0 = no affiliation
 * 1 = OpenStax
 * 2 = LibreTexts
 * 3 = both
 */
import java.io.Serializable;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Topic implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String orderBy;
	@Index	int topicGroup;
	 		String title;
	
	Topic() {}

	Topic(String title,String orderBy,int topicGroup) {
		this.title = title;
		this.orderBy = orderBy;
		this.topicGroup = topicGroup;
	}
	
}