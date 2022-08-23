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
			String platformId;
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
	@Index	String status;
	@Index	Date   created;
	@Index	Date   lastLogin;
			Date   expires;
	public	int    price = 0; // default monthly subscription price in $USD for individual student users
			int    nLicensesRemaining=5;
			
	Deployment() {}
	
	Deployment(String platform_id,String deployment_id,String client_id,String oidc_auth_url,String oauth_access_token_url,String well_known_jwks_url,String contact_name,String email,String organization,String org_url,String lms) 
			throws Exception {
		// Ensure that the platform_id is secure and does not end in a slash:
		if (platform_id.endsWith("/")) platform_id = platform_id.substring(0,platform_id.length()-1);
		if (deployment_id == null) deployment_id = "";
		
		URL platform = new URL(platform_id);
		if (!platform.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.platformId = platform_id;
		this.platform_deployment_id = platform_id + "/" + deployment_id;
		
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
		
		this.scope = "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem "
				+ "https://purl.imsglobal.org/spec/lti-ags/scope/lineitem.readonly "
				+ "https://purl.imsglobal.org/spec/lti-ags/scope/result.readonly "
				+ "https://purl.imsglobal.org/spec/lti-ags/scope/score "
				+ "https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly";
	}
			
	static Deployment getInstance(String platform_deployment_id) {
		try {
			return ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
		} catch (Exception e) {
			return null;
		}
	}
	
	String getDeploymentId() {
		return this.platform_deployment_id.substring(getPlatformId().length()+1);
	}
	
	String getPlatformId() {
		try {
			if (this.platformId != null) return this.platformId;
			String path = new URI(platform_deployment_id).getPath();
			int j = platform_deployment_id.length()-path.length();
			this.platformId = platform_deployment_id.substring(0,j);
			ofy().save().entity(this);
			return this.platformId;
		} catch (Exception e) {
			return null;
		}
	}
	public String getPlatformDeploymentId() {
		return this.platform_deployment_id;
	}
	
	public int getNLicensesRemaining() {
		return this.nLicensesRemaining;
	}
	
	public void putNLicensesRemaining(int n) {
		this.nLicensesRemaining = n;
		ofy().save().entity(this);
	}
	
	public String getOrganization() {
		return this.organization;
	}
	
	boolean equivalentTo(Deployment d) {
		return	((this.platform_deployment_id != null && this.platform_deployment_id.equals(d.platform_deployment_id)) 	|| (d.platform_deployment_id == null && this.platform_deployment_id == null)) &&
				((this.client_id != null && this.client_id.equals(d.client_id)) 										|| (d.client_id == null && this.client_id == null)) &&
				((this.oauth_access_token_url != null && this.oauth_access_token_url.equals(d.oauth_access_token_url)) 	|| (d.oauth_access_token_url == null && this.oauth_access_token_url == null)) &&
				((this.oidc_auth_url != null && this.oidc_auth_url.equals(d.oidc_auth_url)) 							|| (d.oidc_auth_url == null && this.oidc_auth_url == null)) &&
				((this.well_known_jwks_url != null && this.well_known_jwks_url.equals(d.well_known_jwks_url)) 			|| (d.well_known_jwks_url == null && this.well_known_jwks_url == null)) &&
				((this.email != null && this.email.equals(d.email)) 													|| (d.email == null && this.email == null)) &&
				((this.contact_name != null && this.contact_name.equals(d.contact_name))								|| (d.contact_name == null && this.contact_name == null)) &&
				((this.organization != null && this.organization.equals(d.organization)) 								|| (d.organization == null && this.organization == null)) &&
				((this.org_url != null && this.org_url.equals(d.org_url)) 												|| (d.org_url == null && this.org_url == null)) &&
				((this.lms_type != null && this.lms_type.equals(d.lms_type)) 											|| (d.lms_type == null && this.lms_type == null)) &&
				((this.rsa_key_id != null && this.rsa_key_id.equals(d.rsa_key_id)) 										|| (d.rsa_key_id == null && this.rsa_key_id == null)) &&
				((this.scope != null && this.scope.equals(d.scope)) 													|| (d.scope == null && this.scope == null)) &&
				(this.price == d.price) &&
				(this.nLicensesRemaining == d.nLicensesRemaining) &&
				(this.created != null && this.created.equals(d.created));
	}
	
	protected Deployment clone() throws CloneNotSupportedException {
		return (Deployment) super.clone();
	}
}




