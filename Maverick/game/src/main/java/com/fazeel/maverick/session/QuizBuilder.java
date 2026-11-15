package com.fazeel.maverick.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fazeel.maverick.domain.Game;
import com.fazeel.maverick.domain.GameQuestionLevel;
import com.fazeel.maverick.domain.GameQuestionScore;
import com.fazeel.maverick.domain.GameQuestionTime;
import com.fazeel.maverick.domain.Questions;

/**
 * Turns a game definition into the ordered list of questions one player will be asked.
 * <ul>
 * <li>Malformed questions (no stem, fewer than two options, answer not among the options) are dropped.</li>
 * <li>The game's level mix (e.g. 3 easy, 2 medium, 1 advanced) is honoured as far as questions exist;
 * if the game defines no mix every valid question is used.</li>
 * <li>Questions are asked easy first, then medium, then advanced; options are shuffled.</li>
 * <li>Within a level, questions the player has never seen come first (in random order), then the ones
 * they saw longest ago, so a player only meets a question again after running out of new ones.</li>
 * <li>A game without a mix asks {@value #DEFAULT_QUESTIONS} questions.</li>
 * <li>Time limits and points come from the game, falling back to the {@link Difficulty} defaults.</li>
 * </ul>
 */
public class QuizBuilder {

	static final int DEFAULT_QUESTIONS = 10;

	private final RandomGenerator random;

	public QuizBuilder(RandomGenerator random) {
		this.random = random;
	}

	public List<SessionQuestion> build(Game game) {
		return build(game, Map.of());
	}

	/** @param seen when the player last saw each question, by question id */
	public List<SessionQuestion> build(Game game, Map<Integer, Instant> seen) {
		Map<Difficulty, List<Questions>> byLevel = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			byLevel.put(d, new ArrayList<>());
		}
		for (Questions q : Objects.requireNonNullElse(game.getQuestions(), List.<Questions>of())) {
			if (isPlayable(q)) {
				byLevel.get(Difficulty.fromLevel(q.getQuestionLevel())).add(q);
			}
		}

		boolean mixDefined = wanted(game.getQuestionLevels(), null) > 0;
		List<SessionQuestion> result = new ArrayList<>();
		for (Difficulty d : Difficulty.values()) {
			List<Questions> pool = byLevel.get(d);
			Collections.shuffle(pool, random);
			// stable sort keeps the shuffle among equals: never seen first, then oldest sighting first
			pool.sort(Comparator.comparing((Questions q) -> seen.get(q.getQuestionId()),
					Comparator.nullsFirst(Comparator.naturalOrder())));
			int take = mixDefined ? Math.min(wanted(game.getQuestionLevels(), d), pool.size())
					: Math.min(pool.size(), Math.max(0, DEFAULT_QUESTIONS - result.size()));
			for (Questions q : pool.subList(0, take)) {
				result.add(toSessionQuestion(q, d, game));
			}
		}
		return result;
	}

	static boolean isPlayable(Questions q) {
		if (q == null || isBlank(q.getQuestionStem()) || isBlank(q.getCorrectAnswer())) {
			return false;
		}
		List<String> options = options(q);
		return options.size() >= 2
				&& options.stream().anyMatch(o -> GameSession.matches(o, q.getCorrectAnswer()));
	}

	private SessionQuestion toSessionQuestion(Questions q, Difficulty d, Game game) {
		List<String> options = new ArrayList<>(options(q));
		Collections.shuffle(options, random);
		String correct = options.stream().filter(o -> GameSession.matches(o, q.getCorrectAnswer())).findFirst()
				.orElseThrow();
		return new SessionQuestion(q.getQuestionId(), d, q.getQuestionStem().trim(), options, correct,
				seconds(game.getQuestionTime(), d), points(game.getQuestionScore(), d));
	}

	private static List<String> options(Questions q) {
		return Stream.of(q.getOption1(), q.getOption2(), q.getOption3(), q.getOption4())
				.filter(o -> !isBlank(o)).map(String::trim).distinct().toList();
	}

	/** Number of questions of level {@code d} the game asks for; {@code d == null} means all levels. */
	private static int wanted(GameQuestionLevel l, Difficulty d) {
		if (l == null) {
			return 0;
		}
		if (d == null) {
			return Math.max(0, l.getEasyLevel()) + Math.max(0, l.getMediumLevel()) + Math.max(0, l.getAdvancedLevel());
		}
		return Math.max(0, switch (d) {
			case EASY -> l.getEasyLevel();
			case MEDIUM -> l.getMediumLevel();
			case ADVANCED -> l.getAdvancedLevel();
		});
	}

	private static int seconds(GameQuestionTime t, Difficulty d) {
		int configured = t == null ? 0 : switch (d) {
			case EASY -> t.getEasyQuestionsTime();
			case MEDIUM -> t.getMediumQuestionsTime();
			case ADVANCED -> t.getAdvanceQuestionsTime();
		};
		return configured > 0 ? configured : d.defaultSeconds();
	}

	private static int points(GameQuestionScore s, Difficulty d) {
		int configured = s == null ? 0 : switch (d) {
			case EASY -> s.getEasyQuestionsScore();
			case MEDIUM -> s.getMediumQuestionsScore();
			case ADVANCED -> s.getAdvanceQuestionsScore();
		};
		return configured > 0 ? configured : d.defaultPoints();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
