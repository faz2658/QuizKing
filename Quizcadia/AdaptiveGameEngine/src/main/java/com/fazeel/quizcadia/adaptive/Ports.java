package com.fazeel.quizcadia.adaptive;

import java.util.Optional;

import com.fazeel.quizcadia.domain.AdaptiveResult;
import com.fazeel.quizcadia.domain.Game;

/** The engine's outgoing connections, as interfaces so the core logic doesn't depend on Kafka. */
public final class Ports {

	private Ports() {
	}

	/** Fetches games that are not cached yet. */
	public interface GameSource {
		Optional<Game> fetch(int gameId);
	}

	/** Where a finished session's result goes (Kafka in production, a log line when Kafka is off). */
	public interface ResultEvents {
		void sessionCompleted(AdaptiveResult result);
	}
}
