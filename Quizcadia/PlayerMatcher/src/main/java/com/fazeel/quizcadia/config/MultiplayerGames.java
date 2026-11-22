package com.fazeel.quizcadia.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/** Multiplayer games with enough questions, from the game manager (cached for a few minutes). */
public class MultiplayerGames implements Supplier<List<Integer>> {

	private static final Logger log = LoggerFactory.getLogger(MultiplayerGames.class);
	static final Duration CACHE = Duration.ofMinutes(5);
	static final int MIN_QUESTIONS = 5;

	/** A multiplayer game's id and question count, as the game manager lists it. */
	record Summary(int gameId, int questionCount) {
	}

	private final RestClient http;
	private final Clock clock;
	private List<Integer> cached = List.of();
	private Instant fetchedAt = Instant.EPOCH;

	public MultiplayerGames(RestClient.Builder builder, String gameManagerUrl, Clock clock) {
		this.http = builder.baseUrl(gameManagerUrl).build();
		this.clock = clock;
	}

	/**
	 * The ids of multiplayer games that have enough questions to play, refreshed from the game manager every 5
	 * minutes.
	 */
	@Override
	public synchronized List<Integer> get() {
		if (cached.isEmpty() || Duration.between(fetchedAt, clock.instant()).compareTo(CACHE) > 0) {
			try {
				Summary[] games = http.get().uri("/api/game/games/mp").retrieve().body(Summary[].class);
				cached = games == null ? List.of()
						: Arrays.stream(games).filter(g -> g.questionCount() >= MIN_QUESTIONS).map(Summary::gameId).toList();
				fetchedAt = clock.instant();
			}
			catch (RuntimeException e) {
				log.warn("Could not load multiplayer games: {}", e.getMessage());
			}
		}
		return cached;
	}
}
