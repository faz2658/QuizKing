package com.fazeel.maverick.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
			expired.add(AnswerOutcome.of(q));
			// The player's client only moves on once the grace period is over, so that's when the next clock starts.
			advance(deadline);
		}
		return expired;
	}

	/**
	 * Records the player's answer to the current question.
	 *
	 * @throws QuizRuleException if the game is over or the question is not the current one
	 */
	public AnswerOutcome answer(int questionId, String answer, Instant now) {
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
		q.setPointsAwarded(q.isCorrect() ? q.getPoints() : 0);
		score += q.getPointsAwarded();
		AnswerOutcome outcome = AnswerOutcome.of(q);
		advance(now);
		return outcome;
	}

	public Optional<SessionQuestion> currentQuestion() {
		return status == SessionStatus.COMPLETED ? Optional.empty() : Optional.of(current());
	}

	public long secondsRemaining(Instant now) {
		return currentQuestion().map(q -> Math.max(0, Duration.between(now, q.deadline()).toSeconds())).orElse(0L);
	}

	public int maxScore() {
		return questions.stream().mapToInt(SessionQuestion::getPoints).sum();
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

	public boolean isResultPublished() {
		return resultPublished;
	}

	public void markResultPublished() {
		this.resultPublished = true;
	}
}
