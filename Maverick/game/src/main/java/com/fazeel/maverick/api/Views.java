package com.fazeel.maverick.api;

import java.time.Instant;
import java.util.List;

import com.fazeel.maverick.domain.Game;
import com.fazeel.maverick.session.AnswerOutcome;
import com.fazeel.maverick.session.GameSession;
import com.fazeel.maverick.session.SessionQuestion;
import com.fazeel.maverick.session.SessionStatus;

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

	public record GameSummary(int gameId, String gameName, String gameDescription, String gameImage,
			String gameRules, int categoryId, String topic, int questionCount) {

		static GameSummary of(Game g) {
			return new GameSummary(g.getGameId(), g.getGameName(), g.getGameDescription(), g.getGameImage(),
					g.getGameRules(), g.getCategoryId(), g.getTopic() == null ? null : g.getTopic().getTopicName(),
					g.getQuestions() == null ? 0 : g.getQuestions().size());
		}
	}

	public record QuestionView(int questionId, int number, String difficulty, String stem, List<String> options,
			int timeLimitSeconds, long secondsRemaining, int points) {
	}

	public record SessionView(String sessionId, int gameId, String gameName, SessionStatus status,
			int totalQuestions, int answeredQuestions, int score, int maxScore, QuestionView currentQuestion) {

		static SessionView of(GameSession s, Instant now) {
			QuestionView current = s.currentQuestion()
					.map(q -> new QuestionView(q.getQuestionId(), s.getCurrentIndex() + 1, q.getDifficulty().name(),
							q.getStem(), q.getOptions(), q.getTimeLimitSeconds(), s.secondsRemaining(now),
							q.getPoints()))
					.orElse(null);
			return new SessionView(s.getId(), s.getGameId(), s.getGameName(), s.getStatus(), s.getQuestions().size(),
					s.getCurrentIndex(), s.getScore(), s.maxScore(), current);
		}
	}

	public record AnswerView(boolean correct, boolean timedOut, String correctAnswer, int pointsAwarded,
			SessionView session) {

		static AnswerView of(AnswerOutcome o, SessionView session) {
			return new AnswerView(o.correct(), o.timedOut(), o.correctAnswer(), o.pointsAwarded(), session);
		}
	}

	public record ReviewItem(int number, String difficulty, String stem, String yourAnswer, String correctAnswer,
			boolean correct, boolean timedOut, int pointsAwarded, int points) {
	}

	public record ResultView(String sessionId, int gameId, String gameName, int score, int maxScore,
			int percentage, long correctAnswers, int totalQuestions, List<ReviewItem> review) {

		static ResultView of(GameSession s) {
			List<SessionQuestion> qs = s.getQuestions();
			List<ReviewItem> review = new java.util.ArrayList<>();
			for (int i = 0; i < qs.size(); i++) {
				SessionQuestion q = qs.get(i);
				review.add(new ReviewItem(i + 1, q.getDifficulty().name(), q.getStem(), q.getAnswer(),
						q.getCorrectAnswer(), q.isCorrect(), q.isTimedOut(), q.getPointsAwarded(), q.getPoints()));
			}
			return new ResultView(s.getId(), s.getGameId(), s.getGameName(), s.getScore(), s.maxScore(),
					s.percentage(), s.correctCount(), qs.size(), review);
		}
	}
}
