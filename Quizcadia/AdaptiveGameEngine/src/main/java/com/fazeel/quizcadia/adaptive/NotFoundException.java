package com.fazeel.quizcadia.adaptive;

/** A session or game that does not exist (any more); the API answers 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
