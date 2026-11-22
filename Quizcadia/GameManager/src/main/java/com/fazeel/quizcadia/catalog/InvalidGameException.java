package com.fazeel.quizcadia.catalog;

/** A game request that can't be honoured (a missing name, an unknown topic, a bad level mix); the API answers 400. */
public class InvalidGameException extends RuntimeException {

	public InvalidGameException(String message) {
		super(message);
	}
}
