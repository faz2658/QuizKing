package com.stackroute.maverick.account;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stackroute.maverick.account.Account.Role;

@Service
public class AccountService {

	/** Hash of a random password, compared against when the email is unknown so timing doesn't leak it. */
	private final String dummyHash;

	private final AccountRepository accounts;
	private final PasswordEncoder encoder;
	private final AccountEvents events;
	private final Clock clock;
	private final Set<String> adminEmails;

	public AccountService(AccountRepository accounts, PasswordEncoder encoder, AccountEvents events, Clock clock,
			@Value("${maverick.admin-emails:}") Set<String> adminEmails) {
		this.accounts = accounts;
		this.encoder = encoder;
		this.events = events;
		this.clock = clock;
		this.adminEmails = adminEmails.stream().map(AccountService::normalize).filter(e -> !e.isEmpty())
				.collect(Collectors.toSet());
		this.dummyHash = encoder.encode("not-a-real-password-" + System.nanoTime());
	}

	@Transactional
	public Account register(String userName, String email, String password) {
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
				account.getRole().name(), account.getCreatedAt()));
		return account;
	}

	public Account authenticate(String email, String password) {
		Optional<Account> account = accounts.findByEmailIgnoreCase(normalize(email));
		boolean ok = encoder.matches(password, account.map(Account::getPasswordHash).orElse(dummyHash));
		if (account.isEmpty() || !ok) {
			throw new BadCredentialsException();
		}
		return account.get();
	}

	public Optional<Account> find(int userId) {
		return accounts.findById(userId);
	}

	static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
