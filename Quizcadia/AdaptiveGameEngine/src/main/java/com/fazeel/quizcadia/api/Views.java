package com.fazeel.quizcadia.api;

import java.time.Instant;
import java.util.List;

import com.fazeel.quizcadia.adaptive.AdaptiveSession;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** The JSON the adaptive API sends and accepts. The open question never includes its answer. */
public final class Views {

	private Views() {
	}

	/** @param country the player's country (ISO code), optional: other countries' questions are left out */
	public record StartRequest(@Positive int gameId, @Positive int userId,
			@Pattern(regexp = "[A-Za-z]{2}") String country) {
	}

	/** An answer to one question: its id and the option the player picked. */
	public record AnswerRequest(@Positive int questionId, @NotNull String answer) {
	}

	/** The open question as the player sees it: no right answer, the seconds left and what it is worth. */
	public record QuestionView(int questionId, int number, int level, String stem, List<String> options,
			long secondsRemaining, int points) {
	}

	/** A session as the player sees it: level, score, progress and the open question (null when finished). */
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

	/** How an answer went (right or not, the right answer, points, level UP/DOWN/SAME) and the session after it. */
	public record AnswerView(boolean correct, String correctAnswer, int points, String levelChange,
			SessionView session) {
	}
}
