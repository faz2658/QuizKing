package com.fazeel.quizcadia.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.fazeel.quizcadia.report.GameReport.GameMode;

public interface GameReportRepository extends MongoRepository<GameReport, String> {

	List<GameReport> findByUserIdOrderByCompletedAtDesc(int userId);

	List<GameReport> findByUserIdAndModeOrderByCompletedAtDesc(int userId, GameMode mode);

	// The board queries below leave out each report's question review ('questions'), which is most of a report's
	// size and isn't needed to rank anyone. Reports they return have questions == null.

	/** Everyone's run of one day's challenge, without the question review. */
	@Query(value = "{ 'variant': ?0, 'variantKey': ?1 }", fields = "{ 'questions': 0 }")
	List<GameReport> findScoresByVariantAndVariantKey(String variant, String variantKey);

	/** Reports completed since {@code since}, without the question review. */
	@Query(value = "{ 'completedAt': { $gte: ?0 } }", fields = "{ 'questions': 0 }")
	List<GameReport> findScoresCompletedSince(Instant since);

	/** Reports completed in [since, until), without the question review. */
	@Query(value = "{ 'completedAt': { $gte: ?0, $lt: ?1 } }", fields = "{ 'questions': 0 }")
	List<GameReport> findCompletedBetween(Instant since, Instant until);
}
