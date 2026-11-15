package com.stackroute.maverick.bank;

import java.util.List;
import java.util.stream.Stream;

import com.stackroute.maverick.domain.Questions;

/** What makes a question playable. The game engines apply the same rule. */
public final class QuestionRules {

	private QuestionRules() {
	}

	public static void validate(Questions q) {
		if (q == null || blank(q.getQuestionStem())) {
			throw new InvalidQuestionException("A question needs a question text");
		}
		if (q.getQuestionLevel() < 1 || q.getQuestionLevel() > 3) {
			throw new InvalidQuestionException("questionLevel must be 1 (easy), 2 (medium) or 3 (advanced)");
		}
		List<String> options = Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4())
				.filter(o -> !blank(o)).map(String::trim).toList();
		if (options.size() < 2) {
			throw new InvalidQuestionException("A question needs at least two options");
		}
		if (options.stream().map(String::toLowerCase).distinct().count() != options.size()) {
			throw new InvalidQuestionException("Options must be different from each other");
		}
		if (blank(q.getCorrectAnswer())
				|| options.stream().noneMatch(o -> o.equalsIgnoreCase(q.getCorrectAnswer().trim()))) {
			throw new InvalidQuestionException("The correct answer must be one of the options");
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
