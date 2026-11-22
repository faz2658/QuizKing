package com.fazeel.quizcadia.account;

/** Sign-up with an email that already has an account; the API answers 409. */
public class EmailTakenException extends RuntimeException {

	public EmailTakenException() {
		super("An account with this email already exists");
	}
}
