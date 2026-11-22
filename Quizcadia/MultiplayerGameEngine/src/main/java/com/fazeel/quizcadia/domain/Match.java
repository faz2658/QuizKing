package com.fazeel.quizcadia.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Players who play a multiplayer game together, published by the player matcher to the multiplayer game engine.
 * {@code names} and {@code countries} (ISO codes) are shown on the scoreboard; negative player ids are bots.
 * {@code screenId} is a party host who watches on a big screen without playing (null for ordinary matches).
 * {@code quiz} makes it a live room quiz instead of a game's fastest-finger match (null for ordinary matches).
 */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
		Map<Integer, String> countries, Integer screenId, RoomQuiz quiz) {

	/**
	 * A live room quiz (party mode, host-paced): {@code questions} random medium questions from every topic, everyone
	 * who answers right scores (faster scores more), and the answer shows until the host moves on, or for
	 * {@code revealSeconds} when that is above zero.
	 */
	public record RoomQuiz(int questions, int secondsPerQuestion, int revealSeconds) {
	}

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
			Map<Integer, String> countries, Integer screenId) {
		this(matchId, gameId, players, createdAt, names, countries, screenId, null);
	}

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
		this(matchId, gameId, players, createdAt, Map.of(), Map.of(), null);
	}

	public Match(String matchId, int gameId, List<Integer> players, Instant createdAt, Map<Integer, String> names,
			Map<Integer, String> countries) {
		this(matchId, gameId, players, createdAt, names, countries, null);
	}

	/** Display names by player id (never null). */
	public Map<Integer, String> names() {
		return names == null ? Map.of() : names;
	}

	/** Countries (ISO codes) by player id (never null). */
	public Map<Integer, String> countries() {
		return countries == null ? Map.of() : countries;
	}
}
