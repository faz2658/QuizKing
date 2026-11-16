package com.fazeel.quizcadia.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Players who play a multiplayer game together, published by the player matcher to the multiplayer game engine.
 * {@code names} and {@code countries} (ISO codes) are shown on the scoreboard; negative player ids are bots.
 * {@code screenId} is a party host who watches on a big screen without playing (null for ordinary matches).
 */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
		Map<Integer, String> countries, Integer screenId) {

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
		this(matchId, gameId, players, createdAt, Map.of(), Map.of(), null);
	}

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
			Map<Integer, String> countries) {
		this(matchId, gameId, players, createdAt, names, countries, null);
	}

	public Map<Integer, String> names() {
		return names == null ? Map.of() : names;
	}

	public Map<Integer, String> countries() {
		return countries == null ? Map.of() : countries;
	}
}
