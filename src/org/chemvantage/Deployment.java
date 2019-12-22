package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.net.URI;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Deployment {
	@Id 	String platform_deployment_id;
	@Index	String client_id;
			String oauth_access_token_url;
			String oidc_auth_url;
			String well_known_jwks_url;
			String email;
			String contact_name;
			String organization;
			String org_url;
			String rsa_key_id;

	Deployment() {}
	
	Deployment(String platform_id,String deployment_id,String client_id,String oidc_auth_url,String oauth_access_token_url,String well_known_jwks_url,String contact_name,String email,String organization,String org_url) {
		this.platform_deployment_id = platform_id + "/" + deployment_id;
		this.client_id = client_id;
		this.oidc_auth_url = oidc_auth_url;
		this.oauth_access_token_url = oauth_access_token_url;		
		this.well_known_jwks_url = well_known_jwks_url;
		this.contact_name = contact_name;
		this.email = email;
		this.organization = organization;
		this.org_url = org_url;
		this.rsa_key_id = KeyStore.getAKeyId();
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
}




