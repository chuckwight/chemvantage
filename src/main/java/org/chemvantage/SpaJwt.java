package org.chemvantage;

import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

public final class SpaJwt {
	private static final long TOKEN_TTL_MILLIS = 90 * 60 * 1000L;

	private SpaJwt() {}

	static String issue(User user) {
		Date now = new Date();
		Date exp = new Date(now.getTime() + TOKEN_TTL_MILLIS);
		Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
		return JWT.create()
				.withIssuer(Subject.getServerUrl())
				.withIssuedAt(now)
				.withExpiresAt(exp)
				.withClaim("sig", user.getTokenSignature())
				.withClaim("nonce", Nonce.generateNonce())
				.sign(algorithm);
	}

	static String getSig(String token) {
		if (token == null || token.isBlank()) return null;
		try {
			Algorithm algorithm = Algorithm.HMAC256(Subject.getHMAC256Secret());
			DecodedJWT jwt = JWT.require(algorithm)
					.withIssuer(Subject.getServerUrl())
					.build()
					.verify(token);
            String nonce = jwt.getClaim("nonce").asString();
            if (!Nonce.isUnique(nonce)) throw new Exception();
			return jwt.getClaim("sig").asString();
		} catch (Exception e) {
			return null;
		}
	}
}
