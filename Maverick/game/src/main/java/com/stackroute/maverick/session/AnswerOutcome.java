package com.stackroute.maverick.session;

/** What happened when the player answered (or ran out of time on) a question. */
public record AnswerOutcome(int questionId, boolean correct, boolean timedOut, String correctAnswer,
		int pointsAwarded) {

	static AnswerOutcome of(SessionQuestion q) {
		return new AnswerOutcome(q.getQuestionId(), q.isCorrect(), q.isTimedOut(), q.getCorrectAnswer(),
				q.getPointsAwarded());
	}
}
