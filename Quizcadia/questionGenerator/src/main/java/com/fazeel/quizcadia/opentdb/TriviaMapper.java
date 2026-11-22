package com.fazeel.quizcadia.opentdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.fazeel.quizcadia.domain.Questions;

/** Turns Open Trivia DB questions into question-bank questions. */
public class TriviaMapper {

	/** Imported ids live in [1.5e9, 2^31): never colliding with hand-written or generated ones. */
	static final int IMPORTED_ID_BASE = 1_500_000_000;
	static final String TYPE = "imported";

	private final RandomGenerator random;

	public TriviaMapper(RandomGenerator random) {
		this.random = random;
	}

	/** Where a question goes: "Entertainment: Film" becomes category Entertainment, topic Film. */
	public record Placement(String category, String topic) {

		static Placement of(String otdbCategory) {
			String c = otdbCategory == null || otdbCategory.isBlank() ? "General Knowledge" : otdbCategory.trim();
			int colon = c.indexOf(':');
			return colon < 0 ? new Placement(c, c)
					: new Placement(c.substring(0, colon).trim(), c.substring(colon + 1).trim());
		}
	}

	/** The bank category and topic for the question. */
	public Placement placement(TriviaQuestion q) {
		return Placement.of(q.category());
	}

	/**
	 * The bank question, with the options shuffled (True, False for true/false ones); empty when it is incomplete
	 * or has more than four options.
	 */
	public Optional<Questions> map(TriviaQuestion t) {
		if (t.question() == null || t.correctAnswer() == null || t.incorrectAnswers() == null
				|| t.incorrectAnswers().isEmpty()) {
			return Optional.empty();
		}
		List<String> options;
		if ("boolean".equals(t.type())) {
			options = List.of("True", "False");
		} else {
			options = new ArrayList<>(t.incorrectAnswers());
			options.add(t.correctAnswer());
			Collections.shuffle(options, random);
		}
		if (options.size() > 4) {
			return Optional.empty();
		}
		Questions q = new Questions();
		q.setQuestionId(idFor(t.question()));
		q.setQuestionLevel(level(t.difficulty()));
		q.setQuestionType(TYPE);
		q.setQuestionStem(t.question().trim());
		q.setOption1(options.get(0));
		q.setOption2(options.get(1));
		q.setOption3(options.size() > 2 ? options.get(2) : null);
		q.setOption4(options.size() > 3 ? options.get(3) : null);
		q.setCorrectAnswer(t.correctAnswer().trim());
		return Optional.of(q);
	}

	static int level(String difficulty) {
		return switch (difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT)) {
			case "hard" -> 3;
			case "medium" -> 2;
			default -> 1;
		};
	}

	/** Same question text always gets the same id, so re-importing updates instead of duplicating. */
	static int idFor(String questionText) {
		int h = questionText.trim().toLowerCase(Locale.ROOT).hashCode();
		return IMPORTED_ID_BASE + Math.floorMod(h, Integer.MAX_VALUE - IMPORTED_ID_BASE);
	}
}
