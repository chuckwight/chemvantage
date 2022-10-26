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
import java.util.Iterator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Group {
	@Id 	String id;       // lti_ags_lineitems_url
	@Index	Date valid;
			String organization;
			String lms_type;
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
	
	static void update(Deployment d, Assignment a) {
		String group_id = a.lti_ags_lineitems_url;
		Group g = getInstance(group_id);
		g.valid = new Date();
		g.organization = d.organization;
		g.lms_type = d.lms_type;
		g.label = "undisclosed";
		g.title = "undisclosed";
		// use the NPRS MembershipContainer to complete the remaining Group fields
		JsonObject membershipContainer = LTIMessage.getMembershipContainer(a);
		try {  // although the "context" object and id are required, label and title are optional
			JsonElement tmp = membershipContainer.get("context").getAsJsonObject().get("label");
			if (tmp != null) g.label = tmp.getAsString(); else g.label = "";
			tmp = membershipContainer.get("context").getAsJsonObject().get("title");
			if (tmp != null) g.title = tmp.getAsString(); else g.title = "";
		} catch (Exception e) {}

		g.nLearners = 0;
		g.nInstructors = 0;
		g.nAdministrators = 0;
		try {
			JsonArray members = membershipContainer.get("members").getAsJsonArray();
			Iterator<JsonElement> iterator = members.iterator();
			while(iterator.hasNext()){
				JsonObject member = iterator.next().getAsJsonObject();
				String roles = member.get("roles").getAsJsonArray().toString().toLowerCase();
				String role = roles.contains("administrator")?"Administrator":roles.contains("instructor")?"Instructor":"Learner";			
				switch (role) {
				case "Administrator": g.nAdministrators++; break;
				case "Instructor": g.nInstructors++; break;
				default: g.nLearners++;
				}		
			}
		} catch (Exception e) {}
		ofy().save().entity(g);
	}
	
	static String enrollmentReport() {
		StringBuffer buf = new StringBuffer("<h3>Active Group Enrollments (past 30 days)</h3>");
		try {
			Date now = new Date();
			Date oneMonthAgo = new Date(now.getTime()-2592000000L);  // 30 days ago
			List<Group> activeGroups = ofy().load().type(Group.class).filter("valid >",oneMonthAgo).list();
			int nLearners = 0;
			int nInstructors = 0;
			int nAdministrators = 0;
			if (activeGroups.size() > 0) {
				buf.append("<table><tr><th>Organization</th><th>LMS</th><th>Course Label</th><th>Title</th><th>Learners</th><th>Instructors</th><th>Administrators</th></tr>");
				for (Group g : activeGroups) {
					buf.append("<tr style='text-align: center;'><td>" + g.organization + "</td><td>" + g.lms_type + "</td><td>" + g.label + "</td><td>" + g.title + "</td>"
							+ "<td>" + g.nLearners + "</td><td>" + g.nInstructors + "</td><td>" + g.nAdministrators + "</td></tr>");
					nLearners += g.nLearners;
					nInstructors += g.nInstructors;
					nAdministrators += g.nAdministrators;
				}
				buf.append("<tr style='text-align: center;'><td></td><td></td><td></td><td></td><td style='border: 1px solid;'>" + nLearners + "</td><td style='border: 1px solid;'>" + nInstructors + "</td><td style='border: 1px solid;'>" + nAdministrators + "</td></tr>");
				buf.append("</table><br/>");
			} else buf.append("There are no active groups.</br>");
		} catch (Exception e) {
			buf.append("Report failed: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return buf.toString();
	}
}
	
