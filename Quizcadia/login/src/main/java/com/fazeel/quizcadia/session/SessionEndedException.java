package com.fazeel.quizcadia.session;

/** The refresh token is missing, used up, revoked or expired: the player has to sign in again. */
public class SessionEndedException extends RuntimeException {

	public SessionEndedException() {
		super("Your session has ended. Please sign in again.");
	}
}
