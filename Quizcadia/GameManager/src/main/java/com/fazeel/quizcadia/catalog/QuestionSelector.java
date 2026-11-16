package com.fazeel.quizcadia.catalog;

import java.util.List;
import java.util.stream.Stream;

import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.Questions;

/**
 * Decides which questions a game can draw from. A game no longer freezes a fixed list: it carries its
 * topic's whole pool of playable questions and the game engines pick a fresh set for every player
 * (skipping ones that player has already seen), so playing a game again gives new questions.
 */
public class QuestionSelector {

	/**
	 * Every playable question of the topic, after checking that the pool can satisfy the requested mix.
	 *
	 * @throws NotEnoughQuestionsException if a difficulty level has fewer questions than requested
	 */
	public List<Questions> pool(List<Questions> bank, GameQuestionLevel mix) {
		if (mix == null || total(mix) <= 0) {
			throw new InvalidGameException("A game needs at least one question (easy, medium or advanced)");
		}
		if (mix.getEasyLevel() < 0 || mix.getMediumLevel() < 0 || mix.getAdvancedLevel() < 0) {
			throw new InvalidGameException("Question counts cannot be negative");
		}
		List<Questions> playable = playable(bank);
		require(playable, 1, mix.getEasyLevel());
		require(playable, 2, mix.getMediumLevel());
		require(playable, 3, mix.getAdvancedLevel());
		return playable;
	}

	/** Playable questions of any type (hand-written, generated or imported). */
	public static List<Questions> playable(List<Questions> bank) {
		return bank == null ? List.of() : bank.stream().filter(q -> q != null && isPlayable(q)).toList();
	}

	/** Questions available per level: index 0 = easy, 1 = medium, 2 = advanced. */
	public static int[] countByLevel(List<Questions> pool) {
		int[] c = new int[3];
		for (Questions q : pool) {
			if (q.getQuestionLevel() >= 1 && q.getQuestionLevel() <= 3) {
				c[q.getQuestionLevel() - 1]++;
			}
		}
		return c;
	}

	private static void require(List<Questions> playable, int level, int wanted) {
		long available = playable.stream().filter(q -> q.getQuestionLevel() == level).count();
		if (available < wanted) {
			throw new NotEnoughQuestionsException(level, wanted, (int) available);
		}
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
