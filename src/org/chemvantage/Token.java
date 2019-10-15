package org.chemvantage;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

@WebServlet("/auth/token")
public class Token extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	// This servlet is the OpenID Connection starting point for platforms to reach ChemVantage
	// The servlet identifies the deployment corresponding to the request, and returns a Java Web Token 
	// containing information needed for the subsequent launch request or other service request.

	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		StringBuffer debug = new StringBuffer("Debug trail: Start.<br>");
		try {
			String platform_id = request.getParameter("iss");   // this should be the platform_id URL (aud)
			if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id;
			
			String deployment_id = request.getParameter("lti_deployment_id");
			String client_id = request.getParameter("client_id");
			
			Deployment d = Deployment.getInstance(platform_id,deployment_id);
			
			debug.append("iss: " + platform_id + "<br>");
			debug.append("deployment_id = " + deployment_id + "<br>");
			
			if (d==null) debug.append("Deployment is null<br>");
			debug.append("deployment_id: " + d.getDeploymentId() + "<br>"
					+ "client_id: " + d.client_id + "<br>");
			
			debug.append("Deployment: " + d.toString() + "<br>");
			
			String target_link_uri = request.getParameter("target_link_uri");  // should be https://www.chemvantage.org/lti
																	// or a deeplink url like https://www.chemvantage.org/Quiz?AssignmentId=3479228
			debug.append("target_link_url OK: " + target_link_uri + "<br>");
			String redirect_uri = "https://" + request.getServerName() + "/lti";
			
			String login_hint = request.getParameter("login_hint");
			
			debug.append("Redirect URL: " + target_link_uri + "<br>");
			
			Date now = new Date();
			Date exp = new Date(now.getTime() + 300000L); // 5 minutes from now
			String nonce = Nonce.generateNonce();
			String jwtSecret = Subject.getSubject().HMAC256Secret;
			Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
			
			debug.append("JWT algorithm loaded OK.<br>");
			
			String iss = "https://" + request.getServerName();
			
			String token = JWT.create()
					.withIssuer(iss)
					.withSubject(login_hint)
					.withAudience(platform_id)
					.withExpiresAt(exp)
					.withIssuedAt(now)
					.withClaim("nonce", nonce)
					.withClaim("deployment_id",d.getDeploymentId())
					.withClaim("client_id", client_id)
					.withClaim("redirect_uri", redirect_uri)
					.sign(algorithm);
			
			debug.append("JWT constructed and signed OK<br>");
			
			String oidc_auth_url = d.oidc_auth_url
					+ "?response_type=id_token"
					+ "&response_mode=form_post"
					+ "&scope=openid"
					+ "&prompt=none"
					+ "&login_hint=" + login_hint
					+ "&redirect_uri=" + redirect_uri
					+ "&lti_message_hint=" + request.getParameter("lti_message_hint")
					+ "&client_id=" + d.client_id
					+ "&state=" + token
					+ "&nonce=" + nonce;
			
			debug.append("Sending token: " + oidc_auth_url + "<p>");
			
			response.sendRedirect(oidc_auth_url);

		} catch (Exception e) {
			response.getWriter().println("Failed token: " + e.toString() + "<p>" + debug.toString());
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		doGet(request, response);
	}
/*
	static Deployment getDeployment(String iss, String deployment_id, String client_id) {
		// There are 4 possible legal cases:
		// 1 - deployment_id == null: verify only 1 Deployment and verify client_id matches
		// 2 - client_id == null: load the indicated Deployment and return it
		// 3 - both values null: verify that only 1 Deployment exists and return it
		// 4 - both values provided: load the indicated Deployment and verify client_id matches
		try {
			Key<Platform> p_key = Key.create(Platform.class,iss);
			Deployment d = null;
			if (deployment_id == null) {  // Cases 1 & 3
				//List<Deployment> deployments = ofy().load().type(Deployment.class).ancestor(p_key).list();
				//if (deployments.size() != 1) throw new Exception();  // reject connection if deployment is ambiguous
				//d = deployments.get(0);
				d = ofy().load().type(Deployment.class).ancestor(p_key).first().safe();
			} else {  //Cases 2 & 4
				Key<Deployment> d_key = Key.create(p_key,Deployment.class,deployment_id);
				d = ofy().load().key(d_key).safe();
			}
			if (client_id == null || client_id.contentEquals(d.client_id)) return d;			
		} catch (Exception e) {
		}
		return null;
	}
*/	
}
