package com.fazeel.quizking.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.report.GameReport.GameMode;

public interface GameReportRepository extends MongoRepository<GameReport, String> {

	List<GameReport> findByUserIdOrderByCompletedAtDesc(int userId);

	List<GameReport> findByUserIdAndModeOrderByCompletedAtDesc(int userId, GameMode mode);

	/** Everyone's run of one day's challenge. */
	List<GameReport> findByVariantAndVariantKey(String variant, String variantKey);

	List<GameReport> findByCompletedAtGreaterThanEqual(Instant since);
}
