package com.fazeel.quizking.domain;

import java.time.Instant;
import java.util.List;

/**
 * Players who will play a multiplayer game together. Published to the multiplayer game engine.
 * {@code names} and {@code countries} (ISO codes) are shown on the scoreboard; negative player ids are bots.
 */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt,
		java.util.Map<Integer, String> names, java.util.Map<Integer, String> countries) {

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
		this(matchId, gameId, players, createdAt, java.util.Map.of(), java.util.Map.of());
	}

	public java.util.Map<Integer, String> names() {
		return names == null ? java.util.Map.of() : names;
	}

	public java.util.Map<Integer, String> countries() {
		return countries == null ? java.util.Map.of() : countries;
	}
}
