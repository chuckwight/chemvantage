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

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Group {
	@Id 	String id;       // platform_deployment_id + "/" + context_id
	@Index	Date valid;
			String organization;
			String label;
			String title;
			int nAdministrators;
			int nInstructors;
			int nLearners;
			
	Group() {}
	
	Group(String id) {
		this.id = id;
	}
	
	static Group getInstance(String id) {
		Group g = null;
		try {
			g = ofy().load().type(Group.class).id(id).safe();
		} catch (Exception e) {
			g = new Group(id);
		}
		return g;
	}
	
	static void update(Deployment d, JsonObject claims, Assignment a) {
		try {
			JsonObject context = claims.get("https://purl.imsglobal.org/spec/lti/claim/context").getAsJsonObject();
			String group_id = d.platform_deployment_id + "/" + context.get("id").getAsString();
			Group g = getInstance(group_id);
			g.valid = new Date();
			g.organization = d.organization;
			JsonElement label = context.get("label");
			g.label = label==null?"":label.getAsString();
			JsonElement title = context.get("title");
			g.title = title==null?"":title.getAsString();
			Map<String,String[]> members = LTIMessage.getMembership(a);
			if (members != null) {
				g.nLearners = 0;
				g.nInstructors = 0;
				g.nAdministrators = 0;
				for (Map.Entry<String,String[]> entry : members.entrySet()) {
					switch (entry.getValue()[0]) {
					case "Administrator": g.nAdministrators++; break;
					case "Instructor": g.nInstructors++; break;
					default: g.nLearners++;
					}		
				}
			}
			ofy().save().entity(g);
		} catch (Exception e) {}
	}
	
	static String enrollmentReport() {
		StringBuffer buf = new StringBuffer("<h3>Active Group Enrollments (past 30 days)</h3>");
		try {
			Date now = new Date();
			Date oneMonthAgo = new Date(now.getTime()-2592000000L);  // 30 days ago
			List<Group> activeGroups = ofy().load().type(Group.class).filter("valid >",oneMonthAgo).list();
			if (activeGroups.size() > 0) {
				buf.append("<table><tr><th>Organization</th><th>Course Label</th><th>Course Title</th><th>Learners</th><th>Instructors</th><th>Administrators</th></tr>");
				for (Group g : activeGroups) {
					buf.append("<tr style='text-align: center;'><td>" + g.organization + "</td><td>" + g.label + "</td><td>" + g.title + "</td><td>" 
							+ g.nLearners + "</td><td>" + g.nInstructors + "</td><td>" + g.nAdministrators + "</td></tr>");
				}
				buf.append("</table><br/>");
			} else buf.append("There are no active groups.</br>");
		} catch (Exception e) {
			buf.append("Report failed: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}
}
	
