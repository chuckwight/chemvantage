package org.chemvantage;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

@Entity
public class RSAKeyPair {
	@Id String kid;
	byte[] pub;
	byte[] pri;
	
	RSAKeyPair() {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
	        keyGen.initialize(2048);
	        KeyPair keyPair = keyGen.genKeyPair();
	        pri = keyPair.getPrivate().getEncoded();
	        pub = keyPair.getPublic().getEncoded();
	        kid = UUID.randomUUID().toString();
	    } catch (Exception e) {			
		}
	}
	
	protected RSAPublicKey getRSAPublicKey() {
		try {
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pub));
		} catch (Exception e) {
			return null;
		}
	}
	
	protected RSAPrivateKey getRSAPrivateKey() {
		try {
			return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pri));
		} catch (Exception e) {
			return null;
		}
	}

}
