package com.fazeel.quizcadia.match;

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

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

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
		List<Candidate> all = new ArrayList<>();
		for (MultiPlayerGame.Question q : Objects.requireNonNullElse(game.autoquestions(),
				List.<MultiPlayerGame.Question>of())) {
			if (q == null) {
				continue;
			}
			int level = Math.max(1, Math.min(3, q.questionLevel()));
			toQuestion(q).ifPresent(m -> all.add(new Candidate(level, m, code(q.country()))));
		}
		MultiPlayerGame.Level mix = game.questionLevel();
		int target = mix != null && mix.total() > 0 ? mix.total()
				: game.numberOfQuestions() > 0 ? game.numberOfQuestions() : DEFAULT_QUESTIONS;
		List<Candidate> general = all.stream().filter(c -> c.country() == null).toList();
		Players players = players(match);
		List<Candidate> picked;
		if (players.home() != null) {
			// everyone is from one country: mostly that country's questions, the rest general
			List<Candidate> mine = all.stream().filter(c -> players.home().equals(c.country())).toList();
			picked = mine.isEmpty() && general.isEmpty() ? pick(all, mix, target, seenBy)
					: pickWithHome(mine, general, mix, target, seenBy);
		} else if (!general.isEmpty()) {
			// players from different countries (or countries unknown): general questions only, so nobody plays
			// at home and nobody gets another country's questions
			picked = pick(general, mix, target, seenBy);
		} else {
			picked = pick(all, mix, target, seenBy);
		}
		List<MatchQuestion> questions = picked.stream().sorted(Comparator.comparingInt(Candidate::level))
				.map(Candidate::question).toList();
		return new MultiplayerMatch(match.matchId(), game.gameId(), game.gameName(), game.categoryId(),
				match.players(), questions, game.timePerQuestion() > 0 ? game.timePerQuestion() : DEFAULT_SECONDS,
				game.scorePerQuestion() > 0 ? game.scorePerQuestion() : DEFAULT_POINTS, now, match.names(), match.countries())
				.shownOn(match.screenId());
	}

	/**
	 * A live room quiz from {@code pool}, asked in that order (the question service already shuffled it). Malformed
	 * and repeated questions are dropped; options are shuffled like in any match.
	 */
	public MultiplayerMatch roomQuiz(Match match, List<MultiPlayerGame.Question> pool, Instant now) {
		Match.RoomQuiz rules = match.quiz();
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		List<MatchQuestion> questions = pool.stream().filter(q -> q != null && ids.add(q.questionId()))
				.map(this::toQuestion).flatMap(java.util.Optional::stream).limit(Math.max(1, rules.questions())).toList();
		return MultiplayerMatch.roomQuiz(match.matchId(), match.players(), match.names(), questions,
				rules.secondsPerQuestion() > 0 ? rules.secondsPerQuestion() : ROOM_QUIZ_SECONDS, rules.revealSeconds(),
				match.screenId(), now);
	}

	static final int ROOM_QUIZ_SECONDS = 20;

	/**
	 * The game's level mix (or {@code target} questions) from {@code candidates}, preferring questions that the
	 * fewest of the players have seen: per level only unseen ones first, then the least-seen of the rest.
	 */
	private List<Candidate> pick(List<Candidate> candidates, MultiPlayerGame.Level mix, int target,
			Map<Integer, Integer> seenBy) {
		Map<Integer, List<Candidate>> byLevel = byLevel(candidates, seenBy);
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
		return picked;
	}

	/**
	 * About {@value #HOME_SHARE} of the questions from the players' own country and the rest general: unseen
	 * country questions up to that share, unseen general ones for the rest, more unseen country ones if general
	 * ran short, and only then the least-seen of either.
	 */
	private List<Candidate> pickWithHome(List<Candidate> mine, List<Candidate> general, MultiPlayerGame.Level mix,
			int target, Map<Integer, Integer> seenBy) {
		Map<Integer, Integer> need = new TreeMap<>();
		if (mix != null && mix.total() > 0) {
			for (int level = 1; level <= 3; level++) {
				need.put(level, mix.of(level));
			}
		} else {
			need.put(ANY_LEVEL, target);
		}
		List<Candidate> own = sorted(mine, seenBy);
		List<Candidate> world = sorted(general, seenBy);
		List<Candidate> picked = new ArrayList<>();
		int homeShare = (int) Math.round(target * HOME_SHARE);
		takeUnseen(own, need, picked, homeShare, seenBy);
		takeUnseen(world, need, picked, Integer.MAX_VALUE, seenBy);
		takeUnseen(own, need, picked, Integer.MAX_VALUE, seenBy);
		List<Candidate> rest = java.util.stream.Stream.concat(own.stream(), world.stream())
				.filter(c -> !picked.contains(c))
				.sorted(Comparator.comparingInt(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0)))
				.toList();
		for (Candidate c : rest) {
			if (picked.size() >= target) {
				break;
			}
			picked.add(c);
		}
		Collections.shuffle(picked, random); // country and general questions mixed within each level
		return picked;
	}

	/** Moves unseen candidates into {@code picked} while their level still needs some, at most {@code limit}. */
	private static void takeUnseen(List<Candidate> from, Map<Integer, Integer> need, List<Candidate> picked, int limit,
			Map<Integer, Integer> seenBy) {
		int taken = 0;
		for (Candidate c : from) {
			if (taken >= limit) {
				return;
			}
			int bucket = need.containsKey(ANY_LEVEL) ? ANY_LEVEL : c.level();
			if (picked.contains(c) || seenBy.getOrDefault(c.question().getQuestionId(), 0) > 0
					|| need.getOrDefault(bucket, 0) <= 0) {
				continue;
			}
			picked.add(c);
			need.merge(bucket, -1, Integer::sum);
			taken++;
		}
	}

	private Map<Integer, List<Candidate>> byLevel(List<Candidate> candidates, Map<Integer, Integer> seenBy) {
		Map<Integer, List<Candidate>> byLevel = new TreeMap<>();
		candidates.forEach(c -> byLevel.computeIfAbsent(c.level(), k -> new ArrayList<>()).add(c));
		for (List<Candidate> level : byLevel.values()) {
			Collections.shuffle(level, random);
			level.sort(Comparator.comparingInt(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0)));
		}
		return byLevel;
	}

	/** Shuffled, then least-seen first. */
	private List<Candidate> sorted(List<Candidate> candidates, Map<Integer, Integer> seenBy) {
		List<Candidate> out = new ArrayList<>(candidates);
		Collections.shuffle(out, random);
		out.sort(Comparator.comparingInt(c -> seenBy.getOrDefault(c.question().getQuestionId(), 0)));
		return out;
	}

	/** Share of the questions from the players' own country when they all come from one. */
	static final double HOME_SHARE = 0.75;

	private static final int ANY_LEVEL = 0;

	/**
	 * Where the match's people come from: {@code home} when every player (bots aside) is from the same known
	 * country; {@code mixed} when they come from different countries or some are unknown.
	 */
	record Players(String home, boolean mixed) {
	}

	static Players players(Match match) {
		List<Integer> people = match.players() == null ? List.of() : match.players().stream()
				.filter(Objects::nonNull).filter(id -> id > 0).toList();
		if (people.isEmpty()) {
			people = match.players() == null ? List.of() : match.players().stream().filter(Objects::nonNull).toList();
		}
		List<String> codes = people.stream().map(id -> code(match.countries().get(id))).toList();
		if (codes.stream().allMatch(Objects::isNull)) {
			return new Players(null, false); // nothing known: general questions, like a mixed match
		}
		if (codes.stream().noneMatch(Objects::isNull) && codes.stream().distinct().count() == 1) {
			return new Players(codes.get(0), false);
		}
		return new Players(null, true);
	}

	private static String code(String country) {
		return country == null || country.isBlank() ? null : country.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private record Candidate(int level, MatchQuestion question, String country) {
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
