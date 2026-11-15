package com.fazeel.maverick.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fazeel.maverick.domain.GameQuestionLevel;
import com.fazeel.maverick.domain.Questions;

/**
 * Picks the questions for a new game from a topic's question bank.
 * <p>
 * Single-player games use hand-written ("manual") questions, multiplayer games use generated ("auto")
 * questions, matching how the question generator tags them. Question types are compared ignoring case
 * (the generator writes "Auto"); a question without a type counts as manual.
 */
public class QuestionSelector {

	public enum Source {
		MANUAL, AUTO
	}

	private final RandomGenerator random;

	public QuestionSelector(RandomGenerator random) {
		this.random = random;
	}

	/**
	 * Randomly selects exactly the requested number of questions per level.
	 *
	 * @throws NotEnoughQuestionsException if the bank cannot satisfy the requested mix
	 */
	public List<Questions> select(List<Questions> bank, GameQuestionLevel mix, Source source) {
		if (mix == null || total(mix) <= 0) {
			throw new InvalidGameException("A game needs at least one question (easy, medium or advanced)");
		}
		if (mix.getEasyLevel() < 0 || mix.getMediumLevel() < 0 || mix.getAdvancedLevel() < 0) {
			throw new InvalidGameException("Question counts cannot be negative");
		}
		List<Questions> usable = bank == null ? List.of()
				: bank.stream().filter(q -> q != null && sourceOf(q) == source && isPlayable(q)).toList();
		List<Questions> picked = new ArrayList<>();
		picked.addAll(pick(usable, 1, mix.getEasyLevel()));
		picked.addAll(pick(usable, 2, mix.getMediumLevel()));
		picked.addAll(pick(usable, 3, mix.getAdvancedLevel()));
		return picked;
	}

	private List<Questions> pick(List<Questions> usable, int level, int wanted) {
		List<Questions> pool = new ArrayList<>(usable.stream().filter(q -> q.getQuestionLevel() == level).toList());
		if (pool.size() < wanted) {
			throw new NotEnoughQuestionsException(level, wanted, pool.size());
		}
		Collections.shuffle(pool, random);
		return pool.subList(0, wanted);
	}

	static Source sourceOf(Questions q) {
		return "auto".equalsIgnoreCase(q.getQuestionType() == null ? null : q.getQuestionType().trim()) ? Source.AUTO
				: Source.MANUAL;
	}

	/** Same rule the game engines apply: a stem, two or more options, and the answer among them. */
	static boolean isPlayable(Questions q) {
		if (blank(q.getQuestionStem()) || blank(q.getCorrectAnswer())) {
			return false;
		}
		List<String> options = Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4())
				.filter(o -> !blank(o)).map(String::trim).distinct().toList();
		return options.size() >= 2 && options.stream().anyMatch(o -> o.equalsIgnoreCase(q.getCorrectAnswer().trim()));
	}

	private static int total(GameQuestionLevel m) {
		return m.getEasyLevel() + m.getMediumLevel() + m.getAdvancedLevel();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
