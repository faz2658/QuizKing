package com.fazeel.maverick.session;

/** One-off helps a player may use once per game. */
public enum Lifeline {
	/** Removes two wrong options (four-option questions only). */
	FIFTY_FIFTY,
	/** Moves on without answering: no points, but the combo survives. */
	SKIP,
	/** Adds {@value GameSession#EXTRA_SECONDS} seconds to the current question. */
	EXTRA_TIME
}
