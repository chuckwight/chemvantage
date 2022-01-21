package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Key;

@WebServlet("/unsubscribe")
public class Unsubscribe extends HttpServlet {
	private static final long serialVersionUID = 137L;
    
	public Unsubscribe() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		try {
			Key<Contact> k = Key.valueOf(request.getParameter("k"));
			Contact c = ofy().load().key(k).safe();
			c.unsubscribed = true;
			ofy().save().entity(c);
			response.getWriter().println(Subject.header() + Subject.banner + "<h2>You have been successfully unsubscribed.</h2>" + Subject.footer);
		} catch (Exception e) {
			response.getWriter().println("Not found.");
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
