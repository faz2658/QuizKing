package com.fazeel.quizking.google;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Checks a "Sign in with Google" ID token: signed by Google (keys fetched from Google and cached), not
 * expired, issued by Google, and meant for this app's client id. Disabled while GOOGLE_CLIENT_ID is unset.
 */
@Component
public class GoogleTokenVerifier {

	static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
	static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final String clientId;
	private final JwtDecoder decoder;

	public GoogleTokenVerifier(@Value("${quizking.google.client-id:}") String clientId) {
		this.clientId = clientId == null ? "" : clientId.trim();
		this.decoder = this.clientId.isEmpty() ? null : decoder(this.clientId);
	}

	public boolean enabled() {
		return decoder != null;
	}

	/** The client id the browser needs to show the Google button (public, not a secret). */
	public String clientId() {
		return clientId;
	}

	public GoogleIdentity verify(String credential) {
		if (!enabled()) {
			throw new GoogleSignInException("Google sign-in is not set up on this server");
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(credential);
		}
		catch (JwtException e) {
			throw new GoogleSignInException("Google sign-in failed, please try again");
		}
		Boolean verified = jwt.getClaimAsBoolean("email_verified");
		return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), Boolean.TRUE.equals(verified),
				jwt.getClaimAsString("name"));
	}

	static JwtDecoder decoder(String clientId) {
		NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWKS).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		d.setJwtValidator(validator(clientId));
		return d;
	}

	static DelegatingOAuth2TokenValidator<Jwt> validator(String clientId) {
		return new DelegatingOAuth2TokenValidator<>(List.of(new JwtTimestampValidator(), jwt -> {
			String iss = jwt.getClaimAsString("iss");
			List<String> aud = jwt.getAudience();
			boolean ok = iss != null && ISSUERS.contains(iss) && aud != null && aud.contains(clientId)
					&& jwt.getSubject() != null;
			return ok ? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not a Google token for this app", null));
		}));
	}
}
