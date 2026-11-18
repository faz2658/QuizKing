package com.fazeel.quizcadia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class JwtSupportTest {

	static final String SECRET = "a-test-secret-that-is-at-least-32-bytes-long";

	final JwtSupport support = new JwtSupport();

	String token(String secret, String issuer, String subject, List<String> roles) {
		var encoder = NimbusJwtEncoder.withSecretKey(JwtSupport.key(secret)).algorithm(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).claim("roles", roles)
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	@Test
	void acceptsTheLoginServicesTokensAndMapsRoles() {
		JwtDecoder decoder = support.quizcadiaJwtDecoder(SECRET);
		var jwt = decoder.decode(token(SECRET, JwtSupport.ISSUER, "42", List.of("ADMIN")));

		Authentication auth = support.quizcadiaJwtAuthenticationConverter().convert(jwt);
		assertThat(auth.getName()).isEqualTo("42");
		assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_ADMIN").doesNotContain("ROLE_USER");
	}

	@Test
	void noLongerAcceptsTokensSignedBeforeTheRename() {
		// they lasted 8 hours at most, and the rename has been live for longer than that
		assertThatThrownBy(() -> support.quizcadiaJwtDecoder(SECRET).decode(token(SECRET, "quiz-king-login", "42",
				List.of("USER")))).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsTokensSignedWithAnotherSecretOrIssuer() {
		JwtDecoder decoder = support.quizcadiaJwtDecoder(SECRET);

		assertThatThrownBy(() -> decoder.decode(token(SECRET + "-other", JwtSupport.ISSUER, "1", List.of("USER"))))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(SECRET, "someone-else", "1", List.of("USER"))))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void withoutASecretEveryTokenIsRejected() {
		JwtDecoder decoder = support.quizcadiaJwtDecoder("");
		assertThatThrownBy(() -> decoder.decode(token(SECRET, JwtSupport.ISSUER, "1", List.of("USER"))))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void shortSecretsAreRefused() {
		assertThatThrownBy(() -> JwtSupport.key("too-short")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void selfOrAdminComparesThePathUserIdWithTheToken() {
		var rule = ApiSecurity.selfOrAdmin();
		var context = new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("userId", "7"));

		assertThat(rule.authorize(() -> caller("7", "ROLE_USER"), context).isGranted()).isTrue();
		assertThat(rule.authorize(() -> caller("8", "ROLE_USER"), context).isGranted()).isFalse();
		assertThat(rule.authorize(() -> caller("8", "ROLE_GUEST"), context).isGranted()).isFalse();
		assertThat(rule.authorize(() -> caller("1", "ROLE_ADMIN"), context).isGranted()).isTrue();
		assertThat(rule.authorize(() -> null, context).isGranted()).isFalse();
	}

	static Authentication caller(String id, String role) {
		return UsernamePasswordAuthenticationToken.authenticated(id, null, List.of(new SimpleGrantedAuthority(role)));
	}
}
