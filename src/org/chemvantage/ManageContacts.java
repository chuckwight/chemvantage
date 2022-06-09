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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Key;

@WebServlet("/contacts")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class ManageContacts extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    public ManageContacts() {
        super();
    }
   
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		UserService userService = UserServiceFactory.getUserService();
		String userId = userService.getCurrentUser().getUserId();
		User user = new User("https://"+request.getServerName(), userId);
		user.setIsChemVantageAdmin(true);
		user.setToken();
		
		StringBuffer buf = new StringBuffer("<h2>Manage Contacts</h2>");
		int nContacts = ofy().load().type(Contact.class).count();
		int nUnsubscribed = ofy().load().type(Contact.class).filter("unsubscribed",true).count();
		
		buf.append("There are " + nContacts + " contacts in the database, " + nUnsubscribed + " of whom are unsubscribed.");

		boolean clean = Boolean.parseBoolean(request.getParameter("Clean"));
		if (clean) {
			buf.append(cleanContacts());
		}
		
		buf.append(searchContacts());
		
		String[] email = request.getParameterValues("Email");
		if (email!=null) {
			for (int i=0;i<email.length;i++) {
				try {
					buf.append(editExistingContact(ofy().load().type(Contact.class).id(email[i]).safe()));
				} catch (Exception e) {}
				email[0] = null;
			}
		}
		
		buf.append(addNewContact());
		buf.append(pasteNewContact());
			
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		out.println(Subject.getHeader(user) + buf.toString() + Subject.footer);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		UserService userService = UserServiceFactory.getUserService();
		String userId = userService.getCurrentUser().getUserId();
		User user = new User("https://"+request.getServerName(), userId);
		user.setIsChemVantageAdmin(true);
		user.setToken();
		
		String userRequest = request.getParameter("UserRequest");
		
		try {
			switch (userRequest) {
			case "Add New Contact":
				addNewContact(request);
				response.sendRedirect("/contacts?Email=" + request.getParameter("Email"));
				break;
			case "Paste New Contact":
				String email = pasteNewContact(request);
				response.sendRedirect("/contacts?Email=" + email);
			case "Save Revised Contact":
				saveRevisedContact(request);
				response.sendRedirect("/contacts?Email=" + request.getParameter("Email"));
				break;
			case "Delete This Contact":
				deleteContact(request);
				response.sendRedirect("/contacts?Email=" + request.getParameter("Email"));
				break;
			default: throw new Exception("Bad User request.");
			}
		} catch (Exception e) {
			response.getWriter().println("Operation Failed. " + e.toString() + " " + e.getMessage());
		}
	}

	String addNewContact() {
		return "<h4>Add New Contact</h4>"
				+ "<form method=post action=/contacts>"
				+ "<input type=text name=FirstName placeholder='First Name' /> "
				+ "<input type=text name=LastName placeholder='Last Name' />"
				+ "<input type=text name=Email placeholder='Email' /><br/>"
				+ "Role: <select name=Role>"
				+ "<option value=''>Unknown</option>"
				+ "<option value=faculty selected>Faculty</option>"
				+ "<option value=chair>Dept Chair</option>"
				+ "</select><br/>"
				+ "<input type=submit name=UserRequest value='Add New Contact' />"
				+ "</form><br/><br/>";
	}
	
	String pasteNewContact() {
		return "<h4>Paste New Faculty Contacts</h4>"
				+ "<form method=post action=/contacts>"
				+ "<textarea rows=10 cols=80 name=Paste placeholder='First Name [tab] Last Name [tab] Email\nFirst Name [tab] Last Name [tab] Email' ></textarea> "
				+ "<br/>"
				+ "<input type=hidden name=Role value=faculty />"
				+ "<input type=submit name=UserRequest value='Paste New Contact' />"
				+ "</form><br/><br/>"; 
	}
	
	void addNewContact(HttpServletRequest request) throws Exception {
		Contact c = null;
		try {
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).safe();
		} catch (Exception e) {
			c = new Contact(request.getParameter("FirstName"),request.getParameter("LastName"),request.getParameter("Email").trim().toLowerCase());
		}
		c.role = request.getParameter("Role");
		ofy().save().entity(c).now();
	}
	
	String pasteNewContact(HttpServletRequest request) {
		String url = "";
		Contact c = null;
		try {
			String[] lines = request.getParameter("Paste").split("\n");
			for (int i=0;i<lines.length;i++) {
				String[] paste = lines[i].split("\t");
				c = new Contact(paste[0].trim(),paste[1].trim(),paste[2].trim().toLowerCase());
				c.role = request.getParameter("Role");
				ofy().save().entity(c).now();
				url += (i==0?c.email:"&Email=" + c.email);
			}
		} catch (Exception e) {
		}
		return url;
	}
	
	String editExistingContact(Contact c) {
		return "<h4>Edit Contact</h4>"
				+ "<form method=post action=/contacts>"
				+ "<input type=text name=FirstName value='" + c.firstName + "' /> "
				+ "<input type=text name=LastName value='" + c.lastName + "' />"
				+ "<input type=text name=Email value='" + c.email + "' /><br/>"
				+ "Role: <select name=Role>"
				+ "<option value=''>Unknown</option>"
				+ "<option value='faculty'" + ("faculty".equals(c.role)?" selected":"") + ">Faculty</option>"
				+ "<option value='chair'" + ("chair".equals(c.role)?" selected":"") + ">Dept. Chair</option>"
				+ "</select>"
				+ "<input type=checkbox name='Unsubscribed' value=true" + (c.unsubscribed?" checked":"") + ">Unsubscribed<br/>"
				+ "<input type=submit name=UserRequest value='Save Revised Contact' />&nbsp;"
				+ "<input type=submit name=UserRequest value='Delete This Contact' />"
				+ "</form><br/><br/>";
	}
	
	void saveRevisedContact(HttpServletRequest request) throws Exception {
		try {
			Contact c = ofy().load().type(Contact.class).id(request.getParameter("Email")).safe();
			c.firstName = request.getParameter("FirstName");
			c.lastName = request.getParameter("LastName");
			c.role = request.getParameter("Role");
			c.unsubscribed = Boolean.parseBoolean(request.getParameter("Unsubscribed"));
			ofy().save().entity(c).now();
		} catch (Exception e) {
			addNewContact(request);
		}
	}
	
	void deleteContact(HttpServletRequest request) {
		ofy().delete().type(Contact.class).id(request.getParameter("Email")).now();
	}
	
	String searchContacts() {
		return "<h4>Search Contacts</h4>"
				+ "<form method=get action=/contacts>"
				+ "Email: <input type=text name=Email />&nbsp;<input type=submit value='Search' />"
				+ "</form><br/><br/>";
	}
	
	String cleanContacts() {
		StringBuffer buf = new StringBuffer("<h4>The following contacts were modified by trimming the ID value</h4>");
		List<Key<Contact>> contactKeys = ofy().load().type(Contact.class).limit(500).keys().list();  // 500 is max allowed by ofy().delete()
		while (contactKeys.size()>0) {
			List<Key<Contact>> toDeleteKeys = new ArrayList<Key<Contact>>();
			for (Key<Contact> k : contactKeys) {
				String id = k.getName();
				if (id.equals(id.trim())) continue;  // everything is OK
				else toDeleteKeys.add(k);
			}
			Map<Key<Contact>,Contact> contacts = ofy().load().keys(toDeleteKeys);
			List<Contact> toSaveContacts = new ArrayList<Contact>();
			for (Map.Entry<Key<Contact>,Contact> entry : contacts.entrySet()) {
				Contact c = entry.getValue();
				c.email = c.email.trim().toLowerCase();
				toSaveContacts.add(c);
				buf.append(c.email + "<br/>");
			}
			ofy().save().entities(toSaveContacts);
			ofy().delete().keys(toDeleteKeys);
			Key<Contact> lastKey = contactKeys.get(contactKeys.size()-1);
			contactKeys = ofy().load().type(Contact.class).filterKey(">", lastKey).limit(500).keys().list();
		}
		return buf.toString();
	}
}
