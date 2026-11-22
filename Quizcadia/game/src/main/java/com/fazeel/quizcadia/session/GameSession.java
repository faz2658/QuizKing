package com.fazeel.quizcadia.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A single player's run through a game. All scoring and timing decisions are made here, on the
 * server, using server time: the client only ever sends the option it picked.
 */
@Document(collection = "game_sessions")
public class GameSession {

	/** Network latency allowance added to every question's time limit. */
	public static final Duration GRACE = Duration.ofSeconds(2);
	/**
	 * The furthest a question's clock may be restarted after it was asked: a little over the app's longest feedback
	 * pause (4 s, after a wrong answer with an explanation). See {@link #shown}.
	 */
	public static final Duration MAX_SHOW_DELAY = Duration.ofSeconds(5);
	/** Seconds the extra-time lifeline adds. */
	public static final int EXTRA_SECONDS = 10;

	@Id
	private String id;
	@Version
	private Long version;

	private int gameId;
	private int userId;
	private int categoryId;
	private String gameName;
	private List<SessionQuestion> questions = new ArrayList<>();
	private int currentIndex;
	private int score;
	private SessionStatus status = SessionStatus.IN_PROGRESS;
	private Instant startedAt;
	private Instant completedAt;
	private boolean resultPublished;
	private SessionVariant variant = SessionVariant.STANDARD;
	/** The day (DAILY) or challenge id (CHALLENGE) the session belongs to. */
	private String variantKey;
	private String playerName;
	/** The score to beat (CHALLENGE). */
	private Integer opponentScore;
	/** Correct answers in a row right now. */
	private int combo;
	private int bestCombo;
	private Set<Lifeline> usedLifelines = EnumSet.noneOf(Lifeline.class);

	public GameSession() {
	}

