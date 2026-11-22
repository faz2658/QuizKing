package com.fazeel.quizcadia.domain;

import java.time.Instant;
import java.util.List;

/** Final result of an adaptive session, published once when it ends. */
public record AdaptiveResult(String sessionId, int userId, int gameId, String gameName, int categoryId, int topicId,
		int score, int finalLevel, int highestLevel, int correctAnswers, int totalQuestions, Instant completedAt,
		List<Answer> answers) {

	/**
	 * One question of a finished adaptive session: the level, the player's answer and whether it was right, timed
	 * out, and what it scored.
	 */
	public record Answer(int questionId, int level, String question, String correctAnswer, String answer,
			boolean correct, boolean timedOut, int points) {
	}
}
