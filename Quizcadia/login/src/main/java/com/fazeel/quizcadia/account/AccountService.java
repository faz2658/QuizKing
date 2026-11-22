package com.fazeel.quizcadia.account;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fazeel.quizcadia.account.Account.Role;
import com.fazeel.quizcadia.google.GoogleIdentity;
import com.fazeel.quizcadia.google.GoogleSignInException;
import com.fazeel.quizcadia.session.SessionService;

/**
 * The account rules: signing up, checking passwords (with a throttle against guessing), and signing in with
 * Google, linking it to an existing account with the same email.
 */
@Service
public class AccountService {

	/** Hash of a random password, compared against when the email is unknown so timing doesn't leak it. */
	private final String dummyHash;

	private final AccountRepository accounts;
	private final PasswordEncoder encoder;
	private final AccountEvents events;
	private final Clock clock;
	private final Set<String> adminEmails;
	private final LoginThrottle throttle;
	private final SessionService sessions;

	public AccountService(AccountRepository accounts, PasswordEncoder encoder, AccountEvents events, Clock clock,
			@Value("${quizcadia.admin-emails:}") Set<String> adminEmails, LoginThrottle throttle,
			SessionService sessions) {
		this.sessions = sessions;
		this.throttle = throttle;
		this.accounts = accounts;
		this.encoder = encoder;
		this.events = events;
		this.clock = clock;
		this.adminEmails = adminEmails.stream().map(AccountService::normalize).filter(e -> !e.isEmpty())
				.collect(Collectors.toSet());
		this.dummyHash = encoder.encode("not-a-real-password-" + System.nanoTime());
	}

	/** Creates an account without an invite. */
	public Account register(String userName, String email, String password) {
		return register(userName, email, password, null);
	}

	/** @param invite the invite code of the friend who sent the link (ignored if unknown) */
	@Transactional
	public Account register(String userName, String email, String password, String invite) {
		String normalized = normalize(email);
		if (accounts.existsByEmailIgnoreCase(normalized)) {
			throw new EmailTakenException();
		}
		Role role = adminEmails.contains(normalized) ? Role.ADMIN : Role.USER;
		Account account;
		try {
			account = accounts.saveAndFlush(
					new Account(userName.trim(), normalized, encoder.encode(password), role, clock.instant()));
		}
		catch (DataIntegrityViolationException e) {
			throw new EmailTakenException();
		}
		events.registered(new UserRegistered(account.getUserId(), account.getUserName(), account.getEmail(),
				account.getRole().name(), account.getCreatedAt(), referrer(invite, account.getUserId())));
		return account;
	}

	/**
	 * The account for this email and password. Refused the same way for an unknown email or a wrong password (and
	 * in the same time), and refused for a while after too many wrong passwords.
	 */
	public Account authenticate(String email, String password) {
		String key = normalize(email);
		throttle.check(key); // too many wrong passwords for this email lately
		Optional<Account> account = accounts.findByEmailIgnoreCase(key);
		boolean ok = encoder.matches(password, account.map(Account::getPasswordHash).orElse(dummyHash));
		if (account.isEmpty() || !ok) {
			throttle.failed(key);
			throw new BadCredentialsException();
		}
		throttle.succeeded(key);
		return account.get();
	}

	/**
	 * Signs in with a verified Google identity: the account already linked to it, else the account with the
	 * same email (now linked), else a new account. A password account that never proved it owns its email
	 * loses that password when Google links it, so someone who registered with another person's address
	 * before them can't keep a way in. The owner can set a new one with "Forgot password".
	 */
	public Account signInWithGoogle(GoogleIdentity google) {
		return signInWithGoogle(google, null);
	}

	/** @param invite the invite code of the friend who sent the link; only counts when a new account is made */
	@Transactional
	public Account signInWithGoogle(GoogleIdentity google, String invite) {
		if (!google.emailVerified() || google.email() == null || google.email().isBlank()) {
			throw new GoogleSignInException("Your Google account's email isn't verified");
		}
		Optional<Account> linked = accounts.findByGoogleSubject(google.subject());
		if (linked.isPresent()) {
			return linked.get();
		}
		String email = normalize(google.email());
		Optional<Account> sameEmail = accounts.findByEmailIgnoreCase(email);
		if (sameEmail.isPresent()) {
			Account a = sameEmail.get();
			if (!a.isEmailVerified()) {
				a.changePassword(unusablePassword());
				sessions.endAll(a.getUserId()); // and whoever signed in with that password is signed out
			}
			a.linkGoogle(google.subject());
			return a;
		}
		String name = google.name() == null || google.name().isBlank() ? email.substring(0, email.indexOf('@'))
				: google.name().trim();
		Account a = new Account(name.length() > 60 ? name.substring(0, 60) : name, email, unusablePassword(),
				adminEmails.contains(email) ? Role.ADMIN : Role.USER, clock.instant());
		a.linkGoogle(google.subject());
		try {
			a = accounts.saveAndFlush(a);
		}
		catch (DataIntegrityViolationException e) { // the same person signing in twice at once
			return accounts.findByGoogleSubject(google.subject()).orElseThrow(() -> e);
		}
		events.registered(new UserRegistered(a.getUserId(), a.getUserName(), a.getEmail(), a.getRole().name(),
				a.getCreatedAt(), referrer(invite, a.getUserId())));
		return a;
	}

	/** A hash no typed password can match: Google-only accounts log in with Google (or reset to get one). */
	private String unusablePassword() {
		byte[] b = new byte[32];
		new SecureRandom().nextBytes(b);
		return encoder.encode(HexFormat.of().formatHex(b));
	}

	/** Invite codes are account ids in base 36 (short, and ids aren't secret). Null unless it's another account. */
	Integer referrer(String invite, int newUserId) {
		if (invite == null || !invite.matches("[0-9a-zA-Z]{1,7}")) {
			return null; // a broken link never stops anyone signing up
		}
		long id = Long.parseLong(invite, 36);
		return id > 0 && id <= Integer.MAX_VALUE && id != newUserId && accounts.existsById((int) id) ? (int) id : null;
	}

	/** The invite code to put in a player's share link. */
	public static String inviteCode(int userId) {
		return Integer.toString(userId, 36);
	}

	/** The account with this id, if it still exists. */
	public Optional<Account> find(int userId) {
		return accounts.findById(userId);
	}

	static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
