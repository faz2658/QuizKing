package com.fazeel.maverick.domain;

import java.time.Instant;
import java.util.List;

/**
 * Final result of a single-player session, published once when the session ends. {@code variant} is
 * STANDARD, DAILY, CUSTOM or CHALLENGE (null from older engines); {@code variantKey} is the day or challenge id.
 */
public record SinglePlayerResult(String sessionId, int gameId, String gameName, int categoryId, int userId,
		int score, int maxScore, int percentage, int correctAnswers, int totalQuestions, Instant completedAt,
		List<PlayedQuestion> playedData, String variant, String variantKey, Integer opponentScore,
		String playerName) {
}
