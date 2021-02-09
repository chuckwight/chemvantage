package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.googlecode.objectify.Key;;

@WebServlet("/auth/token")
public class Token extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	// This servlet is the OpenID Connection starting point for platforms to reach ChemVantage
	// The servlet identifies the deployment corresponding to the request, and returns a Java Web Token 
	// containing information needed for the subsequent launch request or other service request.

	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		StringBuffer debug = new StringBuffer("Issuing auth token:<br>");
		try {
			// store parameters required by third-party initiated login procedure:
			String platform_id = request.getParameter("iss");   // this should be the platform_id URL (aud)
			debug.append("iss: " + platform_id + "<br>");
			String login_hint = request.getParameter("login_hint");
			debug.append("login_hint: " + login_hint + "<br>");
			String target_link_uri = request.getParameter("target_link_uri");
			debug.append("target_link_uri: " + target_link_uri + "<br>");
			debug.append("parameters: " + request.getParameterMap().keySet().toString() + "<br>");
			
			if (platform_id == null) throw new Exception("Missing required iss parameter.");
			if (login_hint == null) throw new Exception("Missing required login_hint parameter.");
			if (target_link_uri == null) throw new Exception("Missing required target_link_uri parameter.");
			
			String deployment_id = request.getParameter("lti_deployment_id");
			debug.append("deployment_id: " + deployment_id + "<br>");
			
			String client_id = request.getParameter("client_id");
			debug.append("client_id: " + client_id + "<br>");
			
			Deployment d = getDeployment(platform_id,deployment_id,client_id);
			if (d==null) throw new Exception("This deployment was not found in the ChemVantage database. Please check the registration or contact admin@chemvantage.org for assistance.");
			
			String redirect_uri = target_link_uri;
			
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
					.withClaim("client_id", d.client_id)
					.withClaim("redirect_uri", redirect_uri)
					.sign(algorithm);
			
			debug.append("JWT constructed and signed OK<br>");
			String lti_message_hint = request.getParameter("lti_message_hint");
			
			String oidc_auth_url = d.oidc_auth_url
					+ "?response_type=id_token"
					+ "&response_mode=form_post"
					+ "&scope=openid"
					+ "&prompt=none"
					+ "&login_hint=" + login_hint
					+ "&redirect_uri=" + redirect_uri
					+ (lti_message_hint==null?"":"&lti_message_hint=" + lti_message_hint)
					+ "&client_id=" + d.client_id
					+ "&state=" + token
					+ "&nonce=" + nonce;
			
			debug.append("Sending token: " + oidc_auth_url + "<p>");
			
			response.sendRedirect(oidc_auth_url);
			d.claims = oidc_auth_url;
			ofy().save().entity(d);
		} catch (Exception e) {
			response.getWriter().println("Failed token: " + e.toString() + "<br>" + debug.toString());
		}
	}

	private Deployment getDeployment(String platform_id,String deployment_id, String client_id) throws Exception {
		// This method attempts to identify a unique registered Deployment entity based on the required
		// platform_id value and the optional lti_deployment_id and client_id values. The latter should 
		// be used in case the platform supports multiple deployments with different client_id values for the tool.
		// However, this is not technically required by the specifications. Hmm.
		
		URL platform = new URL(platform_id);
		
		// Take the optimistic route first; this should always work if the deploymdent_id has been provided, else return null;
		if (deployment_id != null) {
			String platform_deployment_id = new URL("https",platform.getHost(),platform.getPort(),"/"+deployment_id).toString();
			if (platform_deployment_id.lastIndexOf("/") == platform_deployment_id.length()-1) platform_deployment_id = platform_deployment_id.substring(0, platform_deployment_id.length()-1);
			return ofy().load().type(Deployment.class).id(platform_deployment_id).now();
		}
		
		// Prepare to search for all deployments from this platform:
		Key<Deployment> kstart = Key.create(Deployment.class, platform.toString());
		Key<Deployment> kend = Key.create(Deployment.class, platform.toString() + "~");			
		List<Deployment> deployments = null;
		
		if (client_id != null) {
			// Find all deployments from this platform with the specified client_id; there SHOULD be only one if the deployment_id was not provided.
			deployments = ofy().load().type(Deployment.class).filterKey(">=",kstart).filterKey("<",kend).filter("client_id",client_id).list();
		} else {
			// Find all of the deployments from this platform; there SHOULD be only one if neither deployment_id nor client_id was provided.
			deployments = ofy().load().type(Deployment.class).filterKey(">=",kstart).filterKey("<",kend).list();
		}
		return deployments.size()==1?deployments.get(0):null;  // fails if there are no deployments or multiple deployments in the List
	}
	

	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		doGet(request, response);
	}
}
