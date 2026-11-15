package com.fazeel.quizking.domain;

import java.time.Instant;
import java.util.List;

/** Final result of an adaptive session, published once when it ends. */
public record AdaptiveResult(String sessionId, int userId, int gameId, String gameName, int categoryId, int topicId,
		int score, int finalLevel, int highestLevel, int correctAnswers, int totalQuestions, Instant completedAt,
		List<Answer> answers) {

	public record Answer(int questionId, int level, String question, String correctAnswer, String answer,
			boolean correct, boolean timedOut, int points) {
	}
}
