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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.googlecode.objectify.Objectify;

public class DataTransfer extends HttpServlet {
	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();

	/* The purpose of this servlet is to provide a mechanism for
	 * transferring data between instances of ChemVantage.  This
	 * is useful for backup/restore operations and for testing or
	 * developing new data configurations. For security, the servlet
	 * is configured for read-only access to the production site.
	 */
	public String getServletInfo() {
		return "ChemVantage servlet for transferring datastore objects between instances";
	}

	protected void doGet (HttpServletRequest request,HttpServletResponse response) {
		try {
			// begin admin user authentication section
			UserService userService = UserServiceFactory.getUserService();
			User user = ofy.get(User.class,userService.getCurrentUser().getUserId());
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId", user.id);

			response.setContentType("text/html");
			PrintWriter out = response.getWriter();

			out.println(Home.getHeader(user) + dataRequestForm(request) + Home.footer);
		} catch (Exception e) {
		}

	}

	protected void doPost (HttpServletRequest request,HttpServletResponse response) {
		try {
			// begin admin user authentication section
			UserService userService = UserServiceFactory.getUserService();
			User user = ofy.get(User.class,userService.getCurrentUser().getUserId());
			HttpSession session = request.getSession(true);
			session.setAttribute("UserId", user.id);

			getObjects(request);
			doGet(request,response);
			
			} catch (Exception e) {
		}
	}

	private String dataRequestForm(HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Data Transfer Request</h2>");
		String cn = request.getParameter("ClassName");
		if (cn==null) cn = "";
		int n = 0;
		try {
			n = (Integer) request.getAttribute("Quantity");
		} catch (Exception e) {
		}
		
		if (request.getParameter("UserRequest")==null) {
			buf.append("This servlet imports data objects from the ChemVantage production site "
					+ "datastore into this local instance of ChemVantage. Complete the form below specifying which "
					+ "data objects should be transferred to the local instance you are logged into now.<p>");
		} else if (request.getParameter("UserRequest").equals("Check Quantity")) {
			buf.append("The remote servlet reports that " + n + " " 
					+ cn + " objects are available for transfer.<p>");		
		} else {
			buf.append(n + " " + cn + " objects were successfully transferred "
					+ "to the local datastore.<p>Any objects with duplicate "
					+ "Ids were updated and overwritten with the transferred data.");
		}
		buf.append("<FORM ACTION=DataTransfer METHOD=POST><TABLE>");
		buf.append("<TR><TD>Object Class</TD><TD>");
		buf.append("<select name=ClassName><option value=''>Select a class</option>");
		buf.append("<option value=Assignment" + (cn.equals("Assignment")?" selected":"") + ">Assignment</option>");
		buf.append("<option value=Group" + (cn.equals("Group")?" selected":"") + ">Group</option>");
		buf.append("<option value=HWTransaction" + (cn.equals("HWTransaction")?" selected":"") + ">HWTransaction</option>");
		buf.append("<option value=PracticeExamTransaction" + (cn.equals("PracticeExamTransaction")?" selected":"") + ">PracticeExamTransaction</option>");
		buf.append("<option value=Question" + (cn.equals("Question")?" selected":"") + ">Question</option>");
		buf.append("<option value=QuizTransaction" + (cn.equals("QuizTransaction")?" selected":"") + ">QuizTransaction</option>");
		buf.append("<option value=Response" + (cn.equals("Response")?" selected":"") + ">Response</option>");
		buf.append("<option value=Text" + (cn.equals("Text")?" selected":"") + ">Text</option>");
		buf.append("<option value=Topic" + (cn.equals("Topic")?" selected":"") + ">Topic</option>");
		buf.append("<option value=User" + (cn.equals("User")?" selected":"") + ">User</option>");
		buf.append("<option value=Video" + (cn.equals("Video")?" selected":"") + ">Video</option>");
		buf.append("<option value=VideoTransaction" + (cn.equals("VideoTransaction")?" selected":"") + ">VideoTransaction</option>");
		buf.append("</select></TD></TR>");
		buf.append("<TR><TD COLSPAN=2><input type=submit name=UserRequest value='Check Quantity'><input type=submit name=UserRequest value='Import Objects'></TD></TR>");
		buf.append("</TABLE></FORM>");
		return buf.toString();
	}
	
	private void getObjects(HttpServletRequest request) {
		// this method retrieves a list of Objects from the ChemVantage production server
		// and puts them into the local datastore
		try {
			URL u = new URL("http://chem-vantage.appspot.com/SendObjects");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setUseCaches(false);
			uc.setDefaultUseCaches(false);
			uc.setRequestProperty("Content-Type","application/x-java-serialized-object");
			
			String userRequest = request.getParameter("UserRequest");
			String className = request.getParameter("ClassName");
			ObjectOutputStream oos = new ObjectOutputStream(uc.getOutputStream());
			oos.writeObject(userRequest);
			oos.writeObject(className);
			oos.close();
			
			ObjectInputStream ois = new ObjectInputStream(uc.getInputStream());
			if (userRequest.equals("Check Quantity")) {
				int n = ois.readInt();
				request.setAttribute("Quantity", n);
			} else {
				List<?> objects = (List<?>) ois.readObject();
				ofy.put(objects);
				request.setAttribute("Quantity", objects.size());
			}
			ois.close();
		} catch (Exception e) {
		}
	}
}