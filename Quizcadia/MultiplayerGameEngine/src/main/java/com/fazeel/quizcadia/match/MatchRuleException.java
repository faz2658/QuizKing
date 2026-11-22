package com.fazeel.quizcadia.match;

/**
 * A player broke a rule of the match (not a player, the question is closed, answered twice...); the API answers
 * 409.
 */
public class MatchRuleException extends RuntimeException {

	public MatchRuleException(String message) {
		super(message);
	}
}
