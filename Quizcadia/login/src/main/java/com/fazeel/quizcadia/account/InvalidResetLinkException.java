package com.fazeel.quizcadia.account;

/** The same message for unknown, used and expired links, so they can't be told apart. */
public class InvalidResetLinkException extends RuntimeException {

	public InvalidResetLinkException() {
		super("This reset link is invalid or has expired. Ask for a new one.");
	}
}
