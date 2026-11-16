package com.fazeel.quizcadia.report;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player's result of one game, whatever the mode. The id is derived from the session/match and
 * the player, so a replayed Kafka message overwrites the same report instead of duplicating it.
 */
@Document(collection = "game_reports")
@CompoundIndex(name = "player_history", def = "{'userId': 1, 'completedAt': -1}") // a player's games, newest first
@CompoundIndex(name = "variant_day", def = "{'variant': 1, 'variantKey': 1}") // one day's Daily Challenge runs
public record GameReport(@Id String id, GameMode mode, int userId, int gameId, String gameName,
		int categoryId, int score, Integer maxScore, int correctAnswers, int totalQuestions, Integer rank,
		Integer players, Integer highestLevel, @Indexed Instant completedAt, List<ReviewItem> questions, String variant,
		String variantKey, Integer opponentScore) {

	/** A report without a variant (multiplayer, adaptive, older single-player results). */
	public GameReport(String id, GameMode mode, int userId, int gameId, String gameName, int categoryId, int score,
			Integer maxScore, int correctAnswers, int totalQuestions, Integer rank, Integer players,
			Integer highestLevel, Instant completedAt, List<ReviewItem> questions) {
		this(id, mode, userId, gameId, gameName, categoryId, score, maxScore, correctAnswers, totalQuestions, rank,
				players, highestLevel, completedAt, questions, null, null, null);
	}

	public static final String DAILY = "DAILY";
	public static final String CHALLENGE = "CHALLENGE";
	public static final String ARENA = "ARENA";

	public boolean isDaily() {
		return DAILY.equals(variant);
	}

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
