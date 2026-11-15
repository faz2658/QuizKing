package com.fazeel.maverick.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player's result of one game, whatever the mode. The id is derived from the session/match and
 * the player, so a replayed Kafka message overwrites the same report instead of duplicating it.
 */
@Document(collection = "game_reports")
public record GameReport(@Id String id, GameMode mode, @Indexed int userId, int gameId, String gameName,
		int categoryId, int score, Integer maxScore, int correctAnswers, int totalQuestions, Integer rank,
		Integer players, Integer highestLevel, Instant completedAt, List<ReviewItem> questions) {

	public enum GameMode {
		SINGLE, MULTI, ADAPTIVE
	}

	public record ReviewItem(String question, String correctAnswer, String answer, boolean correct,
			boolean timedOut, int points) {
	}

	/** Correct answers as a whole-number percentage of the questions asked. */
	public int accuracy() {
		return totalQuestions == 0 ? 0 : (int) Math.round(100.0 * correctAnswers / totalQuestions);
	}
}
