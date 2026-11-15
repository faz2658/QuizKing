package com.fazeel.quizking.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.fazeel.quizking.domain.Match;

/**
 * "Play the world": one global queue for everyone, any topic. A match starts as soon as {@value #MAX_PLAYERS}
 * players are in, or once the first has waited {@link #GATHER} with company. Someone still alone after
 * {@link #BOT_AFTER} plays a bot, so nobody waits forever.
 */
public class QuickMatch {

	static final int MAX_PLAYERS = 8;
	static final Duration GATHER = Duration.ofSeconds(5);
	static final Duration BOT_AFTER = Duration.ofSeconds(12);
	static final String[] BOT_NAMES = { "Ada", "Turing", "Hopper", "Curie", "Tesla", "Lovelace", "Euler", "Noether", "Gauss" };
	static final String[] BOT_COUNTRIES = { "GB", "US", "IN", "BR", "JP", "DE", "FR", "NG", "AU", "CA", "MX", "KR" };

	public enum State {
		WAITING, MATCHED, NOT_IN_QUEUE, NO_GAMES
	}

	public record Status(State state, int queueSize, long waitedSeconds, long botInSeconds, String matchId,
			Integer gameId, boolean withBot) {
	}

	private final Clock clock;
	private final RandomGenerator random;
	private final Supplier<List<Integer>> multiplayerGames;
	private final Consumer<Match> onMatch;
	private final Map<Integer, Instant> queue = new LinkedHashMap<>();
	private final Map<Integer, Status> outcomes = new HashMap<>();

	public QuickMatch(Clock clock, RandomGenerator random, Supplier<List<Integer>> multiplayerGames,
			Consumer<Match> onMatch) {
		this.clock = clock;
		this.random = random;
		this.multiplayerGames = multiplayerGames;
		this.onMatch = onMatch;
	}

	public synchronized Status join(int userId) {
		outcomes.remove(userId);
		queue.putIfAbsent(userId, clock.instant());
		tick();
		return status(userId);
	}

	public synchronized void leave(int userId) {
		queue.remove(userId);
		outcomes.remove(userId);
	}

	public synchronized int waiting() {
		return queue.size();
	}

	public synchronized Status status(int userId) {
		Status outcome = outcomes.get(userId);
		if (outcome != null) {
			return outcome;
		}
		Instant joined = queue.get(userId);
		if (joined == null) {
			return new Status(State.NOT_IN_QUEUE, queue.size(), 0, 0, null, null, false);
		}
		Instant now = clock.instant();
		long waited = Duration.between(joined, now).toSeconds();
		long botIn = queue.size() == 1 ? Math.max(0, BOT_AFTER.toSeconds() - waited) : 0;
		return new Status(State.WAITING, queue.size(), waited, botIn, null, null, false);
	}

	/** Called every half second (and on every join). */
	public synchronized void tick() {
		if (queue.isEmpty()) {
			return;
		}
		Instant now = clock.instant();
		Duration oldest = Duration.between(queue.values().iterator().next(), now);
		boolean full = queue.size() >= MAX_PLAYERS;
		boolean gathered = queue.size() >= 2 && oldest.compareTo(GATHER) >= 0;
		boolean lonely = queue.size() == 1 && oldest.compareTo(BOT_AFTER) >= 0;
		if (!full && !gathered && !lonely) {
			return;
		}
		List<Integer> games = multiplayerGames.get();
		if (games.isEmpty()) {
			for (int id : List.copyOf(queue.keySet())) {
				outcomes.put(id, new Status(State.NO_GAMES, 0, 0, 0, null, null, false));
			}
			queue.clear();
			return;
		}
		List<Integer> players = new ArrayList<>(queue.keySet()).subList(0, Math.min(MAX_PLAYERS, queue.size()));
		players = new ArrayList<>(players);
		players.forEach(queue::remove);
		Map<Integer, String> names = new HashMap<>();
		Map<Integer, String> countries = new HashMap<>();
		boolean withBot = players.size() == 1;
		if (withBot) {
			int bot = -1 - random.nextInt(BOT_NAMES.length);
			players.add(bot);
			names.put(bot, "🤖 " + BOT_NAMES[-1 - bot]);
			countries.put(bot, BOT_COUNTRIES[random.nextInt(BOT_COUNTRIES.length)]);
		}
		int gameId = games.get(random.nextInt(games.size()));
		Match match = new Match(UUID.randomUUID().toString(), gameId, players, now, names, countries);
		for (int p : players) {
			if (p > 0) {
				outcomes.put(p, new Status(State.MATCHED, 0, 0, 0, match.matchId(), gameId, withBot));
			}
		}
		onMatch.accept(match);
	}
}
