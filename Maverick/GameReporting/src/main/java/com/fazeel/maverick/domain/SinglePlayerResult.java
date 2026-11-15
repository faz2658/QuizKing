package com.fazeel.maverick.domain;

import java.time.Instant;
import java.util.List;

/** Final result of a single-player session, published once when the session ends. */
public record SinglePlayerResult(String sessionId, int gameId, String gameName, int categoryId, int userId,
		int score, int maxScore, int percentage, int correctAnswers, int totalQuestions, Instant completedAt,
		List<PlayedQuestion> playedData) {
}
