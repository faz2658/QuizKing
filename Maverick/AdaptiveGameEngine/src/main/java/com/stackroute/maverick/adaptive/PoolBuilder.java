package com.stackroute.maverick.adaptive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.stackroute.maverick.domain.Game;

/**
 * Builds a session's question pool: malformed questions are dropped, options are shuffled, and
 * questions the player has not seen in earlier sessions come before ones they have.
 */
public class PoolBuilder {

	private final RandomGenerator random;

	public PoolBuilder(RandomGenerator random) {
		this.random = random;
	}

	public List<PooledQuestion> build(Game game, Set<Integer> alreadySeen) {
		List<PooledQuestion> fresh = new ArrayList<>();
		List<PooledQuestion> seen = new ArrayList<>();
		for (Game.Question q : Objects.requireNonNullElse(game.questions(), List.<Game.Question>of())) {
			toPooled(q).ifPresent(p -> (alreadySeen.contains(p.questionId()) ? seen : fresh).add(p));
		}
		Collections.shuffle(fresh, random);
		Collections.shuffle(seen, random);
		fresh.addAll(seen);
		return fresh;
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
