package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;
import static com.googlecode.objectify.ObjectifyService.key;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.googlecode.objectify.Key;

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
			
			Deployment d = getDeployment(request);
			
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
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			StringBuffer buf = new StringBuffer();
			buf.append(Subject.header());
			int nonceHash = nonce.hashCode();
			// Javascript tries to store the hashCode of nonce for use during launch:
			buf.append("<script>"
					+ "try {"
					+ " window.sessionStorage.setItem('sig','" + nonceHash + "');"
					+ "} catch (error) {}"
					+ "window.location.replace('" + oidc_auth_url + "');"
					+ "</script>");
			buf.append(Subject.footer);
			if (oidc_auth_url.contains("imc")) Utilities.sendEmail("ChemVantage", "admin@chemvantage.org", "IMC OIDC Auth URL", oidc_auth_url);
			out.println(buf.toString());
		} catch (Exception e) {
			Enumeration<String> parameterNames = request.getParameterNames();
			while (parameterNames.hasMoreElements()) {
				String name = parameterNames.nextElement();
				debug.append(name + ":" + request.getParameter(name) + ";");
			}
			response.sendError(401,"Failed Auth Token. " + (e.getMessage()==null?e.toString():e.getMessage()) + "\n" + debug.toString());
		}
	}
	
	private static Deployment getDeployment(HttpServletRequest request) throws Exception {
		// This method attempts to identify a unique registered Deployment entity based on the required
		// platform_id value and the optional lti_deployment_id and client_id values. The latter should 
		// be used in case the platform supports multiple deployments with different client_id values for the tool.
		// However, this is not technically required by the specifications. Hmm.
		Deployment d = null;
		String platform_id = request.getParameter("iss");   // this should be the platform_id URL (aud)
		if (platform_id.endsWith("/")) platform_id = platform_id.substring(0,platform_id.length()-1);  // strip any trailing / from platform_id

		URL platform = new URL(platform_id);
		if (!platform.getProtocol().equals("https")) throw new Exception("The platform_id must be a secure URL.");

		// Take the optimistic route first; this should always work if the deployment_id has been provided, else return null;
		String deployment_id = request.getParameter("lti_deployment_id");  // moodle, brightspace, blackboard
		if (deployment_id == null) deployment_id = request.getParameter("deployment_id");  // canvas
		if (deployment_id == null) deployment_id = request.getParameter("login_hint");  // schoology

		try {
			String platform_deployment_id = platform_id + "/" + deployment_id;
			d = ofy().load().type(Deployment.class).id(platform_deployment_id).now();  // previously used .safe()
			
			if (d != null) return d;
			
			// test to see if the platform has a single registered deployment
			Key<Deployment> kstart = key(Deployment.class, platform_id);
			Key<Deployment> kend = key(Deployment.class, platform_id + "/~");
			List<Deployment> range = ofy().load().type(Deployment.class).filterKey(">=", kstart).filterKey("<", kend).list();
			if (range.size()==1) return range.get(0);
			
			// experimental: automatic deployment registration
			
			if ("https://canvas.instructure.com".equals(platform_id)) {  // auto register canvas account
				String client_id = request.getParameter("client_id");
				String oidc_auth_url = "https://sso.canvaslms.com/api/lti/authorize_redirect";
				String oauth_access_token_url = "https://sso.canvaslms.com/login/oauth2/token";
				String well_known_jwks_url = "https://sso.canvaslms.com/api/lti/security/jwks";
				String contact_name = null;
				String email = null;
				String organization = null;
				String org_url = null;
				String lms = "canvas";
				d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,contact_name,email,organization,org_url,lms);
				d.status = "auto";
				d.nLicensesRemaining = 0;
				ofy().save().entity(d).now();
				String message = "<h3>Deployment Registration</h3>";
				try {
					String token = request.getParameter("lti_message_hint");
					String canvas_domain = JWT.decode(token).getClaims().get("canvas_domain").asString();
					message += "Canvas domain: " + canvas_domain + "<br/><br/>";
				} catch (Exception e) {}
				Map<String,String[]> params = request.getParameterMap();
				message += "Query parameters:<br/>";
				for (String name : params.keySet()) message += name + "=" + params.get(name)[0] + "<br/>";
				Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Automatic Canvas Registration",message);
				return d;
			} else if ("https://schoology.schoology.com".equals(platform_id)) {  // auto register schoology account
				String client_id = request.getParameter("client_id");
				String oidc_auth_url = "https://lti-service.svc.schoology.com/lti-service/authorize-redirect";
				String well_known_jwks_url = "https://lti-service.svc.schoology.com/lti-service/.well-known/jwks";
				String oauth_access_token_url = "https://lti-service.svc.schoology.com/lti-service/access-token";
				String contact_name = null;
				String email = null;
				String organization = null;
				String org_url = null;
				String lms = "schoology";
				d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,contact_name,email,organization,org_url,lms);
				d.status = "auto";
				d.nLicensesRemaining = 0;
				ofy().save().entity(d).now();
				Map<String,String[]> params = request.getParameterMap();
				String message = "<h3>Deployment Registration</h3>Query parameters:<br/>";
				for (String name : params.keySet()) message += name + "=" + params.get(name)[0] + "<br/>";
				Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Automatic Schoology Registration",message);
				return d;
			} else if ("https://blackboard.com".equals(platform_id)) {
				String client_id = request.getParameter("client_id");
				String oidc_auth_url = "https://developer.blackboard.com/api/v1/gateway/oidcauth";
				String well_known_jwks_url = "https://developer.blackboard.com/api/v1/management/applications/be1004de-6f8e-45b9-aae4-2c1370c24e1e/jwks.json";
				String oauth_access_token_url = "https://developer.blackboard.com/api/v1/gateway/oauth2/jwttoken";
				String contact_name = null;
				String email = null;
				String organization = null;
				String org_url = null;
				String lms = "blackboard";
				d = new Deployment(platform_id,deployment_id,client_id,oidc_auth_url,oauth_access_token_url,well_known_jwks_url,contact_name,email,organization,org_url,lms);
				d.status = "auto";
				d.nLicensesRemaining = 0;
				ofy().save().entity(d).now();
				Map<String,String[]> params = request.getParameterMap();
				String message = "<h3>Deployment Registration</h3>Query parameters:<br/>";
				for (String name : params.keySet()) message += name + "=" + params.get(name)[0] + "<br/>";
				Utilities.sendEmail("ChemVantage","admin@chemvantage.org","Automatic Blackboard Registration",message);
				return d;	
			} else {
				throw new Exception("Deployment Not Found");
			}
		} catch (Exception e) {
			// send advisory email to ChemVantage administrator:
			Map<String,String[]> params = request.getParameterMap();
			String message = "<h3>Deployment Not Found</h3>Query parameters:<br/>";
			for (String name : params.keySet()) message += name + "=" + params.get(name)[0] + "<br/>";
			Utilities.sendEmail("ChemVantage","admin@chemvantage.org","AuthToken Request Failure",message);
		}
		return d;
}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		doGet(request, response);
	}
	
}
