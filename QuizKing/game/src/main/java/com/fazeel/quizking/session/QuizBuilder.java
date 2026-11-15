package com.fazeel.quizking.session;

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

import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.domain.GameQuestionLevel;
import com.fazeel.quizking.domain.GameQuestionScore;
import com.fazeel.quizking.domain.GameQuestionTime;
import com.fazeel.quizking.domain.Questions;

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
		Map<Difficulty, Integer> quota = new EnumMap<>(Difficulty.class);
		Map<Difficulty, List<Questions>> unseen = new EnumMap<>(Difficulty.class);
		Map<Difficulty, List<Questions>> seenOldestFirst = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			List<Questions> pool = byLevel.get(d);
			Collections.shuffle(pool, random);
			// stable sort keeps the shuffle among equals: never seen first, then oldest sighting first
			pool.sort(Comparator.comparing((Questions q) -> seen.get(q.getQuestionId()),
					Comparator.nullsFirst(Comparator.naturalOrder())));
			unseen.put(d, new ArrayList<>(pool.stream().filter(q -> !seen.containsKey(q.getQuestionId())).toList()));
			seenOldestFirst.put(d, new ArrayList<>(pool.stream().filter(q -> seen.containsKey(q.getQuestionId())).toList()));
			quota.put(d, mixDefined ? wanted(game.getQuestionLevels(), d) : d == Difficulty.EASY ? DEFAULT_QUESTIONS : 0);
		}

		Map<Difficulty, List<Questions>> picked = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			picked.put(d, new ArrayList<>());
		}
		Map<Difficulty, Integer> need = new EnumMap<>(quota);
		// 1. unseen questions of the requested level
		for (Difficulty d : Difficulty.values()) {
			need.merge(d, -take(unseen.get(d), picked.get(d), need.get(d)), Integer::sum);
		}
		// 2. a level without enough unseen questions borrows unseen ones from the nearest other level,
		// so nothing repeats while the topic still has questions the player hasn't met
		for (Difficulty d : Difficulty.values()) {
			for (Difficulty other : byDistance(d)) {
				need.merge(d, -take(unseen.get(other), picked.get(other), need.get(d)), Integer::sum);
			}
		}
		// 3. only then repeat: the questions the player saw longest ago, own level first
		for (Difficulty d : Difficulty.values()) {
			need.merge(d, -take(seenOldestFirst.get(d), picked.get(d), need.get(d)), Integer::sum);
			for (Difficulty other : byDistance(d)) {
				need.merge(d, -take(seenOldestFirst.get(other), picked.get(other), need.get(d)), Integer::sum);
			}
		}

		List<SessionQuestion> result = new ArrayList<>();
		for (Difficulty d : Difficulty.values()) {
			for (Questions q : picked.get(d)) {
				result.add(toSessionQuestion(q, d, game));
			}
		}
		return result;
	}

	/** Moves up to {@code n} questions from the front of {@code from} to {@code to}. @return how many */
	private static int take(List<Questions> from, List<Questions> to, int n) {
		int k = Math.max(0, Math.min(n, from.size()));
		for (int i = 0; i < k; i++) {
			to.add(from.remove(0));
		}
		return k;
	}

	/** Other levels, nearest first (ties: the easier one). */
	private static List<Difficulty> byDistance(Difficulty d) {
		return java.util.Arrays.stream(Difficulty.values()).filter(o -> o != d)
				.sorted(Comparator.comparingInt((Difficulty o) -> Math.abs(o.level() - d.level()))
						.thenComparingInt(Difficulty::level))
				.toList();
	}

	static boolean isTrueFalse(List<String> options) {
		return options.size() == 2 && options.stream().map(o -> o.toLowerCase(java.util.Locale.ROOT)).sorted().toList()
				.equals(List.of("false", "true"));
	}

	public static boolean isPlayable(Questions q) {
		if (q == null || isBlank(q.getQuestionStem()) || isBlank(q.getCorrectAnswer())) {
			return false;
		}
		List<String> options = options(q);
		return options.size() >= 2
				&& options.stream().anyMatch(o -> GameSession.matches(o, q.getCorrectAnswer()));
	}

	/** One question with the game's time and points for its level (defaults when {@code game} is null). */
	public SessionQuestion toSessionQuestion(Questions q, Game game) {
		return toSessionQuestion(q, Difficulty.fromLevel(q.getQuestionLevel()), game);
	}

	private SessionQuestion toSessionQuestion(Questions q, Difficulty d, Game game) {
		List<String> options = new ArrayList<>(options(q));
		if (isTrueFalse(options)) {
			options = List.of("True", "False"); // keep the natural order
		} else {
			Collections.shuffle(options, random);
		}
		String correct = options.stream().filter(o -> GameSession.matches(o, q.getCorrectAnswer())).findFirst()
				.orElseThrow();
		SessionQuestion sq = new SessionQuestion(q.getQuestionId(), d, q.getQuestionStem().trim(), options, correct,
				seconds(game == null ? null : game.getQuestionTime(), d),
				points(game == null ? null : game.getQuestionScore(), d));
		sq.setExplanation(isBlank(q.getExplanation()) ? null : q.getExplanation().trim());
		return sq;
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
