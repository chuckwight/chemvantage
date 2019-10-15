package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet(urlPatterns = {"/jwks", "/jwks/"})
public class KeyStore extends HttpServlet {
	private static final long serialVersionUID = 1L;
      
	private static String jwks = null;
	private static Map<String,RSAKeyPair> rsaKeys = new HashMap<String,RSAKeyPair>();
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		if (jwks == null) buildJwks();
		response.getWriter().println(jwks);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	protected static String getjwks() {
		if (jwks == null) return buildJwks();
		else return jwks;
	}
	
	protected static JsonObject getJwk(String rsa_key_id) {
		try {
			RSAPublicKey pk = getRSAPublicKey(rsa_key_id);
			JsonObject jwk = new JsonObject();
			jwk.addProperty("kty", pk.getAlgorithm());
			jwk.addProperty("kid", rsa_key_id);
			jwk.addProperty("n", Base64.getUrlEncoder().encodeToString(pk.getModulus().toByteArray()));
			jwk.addProperty("e", Base64.getUrlEncoder().encodeToString(pk.getPublicExponent().toByteArray()));
			jwk.addProperty("alg", "RS256");
			jwk.addProperty("use", "sig");
			return jwk;
		} catch (Exception e) {
			return null;
		}	
	}
	
	protected static String buildJwks() {
		if (rsaKeys.isEmpty()) createNewKeyMap();
		
		JsonObject json = new JsonObject();
		JsonArray keys = new JsonArray();
		for (RSAKeyPair k : rsaKeys.values()) {
			try {		
				JsonObject jwk = new JsonObject();
				jwk.addProperty("kty", "RSA");
				jwk.addProperty("kid", k.kid);
				RSAPublicKey pub = k.getRSAPublicKey();
				jwk.addProperty("n", Base64.getUrlEncoder().encodeToString(pub.getModulus().toByteArray()));
				jwk.addProperty("e", Base64.getUrlEncoder().encodeToString(pub.getPublicExponent().toByteArray()));
				jwk.addProperty("alg", "RS256");
				jwk.addProperty("use", "sig");
				keys.add(jwk);
			} catch (Exception e) {
			}			
		}
		json.add("keys", keys);
		jwks = json.toString();
		return jwks;
	}
	
	protected static void createNewKeyMap() {
		List<RSAKeyPair> keypairs = ofy().load().type(RSAKeyPair.class).list();
		if (keypairs.size()==0) makeNewRSAKey();
		else for (RSAKeyPair kp: keypairs) rsaKeys.put(kp.kid, kp);
	}
	
	protected static void makeNewRSAKey() {
		RSAKeyPair kp = new RSAKeyPair();
		rsaKeys.put(kp.kid,kp);
	    ofy().save().entity(kp);	        		
	}
	
	protected static RSAPublicKey getRSAPublicKey(String kid) {
		try {
			RSAKeyPair kp = ofy().load().type(RSAKeyPair.class).id(kid).safe();
			return kp.getRSAPublicKey();
		} catch (Exception e) {
			return null;
		}
	}
	
	protected static RSAPrivateKey getRSAPrivateKey(String kid) {
		try {
			RSAKeyPair kp = ofy().load().type(RSAKeyPair.class).id(kid).safe();
			return kp.getRSAPrivateKey();
		} catch (Exception e) {
			return null;
		}
	}
	
	protected static String getAKeyId() {
		// this method retrieves one of the available ChemVantage rsa_key_id values (randomly)
		if (rsaKeys.isEmpty()) createNewKeyMap();
		int n = new Random().nextInt(rsaKeys.size());
		List<RSAKeyPair> keys = new ArrayList<RSAKeyPair>(rsaKeys.values());
		RSAKeyPair k = keys.get(n);
		return k.kid;
	}
	
	protected static String getRSAPublicKeyX509(String rsa_key_id) {
		// This method converts the RSA Public Key to X.509 so that it can be printed and shared
		StringBuffer key = new StringBuffer("-----BEGIN PUBLIC KEY-----" + "\n");
        try {
        	RSAPublicKey pubkey = getRSAPublicKey(rsa_key_id);
	        String pub = Base64.getEncoder().encodeToString(pubkey.getEncoded());
	        for(int n = 0; n < pub.length(); n+=64) {
	        	key.append(pub.substring(n,(n+64>pub.length()?pub.length():n+64)) + "\n");
	        }
	        key.append("-----END PUBLIC KEY-----" + "<p>");
		} catch (Exception e) {
			return e.toString();
		}
		return key.toString();
	}

}
