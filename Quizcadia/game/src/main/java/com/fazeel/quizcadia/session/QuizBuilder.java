package com.fazeel.quizcadia.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.GameQuestionScore;
import com.fazeel.quizcadia.domain.GameQuestionTime;
import com.fazeel.quizcadia.domain.Questions;

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

	/** Share of a game's questions that come from the player's own country when it has enough. */
	static final double HOME_SHARE = 0.75;

	/** Pass as the country for an International quiz: questions from every country and general ones alike. */
	public static final String EVERYWHERE = "*";

	/**
	 * The country a quiz for several players favours: their country when every one of them is from the same known
	 * country, otherwise null, which means general questions only (players from different countries, or a
	 * country unknown). The same rule as multiplayer matches.
	 */
	public static String sharedCountry(Collection<String> countries) {
		List<String> codes = countries.stream()
				.map(c -> c == null || c.isBlank() ? null : c.trim().toUpperCase(Locale.ROOT)).toList();
		if (codes.isEmpty() || codes.stream().anyMatch(Objects::isNull) || codes.stream().distinct().count() != 1) {
			return null;
		}
		return codes.get(0);
	}

	/**
	 * Like {@link #build(Game, Map)}, for a player from {@code country}. Questions tagged with that country make
	 * up about {@value #HOME_SHARE} of the quiz and general (untagged) questions the rest; other countries'
	 * questions are left out. Unseen questions still come first: when the player has met all their country's
	 * questions, general ones fill the gap before anything repeats. Without a country only general questions are
	 * used, since shared topics hold every country's questions; a game with neither the player's country nor
	 * general questions uses all it has.
	 */
	public List<SessionQuestion> build(Game game, Map<Integer, Instant> seen, String country) {
		if (EVERYWHERE.equals(country)) {
			return build(game, seen);
		}
		String home = country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
		List<Questions> playable = Objects.requireNonNullElse(game.getQuestions(), List.<Questions>of()).stream()
				.filter(QuizBuilder::isPlayable).toList();
		List<Questions> mine = home == null ? List.of()
				: playable.stream().filter(q -> home.equalsIgnoreCase(trimmed(q.getCountry()))).toList();
		List<Questions> general = playable.stream().filter(q -> isBlank(q.getCountry())).toList();
		if (home == null) {
			return build(game, general.isEmpty() ? playable : general, seen);
		}
		if (mine.isEmpty() && general.isEmpty()) {
			return build(game, seen);
		}

		Map<Difficulty, Integer> need = quota(game);
		int total = need.values().stream().mapToInt(Integer::intValue).sum();
		int homeShare = (int) Math.round(total * HOME_SHARE);
		Pool own = new Pool(mine, seen);
		Pool world = new Pool(general, seen);
		Map<Difficulty, List<Questions>> picked = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			picked.put(d, new ArrayList<>());
		}
		// new questions first: the home share from the player's country, the rest from general questions,
		// then more of the country's if general ones ran out
		int fromHome = fill(own.unseen, need, picked, homeShare);
		fill(world.unseen, need, picked, Integer.MAX_VALUE);
		fromHome += fill(own.unseen, need, picked, Integer.MAX_VALUE);
		// only then repeats, oldest sighting first, keeping the same proportion
		fill(own.seenOldestFirst, need, picked, Math.max(0, homeShare - fromHome));
		fill(world.seenOldestFirst, need, picked, Integer.MAX_VALUE);
		fill(own.seenOldestFirst, need, picked, Integer.MAX_VALUE);

		List<SessionQuestion> result = new ArrayList<>();
		for (Difficulty d : Difficulty.values()) {
			List<Questions> level = picked.get(d);
			Collections.shuffle(level, random); // mix country and general questions within each level
			for (Questions q : level) {
				result.add(toSessionQuestion(q, d, game));
			}
		}
		return result;
	}

	/** How many questions of each level the game asks for. */
	private static Map<Difficulty, Integer> quota(Game game) {
		boolean mixDefined = wanted(game.getQuestionLevels(), null) > 0;
		Map<Difficulty, Integer> quota = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			quota.put(d, mixDefined ? wanted(game.getQuestionLevels(), d) : d == Difficulty.EASY ? DEFAULT_QUESTIONS : 0);
		}
		return quota;
	}

	/** Some questions sorted for picking: per level, never-seen ones in random order, and seen ones oldest first. */
	private final class Pool {
		final Map<Difficulty, List<Questions>> unseen = new EnumMap<>(Difficulty.class);
		final Map<Difficulty, List<Questions>> seenOldestFirst = new EnumMap<>(Difficulty.class);

		Pool(List<Questions> questions, Map<Integer, Instant> seen) {
			for (Difficulty d : Difficulty.values()) {
				List<Questions> level = new ArrayList<>(questions.stream()
						.filter(q -> Difficulty.fromLevel(q.getQuestionLevel()) == d).toList());
				Collections.shuffle(level, random);
				unseen.put(d, new ArrayList<>(level.stream().filter(q -> !seen.containsKey(q.getQuestionId())).toList()));
				List<Questions> old = new ArrayList<>(level.stream().filter(q -> seen.containsKey(q.getQuestionId())).toList());
				old.sort(Comparator.comparing((Questions q) -> seen.get(q.getQuestionId())));
				seenOldestFirst.put(d, old);
			}
		}
	}

	/**
	 * Takes questions from {@code source} for what each level still {@code need}s, own level first and then the
	 * nearest other level, at most {@code limit} in all. @return how many were taken
	 */
	private static int fill(Map<Difficulty, List<Questions>> source, Map<Difficulty, Integer> need,
			Map<Difficulty, List<Questions>> picked, int limit) {
		int taken = 0;
		for (Difficulty d : Difficulty.values()) {
			int k = take(source.get(d), picked.get(d), Math.min(need.get(d), limit - taken));
			need.merge(d, -k, Integer::sum);
			taken += k;
		}
		for (Difficulty d : Difficulty.values()) {
			for (Difficulty other : byDistance(d)) {
				int k = take(source.get(other), picked.get(other), Math.min(need.get(d), limit - taken));
				need.merge(d, -k, Integer::sum);
				taken += k;
			}
		}
		return taken;
	}

	private static String trimmed(String s) {
		return s == null ? "" : s.trim();
	}

	/** @param seen when the player last saw each question, by question id */
	public List<SessionQuestion> build(Game game, Map<Integer, Instant> seen) {
		return build(game, Objects.requireNonNullElse(game.getQuestions(), List.<Questions>of()), seen);
	}

	/** The game's quiz from {@code candidates} instead of all of the game's questions. */
	private List<SessionQuestion> build(Game game, List<Questions> candidates, Map<Integer, Instant> seen) {
		Map<Difficulty, List<Questions>> byLevel = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			byLevel.put(d, new ArrayList<>());
		}
		for (Questions q : candidates) {
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
		return Arrays.stream(Difficulty.values()).filter(o -> o != d)
				.sorted(Comparator.comparingInt((Difficulty o) -> Math.abs(o.level() - d.level()))
						.thenComparingInt(Difficulty::level))
				.toList();
	}

	static boolean isTrueFalse(List<String> options) {
		return options.size() == 2 && options.stream().map(o -> o.toLowerCase(Locale.ROOT)).sorted().toList()
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
