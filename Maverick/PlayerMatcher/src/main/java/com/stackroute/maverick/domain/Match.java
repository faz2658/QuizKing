package com.stackroute.maverick.domain;

import java.time.Instant;
import java.util.List;

/** Players who will play a multiplayer game together. Published to the multiplayer game engine. */
public record Match(String matchId, int gameId, List<Integer> players, Instant createdAt) {
}
