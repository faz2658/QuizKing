package com.fazeel.quizcadia.catalog;

/** A category, topic or game that doesn't exist; the API answers 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
