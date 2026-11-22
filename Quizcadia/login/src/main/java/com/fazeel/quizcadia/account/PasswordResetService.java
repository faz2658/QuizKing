package com.fazeel.quizcadia.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fazeel.quizcadia.mail.Mailer;
import com.fazeel.quizcadia.session.SessionService;

/**
 * Forgot password: emails a one-time link that lets the owner of the address choose a new password.
 * The request answers the same way whether or not the email has an account, so it can't be used to find
 * out who plays here.
 */
@Service
public class PasswordResetService {

	static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
	/** At most this many emails per account per hour, so nobody can flood someone's inbox. */
	static final int MAX_PER_HOUR = 3;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountRepository accounts;
	private final PasswordResetRepository resets;
	private final PasswordEncoder encoder;
	private final Mailer mailer;
	private final Clock clock;
	private final String publicUrl;
	private final SessionService sessions;

	public PasswordResetService(AccountRepository accounts, PasswordResetRepository resets, PasswordEncoder encoder,
			Mailer mailer, Clock clock, @Value("${quizcadia.public-url}") String publicUrl,
			SessionService sessions) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.resets = resets;
		this.encoder = encoder;
		this.mailer = mailer;
		this.clock = clock;
		this.publicUrl = publicUrl.replaceAll("/+$", "");
	}

	/**
	 * Emails a reset link valid for 30 minutes, at most 3 an hour per account. Does nothing, and says nothing, for
	 * an unknown email; also clears links that expired over a day ago.
	 */
	@Transactional
	public void requestReset(String email) {
		Instant now = clock.instant();
		resets.deleteByExpiresAtBefore(now.minus(Duration.ofDays(1)));
		Optional<Account> found = accounts.findByEmailIgnoreCase(AccountService.normalize(email));
		if (found.isEmpty()) {
			return;
		}
		Account account = found.get();
		if (resets.countByUserIdAndCreatedAtAfter(account.getUserId(), now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
			return;
		}
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		resets.save(new PasswordReset(account.getUserId(), hash(token), now, now.plus(LINK_LIFETIME)));
		mailer.send(account.getEmail(), "Reset your QuizCadia password", """
				Hi %s,

				Someone (hopefully you) asked to reset the password for your QuizCadia account.
				Choose a new password here (the link works once, for %d minutes):

				%s/reset-password?token=%s

				If you didn't ask for this, ignore this email: your password stays the same.
				""".formatted(account.getUserName(), LINK_LIFETIME.toMinutes(), publicUrl, token));
	}

	/** Sets the new password and uses up every open link for the account. @return the account, to log in */
	@Transactional
	public Account reset(String token, String newPassword) {
		Instant now = clock.instant();
		PasswordReset reset = resets.findByTokenHash(hash(token == null ? "" : token.trim()))
				.filter(r -> r.usableAt(now)).orElseThrow(InvalidResetLinkException::new);
		Account account = accounts.findById(reset.getUserId()).orElseThrow(InvalidResetLinkException::new);
		account.changePassword(encoder.encode(newPassword));
		account.markEmailVerified(); // the link reached their inbox
		resets.findByUserIdAndUsedAtIsNull(account.getUserId()).forEach(r -> r.use(now));
		sessions.endAll(account.getUserId()); // whoever knew the old password is signed out everywhere
		return account;
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
