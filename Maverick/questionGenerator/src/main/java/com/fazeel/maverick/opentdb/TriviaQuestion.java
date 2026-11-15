package com.fazeel.maverick.opentdb;

import java.util.List;

/** One question as Open Trivia DB returns it, already decoded from base64. */
public record TriviaQuestion(String type, String difficulty, String category, String question, String correctAnswer,
		List<String> incorrectAnswers) {
}
