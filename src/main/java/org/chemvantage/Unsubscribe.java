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
		boolean signMeUpAgain = Boolean.parseBoolean(request.getParameter("s"));
		StringBuffer buf = new StringBuffer(Subject.header() + Subject.banner);
		try {
			Key<Contact> k = Key.valueOf(request.getParameter("k"));
			Contact c = ofy().load().key(k).safe();
			if (signMeUpAgain) {
				buf.append("<h4>Thanks. You are now subscribed to occasional ChemVantage messages.</h4>");
				c.unsubscribed = false;
				ofy().save().entity(c);
			} else {  // unsubscribe request
				if (c.unsubscribed) buf.append("<h4>You remain unsubscribed from ChemVantage messages.</h4>");
				else {
					c.unsubscribed = true;
					//c.vetted = true;
					ofy().save().entity(c);
					buf.append("<h4>You have been successfully unsubscribed from ChemVantage messages.</h4>");
				}
				buf.append("<a href=/unsubscribe?k=" + request.getParameter("k") + "&s=true>Wait! I changed my mind. Please sign me up again.</a><br/><br/>");
			}
		} catch (Exception e) {
			buf.append("<h4>Sorry, we were unable to find your record in our system.</h4>");
		}
		buf.append(Subject.footer);
		response.getWriter().println(buf.toString());
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
