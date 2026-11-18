package com.fazeel.quizcadia.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

	Optional<RefreshSession> findByTokenHash(String tokenHash);

	List<RefreshSession> findByFamilyIdAndRevokedAtIsNull(String familyId);

	List<RefreshSession> findByUserIdAndGuestFalseAndRevokedAtIsNull(int userId);

	/** Uses the token up. 0 when another request (a second tab) used it first. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshSession s set s.usedAt = :now where s.id = :id and s.usedAt is null and s.revokedAt is null")
	int markUsed(@Param("id") long id, @Param("now") Instant now);

	@Modifying
	@Query("delete from RefreshSession s where s.familyExpiresAt < :cutoff")
	int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
