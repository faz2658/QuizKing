package com.stackroute.maverick.session;

/** Raised when a player action breaks the rules of the game (answering out of turn, after the end...). */
public class QuizRuleException extends RuntimeException {

	public QuizRuleException(String message) {
		super(message);
	}
}
