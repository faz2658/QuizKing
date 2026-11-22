package com.fazeel.quizcadia.bank;

/** A question or bank change breaks a rule; the bank API answers 422, the feedback API 400. */
public class InvalidQuestionException extends RuntimeException {

	public InvalidQuestionException(String message) {
		super(message);
	}
}
