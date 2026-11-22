package com.fazeel.quizcadia.match;

/** A match or game that doesn't exist (yet, while the match is being created); the API answers 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
