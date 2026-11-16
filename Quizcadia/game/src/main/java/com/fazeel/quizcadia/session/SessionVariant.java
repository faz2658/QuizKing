package com.fazeel.quizcadia.session;

/** How a session was started. Everything except STANDARD is built from more than one game or a fixed list. */
public enum SessionVariant {
	/** A regular game. */
	STANDARD,
	/** Today's challenge: the same questions for every player, one attempt each. */
	DAILY,
	/** A player-picked mix of topics, difficulty and length. */
	CUSTOM,
	/** A replay of another player's exact quiz, to beat their score. */
	CHALLENGE
}
