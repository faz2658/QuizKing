package com.stackroute.maverick.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Players waiting to play one game. A lobby closes as soon as it is full, or once the waiting time
 * has passed with at least the minimum number of players. A lobby that times out with too few
 * players is abandoned.
 */
final class Lobby {

	private final int gameId;
	private final Instant openedAt;
	private final Set<Integer> players = new LinkedHashSet<>();

	Lobby(int gameId, Instant openedAt) {
		this.gameId = gameId;
		this.openedAt = openedAt;
	}

	int gameId() {
		return gameId;
	}

	boolean join(int userId) {
		return players.add(userId);
	}

	boolean leave(int userId) {
		return players.remove(userId);
	}

	boolean contains(int userId) {
		return players.contains(userId);
	}

	boolean isEmpty() {
		return players.isEmpty();
	}

	boolean isFull(int maxPlayers) {
		return players.size() >= maxPlayers;
	}

	boolean waitedLongEnough(Instant now, Duration wait) {
		return !now.isBefore(openedAt.plus(wait));
	}

	boolean hasEnough(int minPlayers) {
		return players.size() >= minPlayers;
	}

	Instant closesAt(Duration wait) {
		return openedAt.plus(wait);
	}

	List<Integer> players() {
		return List.copyOf(new ArrayList<>(players));
	}
}
