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

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

/**
 * Servlet implementation class ManageContacts
 */
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

		String email = request.getParameter("Email");
		Contact c = null;
		if (email != null) c = ofy().load().type(Contact.class).id(email).now();
		
		buf.append(searchContacts());
		
		if (c!=null) {
			buf.append(editExistingContact(c));
			email = null;
		}
				
		buf.append(addNewContact(email));
			
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

	String addNewContact(String email) {
		return "<h4>Add New Contact</h4>"
				+ "<form method=post action=/contacts>"
				+ "<input type=text name=FirstName placeholder='First Name' /> "
				+ "<input type=text name=LastName placeholder='Last Name' />"
				+ "<input type=text name=Email " + (email==null?"placeholder='Email'":"value='" + email + "'") + " /><br/>"
				+ "Role: <select name=Role>"
				+ "<option value=''>Unknown</option>"
				+ "<option value=faculty>Faculty</option>"
				+ "<option value=chair>Dept Chair</option>"
				+ "</select><br/>"
				+ "<input type=submit name=UserRequest value='Add New Contact' />"
				+ "</form><br/><br/>";
	}
	
	void addNewContact(HttpServletRequest request) throws Exception {
		Contact c = null;
		try {
			c = ofy().load().type(Contact.class).id(request.getParameter("Email")).safe();
		} catch (Exception e) {
			c = new Contact(request.getParameter("FirstName"),request.getParameter("LastName"),request.getParameter("Email"));
		}
		c.role = request.getParameter("Role");
		ofy().save().entity(c).now();
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
}
