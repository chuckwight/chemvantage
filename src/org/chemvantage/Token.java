package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
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
		StringBuffer debug = new StringBuffer("");
		try {
			// store parameters required by third-party initiated login procedure:
			String platform_id = request.getParameter("iss");   // this should be the platform_id URL (aud)
			String login_hint = request.getParameter("login_hint");
			String target_link_uri = request.getParameter("target_link_uri");
			
			if (platform_id == null) throw new Exception("Missing required iss parameter.");
			if (login_hint == null) throw new Exception("Missing required login_hint parameter.");
			if (target_link_uri == null) throw new Exception("Missing required target_link_uri parameter.");
			
			String deployment_id = request.getParameter("lti_deployment_id");
			if (deployment_id==null) deployment_id = request.getParameter("login_hint");
			String client_id = request.getParameter("client_id");
			
			Deployment d = getDeployment(platform_id,deployment_id,client_id);
			//if (d==null) response.sendRedirect("/Registration.jsp?message=To+start+using+ChemVantage,+please+register+your+LMS.");
			
			if (d==null) throw new Exception("ChemVantage was unable to identify this deployment from your LMS. "
					+ "If you received a registration email within the past 7 days, please use the tokenized link in that message to "
					+ "submit (or resubmit) the deployment_id and other required parameters. Otherwise, you may "
					+ "repeat the registration process at https://www.chemvantage.org/lti/registration");
			
			String redirect_uri = target_link_uri;
			
			Date now = new Date();
			Date exp = new Date(now.getTime() + 300000L); // 5 minutes from now
			String nonce = Nonce.generateNonce();
			Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
			
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
		} catch (Exception e) {
			Enumeration<String> parameterNames = request.getParameterNames();
			while (parameterNames.hasMoreElements()) {
				String name = parameterNames.nextElement();
				debug.append(name + ":" + request.getParameter(name) + ";");
			}
			response.sendError(401,"Failed Auth Token. " + (e.getMessage()==null?e.toString():e.getMessage()) + "\n" + debug.toString());
		}
	}

	private Deployment getDeployment(String platform_id,String deployment_id, String client_id) throws Exception {
		// This method attempts to identify a unique registered Deployment entity based on the required
		// platform_id value and the optional lti_deployment_id and client_id values. The latter should 
		// be used in case the platform supports multiple deployments with different client_id values for the tool.
		// However, this is not technically required by the specifications. Hmm.
		
		Deployment d = null;  // this will ultimately be the return value
		
		if (platform_id.endsWith("/")) platform_id = platform_id.substring(0,platform_id.length()-1);
		
		URL platform = new URL(platform_id);
		if (!platform.getProtocol().equals("https")) throw new Exception("The platform_id must be a secure URL.");
		
		// Take the optimistic route first; this should always work if the deployment_id has been provided, else return null;
		if (deployment_id != null) {
			String platform_deployment_id = platform_id + "/" + deployment_id;
			d = ofy().load().type(Deployment.class).id(platform_deployment_id).now();
			if (d == null) {
				try {  // look for a deployment with a blank deployment_id from this platform
					d = ofy().load().type(Deployment.class).id(platform_id + "/").safe();
					ofy().delete().entity(d);
					d.platform_deployment_id = platform_id + "/" + deployment_id;
					d.created = new Date();
					ofy().save().entity(d).now();
				} catch (Exception e) {
				}
			}
			return d;
		}

		// DeploymentId was not sent; prepare to search for all deployments from this platform:
		Key<Deployment> kstart = Key.create(Deployment.class, platform_id);
		Key<Deployment> kend = Key.create(Deployment.class, platform_id + "~");			
		List<Deployment> deployments = null;
		if (client_id != null) {
			// Find all deployments from this platform with the specified client_id; there SHOULD be only one if the deployment_id was not provided.
			deployments = ofy().load().type(Deployment.class).filterKey(">=",kstart).filterKey("<",kend).filter("client_id",client_id).list();
			if (deployments.size()==1) return deployments.get(0);
		} else {
			// Find all of the deployments from this platform; there SHOULD be only one if neither deployment_id nor client_id was provided.
			deployments = ofy().load().type(Deployment.class).filterKey(">=",kstart).filterKey("<",kend).list();
			if (deployments.size()==1) return deployments.get(0);
		}
		
		if (d==null) {  // Still no joy. Check to see if there might be an error in the platform_id or iss value (e.g., using an instructure test platform)
			deployments =  ofy().load().type(Deployment.class).filter("client_id",client_id).list();
			if (deployments.size()==1) {
				d = deployments.get(0);
				if ("canvas".equals(d.lms_type) && platform_id.contains("instructure.com") && d.lastLogin==null) {
					// create new Deployment with the correct platform_id and access points
					String oidc_auth_url = platform_id + "/api/lti/authorize_redirect";
					String oauth_access_token_url = platform_id + "/login/oauth2/token";
					String well_known_jwks_url = platform_id + "/api/lti/security/jwks";
					String client_name = d.contact_name;
					String email = d.email;
					String organization = d.organization;
					String org_url = d.org_url;
					String lms = d.lms_type;
					Deployment d2 = new Deployment(platform_id,d.getDeploymentId(),client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,client_name,email,organization,org_url,lms);				
					ofy().save().entity(d2).now();
					ofy().delete().entity(d);
					return d2;
				}
			}
		}
		
		return d;
	}
	

	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		doGet(request, response);
	}
}
