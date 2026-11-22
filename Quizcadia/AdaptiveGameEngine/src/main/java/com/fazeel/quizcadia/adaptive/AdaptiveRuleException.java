package com.fazeel.quizcadia.adaptive;

/**
 * A player broke a rule of the adaptive game (answering a question that is no longer open, or after the end); the
 * API answers 409.
 */
public class AdaptiveRuleException extends RuntimeException {

	public AdaptiveRuleException(String message) {
		super(message);
	}
}
