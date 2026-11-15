package com.stackroute.maverick.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.stackroute.maverick.account.Account;

/** Issues signed access tokens. The subject is the user id; the role is in the "roles" claim. */
public class TokenService {

	public static final String ISSUER = "maverick-login";

	private final JwtEncoder encoder;
	private final Duration lifetime;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, Duration lifetime, Clock clock) {
		this.encoder = encoder;
		this.lifetime = lifetime;
		this.clock = clock;
	}

	public IssuedToken issue(Account account) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(lifetime);
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).issuedAt(now).expiresAt(expiresAt)
				.subject(String.valueOf(account.getUserId())).claim("name", account.getUserName())
				.claim("email", account.getEmail()).claim("roles", java.util.List.of(account.getRole().name()))
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
