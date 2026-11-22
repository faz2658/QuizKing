package com.fazeel.quizcadia.adaptive;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player's adaptive run. The player starts on easy questions. After every block of questions
 * at a level the block's score decides the next level: at or above the promotion threshold the player
 * moves up, below the demotion threshold down, otherwise they stay. Harder questions are worth more.
 * Timing and scoring happen on the server.
 */
@Document(collection = "adaptive_sessions")
public class AdaptiveSession {

	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 3;
	public static final Duration GRACE = Duration.ofSeconds(2);

	/** IN_PROGRESS while questions remain; COMPLETED after the last one (answered or timed out). */
	public enum Status {
		IN_PROGRESS, COMPLETED
	}

	@Id
	private String id;
	@Version
	private Long version;
	private int userId;
	private int gameId;
	private String gameName;
	private int categoryId;
	private int topicId;
	private AdaptiveRules rules;

	private List<PooledQuestion> pool = new ArrayList<>();

	/** The questions this run may ask (package-private, for tests). */
	List<PooledQuestion> pool() {
		return pool;
	}
	private List<AskedQuestion> asked = new ArrayList<>();
	private int level = MIN_LEVEL;
	private int highestLevel = MIN_LEVEL;
	private int blockAnswered;
	private int blockCorrect;
	private int score;
	private Status status = Status.IN_PROGRESS;
	private Instant startedAt;
	private Instant completedAt;
	private boolean resultPublished;

	public AdaptiveSession() {
	}

	/** @param pool playable questions, in the order they should be preferred within a level */
	public AdaptiveSession(String id, int userId, int gameId, String gameName, int categoryId, int topicId,
			List<PooledQuestion> pool, AdaptiveRules rules, Instant now) {
		if (pool.isEmpty()) {
			throw new AdaptiveRuleException("This game has no playable questions");
		}
		this.id = id;
		this.userId = userId;
		this.gameId = gameId;
		this.gameName = gameName;
		this.categoryId = categoryId;
		this.topicId = topicId;
		this.rules = rules;
		this.pool = new ArrayList<>(pool);
		this.startedAt = now;
		askNext(now);
	}

	/**
	 * Closes questions whose time plus the grace period has passed, as if the player let the timer run out.
	 * @return true if anything changed (the caller then saves)
	 */
	public boolean expireOverdue(Instant now) {
		boolean changed = false;
		while (status == Status.IN_PROGRESS) {
			AskedQuestion q = current();
			Instant deadline = q.getAskedAt().plusSeconds(rules.secondsPerQuestion());
			if (!now.isAfter(deadline.plus(GRACE))) {
				break;
			}
			q.timeOut();
			record(false);
			askNext(deadline.plus(GRACE)); // when the player's client moves on
			changed = true;
		}
		return changed;
	}

	/**
	 * Scores the answer to the open question, moves the level when its block is complete and asks the next
	 * question. Throws when the game is over or the question is not the open one.
	 */
	public Outcome answer(int questionId, String answer, Instant now) {
		expireOverdue(now);
		if (status == Status.COMPLETED) {
			throw new AdaptiveRuleException("The game is already over");
		}
		AskedQuestion q = current();
		if (q.getQuestionId() != questionId) {
			throw new AdaptiveRuleException("Question " + questionId + " is not the current question");
		}
		boolean correct = answer != null && answer.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
		int points = correct ? q.getLevel() * rules.pointsPerLevel() : 0;
		q.answer(answer, correct, points);
		score += points;
		int before = level;
		record(correct);
		askNext(now);
		return new Outcome(correct, q.getCorrectAnswer(), points, Integer.compare(level, before));
	}

	/** Counts the answer towards the current block and moves the level when the block is complete. */
	private void record(boolean correct) {
		blockAnswered++;
		if (correct) {
			blockCorrect++;
		}
		if (blockAnswered < rules.blockSize()) {
			return;
		}
		double percent = 100.0 * blockCorrect / blockAnswered;
		if (percent >= rules.promoteAtPercent() && level < MAX_LEVEL) {
			level++;
		} else if (percent < rules.demoteBelowPercent() && level > MIN_LEVEL) {
			level--;
		}
		highestLevel = Math.max(highestLevel, level);
		blockAnswered = 0;
		blockCorrect = 0;
	}

	/** Asks a question at the current level, or the nearest level that still has questions. */
	private void askNext(Instant now) {
		if (asked.size() >= rules.totalQuestions() || pool.isEmpty()) {
			status = Status.COMPLETED;
			completedAt = now;
			return;
		}
		PooledQuestion next = pool.stream()
				.min(Comparator.comparingInt((PooledQuestion q) -> Math.abs(q.level() - level))
						.thenComparingInt(q -> q.level() > level ? 1 : 0))
				.orElseThrow();
		pool.remove(next);
		asked.add(new AskedQuestion(next, now));
	}

	/** The open question, or empty once the session is over. */
	public Optional<AskedQuestion> currentQuestion() {
		return status == Status.COMPLETED ? Optional.empty() : Optional.of(current());
	}

	/** Whole seconds left on the open question (0 when none is open or time is up). */
	public long secondsRemaining(Instant now) {
		return currentQuestion()
				.map(q -> Math.max(0, Duration.between(now, q.getAskedAt().plusSeconds(rules.secondsPerQuestion()))
						.toSeconds()))
				.orElse(0L);
	}

	/** How many questions the player got right so far. */
	public long correctCount() {
		return asked.stream().filter(AskedQuestion::isCorrect).count();
	}

	private AskedQuestion current() {
		return asked.get(asked.size() - 1);
	}

	/** Notes that the result went to Kafka, so it is published exactly once. */
	public void markResultPublished() {
		resultPublished = true;
	}

	public boolean isResultPublished() {
		return resultPublished;
	}

	public String getId() {
		return id;
	}

	public int getUserId() {
		return userId;
	}

	public int getGameId() {
		return gameId;
	}

	public String getGameName() {
		return gameName;
	}

	public int getCategoryId() {
		return categoryId;
	}

	public int getTopicId() {
		return topicId;
	}

	public AdaptiveRules getRules() {
		return rules;
	}

	public List<AskedQuestion> getAsked() {
		return asked;
	}

	public int getLevel() {
		return level;
	}

	public int getHighestLevel() {
		return highestLevel;
	}

	public int getScore() {
		return score;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	/** @param levelChange +1 moved up, -1 moved down, 0 stayed */
	public record Outcome(boolean correct, String correctAnswer, int points, int levelChange) {
	}
}
