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

import org.chemvantage.samples.apps.marketplace.UserInfo;
import org.chemvantage.samples.apps.marketplace.openid.ConsumerFactory;
import com.google.step2.AuthRequestHelper;
import com.google.step2.AuthResponseHelper;
import com.google.step2.ConsumerHelper;
import com.google.step2.Step2;
import com.google.step2.discovery.IdpIdentifier;
import com.google.step2.openid.ui.UiMessageRequest;
import com.googlecode.objectify.Objectify;

import org.apache.commons.lang.StringUtils;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.ParameterList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Servlet for handling OpenID logins.  Uses the Step2 library from code.google.com and the
 * underlying OpenID4Java library.
 */
public class OpenIdLaunch extends HttpServlet {

	private static final long serialVersionUID = 137L;
	protected ConsumerHelper consumerHelper;
	protected String realm;
	protected String returnToPath;
	protected String homePath;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		returnToPath = getInitParameter("return_to_path", "/openid");
		homePath = getInitParameter("home_path", "/");
		realm = getInitParameter("realm", null);
		ConsumerFactory factory = new ConsumerFactory(
				new InMemoryConsumerAssociationStore());
		consumerHelper = factory.getConsumerHelper();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String domain = req.getParameter("hd");
		if (domain != null) {
			// User attempting to login with provided domain, build an OpenID request and redirect
			try {
				AuthRequest authRequest = startAuthentication(domain, req);
				String url = authRequest.getDestinationUrl(true);
				resp.sendRedirect(url);
			} catch (OpenIDException e) {
				throw new ServletException("Error initializing OpenID request", e);
			}
		} else {
			// This is a response from the provider, go ahead and validate
			doPost(req, resp);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			UserInfo userInfo = completeAuthentication(req);
			User user = ofy.find(User.class,userInfo.getClaimedId());
			if (user == null) user = User.createOpenIdUser(userInfo);
			req.getSession().setAttribute("UserId", user.getId());
			
			// try to set a Cookie with the user's ID provider:
			Cookie c = new Cookie("IDProvider","Google");
			c.setMaxAge(2592000); // expires after 30 days (in seconds)
			resp.addCookie(c);
		
			resp.sendRedirect(homePath);
		} catch (OpenIDException e) {
			throw new ServletException("Error processing OpenID response", e);
		}
	}

	AuthRequest startAuthentication(String op, HttpServletRequest request)
			throws OpenIDException {
		IdpIdentifier openId = new IdpIdentifier(op);

        String realm = realm(request);
        String returnToUrl = returnTo(request);

        AuthRequestHelper helper = consumerHelper.getAuthRequestHelper(openId, returnToUrl);
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

	UserInfo completeAuthentication(HttpServletRequest request)
			throws OpenIDException {
		HttpSession session = request.getSession();
        ParameterList openidResp = Step2.getParameterList(request);
        String receivingUrl = currentUrl(request);
        DiscoveryInformation discovered =
                (DiscoveryInformation) session.getAttribute("discovered");

        AuthResponseHelper authResponse =
                consumerHelper.verify(receivingUrl, openidResp, discovered);
        if (authResponse.getAuthResultType() == AuthResponseHelper.ResultType.AUTH_SUCCESS) {
            return onSuccess(authResponse, request);
        }
        return onFail(authResponse, request);
    }

	void addAttributes(AuthRequestHelper helper) {
        helper.requestAxAttribute(Step2.AxSchema.EMAIL, true)
            .requestAxAttribute(Step2.AxSchema.FIRST_NAME, true)
            .requestAxAttribute(Step2.AxSchema.LAST_NAME, true);
    }

	String currentUrl(HttpServletRequest request) {
		return Step2.getUrlWithQueryString(request);
	}

	 String realm(HttpServletRequest request) {
         if (StringUtils.isNotBlank(realm)) {
             return realm;
         } else {
             return baseUrl(request);
         }
     }

	 String returnTo(HttpServletRequest request) {
         return new StringBuffer(baseUrl(request))
                 .append(request.getContextPath())
                 .append(returnToPath).toString();
     }

	 String baseUrl(HttpServletRequest request) {
         StringBuffer url = new StringBuffer(request.getScheme())
                 .append("://").append(request.getServerName());

         if ((request.getScheme().equalsIgnoreCase("http")
                 && request.getServerPort() != 80)
                 || (request.getScheme().equalsIgnoreCase("https")
                 && request.getServerPort() != 443)) {
             url.append(":").append(request.getServerPort());
         }

         return url.toString();
     }

	 UserInfo onSuccess(AuthResponseHelper helper, HttpServletRequest request) {
         return new UserInfo(helper.getClaimedId().toString(),
                 helper.getAxFetchAttributeValue(Step2.AxSchema.EMAIL),
                 helper.getAxFetchAttributeValue(Step2.AxSchema.FIRST_NAME),
                 helper.getAxFetchAttributeValue(Step2.AxSchema.LAST_NAME));
     }

	 UserInfo onFail(AuthResponseHelper helper, HttpServletRequest request) {
		 return null;
	 }

     protected String getInitParameter(String key, String defaultValue) {
         String value = getInitParameter(key);
         return StringUtils.isBlank(value) ? defaultValue : value;
     }
}
