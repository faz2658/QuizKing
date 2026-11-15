package com.stackroute.maverick.domain;

import java.time.Instant;
import java.util.List;

/** Players matched by the player matcher. */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
}
