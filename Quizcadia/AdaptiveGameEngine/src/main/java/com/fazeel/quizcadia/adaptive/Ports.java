package com.fazeel.quizcadia.adaptive;

import java.util.Optional;

import com.fazeel.quizcadia.domain.AdaptiveResult;
import com.fazeel.quizcadia.domain.Game;

public final class Ports {

	private Ports() {
	}

	/** Fetches games that are not cached yet. */
	public interface GameSource {
		Optional<Game> fetch(int gameId);
	}

	public interface ResultEvents {
		void sessionCompleted(AdaptiveResult result);
	}
}
