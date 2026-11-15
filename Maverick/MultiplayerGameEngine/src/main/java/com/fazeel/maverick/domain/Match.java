package com.fazeel.maverick.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Players matched by the player matcher. {@code names} and {@code countries} (ISO codes) are shown on the
 * scoreboard; negative player ids are bots.
 */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
		Map<Integer, String> countries) {

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
		this(matchId, gameId, players, createdAt, Map.of(), Map.of());
	}

	public Map<Integer, String> names() {
		return names == null ? Map.of() : names;
	}

	public Map<Integer, String> countries() {
		return countries == null ? Map.of() : countries;
	}
}