	public GameSession(String id, int gameId, int userId, int categoryId, String gameName,
			List<SessionQuestion> questions, Instant now) {
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("A session needs at least one question");
		}
		this.id = id;
		this.gameId = gameId;
		this.userId = userId;
		this.categoryId = categoryId;
		this.gameName = gameName;
		this.questions = new ArrayList<>(questions);
		this.startedAt = now;
		this.questions.get(0).setAskedAt(now);
	}

	/**
	 * Expires every question whose time ran out before {@code now}, moving the clock forward as if
	 * the player had sat and watched each timer reach zero.
	 *
	 * @return outcomes of the questions that expired, oldest first
	 */
	public List<AnswerOutcome> expireOverdueQuestions(Instant now) {
		List<AnswerOutcome> expired = new ArrayList<>();
		while (status == SessionStatus.IN_PROGRESS) {
			SessionQuestion q = current();
			Instant deadline = q.deadline().plus(GRACE);
			if (!now.isAfter(deadline)) {
				break;
			}
			q.setTimedOut(true);
			q.setAnsweredAt(q.deadline());
			combo = 0;
			expired.add(AnswerOutcome.of(q, combo));
			// The player's client only moves on once the grace period is over, so that's when the next clock starts.
			advance(deadline);
		}
		return expired;
	}

	/**
	 * The player's screen now shows question {@code questionId}. Its clock started when the previous answer arrived,
	 * but the app shows that answer's result first (up to 4 s), so the player would lose that time; the clock now
	 * starts when the question appears instead. Bounded so it can't be stretched: once per question, only for the
	 * open, unanswered current question, and never more than {@link #MAX_SHOW_DELAY} after it was asked (the question
	 * reaches the app with the previous answer, so a later "shown" could only buy time to read it).
	 *
	 * @return true if the clock was restarted
	 */
	public boolean shown(int questionId, Instant now) {
		if (status != SessionStatus.IN_PROGRESS) {
			return false;
		}
		SessionQuestion q = current();
		if (q.getQuestionId() != questionId || q.getShownAt() != null || q.isAnswered() || q.getAskedAt() == null
				|| now.isBefore(q.getAskedAt())) {
			return false;
		}
		Instant latest = q.getAskedAt().plus(MAX_SHOW_DELAY);
		q.setShownAt(now);
		q.setAskedAt(now.isAfter(latest) ? latest : now);
		return true;
	}

	/**
	 * Records the player's answer to the current question.
	 *
	 * @throws QuizRuleException if the game is over or the question is not the current one
	 */
	public AnswerOutcome answer(int questionId, String answer, Instant now) {
		AnswerOutcome again = sameAnswerAgain(questionId, answer);
		if (again != null) {
			return again;
		}
		expireOverdueQuestions(now);
		if (status == SessionStatus.COMPLETED) {
			throw new QuizRuleException("The game is already over");
		}
		SessionQuestion q = current();
		if (q.getQuestionId() != questionId) {
			throw new QuizRuleException("Question " + questionId + " is not the current question");
		}
		q.setAnswer(answer);
		q.setAnsweredAt(now);
		q.setCorrect(matches(answer, q.getCorrectAnswer()));
		if (q.isCorrect()) {
			combo++;
			bestCombo = Math.max(bestCombo, combo);
			q.setMultiplier(multiplier(combo));
			q.setPointsAwarded((int) Math.round(q.getPoints() * q.getMultiplier()));
		} else {
			combo = 0;
			q.setPointsAwarded(0);
		}
		score += q.getPointsAwarded();
		AnswerOutcome outcome = AnswerOutcome.of(q, combo);
		advance(now);
		return outcome;
	}

	/**
	 * The outcome of an answer the player already gave, when the same answer to the same question arrives again: a
	 * retry whose first reply was lost. Nothing changes; without this the retry would be refused as "not the current
	 * question" and the player would never see how they did. A different answer is still refused.
	 */
	private AnswerOutcome sameAnswerAgain(int questionId, String answer) {
		int run = 0; // the combo as it was after each question: a timeout breaks it, a skip leaves it
		for (SessionQuestion q : questions) {
			if (q.getAnsweredAt() == null) {
				return null; // reached the questions not played yet
			}
			if (q.isTimedOut()) {
				run = 0;
			}
			else if (!q.isSkipped()) {
				run = q.isCorrect() ? run + 1 : 0;
			}
			if (q.getQuestionId() == questionId) {
				boolean answered = !q.isTimedOut() && !q.isSkipped();
				return answered && Objects.equals(q.getAnswer(), answer) ? AnswerOutcome.of(q, run) : null;
			}
		}
		return null;
	}

	/** Combo bonus for the {@code n}-th correct answer in a row: x1.5 from the 3rd, x2 from the 5th. */
	public static double multiplier(int n) {
		return n >= 5 ? 2.0 : n >= 3 ? 1.5 : 1.0;
	}

	/**
	 * Uses a lifeline on the current question. Each lifeline works once per game.
	 *
	 * @return the outcome of the skipped question for SKIP, otherwise empty
	 * @throws QuizRuleException if the game is over, it isn't the current question, the lifeline was used, or a
	 *         50/50 is asked for on a question with fewer than four options
	 */
	public Optional<AnswerOutcome> useLifeline(Lifeline lifeline, int questionId, Instant now, RandomGenerator random) {
		expireOverdueQuestions(now);
		if (status == SessionStatus.COMPLETED) {
			throw new QuizRuleException("The game is already over");
		}
		SessionQuestion q = current();
		if (q.getQuestionId() != questionId) {
			throw new QuizRuleException("Question " + questionId + " is not the current question");
		}
		if (getUsedLifelines().contains(lifeline)) {
			throw new QuizRuleException("You've already used that lifeline in this game");
		}
		switch (lifeline) {
			case FIFTY_FIFTY -> {
				List<String> wrong = new ArrayList<>(q.getOptions().stream().filter(o -> !matches(o, q.getCorrectAnswer())).toList());
				if (wrong.size() < 3) {
					throw new QuizRuleException("50/50 needs a question with four options");
				}
				Collections.shuffle(wrong, Random.from(random));
				q.setRemovedOptions(List.copyOf(wrong.subList(0, 2)));
			}
			case EXTRA_TIME -> q.setBonusSeconds(q.getBonusSeconds() + EXTRA_SECONDS);
			case SKIP -> {
				q.setSkipped(true);
				q.setAnsweredAt(now);
				q.setPointsAwarded(0);
			}
		}
		getUsedLifelines().add(lifeline);
		if (lifeline == Lifeline.SKIP) {
			AnswerOutcome outcome = AnswerOutcome.of(q, combo);
			advance(now);
			return Optional.of(outcome);
		}
		return Optional.empty();
	}

	public Optional<SessionQuestion> currentQuestion() {
		return status == SessionStatus.COMPLETED ? Optional.empty() : Optional.of(current());
	}

	public long secondsRemaining(Instant now) {
		return currentQuestion().map(q -> Math.max(0, Duration.between(now, q.deadline()).toSeconds())).orElse(0L);
	}

	/** The best possible score: every question right, so the combo bonus applies from the 3rd one on. */
	public int maxScore() {
		int max = 0;
		for (int i = 0; i < questions.size(); i++) {
			max += (int) Math.round(questions.get(i).getPoints() * multiplier(i + 1));
		}
		return max;
	}

	public long correctCount() {
		return questions.stream().filter(SessionQuestion::isCorrect).count();
	}

	/** Score as a whole-number percentage of the best possible score. */
	public int percentage() {
		int max = maxScore();
		return max == 0 ? 0 : (int) Math.round(100.0 * score / max);
	}

	static boolean matches(String given, String expected) {
		return given != null && expected != null && given.trim().equalsIgnoreCase(expected.trim());
	}

	private SessionQuestion current() {
		return questions.get(currentIndex);
	}

	private void advance(Instant now) {
		currentIndex++;
		if (currentIndex >= questions.size()) {
			currentIndex = questions.size();
			status = SessionStatus.COMPLETED;
			completedAt = now;
		} else {
			current().setAskedAt(now);
		}
	}

	public String getId() {
		return id;
	}

	public Long getVersion() {
		return version;
	}

	public int getGameId() {
		return gameId;
	}

	public int getUserId() {
		return userId;
	}

	public int getCategoryId() {
		return categoryId;
	}

	public String getGameName() {
		return gameName;
	}

	public List<SessionQuestion> getQuestions() {
		return questions;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public int getScore() {
		return score;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	/** Marks the session as a daily, custom or challenge run. */
	public GameSession asVariant(SessionVariant variant, String variantKey, Integer opponentScore) {
		this.variant = variant;
		this.variantKey = variantKey;
		this.opponentScore = opponentScore;
		return this;
	}

	public int getCombo() {
		return combo;
	}

	public int getBestCombo() {
		return bestCombo;
	}

	public Set<Lifeline> getUsedLifelines() {
		if (usedLifelines == null) {
			usedLifelines = EnumSet.noneOf(Lifeline.class);
		}
		return usedLifelines;
	}

	public GameSession playedBy(String playerName) {
		this.playerName = playerName;
		return this;
	}

	public SessionVariant getVariant() {
		return variant == null ? SessionVariant.STANDARD : variant;
	}

	public String getVariantKey() {
		return variantKey;
	}

	public String getPlayerName() {
		return playerName;
	}

	public Integer getOpponentScore() {
		return opponentScore;
	}

	public boolean isResultPublished() {
		return resultPublished;
	}

	public void markResultPublished() {
		this.resultPublished = true;
	}
}
