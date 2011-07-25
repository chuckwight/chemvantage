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

import java.text.DecimalFormat;

import javax.persistence.Id;

import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;

public class Subject {
	@Id Long id;
	String title;
	int nStarReports;
	double avgStars;
	
	Subject() {}
	
	Subject(String title) {
		this.title = title;
	}

	public String getTopicSelectBox() {
		return getTopicSelectBox(0);
	}
	
	public String getTopicSelectBox(long id) {
		StringBuffer buf = new StringBuffer();
		Query<Topic> topics = ObjectifyService.begin().query(Topic.class);
		buf.append("Topic: <SELECT NAME=TopicId>");
		if (id == 0) buf.append("<OPTION VALUE=0>Select a topic:</OPTION>");
		for (Topic t : topics) {
			buf.append("<OPTION " + ((t.id == id)?"SELECTED ":"")
					+ "VALUE='" + t.id + "'>" + t.title + "</OPTION>");
		}
		buf.append("</SELECT><br>");
		return buf.toString();
	}

	public void addStarReport(int stars) {
		avgStars = (avgStars*nStarReports + stars)/(nStarReports+1);
		nStarReports++;
		ObjectifyService.begin().put(this);
	}

	public double getAvgStars() {
		DecimalFormat df2 = new DecimalFormat("#.#");
		return Double.valueOf(df2.format(avgStars));
	}
}
