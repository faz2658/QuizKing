package com.fazeel.maverick.adaptive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fazeel.maverick.domain.Game;

/**
 * Builds a session's question pool: malformed questions are dropped, options are shuffled, and
 * within each level questions the player has never seen come first, then the ones seen longest ago.
 */
public class PoolBuilder {

	private final RandomGenerator random;

	public PoolBuilder(RandomGenerator random) {
		this.random = random;
	}

	/**
	 * @param seen when the player last saw each question, by id
	 * @param perLevel at most this many questions per level are kept (a session never needs more)
	 */
	public List<PooledQuestion> build(Game game, Map<Integer, Instant> seen, int perLevel) {
		Map<Integer, List<PooledQuestion>> byLevel = new TreeMap<>();
		for (Game.Question q : Objects.requireNonNullElse(game.questions(), List.<Game.Question>of())) {
			toPooled(q).ifPresent(p -> byLevel.computeIfAbsent(p.level(), k -> new ArrayList<>()).add(p));
		}
		List<PooledQuestion> pool = new ArrayList<>();
		for (List<PooledQuestion> level : byLevel.values()) {
			Collections.shuffle(level, random);
			// never seen first (random order), then the ones seen longest ago
			level.sort(Comparator.comparing((PooledQuestion p) -> seen.get(p.questionId()),
					Comparator.nullsFirst(Comparator.naturalOrder())));
			pool.addAll(level.subList(0, Math.min(perLevel, level.size())));
		}
		return pool;
	}

	private Optional<PooledQuestion> toPooled(Game.Question q) {
		if (q == null || blank(q.questionStem()) || blank(q.correctAnswer())) {
			return Optional.empty();
		}
		List<String> options = new ArrayList<>(Stream.of(q.option1(), q.option2(), q.option3(), q.option4())
				.filter(o -> !blank(o)).map(String::trim).distinct().toList());
		String correct = options.stream().filter(o -> o.equalsIgnoreCase(q.correctAnswer().trim())).findFirst()
				.orElse(null);
		if (options.size() < 2 || correct == null) {
			return Optional.empty();
		}
		Collections.shuffle(options, random);
		int level = Math.max(AdaptiveSession.MIN_LEVEL, Math.min(AdaptiveSession.MAX_LEVEL, q.questionLevel()));
		return Optional.of(new PooledQuestion(q.questionId(), level, q.questionStem().trim(), List.copyOf(options),
				correct));
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
