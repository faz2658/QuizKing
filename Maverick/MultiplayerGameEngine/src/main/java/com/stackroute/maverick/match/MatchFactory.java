package com.stackroute.maverick.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.stackroute.maverick.domain.Match;
import com.stackroute.maverick.domain.MultiPlayerGame;

/** Builds a match from matched players and a game definition, dropping malformed questions. */
public class MatchFactory {

	static final int DEFAULT_SECONDS = 15;
	static final int DEFAULT_POINTS = 10;

	private final RandomGenerator random;

	public MatchFactory(RandomGenerator random) {
		this.random = random;
	}

	public MultiplayerMatch create(Match match, MultiPlayerGame game, Instant now) {
		List<MatchQuestion> questions = new ArrayList<>();
		for (MultiPlayerGame.Question q : Objects.requireNonNullElse(game.autoquestions(),
				List.<MultiPlayerGame.Question>of())) {
			toQuestion(q).ifPresent(questions::add);
		}
		Collections.shuffle(questions, random);
		return new MultiplayerMatch(match.matchId(), game.gameId(), game.gameName(), game.categoryId(),
				match.players(), questions, game.timePerQuestion() > 0 ? game.timePerQuestion() : DEFAULT_SECONDS,
				game.scorePerQuestion() > 0 ? game.scorePerQuestion() : DEFAULT_POINTS, now);
	}

	private java.util.Optional<MatchQuestion> toQuestion(MultiPlayerGame.Question q) {
		if (q == null || blank(q.questionStem()) || blank(q.correctAnswer())) {
			return java.util.Optional.empty();
		}
		List<String> options = new ArrayList<>(Stream.of(q.option1(), q.option2(), q.option3(), q.option4())
				.filter(o -> !blank(o)).map(String::trim).distinct().toList());
		String correct = options.stream().filter(o -> o.equalsIgnoreCase(q.correctAnswer().trim())).findFirst()
				.orElse(null);
		if (options.size() < 2 || correct == null) {
			return java.util.Optional.empty();
		}
		Collections.shuffle(options, random);
		return java.util.Optional.of(new MatchQuestion(q.questionId(), q.questionStem().trim(), options, correct));
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
