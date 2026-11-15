package com.fazeel.maverick.session;

/**
 * What happened when the player answered (or ran out of time on, or skipped) a question. {@code combo} is the
 * run of correct answers after this one and {@code multiplier} the bonus the points were awarded with.
 */
public record AnswerOutcome(int questionId, boolean correct, boolean timedOut, String correctAnswer,
		int pointsAwarded, int combo, double multiplier, boolean skipped, String explanation) {

	static AnswerOutcome of(SessionQuestion q, int combo) {
		return new AnswerOutcome(q.getQuestionId(), q.isCorrect(), q.isTimedOut(), q.isSkipped() ? null : q.getCorrectAnswer(),
				q.getPointsAwarded(), combo, q.getMultiplier(), q.isSkipped(), q.isSkipped() ? null : q.getExplanation());
	}
}
