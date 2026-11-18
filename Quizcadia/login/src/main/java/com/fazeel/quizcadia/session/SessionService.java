package com.fazeel.quizcadia.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fazeel.quizcadia.account.Account;
import com.fazeel.quizcadia.account.AccountRepository;
import com.fazeel.quizcadia.security.TokenService;
import com.fazeel.quizcadia.security.TokenService.IssuedToken;

/**
 * Keeps players signed in with short access tokens and rotating refresh tokens.
 * <ul>
 * <li>Each refresh uses the refresh token up and hands out the next one; a used-up token that comes back
 * later means it was copied, so that whole sign-in is ended.</li>
 * <li>A token used again within {@link #GRACE} (two tabs refreshing at once) only gets a new access token.</li>
 * <li>Signing out ends the sign-in; a password reset or Google taking over an account ends all of them.</li>
 * </ul>
 * Services other than this one keep checking the access token alone, so an ended sign-in can still call
 * them until its access token runs out (quizcadia.jwt.lifetime, 15 minutes by default).
 */
@Service
public class SessionService {

	private static final Logger log = LoggerFactory.getLogger(SessionService.class);

	static final Duration GRACE = Duration.ofSeconds(30);

	private final RefreshSessionRepository sessions;
	private final AccountRepository accounts;
	private final TokenService tokens;
	private final Clock clock;
	private final Duration idle;
	private final Duration absolute;
	private final Duration guestLifetime;
	private final SecureRandom random = new SecureRandom();

	public SessionService(RefreshSessionRepository sessions, AccountRepository accounts, TokenService tokens, Clock clock,
			@Value("${quizcadia.session.idle:30d}") Duration idle,
			@Value("${quizcadia.session.absolute:90d}") Duration absolute,
			@Value("${quizcadia.session.guest:1d}") Duration guestLifetime) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.tokens = tokens;
		this.clock = clock;
		this.idle = idle;
		this.absolute = absolute;
		this.guestLifetime = guestLifetime;
	}

	/** A refresh token to put in the cookie, and when it stops working. */
	public record Issued(String token, Instant expiresAt) {
	}

	/** A new access token for the player, and the next refresh token (null inside the grace period). */
	public record Renewal(IssuedToken access, int userId, String userName, String email, String role, Issued refresh) {
	}

	/** Starts a sign-in for an account (log in, sign up, Google, password reset). */
	@Transactional
	public Issued start(Account account) {
		return begin(account.getUserId(), false, null, idle, absolute);
	}

	/** Starts a guest's sign-in: no account, so the name is kept with the session. */
	@Transactional
	public Issued startGuest(int guestId, String name) {
		return begin(guestId, true, name, guestLifetime, guestLifetime);
	}

	private Issued begin(int userId, boolean guest, String guestName, Duration idleFor, Duration lastsFor) {
		Instant now = clock.instant();
		sessions.deleteEndedBefore(now.minus(Duration.ofDays(1)));
		Instant familyEnd = now.plus(lastsFor);
		return add(UUID.randomUUID().toString(), userId, guest, guestName, now, min(now.plus(idleFor), familyEnd), familyEnd);
	}

	/** Swaps a refresh token for a new access token and the next refresh token. */
	@Transactional(noRollbackFor = SessionEndedException.class)
	public Renewal refresh(String token) {
		Instant now = clock.instant();
		RefreshSession s = find(token);
		if (s.getRevokedAt() != null || s.expiredAt(now)) {
			throw new SessionEndedException();
		}
		if (s.getUsedAt() != null) {
			if (s.getUsedAt().plus(GRACE).isAfter(now)) {
				return renewal(s, null);
			}
			log.warn("A used refresh token came back for user {}: ending that sign-in", s.getUserId());
			endFamily(s.getFamilyId(), now);
			throw new SessionEndedException();
		}
		if (sessions.markUsed(s.getId(), now) == 0) {
			return renewal(s, null); // a second tab got there first: it has already set the next cookie
		}
		Duration idleFor = s.isGuest() ? guestLifetime : idle;
		Issued next = add(s.getFamilyId(), s.getUserId(), s.isGuest(), s.getGuestName(), now,
				min(now.plus(idleFor), s.getFamilyExpiresAt()), s.getFamilyExpiresAt());
		return renewal(s, next);
	}

	private Renewal renewal(RefreshSession s, Issued next) {
		if (s.isGuest()) {
			IssuedToken access = tokens.issueGuest(s.getUserId(), s.getGuestName());
			return new Renewal(access, s.getUserId(), s.getGuestName(), "", TokenService.GUEST, next);
		}
		Account a = accounts.findById(s.getUserId()).orElse(null);
		if (a == null) {
			endFamily(s.getFamilyId(), clock.instant());
			throw new SessionEndedException();
		}
		return new Renewal(tokens.issue(a), a.getUserId(), a.getUserName(), a.getEmail(), a.getRole().name(), next);
	}

	/** Signs this browser out. Unknown tokens are ignored: signing out always works. */
	@Transactional
	public void end(String token) {
		if (token == null || token.isBlank()) {
			return;
		}
		sessions.findByTokenHash(hash(token)).ifPresent(s -> endFamily(s.getFamilyId(), clock.instant()));
	}

	/** Signs an account out everywhere (password reset, Google taking over the account, "sign out everywhere"). */
	@Transactional
	public void endAll(int userId) {
		Instant now = clock.instant();
		sessions.findByUserIdAndGuestFalseAndRevokedAtIsNull(userId).forEach(s -> s.revoke(now));
	}

	private void endFamily(String familyId, Instant now) {
		sessions.findByFamilyIdAndRevokedAtIsNull(familyId).forEach(s -> s.revoke(now));
	}

	private RefreshSession find(String token) {
		if (token == null || token.isBlank() || token.length() > 100) {
			throw new SessionEndedException();
		}
		return sessions.findByTokenHash(hash(token)).orElseThrow(SessionEndedException::new);
	}

	private Issued add(String familyId, int userId, boolean guest, String guestName, Instant now, Instant expiresAt,
			Instant familyEnd) {
		byte[] b = new byte[32];
		random.nextBytes(b);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
		sessions.save(new RefreshSession(familyId, userId, guest, guestName, hash(token), now, expiresAt, familyEnd));
		return new Issued(token, expiresAt);
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	static String hash(String token) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
