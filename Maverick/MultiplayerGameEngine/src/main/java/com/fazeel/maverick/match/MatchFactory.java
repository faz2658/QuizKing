package com.fazeel.maverick.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fazeel.maverick.domain.Match;
import com.fazeel.maverick.domain.MultiPlayerGame;

/** Builds a match from matched players and a game definition, dropping malformed questions. */
public class MatchFactory {

	static final int DEFAULT_SECONDS = 15;
	static final int DEFAULT_POINTS = 10;
	static final int DEFAULT_QUESTIONS = 10;

	private final RandomGenerator random;

	public MatchFactory(RandomGenerator random) {
		this.random = random;
	}

	public MultiplayerMatch create(Match match, MultiPlayerGame game, Instant now) {
		return create(match, game, now, Map.of());
	}

	/**
	 * Picks the match's questions from the game's pool: the game's level mix (or its question count,
	 * default {@value #DEFAULT_QUESTIONS}), preferring questions that the fewest of these players have
	 * already seen. Questions are asked from easy to advanced.
	 *
	 * @param seenBy how many of the match's players have already seen each question, by question id
	 */
	public MultiplayerMatch create(Match match, MultiPlayerGame game, Instant now, Map<Integer, Integer> seenBy) {
		Map<Integer, List<Candidate>> byLevel = new TreeMap<>();
		for (MultiPlayerGame.Question q : Objects.requireNonNullElse(game.autoquestions(),
				List.<MultiPlayerGame.Question>of())) {
			if (q == null) {
				continue;
			}
			int level = Math.max(1, Math.min(3, q.questionLevel()));
			toQuestion(q).ifPresent(m -> byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(new Candidate(level, m)));
		}
		for (List<Candidate> level : byLevel.values()) {
			Collections.shuffle(level, random);
			level.sort(Comparator.comparingInt(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0)));
		}
		MultiPlayerGame.Level mix = game.questionLevel();
		int target = mix != null && mix.total() > 0 ? mix.total()
				: game.numberOfQuestions() > 0 ? game.numberOfQuestions() : DEFAULT_QUESTIONS;
		List<Candidate> picked = new ArrayList<>();
		if (mix != null && mix.total() > 0) {
			// per level, only questions none of the players has seen; shortfalls are filled below
			byLevel.forEach((level, cs) -> picked.addAll(cs.stream()
					.filter(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0) == 0).limit(mix.of(level)).toList()));
		}
		// fill up (no mix, or a level ran short) with the least-seen remaining questions
		List<Candidate> rest = byLevel.values().stream().flatMap(List::stream).filter(c -> !picked.contains(c))
				.sorted(Comparator.comparingInt(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0)))
				.toList();
		for (Candidate c : rest) {
			if (picked.size() >= target) {
				break;
			}
			picked.add(c);
		}
		List<MatchQuestion> questions = picked.stream().sorted(Comparator.comparingInt(Candidate::level))
				.map(Candidate::question).toList();
		return new MultiplayerMatch(match.matchId(), game.gameId(), game.gameName(), game.categoryId(),
				match.players(), questions, game.timePerQuestion() > 0 ? game.timePerQuestion() : DEFAULT_SECONDS,
				game.scorePerQuestion() > 0 ? game.scorePerQuestion() : DEFAULT_POINTS, now, match.names(), match.countries());
	}

	private record Candidate(int level, MatchQuestion question) {
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
		if (options.size() == 2 && options.stream().map(o -> o.toLowerCase(java.util.Locale.ROOT)).sorted().toList()
				.equals(List.of("false", "true"))) {
			options = new ArrayList<>(List.of("True", "False")); // keep the natural order
		} else {
			Collections.shuffle(options, random);
		}
		return java.util.Optional.of(new MatchQuestion(q.questionId(), q.questionStem().trim(), options, correct));
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
