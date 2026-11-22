package com.fazeel.quizcadia.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Password-reset links, stored only as a hash of their token. */
public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

	Optional<PasswordReset> findByTokenHash(String tokenHash);

	long countByUserIdAndCreatedAtAfter(int userId, Instant since);

	List<PasswordReset> findByUserIdAndUsedAtIsNull(int userId);

	void deleteByExpiresAtBefore(Instant cutoff);
}
