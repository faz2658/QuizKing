package com.fazeel.quizking.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Verifies the HS256 tokens the login service signs. The subject is the user id and the "roles" claim
 * (USER, ADMIN or GUEST) becomes ROLE_* authorities.
 */
@Configuration(proxyBeanMethods = false)
public class JwtSupport {

	/** Must match the login service's TokenService.ISSUER. */
	public static final String ISSUER = "quiz-king-login";

	private static final Logger log = LoggerFactory.getLogger(JwtSupport.class);

	@Bean
	JwtDecoder quizKingJwtDecoder(@Value("${quizking.jwt.secret:}") String secret) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	@Bean
	JwtAuthenticationConverter quizKingJwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	/** Same key rules as the login service: base64 or plain text, at least 32 bytes. */
	static SecretKey key(String secret) {
		byte[] bytes;
		if (secret == null || secret.isBlank()) {
			// No secret: a random key means every token is rejected (fail closed), which is fine for tests
			bytes = new byte[32];
			new SecureRandom().nextBytes(bytes);
			log.warn("QUIZKING_JWT_SECRET is not set: this service will reject every token");
		}
		else {
			bytes = decode(secret);
			if (bytes.length < 32) {
				throw new IllegalStateException("QUIZKING_JWT_SECRET must be at least 32 bytes");
			}
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	private static byte[] decode(String secret) {
		try {
			return Base64.getDecoder().decode(secret);
		}
		catch (IllegalArgumentException notBase64) {
			return secret.getBytes(StandardCharsets.UTF_8);
		}
	}
}
