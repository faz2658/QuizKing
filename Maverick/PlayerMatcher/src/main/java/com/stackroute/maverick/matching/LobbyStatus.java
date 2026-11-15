package com.stackroute.maverick.matching;

import java.time.Instant;
import java.util.List;

/**
 * What a player sees while waiting.
 *
 * @param state WAITING (still in the lobby), MATCHED (match ready, see matchId) or EXPIRED (not enough
 * players joined in time; join again to retry)
 */
public record LobbyStatus(State state, int gameId, List<Integer> players, Instant closesAt, String matchId) {

	public enum State {
		WAITING, MATCHED, EXPIRED
	}
}
