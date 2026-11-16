package com.fazeel.quizcadia.api;

import java.time.Instant;
import java.util.List;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.session.AnswerOutcome;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.Lifeline;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request and response bodies of the game engine API. Correct answers are only revealed after answering. */
public final class Views {

	private Views() {
	}

	public record StartRequest(@Positive int gameId, @Positive int userId) {
	}

	public record AnswerRequest(@Positive int questionId, @NotNull String answer) {
	}

	public record CustomRequest(@NotNull List<Integer> gameIds,
			@NotNull com.fazeel.quizcadia.service.GameSessionService.CustomDifficulty difficulty, int count) {
	}

	public record LifelineRequest(@NotNull Lifeline lifeline, @Positive int questionId) {
	}

	public record GameSummary(int gameId, String gameName, String gameDescription, String gameImage,
			String gameRules, int categoryId, String topic, int questionCount) {

		static GameSummary of(Game g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), g.getTopic() == null ? null : g.getTopic().getTopicName(),
					g.getQuestions() == null ? 0 : g.getQuestions().size());
		}
	}

	/** {@code options} leaves out the ones a 50/50 removed; {@code timeLimitSeconds} includes extra time. */
	public record QuestionView(int questionId, int number, String difficulty, String stem, List<String> options,
			int timeLimitSeconds, long secondsRemaining, int points) {
	}

	/**
	 * {@code combo} is the current run of correct answers and {@code nextMultiplier} the bonus the next correct
	 * answer earns; {@code lifelines} are the ones still available.
	 */
	public record SessionView(String sessionId, int gameId, String gameName, SessionStatus status,
			int totalQuestions, int answeredQuestions, int score, int maxScore, QuestionView currentQuestion,
			String variant, int combo, double nextMultiplier, List<Lifeline> lifelines) {

		static SessionView of(GameSession s, Instant now) {
			QuestionView current = s.currentQuestion()
					.map(q -> new QuestionView(q.getQuestionId(), s.getCurrentIndex() + 1, q.getDifficulty().name(),
							q.getStem(), q.getOptions().stream().filter(o -> !q.getRemovedOptions().contains(o)).toList(),
							q.getTimeLimitSeconds() + q.getBonusSeconds(), s.secondsRemaining(now), q.getPoints()))
					.orElse(null);
			return new SessionView(s.getId(), s.getGameId(), s.getGameName(), s.getStatus(), s.getQuestions().size(),
					s.getCurrentIndex(), s.getScore(), s.maxScore(), current, s.getVariant().name(), s.getCombo(),
					GameSession.multiplier(s.getCombo() + 1), java.util.Arrays.stream(Lifeline.values())
							.filter(l -> !s.getUsedLifelines().contains(l)).toList());
		}
	}

	public record AnswerView(boolean correct, boolean timedOut, String correctAnswer, int pointsAwarded,
			SessionView session, int combo, double multiplier, boolean skipped, String explanation) {

		static AnswerView of(AnswerOutcome o, SessionView session) {
			return new AnswerView(o.correct(), o.timedOut(), o.correctAnswer(), o.pointsAwarded(), session, o.combo(),
					o.multiplier(), o.skipped(), o.explanation());
		}
	}

	/** After a lifeline: the updated session, and for a skip what happened to the skipped question. */
	public record LifelineView(Lifeline lifeline, AnswerView skipped, SessionView session) {
	}

	public record ReviewItem(int questionId, int number, String difficulty, String stem, String yourAnswer, String correctAnswer,
			boolean correct, boolean timedOut, int pointsAwarded, int points, boolean skipped, double multiplier,
			String explanation) {
	}

	public record ResultView(String sessionId, int gameId, String gameName, int score, int maxScore,
			int percentage, long correctAnswers, int totalQuestions, List<ReviewItem> review, String variant,
			String variantKey, Integer opponentScore) {

		static ResultView of(GameSession s) {
			List<SessionQuestion> qs = s.getQuestions();
			List<ReviewItem> review = new java.util.ArrayList<>();
			for (int i = 0; i < qs.size(); i++) {
				SessionQuestion q = qs.get(i);
				review.add(new ReviewItem(q.getQuestionId(), i + 1, q.getDifficulty().name(), q.getStem(), q.getAnswer(),
						q.getCorrectAnswer(), q.isCorrect(), q.isTimedOut(), q.getPointsAwarded(), q.getPoints(),
						q.isSkipped(), q.getMultiplier(), q.getExplanation()));
			}
			return new ResultView(s.getId(), s.getGameId(), s.getGameName(), s.getScore(), s.maxScore(),
					s.percentage(), s.correctCount(), qs.size(), review, s.getVariant().name(), s.getVariantKey(),
					s.getOpponentScore());
		}
	}

	/** Today's challenge from the player's side. */
	public record DailyView(String date, int totalQuestions, List<String> topics, String status, String sessionId,
			Integer score, Integer maxScore, long secondsUntilNext) {
	}
}
