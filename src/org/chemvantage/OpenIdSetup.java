/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.chemvantage;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.chemvantage.DAO;
import org.chemvantage.User;
import org.chemvantage.openid.ConsumerFactory;
import org.chemvantage.openid.UserInfo;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.ParameterList;

import com.google.step2.AuthRequestHelper;
import com.google.step2.AuthResponseHelper;
import com.google.step2.ConsumerHelper;
import com.google.step2.Step2;
import com.google.step2.discovery.IdpIdentifier;
import com.google.step2.openid.ui.UiMessageRequest;
import com.googlecode.objectify.Objectify;

/**
 * Servlet for handling Google Apps Marketplace domain setup. Uses the Step2
 * library from code.google.com and the underlying OpenID4Java library.
 */
public class OpenIdSetup extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected ConsumerHelper consumerHelper;
	protected String realm;
	protected String returnToPath;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();

	/**
	 * Init the servlet. For demo purposes, we're just using an in-memory
	 * version of OpenID4Java's ConsumerAssociationStore. Production apps,
	 * particularly those in a clustered environment, should consider using an
	 * implementation backed by shared storage (memcache, DB, etc.)
	 * 
	 * @param config
	 * @throws ServletException
	 */
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		returnToPath = getInitParameter("return_to_path", "/setup");
		realm = getInitParameter("realm", null);
		ConsumerFactory factory = new ConsumerFactory(
				new InMemoryConsumerAssociationStore());
		consumerHelper = factory.getConsumerHelper();
	}

	/**
	 * Either initiates a login to a given provider or processes a response from
	 * an IDP.
	 * 
	 * @param req
	 * @param resp
	 * @throws ServletException
	 * @throws IOException
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String domain = req.getParameter("hd");
		if (domain != null) {
			// User attempting to login with provided domain, build an OpenID
			// request and redirect
			try {
				AuthRequest authRequest = startAuthentication(domain, req);
				String url = authRequest.getDestinationUrl(true);
				
				String callback = req.getParameter("callback");
				if (callback != null && !callback.isEmpty()) req.getSession().setAttribute("callback",callback);
				
				resp.sendRedirect(url);
			} catch (OpenIDException e) {
				resp.sendRedirect("?errorString=Error initializing OpenID request: "
						+ e.getMessage());
			}
		} else {
			// This is a response from the provider, go ahead and validate
			doPost(req, resp);
		}
	}

	/**
	 * Handle the response from the OpenID Provider.
	 * 
	 * @param req
	 *            Current servlet request
	 * @param resp
	 *            Current servlet response
	 * @throws ServletException
	 *             if unable to process request
	 * @throws IOException
	 *             if unable to process request
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			UserInfo userInfo = completeAuthentication(req);
			User user = ofy.find(User.class, userInfo.getClaimedId());
			if (user == null) {
				user = User.createOpenIdUser(userInfo);
				user.setIsAdministrator(true);
				ofy.put(user);
			}
			HttpSession session = req.getSession();
			session.setAttribute("UserId", user.getId());

			String callback = (String)req.getSession().getAttribute("callback");
			
			Domain domain = ofy.query(Domain.class).filter("domainName",user.domain).get();
			if (domain == null) {
				domain = new Domain(user.domain);
				domain.addAdmin(user.id);
				ofy.put(domain);
				resp.sendRedirect(callback);
			} else {			
				resp.setContentType("text/html");
				PrintWriter out = resp.getWriter();
				out.println(Home.getHeader(user) + welcomeMessage(user,domain,callback) + Home.footer);

				// try to set a Cookie with the user's ID provider:
				Cookie c = new Cookie("IDProvider", "Google");
				c.setMaxAge(2592000); // expires after 30 days (in seconds)
				resp.addCookie(c);
			}
		} catch (OpenIDException e) {
			resp.sendRedirect("?errorString=Error processing OpenID response: "
					+ e.getMessage());
		}
	}

	String welcomeMessage(User user,Domain domain,String callback) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>Thank you for adding ChemVantage to your Google Apps domain</h2>"
				+ "Google Apps Domain: " + domain.domainName + "<br>");
		for (String s : domain.domainAdmins) {
			buf.append("Administrator: " + User.getBothNames(s) + " (" + User.getEmail(s) + ")<br>");
		}
		buf.append("<span style='color:red;font-weight:bold'>Setup is complete</span>");
		Date now = new Date();
		if (domain.freeTrialExpires.after(now)) buf.append("<h3>Free Trial Period</h3>"
				+ "Your free trial period expires " + domain.freeTrialExpires.toString()
				+ "<br>Until then, the number of free premium account seats in your domain is unlimited.<p>");
		
		buf.append("<h3>Basic and Premium Accounts</h3>"
				+ "Any individual user may browse ChemVantage without charge using a free basic account simply by navigating to the site.<br>"
				+ "In order to join a ChemVantage group (usually a chemistry class taught by one of your instructors), a user must upgrade to a premium account.<br>"
				+ "During the free trial period this happens automatically.<p>"
				+ "There are 2 ways to upgrade to a premium account after the free trial period:<ol>"
				+ "<li>The domain (e.g., school or college) may purchase premium account seats ($2.00/ea in quantities of 50 or more) that are allocated to users when they first join a group. If you want to purchase premium accounts on behalf of your students, you simply have to ensure that a sufficient number of seats are purchased in advance."
				+ "<li>If no seats are available, the user will be asked to purchase an individual premium account upgrade ($4.99) when joining a group for the first time. If you want students to purchase their own premium accounts, you don't have to do anything; it's automatic."
				+ "</ol>");
		
		buf.append("<h3>Instructor and Admin Accounts</h3>"
				+ "As the domain administrator, you have the ability to grant instructor privileges to users in your domain (i.e., users with a user@" + domain.domainName + " email address). "
				+ "To do this, you must click the 'Admin' link at the top of the page. Use the user search tool to find the user's account and then edit it to grant or revoke privileges. "
				+ "You may also use this tool to grant/revoke administrator privileges.  "
				+ "All instructors and administrators are automatically provided premium accounts without charge.<p>");
		
		buf.append("<h3>Questions or Comments</h3>"
				+ "See the <a href=/help.html>Help Page</a> for useful tips and tricks, or send us a message using the <a href=/Feedback>Feedback Page</a>, or contact us directly at <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>.<br>"
				+ "For emergencies, call us at 1-801-810-4401 (domain administrators only, please)");
		
		return buf.toString();
	}
	/**
	 * Builds an auth request for a given OpenID provider.
	 * 
	 * @param op
	 *            OpenID Provider URL. In the context of Google Apps, this can
	 *            be a naked domain name such as "saasycompany.com". The length
	 *            of the domain can exceed 100 chars.
	 * @param request
	 *            Current servlet request
	 * @return Auth request
	 * @throws org.openid4java.OpenIDException
	 *             if unable to discover the OpenID endpoint
	 */
	AuthRequest startAuthentication(String op, HttpServletRequest request)
			throws OpenIDException {
		IdpIdentifier openId = new IdpIdentifier(op);

		String realm = realm(request);
		String returnToUrl = returnTo(request);

		AuthRequestHelper helper = consumerHelper.getAuthRequestHelper(openId,
				returnToUrl);
		addAttributes(helper);

		HttpSession session = request.getSession();
		AuthRequest authReq = helper.generateRequest();
		authReq.setRealm(realm);

		UiMessageRequest uiExtension = new UiMessageRequest();
		uiExtension.setIconRequest(true);
		authReq.addExtension(uiExtension);

		session.setAttribute("discovered", helper.getDiscoveryInformation());
		return authReq;
	}

	/**
	 * Validates the response to an auth request, returning an authenticated
	 * user object if successful.
	 * 
	 * @param request
	 *            Current servlet request
	 * @return User
	 * @throws org.openid4java.OpenIDException
	 *             if unable to verify response
	 */

	UserInfo completeAuthentication(HttpServletRequest request)
			throws OpenIDException {
		HttpSession session = request.getSession();
		ParameterList openidResp = Step2.getParameterList(request);
		String receivingUrl = currentUrl(request);
		DiscoveryInformation discovered = (DiscoveryInformation) session
				.getAttribute("discovered");

		AuthResponseHelper authResponse = consumerHelper.verify(receivingUrl,
				openidResp, discovered);
		if (authResponse.getAuthResultType() == AuthResponseHelper.ResultType.AUTH_SUCCESS) {
			return onSuccess(authResponse, request);
		}
		return onFail(authResponse, request);
	}

	/**
	 * Adds the requested AX attributes to the request
	 * 
	 * @param helper
	 *            Request builder
	 */
	void addAttributes(AuthRequestHelper helper) {
		helper.requestAxAttribute(Step2.AxSchema.EMAIL, true)
				.requestAxAttribute(Step2.AxSchema.FIRST_NAME, true)
				.requestAxAttribute(Step2.AxSchema.LAST_NAME, true);
	}

	/**
	 * Reconstructs the current URL of the request, as sent by the user
	 * 
	 * @param request
	 *            Current servlet request
	 * @return URL as sent by user
	 */
	String currentUrl(HttpServletRequest request) {
		return Step2.getUrlWithQueryString(request);
	}

	/**
	 * Gets the realm to advertise to the IDP. If not specified in the servlet
	 * configuration. it dynamically constructs the realm based on the current
	 * request.
	 * 
	 * @param request
	 *            Current servlet request
	 * @return Realm
	 */
	String realm(HttpServletRequest request) {
		if (StringUtils.isNotBlank(realm)) {
			return realm;
		} else {
			return baseUrl(request);
		}
	}

	/**
	 * Gets the <code>openid.return_to</code> URL to advertise to the IDP.
	 * Dynamically constructs the URL based on the current request.
	 * 
	 * @param request
	 *            Current servlet request
	 * @return Return to URL
	 */
	String returnTo(HttpServletRequest request) {
		return new StringBuffer(baseUrl(request))
				.append(request.getContextPath()).append(returnToPath)
				.toString();
	}

	/**
	 * Dynamically constructs the base URL for the application based on the
	 * current request
	 * 
	 * @param request
	 *            Current servlet request
	 * @return Base URL (path to servlet context)
	 */
	String baseUrl(HttpServletRequest request) {
		StringBuffer url = new StringBuffer(request.getScheme()).append("://")
				.append(request.getServerName());

		if ((request.getScheme().equalsIgnoreCase("http") && request
				.getServerPort() != 80)
				|| (request.getScheme().equalsIgnoreCase("https") && request
						.getServerPort() != 443)) {
			url.append(":").append(request.getServerPort());
		}

		return url.toString();
	}

	/**
	 * Map the OpenID response into a user for our app.
	 * 
	 * @param helper
	 *            Auth response
	 * @param request
	 *            Current servlet request
	 * @return User representation
	 */
	UserInfo onSuccess(AuthResponseHelper helper, HttpServletRequest request) {
		return new UserInfo(helper.getClaimedId().toString(),
				helper.getAxFetchAttributeValue(Step2.AxSchema.EMAIL),
				helper.getAxFetchAttributeValue(Step2.AxSchema.FIRST_NAME),
				helper.getAxFetchAttributeValue(Step2.AxSchema.LAST_NAME));
	}

	/**
	 * Handles the case where authentication failed or was canceled. Just a
	 * no-op here.
	 * 
	 * @param helper
	 *            Auth response
	 * @param request
	 *            Current servlet request
	 * @return User representation
	 */
	UserInfo onFail(AuthResponseHelper helper, HttpServletRequest request) {
		return null;
	}

	/**
	 * Small helper for fetching init params with default values
	 * 
	 * @param key
	 *            Parameter to fetch
	 * @param defaultValue
	 *            Default value to use if not set in web.xml
	 * @return Parameter value or defaultValue
	 */
	protected String getInitParameter(String key, String defaultValue) {
		String value = getInitParameter(key);
		return StringUtils.isBlank(value) ? defaultValue : value;
	}

}
