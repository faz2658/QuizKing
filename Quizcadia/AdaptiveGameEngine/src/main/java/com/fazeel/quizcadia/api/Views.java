package com.fazeel.quizcadia.api;

import java.time.Instant;
import java.util.List;

import com.fazeel.quizcadia.adaptive.AdaptiveSession;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class Views {

	private Views() {
	}

	/** @param country the player's country (ISO code), optional: other countries' questions are left out */
	public record StartRequest(@Positive int gameId, @Positive int userId,
			@Pattern(regexp = "[A-Za-z]{2}") String country) {
	}

	public record AnswerRequest(@Positive int questionId, @NotNull String answer) {
	}

	public record QuestionView(int questionId, int number, int level, String stem, List<String> options,
			long secondsRemaining, int points) {
	}

	public record SessionView(String sessionId, int gameId, String gameName, AdaptiveSession.Status status,
			int level, int score, int answered, int totalQuestions, QuestionView currentQuestion) {

		static SessionView of(AdaptiveSession s, Instant now) {
			QuestionView q = s.currentQuestion()
					.map(c -> new QuestionView(c.getQuestionId(), s.getAsked().size(), c.getLevel(), c.getStem(),
							c.getOptions(), s.secondsRemaining(now), c.getLevel() * s.getRules().pointsPerLevel()))
					.orElse(null);
			int answered = s.getAsked().size() - (q == null ? 0 : 1);
			return new SessionView(s.getId(), s.getGameId(), s.getGameName(), s.getStatus(), s.getLevel(), s.getScore(),
					answered, s.getRules().totalQuestions(), q);
		}
	}

	public record AnswerView(boolean correct, String correctAnswer, int points, String levelChange,
			SessionView session) {
	}
}
