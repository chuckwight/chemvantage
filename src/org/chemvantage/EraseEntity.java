package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

/**
 * Servlet implementation class EraseEntity
 */
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
			boolean success = false;
			
			switch(entityType) {
				case("User"): success = deleteUser(id); break;
				case("Group"): success = deleteGroup(id); break;
				case("Domain"): success = deleteDomain(id); break;
				default: throw new Exception();
			}
			out.println(success?"The entity was successfully deleted from the database.":"Operation failed, most likely because the entity was not found.");
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
	
	boolean deleteUser(String id) {
		try {
			User user = ofy().load().type(User.class).id(id).safe();
			user.changeGroups(0);
			List<Key<QuizTransaction>> qkeys = ofy().load().type(QuizTransaction.class).filter("userId",user.id).keys().list();
			ofy().delete().keys(qkeys);
			List<Key<HWTransaction>> hkeys = ofy().load().type(HWTransaction.class).filter("userId",user.id).keys().list();
			ofy().delete().keys(hkeys);
			List<Key<PracticeExamTransaction>> pkeys = ofy().load().type(PracticeExamTransaction.class).filter("userId",user.id).keys().list();
			ofy().delete().keys(pkeys);
			List<Key<Score>> skeys = ofy().load().type(Score.class).ancestor(user).keys().list();
			ofy().delete().keys(skeys);
			ofy().delete().entity(user);
			return true;
		} catch(Exception e) {			
		}
		return false;
	}

	boolean deleteGroup(String id) {
		try {
			long groupId = Long.parseLong(id);
			return deleteGroup(groupId);
		} catch (Exception e) {
			return false;
		}
	}
	
	boolean deleteGroup(long id) {
		try {
			Group group = ofy().load().type(Group.class).id(id).safe();
			List<Key<Assignment>> akeys = ofy().load().type(Assignment.class).filter("groupId",group.id).keys().list();
			ofy().delete().keys(akeys);
			for (String userId : group.memberIds) deleteUser(userId);
			ofy().delete().entity(group);
			return true;
		} catch(Exception e) {			
		}
		return false;
	}
	
	boolean deleteDomain(String id) {
		try {
			Domain domain = ofy().load().type(Domain.class).filter("domainName",id).first().safe();
			BLTIConsumer cons = ofy().load().type(BLTIConsumer.class).id(domain.domainName).safe();
			List<Key<Group>> keys = ofy().load().type(Group.class).filter("domain",domain.id).keys().list();
			for (Key<Group> k : keys) deleteGroup(k.getId());
			ofy().delete().entity(domain);
			ofy().delete().entity(cons);
		} catch(Exception e) {
			
		}
		return false;
	}
}
