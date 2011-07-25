/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*	This servlet file is adapted from an open-source Java servlet 
*	LTIProviderServlet written by Charles Severance at imsglobal.org
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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

public class BLTILaunch extends HttpServlet {

	DAO dao = new DAO();
	private static final long serialVersionUID = 137L;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		doPost(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
		//System.out.println("Basic LTI Provider request from IP=" + request.getRemoteAddr());

		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		String user_id = request.getParameter("user_id");
		String context_id = request.getParameter("context_id");
		String resource_link_id = request.getParameter("resource_link_id");
		if ( ! "basic-lti-launch-request".equals(request.getParameter("lti_message_type")) ||
				! "LTI-1p0".equals(request.getParameter("lti_version")) ||
				oauth_consumer_key == null || resource_link_id == null ) {
			doError(request, response, "Missing required parameter.", null, null);
			return;
		}

		// Lookup the secret that corresponds to the oauth_consumer_key in the AppEngine datastore
		String oauth_secret = BLTIConsumer.getSecret(oauth_consumer_key);

		OAuthMessage oam = OAuthServlet.getMessage(request, null);
		OAuthValidator oav = new SimpleOAuthValidator();
		OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", 
				oauth_consumer_key, oauth_secret, null);

		OAuthAccessor acc = new OAuthAccessor(cons);

		String base_string = null;
		try {
			base_string = OAuthSignatureMethod.getBaseString(oam);
		} catch (Exception e) {
			base_string = null;
		}
		
		try {
			oav.validateMessage(oam,acc);
		} catch(Exception e) {
			System.out.println("Provider failed to validate message");
			System.out.println(e.getMessage());
			if ( base_string != null ) System.out.println(base_string);
			doError(request, response,"Launch data does not validate.", context_id, null);
			return;
		}
		// BLTI Launch message was validated successfully. 
		
		// Provision a new user, if necessary, and store userId in the user's session
		try {
			String userId = oauth_consumer_key + (user_id==null?"":":"+user_id);

			Objectify ofy = ObjectifyService.begin();
			User user = ofy.find(User.class,userId);
			if (user==null) user = User.createNew(request); // first-ever login for this user
			
			// Update the user's email address, if necessary:
			String email = request.getParameter("lis_person_contact_email_primary");
			if (email != null && !user.email.equals(email)) {
				user.setEmail(email);
				user.verifiedEmail = true;
				ofy.put(user);
			}
			
			request.getSession(true).setAttribute("UserId",userId);

			// Provision a new context (group), if necessary and put the user into it
			String groupKey = oauth_consumer_key + (context_id==null?"":":"+context_id);
			System.out.println(groupKey);
			if (context_id != null && context_id.length() > 0) {
				Group g = ofy.query(Group.class).filter("context_id",context_id).get();
				if (g == null) {
					g = new Group("BLTI",context_id,request.getParameter("context_title"));
					ofy.put(g);
					if (user.isInstructor()) user.changeGroups(g.id);
				}
				if (user.isInstructor()) {
					if (g.instructorId==null || !g.instructorId.equals(user.id)) {
						g.instructorId = user.id;
						ofy.put(g);
						user.changeGroups(g.id);
					}
				} else if (user.myGroupId != g.id) user.changeGroups(g.id);
			}
		} catch (Exception e) {
			System.out.println("Provider failed to validate user and/or group");
			System.out.println(e.getMessage());
			if ( base_string != null ) System.out.println(base_string);
			doError(request, response,"User data does not validate", context_id, null);
			return;

		}
		// Redirect the user's browser to the ChemVantage Home page
		response.sendRedirect("/Home");	
	}

	public void doError(HttpServletRequest request, HttpServletResponse response, 
			String s, String message, Exception e)
	throws java.io.IOException
	{
		System.out.println(s);
		String return_url = request.getParameter("launch_presentation_return_url");
		if ( return_url != null && return_url.length() > 1 ) {
			if ( return_url.indexOf('?') > 1 ) {
				return_url += "&lti_msg=" + URLEncoder.encode(s,"UTF-8");
			} else {
				return_url += "?lti_msg=" + URLEncoder.encode(s,"UTF-8");
			}
			response.sendRedirect(return_url);
			return;
		}
		PrintWriter out = response.getWriter();
		out.println(s);
	}

	@Override
	public void destroy() {

	}

}