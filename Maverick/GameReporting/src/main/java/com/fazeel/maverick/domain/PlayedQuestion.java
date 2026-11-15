package com.fazeel.maverick.domain;

/** One answered question, as reported to the reporting service. */
public record PlayedQuestion(int questionId, int questionLevel, String question, String correctAnswer,
		String answer, boolean correct, boolean timedOut, int pointsAwarded) {
}
