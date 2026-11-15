package com.stackroute.maverick.domain;

import java.time.Instant;
import java.util.List;

/** Final standings of a multiplayer match, published once when the match ends. */
public record MultiPlayerResult(String matchId, int gameId, String gameName, int categoryId, Instant completedAt,
		List<Standing> standings, List<QuestionResult> questions) {

	public record Standing(int rank, int userId, int score, int questionsWon, int wrongAnswers) {
	}

	public record QuestionResult(int questionId, String question, String correctAnswer, Integer winnerUserId,
			List<PlayerAnswer> answers) {
	}

	public record PlayerAnswer(int userId, String answer, boolean correct, long answeredAfterMillis) {
	}
}
