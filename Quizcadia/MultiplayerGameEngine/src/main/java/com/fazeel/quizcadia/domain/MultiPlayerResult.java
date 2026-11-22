package com.fazeel.quizcadia.domain;

import java.time.Instant;
import java.util.List;

/** Final standings of a multiplayer match, published once when the match ends. */
public record MultiPlayerResult(String matchId, int gameId, String gameName, int categoryId, Instant completedAt,
		List<Standing> standings, List<QuestionResult> questions) {

	/** A player's final place in the match: score, questions won and wrong answers. */
	public record Standing(int rank, int userId, int score, int questionsWon, int wrongAnswers) {
	}

	/** One question: the right answer, who got it first, and every player's answer. */
	public record QuestionResult(int questionId, String question, String correctAnswer, Integer winnerUserId,
			List<PlayerAnswer> answers) {
	}

	/** One player's answer and how long after the question opened it came. */
	public record PlayerAnswer(int userId, String answer, boolean correct, long answeredAfterMillis) {
	}
}
