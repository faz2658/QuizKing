package com.fazeel.quizcadia.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.fazeel.quizcadia.report.GameReport.GameMode;

/** Game reports. Most reads leave out the question reviews (the bulk of each report) through projections. */
public interface GameReportRepository extends MongoRepository<GameReport, String> {

	List<GameReport> findByUserIdOrderByCompletedAtDesc(int userId);

	/**
	 * A player's games, newest first, without the question-by-question review: the review's text is most of each
	 * report, and XP, the streak, My stats and the summary never read it. (500 games: about 1 MB less per call.)
	 */
	@Query(value = "{ 'userId': ?0 }", sort = "{ 'completedAt': -1 }", fields = "{ 'questions': 0 }")
	List<GameReport> findSummariesByUserId(int userId);

	@Query(value = "{ 'userId': ?0, 'mode': ?1 }", sort = "{ 'completedAt': -1 }", fields = "{ 'questions': 0 }")
	List<GameReport> findSummariesByUserIdAndMode(int userId, GameMode mode);

	/** The same with each answer's right/wrong but not its text: the badges look for runs of right answers. */
	@Query(value = "{ 'userId': ?0 }", sort = "{ 'completedAt': -1 }",
			fields = "{ 'questions.question': 0, 'questions.correctAnswer': 0, 'questions.answer': 0 }")
	List<GameReport> findForBadgesByUserId(int userId);

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

	/**
	 * Who finished a game when, for every game ever: the retention cohorts. Only those two fields are read, into
	 * their own record (a {@link GameReport} can't be built from them: its number fields can't be empty).
	 */
	@Query(value = "{ 'completedAt': { $ne: null } }", fields = "{ 'userId': 1, 'completedAt': 1 }")
	List<PlayTime> findAllPlayTimes();
}
