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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Assignment {
	@Id 	Long id;
	@Index 	long groupId;
	@Index	String domain;
	@Index	String assignmentType;
	@Index	long topicId;
	@Index	String resourceLinkId;
			List<Long> topicIds; // used for practice exams which have multiple topicIds
			List<String> resourceLinkIds = new ArrayList<String>();
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();

    Assignment() {}

   Assignment(long groupId,String domain,String resourceLinkId,long topicId,String assignmentType) {  // specific to Quiz and Homework assignments with a single topicId
	   	this.groupId = groupId;
   		this.domain = domain;
    	this.resourceLinkId = resourceLinkId;
    	this.topicId = topicId;
    	this.assignmentType = assignmentType;
    	questionKeys = ofy().load().type(Question.class).filter("assignmentType",assignmentType).filter("topicId",topicId).keys().list();
    }
    
    Assignment(long groupId,String domain,String resourceLinkId, List<Long> topicIds,String assignmentType) {   // specific to Practice Exam assignments with multiple topicIds
    	this.groupId = groupId;
    	this.domain = domain;
    	this.resourceLinkId = resourceLinkId;
    	this.topicIds = topicIds;
    	this.assignmentType = assignmentType;
    	for (Long topicId : topicIds) questionKeys.addAll(ofy().load().type(Question.class).filter("assignmentType","Exam").filter("topicId",topicId).keys().list());
    }

    public void setResourceLinkId (String r) {
    	this.resourceLinkId = r;
    	if (resourceLinkIds.contains(r)) resourceLinkIds.remove(r);
    }
    
    public String getresourceLinkId() {
    	return this.resourceLinkId;
    }
    
	public String showGroupScores(User user,Group group) {
		long LIMIT_MILLIS = 1000 * 25; // response time limit in milliseconds
		StringBuffer buf = new StringBuffer();

		try {
			long startTime = System.currentTimeMillis();
			buf.append("<h2>" + assignmentType + " Scores</h2>");

			DateFormat df = DateFormat.getDateTimeInstance();
			Date now = new Date();
			buf.append(df.format(now) + "<br>");

			buf.append("Group: " + group.description);

			// Make a table of assignments for this group

			List<Assignment> assignments = ofy().load().type(Assignment.class).filter("groupId",group.id).filter("assignmentType",assignmentType).order("deadline").list();
			if (assignments.size()==0) buf.append("<p>There are currently no " + assignmentType + " assignments for this group.");
			else {
				buf.append("<p><b>" + assignmentType + " Assignments for this Group</b><br>");			
				int counter = 1;
				df = DateFormat.getDateInstance(DateFormat.SHORT);
				buf.append("<TABLE BORDER=1 CELLSPACING=0><TR><TH>#</TH><TH ALIGN=LEFT>Topic</TH></TR>");

				for (Assignment a : assignments) {
					buf.append("<TR><TD><b>" + (assignmentType.equals("Quiz")?"Q":"HW") + counter + "</b></TD>"
							+ "<TD>" + ofy().load().type(Topic.class).id(a.topicId).safe().title + "</TD></TR>");
					counter++;
				}
				buf.append("</TABLE>");
			}
			
			// Make a table of user scores for the assignments (1 row per user; 1 column per assignment)
			List<User> groupMembers = new ArrayList<User>(ofy().load().type(User.class).ids(group.memberIds).values());
			List<User> groupInstructors = new ArrayList<User>();

			for (User u: groupMembers) if (u.isInstructor() || u.isAdministrator() || u.isTeachingAssistant()) groupInstructors.add(u);
			groupMembers.removeAll(groupInstructors);
			
			if (groupInstructors.size()>0) {  // print a short table of instructor scores
				Set<Key<Score>> keys = new HashSet<Key<Score>>();
				for (User u : groupInstructors) for (Assignment a : assignments) keys.add(Key.create(Key.create(User.class,u.id),Score.class,a.id));
				Map<Key<Score>,Score> scoresMap = ofy().load().keys((keys));

				buf.append("<p><b>Instructor Scores</b><br>");
				buf.append("<TABLE BORDER=1 CELLSPACING=0><TR ALIGN=LEFT><TH>#</TH><TH>Name</TH>");

				for (int k=1;k<=assignments.size();k++) { // add a header for each assigned quiz or homework
					buf.append("<TH>" + (assignmentType.equals("Quiz")?"Q":"HW") + k + "</TH>");
				}
				buf.append("<TH>Total</TH></TR>"); // end of header row for scores table

				int i = 0;
				int[] sumScores = new int[assignments.size()];
				int[] nScores = new int[assignments.size()];
				int[] nAttempts = new int[assignments.size()];
				
				for (User u : groupInstructors) {
					if (u.myGroupId != group.id) {  // user has been removed from this group; skip this entry
						group.memberIds.remove(u.id);
						ofy().save().entity(group);
						continue;
					}
					i++;
					String name = u.getId();
					buf.append("<TR><TD>" + i + ".</TD><TD>" + name + "</TD>");
					int j = 0;
					int studentTotalScore = 0;
					for (Assignment a : assignments) {
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,a.id);
						Score s = scoresMap.get(k);
						if (s==null) s = u.getScore(a);
						sumScores[j] += s.score;
						studentTotalScore += s.score;
						if (s.numberOfAttempts>0) {
							nScores[j]++;
							nAttempts[j] += s.numberOfAttempts;
						}
						j++;
						buf.append("<TD ALIGN=CENTER>" + s.getScore() + "</TD>");
					}
					buf.append("<TD ALIGN=CENTER>" + studentTotalScore + "</TD></TR>");
				}
				buf.append("</TABLE><p>");
			}

			if (groupMembers.size()>0) {  // print a table of students scores
				Set<Key<Score>> keys = new HashSet<Key<Score>>();
				for (User u : groupMembers) for (Assignment a : assignments) keys.add(Key.create(Key.create(User.class,u.id),Score.class,a.id));
				Map<Key<Score>,Score> scoresMap = ofy().load().keys(keys);
				
				buf.append("<p><b>Student Scores</b><br>");
				buf.append("<TABLE BORDER=1 CELLSPACING=0><TR ALIGN=LEFT><TH>#</TH><TH>Name</TH>");

				for (int k=1;k<=assignments.size();k++) { // add a header for each assigned quiz or homework
					buf.append("<TH>" + (assignmentType.equals("Quiz")?"Q":"HW") + k + "</TH>");
				}
				buf.append("<TH>Total</TH></TR>"); // end of header row for scores table

				int i = 0;
				int[] sumScores = new int[assignments.size()];
				int[] nScores = new int[assignments.size()];
				int[] nAttempts = new int[assignments.size()];
				boolean partialTable = false;

				for (User u : groupMembers) {
					if (u.myGroupId != group.id) {  // user has been removed from this group; skip this entry
						group.memberIds.remove(u.id);
						ofy().save().entity(group);
						continue;
					}
					i++;
					String name = u.getId();
					buf.append("<TR><TD>" + i + ".</TD><TD>" + name + "</TD>");
					int j = 0;

					int studentTotalScore = 0;
					for (Assignment a : assignments) {
						Key<Score> k = Key.create(Key.create(User.class,u.id),Score.class,a.id);
						Score s = scoresMap.get(k);
						if (s==null) s = u.getScore(a);
						sumScores[j] += s.score;
						studentTotalScore += s.score;
						if (s.numberOfAttempts>0) {
							nScores[j]++;
							nAttempts[j] += s.numberOfAttempts;
						}
						j++;
						buf.append("<TD ALIGN=CENTER>" + s.getScore() + "</TD>");
					}
					buf.append("<TD ALIGN=CENTER>" + studentTotalScore + "</TD></TR>");

					if (System.currentTimeMillis()-startTime > LIMIT_MILLIS) {
						partialTable = true;
						break; 
					}

				}

				if (partialTable) {  // print a warning message
					buf.append("<TR><TD COLSPAN=" + (assignments.size() + 2) + ">"
							+ "Due to a timeout limitation of the system, this is only a partial list of scores.<br>" 
							+ "Please refresh this page to resume calculating the scores.</TD></TR>");
				} else {
					// print a row of average scores for the assignments
					buf.append("<TR><TD COLSPAN=2>Average Score (Attempts)</TD>");
					double totalPercentCorrect = 0;
					double totalSubmissions = 0;
					int numberOfAssignments = 0;
					for (Assignment a : assignments) {
						try {
							int j = assignments.indexOf(a);
							int maxScore = "Quiz".equals(a.assignmentType)?10:a.questionKeys.size();
							double percentCorrect = Math.round(1000*sumScores[j]/nScores[j]/maxScore)/10.0;
							totalPercentCorrect += percentCorrect;
							double avgSubmissions = Math.round(10*nAttempts[j]/nScores[j]/("Homework".equals(a.assignmentType)?maxScore:1))/10.0;
							totalSubmissions += avgSubmissions;
							if (nScores[j] > 0) buf.append("<TD ALIGN=CENTER>" + percentCorrect  + "% (" + avgSubmissions + ")</TD>");
							else buf.append("<TD>&nbsp;</TD>");
							numberOfAssignments++;
						} catch (Exception e) {
							buf.append("<TD>&nbsp;</TD>");
						}
					}
					if (numberOfAssignments>0) buf.append("<TD>" + Math.round(10*totalPercentCorrect/numberOfAssignments)/10. + "% (" + Math.round(10*totalSubmissions/numberOfAssignments)/10. + ")</TD></TR>");
					else buf.append("<TD></TD></TR>");
				}
				buf.append("</TABLE>");
			} else buf.append("<p>There are currently no students enrolled in this group.");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
    
/*    
    public boolean matches(String assignmentType,List<Long> topicIds) { // this method applies only to PracticeExam assignments
    	if ("PracticeExam".equals(this.assignmentType) && "PracticeExam".equals(assignmentType) && this.topicIds!=null && this.topicIds.equals(topicIds)) return true;
    	else return false;
    }
    
    public boolean matches(String assignmentType,long topicId) {
    	if (!("Quiz".equals(assignmentType) || "Homework".equals(assignmentType))) return false; // this method only applies to Quiz or Homework assignments 
    	return (assignmentType.equals(this.assignmentType) && this.topicId==topicId);
    }
    */
}