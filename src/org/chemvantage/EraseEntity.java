package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

public class EraseEntity extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
    	response.getWriter().println(Home.header + menu() + Home.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		out.println(Home.header);
		
		try {
			String entityType = request.getParameter("EntityType");
			String id = request.getParameter("Id");
			assert(entityType!=null && !entityType.isEmpty());
			assert(id!=null && !id.isEmpty());
			
			switch(entityType) {
				case("User"): deleteUser(id); break;
				case("Group"): deleteGroup(id); break;
				case("Domain"): deleteDomain(id); break;
				default: throw new Exception();
			}
			out.println("Done.");
		} catch (Exception e) {
			out.println("You must select an entity type and provide an id.");
		}
		out.println(Home.footer);
	}

	String menu() {
		StringBuffer buf = new StringBuffer("<h3>Database Utility for Permanent Erasure</h3>");
		buf.append("The ChemVantage administrator can use this utility to permanently delete the following from the datastore:"
				+ "<ul>"
				+ "<li>An individual user, along with all quiz, homework and practice exam transactions and Score objects"
				+ "<li>A group of users, including all associated users and assignments"
				+ "<li>A domain, includig all associated groups, users and BLTI credentials"
				+ "</ul>");
		buf.append("<font color=red>Warning: This action cannot be undone.</font><p>");
		buf.append("Select the type of entity to be deleted and the associated, userId, groupId or consumer_key:<br>"
				+ "<form method=post>"
				+ "<select name=EntityType>"
				+ "<option value='' SELECTED>Select an etity type</option>"
				+ "<option value='User'>User (including transactions and scores)</option>"
				+ "<option value='Group'>Group (including users and assignments)</option>"
				+ "<option value='Domain'>Domain (including groups and BLTI credentials)</option>"
				+ "</select> "
				+ "ID (case-sensitive): <input name=Id type=text size=12>"
				+ "<input type=submit name=UserRequest value='Delete Entity'>"
				+ "</form>");
		return buf.toString();
	}
	
	void deleteUser(String id) {
		try {
			User user = ofy().load().type(User.class).id(id).safe();
			
			user.changeGroups(0);
			
			List<Key<QuizTransaction>> qkeys = ofy().load().type(QuizTransaction.class).filter("userId",user.id).keys().list();
			if (!qkeys.isEmpty()) ofy().delete().keys(qkeys).now();
			
			List<Key<HWTransaction>> hkeys = ofy().load().type(HWTransaction.class).filter("userId",user.id).keys().list();
			if (!hkeys.isEmpty()) ofy().delete().keys(hkeys).now();
			
			List<Key<PracticeExamTransaction>> pkeys = ofy().load().type(PracticeExamTransaction.class).filter("userId",user.id).keys().list();
			if (!pkeys.isEmpty()) ofy().delete().keys(pkeys).now();
			
			List<Key<Score>> skeys = ofy().load().type(Score.class).ancestor(user).keys().list();
			if (!skeys.isEmpty()) ofy().delete().keys(skeys).now();
			
			ofy().delete().entity(user).now();
		} catch(Exception e) {			
		}
	}
		
	void deleteGroup(String id) {
		deleteGroup(Long.parseLong(id));
	}
	
	void deleteGroup(long id) {
		try {
			Group group = ofy().load().key(Key.create(Group.class,id)).safe();
		 	
			List<Key<Score>> scoreKeys = ofy().load().type(Score.class).filter("groupId",group.id).keys().list();
		 	if (!scoreKeys.isEmpty()) ofy().delete().keys(scoreKeys).now();
		 	
		 	List<Key<Assignment>> assignmentKeys = ofy().load().type(Assignment.class).filter("groupId",group.id).keys().list();
	    	if (!assignmentKeys.isEmpty()) ofy().delete().keys(assignmentKeys).now();	   
			
	    	List<Key<User>> userKeys = new ArrayList<Key<User>>();
			for (String uId : group.memberIds) userKeys.add(Key.create(User.class,uId));
			if (!userKeys.isEmpty()) ofy().delete().keys(userKeys).now();
			
			for (Key<User> k : userKeys) deleteUser(k.getName());
			
			ofy().delete().entity(group).now();
		} catch(Exception e) {			
		}
	}
	
	void deleteDomain(String id) {
		try {
			Domain domain = ofy().load().type(Domain.class).filter("domainName",id).first().safe();
			
			List<Key<Group>> groupKeys = ofy().load().type(Group.class).filter("domain",domain.domainName).keys().list();
			for (Key<Group> k : groupKeys) deleteGroup(k.getId());
			
		 	ofy().delete().entity(domain).now();
		} catch(Exception e) {		
		}
	}
}
