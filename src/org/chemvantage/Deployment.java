package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.net.URI;
import java.net.URL;
import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Deployment implements java.lang.Cloneable {
	@Id 	String platform_deployment_id;
	@Index	String client_id;
			String oauth_access_token_url;
			String oidc_auth_url;
			String well_known_jwks_url;
			String email;
			String contact_name;
			String organization;
			String org_url;
			String lms_type;
			String rsa_key_id;
			String scope;
			String claims;
	@Index	Date   created;
	@Index	Date   lastLogin;
			
	Deployment() {}
	
	Deployment(String platform_id,String deployment_id,String client_id,String oidc_auth_url,String oauth_access_token_url,String well_known_jwks_url,String contact_name,String email,String organization,String org_url,String lms) 
			throws Exception {
		// Ensure that the platform_id is secure and does not end in a slash:
		URL platform = new URL(platform_id);
		if (!platform.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.platform_deployment_id = new URL(platform.getProtocol(),platform.getHost(),platform.getPort(),"/"+deployment_id).toString();
		
		URL auth = new URL(oidc_auth_url);
		if (!auth.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.oidc_auth_url = auth.toString();
		
		URL token = new URL(oauth_access_token_url);
		if (!token.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.oauth_access_token_url = token.toString();		
		
		URL jwks = new URL(well_known_jwks_url);
		if (!jwks.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.well_known_jwks_url = jwks.toString();
		
		this.client_id = client_id;
		this.contact_name = contact_name;
		this.email = email;
		this.organization = organization;
		this.org_url = org_url;
		this.lms_type = lms;
		this.rsa_key_id = KeyStore.getAKeyId(lms);
		this.created = new Date();
	}
			
	static Deployment getInstance(String platform_deployment_id) {
		try {
			return ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
		} catch (Exception e) {
			return null;
		}
	}
	
	String getDeploymentId() {
		try {
			String path = new URI(platform_deployment_id).getPath();
			return path.substring(1);  // removes the leading slash character, if any
		} catch (Exception e) {
			return "";
		}
	}
	
	String getPlatformId() {
		try {
			String path = new URI(platform_deployment_id).getPath();
			int j = platform_deployment_id.length()-path.length();
			return platform_deployment_id.substring(0,j);
		} catch (Exception e) {
			return null;
		}
	}
	
	boolean equivalentTo(Deployment d) {
		if (d==null) return false;
		
		return	((this.platform_deployment_id != null && this.platform_deployment_id.contentEquals(d.platform_deployment_id)) 	|| (d.platform_deployment_id == null && this.platform_deployment_id == null)) &&
				((this.client_id != null && this.client_id.contentEquals(d.client_id)) 											|| (d.client_id == null && this.client_id == null)) &&
				((this.oauth_access_token_url != null && this.oauth_access_token_url.contentEquals(d.oauth_access_token_url)) 	|| (d.oauth_access_token_url == null && this.oauth_access_token_url == null)) &&
				((this.oidc_auth_url != null && this.oidc_auth_url.contentEquals(d.oidc_auth_url)) 								|| (d.oidc_auth_url == null && this.oidc_auth_url == null)) &&
				((this.well_known_jwks_url != null && this.well_known_jwks_url.contentEquals(d.well_known_jwks_url)) 			|| (d.well_known_jwks_url == null && this.well_known_jwks_url == null)) &&
				((this.email != null && this.email.contentEquals(d.email)) 														|| (d.email == null && this.email == null)) &&
				((this.contact_name != null && this.contact_name.contentEquals(d.contact_name))									|| (d.contact_name == null && this.contact_name == null)) &&
				((this.organization != null && this.organization.contentEquals(d.organization)) 								|| (d.organization == null && this.organization == null)) &&
				((this.org_url != null && this.org_url.contentEquals(d.org_url)) 												|| (d.org_url == null && this.org_url == null)) &&
				((this.lms_type != null && this.lms_type.contentEquals(d.lms_type)) 											|| (d.lms_type == null && this.lms_type == null)) &&
				((this.rsa_key_id != null && this.rsa_key_id.contentEquals(d.rsa_key_id)) 										|| (d.rsa_key_id == null && this.rsa_key_id == null)) &&
				((this.scope != null && this.scope.contentEquals(d.scope)) 														|| (d.scope == null && this.scope == null)) &&
				(this.created != null && this.created.equals(d.created)) &&
				(this.lastLogin != null && this.lastLogin.equals(d.lastLogin));
	}
	
	protected Deployment clone() throws CloneNotSupportedException {
		return (Deployment) super.clone();
	}
}




