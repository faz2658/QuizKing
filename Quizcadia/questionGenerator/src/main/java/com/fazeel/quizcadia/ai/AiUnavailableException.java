package com.fazeel.quizcadia.ai;

/** No question model is configured, or it could not be reached. */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}
}
