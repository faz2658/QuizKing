package com.fazeel.quizking.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** The claims checks on Google ID tokens (the signature check is Spring Security's JWKS decoder). */
class GoogleTokenVerifierTest {

	static final String CLIENT = "123-abc.apps.googleusercontent.com";

	static Jwt token(String iss, String aud, Instant exp) {
		return Jwt.withTokenValue("t").header("alg", "RS256").issuer(iss).audience(List.of(aud)).subject("g-1")
				.issuedAt(exp.minusSeconds(3600)).expiresAt(exp).build();
	}

	boolean ok(Jwt jwt) {
		return !GoogleTokenVerifier.validator(CLIENT).validate(jwt).hasErrors();
	}

	@Test
	void acceptsOnlyFreshGoogleTokensForThisApp() {
		Instant later = Instant.now().plusSeconds(600);
		assertThat(ok(token("https://accounts.google.com", CLIENT, later))).isTrue();
		assertThat(ok(token("accounts.google.com", CLIENT, later))).isTrue();
		assertThat(ok(token("https://evil.example", CLIENT, later))).isFalse();
		assertThat(ok(token("https://accounts.google.com", "someone-elses-app", later))).isFalse();
		assertThat(ok(token("https://accounts.google.com", CLIENT, Instant.now().minusSeconds(600)))).isFalse();
	}

	@Test
	void isOffWithoutAClientId() {
		GoogleTokenVerifier off = new GoogleTokenVerifier(" ");
		assertThat(off.enabled()).isFalse();
		assertThatThrownBy(() -> off.verify("anything")).isInstanceOf(GoogleSignInException.class);
		assertThat(new GoogleTokenVerifier(CLIENT).enabled()).isTrue();
	}
}
