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
/*
 * This entity represents a key concept, which is roughly equivalent to a section of a textbook chapter.
 * Typically, we can expect 4-8 Concepts per Topic.
 * Topics have an optional field consisting of a List of conceptId values
 * Questions have an optional field of one conceptId value, so they can be filtered in a query.
 */
import java.io.Serializable;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Concept implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String orderBy;
	 		String title;
	
	Concept() {}

	Concept(String title,String orderBy) {
		this.title = title;
		this.orderBy = orderBy;
	}
}