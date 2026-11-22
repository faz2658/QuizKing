package com.fazeel.quizcadia.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.fazeel.quizcadia.account.Account;

/**
 * Issues signed access tokens. The subject is the user id; the role is in the "roles" claim. They are short-lived
 * (quizcadia.jwt.lifetime, 15 minutes by default): the refresh cookie (SessionService) gets the next one.
 */
public class TokenService {

	public static final String ISSUER = "quizcadia-login";

	private final JwtEncoder encoder;
	private final Duration lifetime;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, Duration lifetime, Clock clock) {
		this.encoder = encoder;
		this.lifetime = lifetime;
		this.clock = clock;
	}

	/** A short-lived access token for this account (subject = user id, with name, email and role). */
	public IssuedToken issue(Account account) {
		return sign(account.getUserId(), account.getUserName(), account.getEmail(), account.getRole().name());
	}

	/** A token for someone playing without an account: no email, role GUEST, nothing stored. */
	public IssuedToken issueGuest(int guestId, String name) {
		return sign(guestId, name, "", GUEST);
	}

	public static final String GUEST = "GUEST";

	private IssuedToken sign(int userId, String name, String email, String role) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(lifetime);
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).issuedAt(now).expiresAt(expiresAt)
				.subject(String.valueOf(userId)).claim("name", name).claim("email", email)
				.claim("roles", List.of(role)).build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	/** A signed token and when it expires. */
	public record IssuedToken(String value, Instant expiresAt) {
	}
}
