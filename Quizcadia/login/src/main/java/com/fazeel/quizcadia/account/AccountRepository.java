package com.fazeel.quizcadia.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Accounts in MySQL, found by id, email (case ignored) or Google subject. */
public interface AccountRepository extends JpaRepository<Account, Integer> {

	Optional<Account> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	Optional<Account> findByGoogleSubject(String googleSubject);
}
