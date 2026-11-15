package com.fazeel.quizking.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

	Optional<PasswordReset> findByTokenHash(String tokenHash);

	long countByUserIdAndCreatedAtAfter(int userId, Instant since);

	List<PasswordReset> findByUserIdAndUsedAtIsNull(int userId);

	void deleteByExpiresAtBefore(Instant cutoff);
}
