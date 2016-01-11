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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class SendObjects extends GenericServlet {
	private static final long serialVersionUID = 137L;
	boolean servletEnabled = true;  // set to false to disable all transfers
	
	@Override
	public void service(ServletRequest request,ServletResponse response) throws ServletException, IOException {
		response.setContentType("application/x-java-serialized-object");
		if (servletEnabled && request.getServerName().equals("dev-vantage.appspot.com")) {
			int value = 0;
			try {
				// read a String object containing the data request parameters
				ObjectInputStream is = new ObjectInputStream(request.getInputStream());
				String userRequest = (String) is.readObject();
				String className = "org.chemvantage." + (String) is.readObject();
				is.close();

				Class<?> c = Class.forName(className);
				ObjectOutputStream oStream = new ObjectOutputStream(response.getOutputStream());
				if ("Check Quantity".equals(userRequest)) {
					value = ofy().load().type(c).keys().list().size();
					oStream.writeInt(value);
				} else {
					List<?> objects = ofy().load().type(c).list();
					oStream.writeObject(objects);
				}
				oStream.close();
			} catch (Exception e) {
			}
		}
	}
}