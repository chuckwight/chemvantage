package org.chemvantage;

import java.net.URI;
import java.net.URL;

import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

public class ProvisionalDeployment implements java.lang.Cloneable {
	@Id 	Long id;
	@Index	String client_id;
			String platformId;
			String email;
			String contact_name;
			String organization;
			String org_url;
	
	ProvisionalDeployment() {}
	
	ProvisionalDeployment(String platform_id,String client_id,String contact_name,String email,String organization,String org_url) 
			throws Exception {
		// Ensure that the platform_id is secure and does not end in a slash:
		if (platform_id.endsWith("/")) platform_id = platform_id.substring(0,platform_id.length()-1);
		
		URL platform = new URI(platform_id).toURL();
		if (!platform.getProtocol().equals("https")) throw new Exception("All URLs must be secure (https)");
		this.platformId = platform_id;		
		this.client_id = client_id;
		this.contact_name = contact_name;
		this.email = email;
		this.organization = organization;
		this.org_url = org_url;
	}
}
