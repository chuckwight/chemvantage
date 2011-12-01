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

/*	This servlet utilizes the OpenID4Java libraries to conduct OpenID authentication	*/

package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.FetchRequest;

import com.googlecode.objectify.Objectify;

public class OpenIdSetup extends HttpServlet {

	private static final long serialVersionUID = 137L;
	protected String realm;
	protected String returnToUrl;
	protected ConsumerManager manager;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		realm = getInitParameter("realm");
		if (realm.isEmpty()) realm = "http://www.chemvantage.org";
		manager = new ConsumerManager();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (returnToUrl==null || returnToUrl.isEmpty()) {
			String returnToPath = getInitParameter("return_to_path");
			if (returnToPath.isEmpty()) returnToPath = "/setup";
			returnToUrl = "http://" + request.getServerName() + returnToPath;
		}
		String domain = request.getParameter("hd");
		if (domain != null) { // this is an openId authentication request from a user
			try {
				// store the callback URL to Google Apps Marketplace in the user's session
				String callback = request.getParameter("callback");
				if (callback != null && !callback.isEmpty()) request.getSession().setAttribute("callback",callback);
				else throw new Exception();
				
				// perform discovery on the user-supplied domain
				List<?> discoveries = new ArrayList<DiscoveryInformation>();
				discoveries = manager.discover(domain);
				// attempt to associate with the OpenID provider
				// and retrieve one service endpoint for authentication
				DiscoveryInformation discovered = manager.associate(discoveries);

				// store the discovery information in the user's session
				request.getSession().setAttribute("openid-disc", discovered);

				// obtain a AuthRequest message to be sent to the OpenID provider
				AuthRequest authReq = manager.authenticate(discovered, returnToUrl);

				// Attribute Exchange example: fetching the user attributes (email, firstName, lastName):
				FetchRequest fetch = FetchRequest.createFetchRequest();
				fetch.addAttribute("email", "http://axschema.org/contact/email", true);
				fetch.addAttribute("firstName", "http://axschema.org/namePerson/first", true);
				fetch.addAttribute("lastName", "http://axschema.org/namePerson/last", true);
				// attach the extension to the authentication request
				authReq.addExtension(fetch);
				response.sendRedirect(authReq.getDestinationUrl(true));
			} catch (Exception e) {
				response.setContentType("text/html");
				response.getWriter().println("Sorry, the OpenId authentication request failed. - admin@chemvantage.org");
			}
		} else {
			// This is a response from the provider, go ahead and validate
			doPost(request, response);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		HttpSession session = request.getSession();
		try {
            // extract the parameters from the authentication response
            // (which comes in as a HTTP request from the OpenID provider)
            ParameterList openIdProviderResponse = new ParameterList(request.getParameterMap());

            // retrieve the previously stored discovery information
            DiscoveryInformation discovered = (DiscoveryInformation) request.getSession().getAttribute("openid-disc");

            // extract the receiving URL from the HTTP request
            StringBuffer receivingURL = request.getRequestURL();
            String queryString = request.getQueryString();
            if (queryString != null && queryString.length() > 0) receivingURL.append("?").append(request.getQueryString());

            // verify the response; ConsumerManager needs to be the same
            // (static) instance used to place the authentication request
            VerificationResult verification = manager.verify(receivingURL.toString(), openIdProviderResponse, discovered);

            // examine the verification result and authenticate the user (creating a new one, if necessary)
            Identifier verified = verification.getVerifiedId();
            if (verified != null) {
            	User user = ofy.find(User.class,verified.getIdentifier());
            	if (user==null) {
            		AuthSuccess authResponse = (AuthSuccess) verification.getAuthResponse();
            		user = User.createOpenIdUser(authResponse);
            	}
            	session.setAttribute("UserId", user.getId());
            	
            	// create a new ChemVantage domain, if necessary
            	Domain domain = ofy.query(Domain.class).filter("domainName",user.domain).get();
    			if (domain == null) {
    				domain = new Domain(user.domain);
    				domain.addAdmin(user.id);
    				ofy.put(domain);
    				user.setIsAdministrator(true);
    				user.setIsInstructor(true);
    				ofy.put(user);
    			}
    		
            	// try to set a Cookie with the user's ID provider:
    			Cookie c = new Cookie("IDProvider","Google");
    			c.setMaxAge(2592000); // expires after 30 days (in seconds)
    			response.addCookie(c);
    			
    			// retrieve the callback URL from the user's session, if it exists
    			String callback = (String)session.getAttribute("callback");
    			if (callback==null) callback = "";
    			session.removeAttribute("callback");
    			
    			if (callback.isEmpty()) {
    				response.setContentType("text/html");
    				PrintWriter out = response.getWriter();
    				out.println(Home.getHeader(user) + welcomeMessage(user,domain) + Home.footer);
    			} else response.sendRedirect(callback);
    			
            } else throw new Exception();
		}
		catch (Exception e) {
			session.invalidate();
            response.setContentType("text/html");
            response.getWriter().println("Sorry, the OpenID authentication request failed. - admin@chemvantage.org");
        }
	}

	String welcomeMessage(User user,Domain domain) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Thank you for installing ChemVantage</h2>");
		buf.append("<table>");
		buf.append("<tr><td style='text-align:right'>Google Apps Domain: </td><td>" + domain.domainName + "</td></tr>");
		for (String s : domain.domainAdmins) {
			buf.append("<tr><td style='text-align:right'>Administrator: </td><td>" + User.getBothNames(s) + " (" + User.getEmail(s) + ")</td></tr>");
		}
		buf.append("<tr><td style='text-align:right'>Free trial period expires: </td><td>" + domain.freeTrialExpires.toString() + "</td></tr>");
		buf.append("</table>");
		
		buf.append("<p><span style='color:red;font-weight:bold'>Your account setup is complete.</span><p>");
		return buf.toString();
	}
}
