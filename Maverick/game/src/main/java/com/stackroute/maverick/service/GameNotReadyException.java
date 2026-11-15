package com.stackroute.maverick.service;

/** The game is not cached yet; it has been requested from the game manager. */
public class GameNotReadyException extends RuntimeException {

	public GameNotReadyException(int gameId) {
		super("Game " + gameId + " is being loaded, try again shortly");
	}
}
