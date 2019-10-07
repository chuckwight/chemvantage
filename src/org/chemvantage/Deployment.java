package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.net.URI;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import com.googlecode.objectify.Key;
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
			byte[] rsa_private_key;

	Deployment() {}
	
	Deployment(String platform_id,String deployment_id,String client_id,String oidc_auth_url,String oauth_access_token_url,String well_known_jwks_url,String email) {
		this.platform_deployment_id = platform_id + "/" + deployment_id;
		this.client_id = client_id;
		this.oidc_auth_url = oidc_auth_url;
		this.oauth_access_token_url = oauth_access_token_url;		
		this.well_known_jwks_url = well_known_jwks_url;
		this.email = email;
	}

	static Deployment getInstance(String platform_id,String deployment_id) {
		Deployment d = null;
		if (deployment_id==null) deployment_id = "";  // may be empty String for 1-deployment platforms
		if (!platform_id.startsWith("http")) platform_id = "http://" + platform_id; // make it into a URL
		String platform_deployment_id = platform_id + "/" + deployment_id;
		try {
				return ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
		} catch (Exception e) {
			if (deployment_id.isEmpty()) { // look for exactly one child deployment from this platform
				Key<Deployment> k = Key.create(Deployment.class,platform_id+"/");
				Deployment child = ofy().load().type(Deployment.class).filterKey(">", k).limit(1).first().safe();
				if (child.platform_deployment_id.startsWith(platform_id + "/")) d = child;
				d.platform_deployment_id = platform_id + "/";
				ofy().save().entity(d);
			} else { // check to see if we should create a new child Deployment for this platform
				try {
					d = ofy().load().type(Deployment.class).id(platform_id + "/").safe(); // gets parent Deployment
					d.platform_deployment_id = platform_deployment_id;
					ofy().save().entity(d);
				} catch (Exception e1) {
				}
			}
		} 
		return d;
	}
	
	static Deployment getInstance(String platform_deployment_id) {
		try {
			return ofy().load().type(Deployment.class).id(platform_deployment_id).safe();
		} catch (Exception e) {
			return null;
		}
	}
	
	String getRSAPublicKey() {
		// This method computes a NEW RSA key pair, stores the private key 
		// in PEM format (RFC 7468) and returns the public key
		StringBuffer key;
		StringBuffer debug = new StringBuffer("Debug getRSAPublicKey:" + "<br>");
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
	        keyGen.initialize(2048);
	        KeyPair keyPair = keyGen.genKeyPair();
	        this.rsa_private_key = keyPair.getPrivate().getEncoded();
	        ofy().save().entity(this);
	        
	        Encoder enc = Base64.getEncoder();
	        String pub = enc.encodeToString(keyPair.getPublic().getEncoded());
	        debug.append("Public Key: " + pub + "<br>");
	        debug.append("Public key length: " + pub.length() + "<br>");
	        key = new StringBuffer("-----BEGIN PUBLIC KEY-----" + "<br>");
	        for(int n = 0; n < pub.length(); n+=64) {
	        	key.append(pub.substring(n,(n+64>pub.length()?pub.length():n+64)) + "<br>");
	        }
	        key.append("-----END PUBLIC KEY-----" + "<p>");
		} catch (Exception e) {
			return e.toString() + "<p>" + debug.toString();
		}
		return key.toString();
	}
	
	RSAPrivateKey getRSAPrivateKey() {
		if (this.rsa_private_key == null) return null;
		Decoder dec = Base64.getDecoder();
		String key = this.rsa_private_key.toString();
		if (key.startsWith("-----BEGIN ")) { // clean up the key contents
			key = key.substring(key.indexOf("PRIVATE KEY-----")+16);  // strips header (e.g. "-----BEGIN RSA PUBLIC KEY-----")
			key = key.substring(key.indexOf("-----END ",key.length()));  // strips footer
			key = key.replaceAll("\\n", ""); // removes all line separators
			this.rsa_private_key = dec.decode(key);
			ofy().save().entity(this);
		}
		try {
			EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(this.rsa_private_key);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);
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




