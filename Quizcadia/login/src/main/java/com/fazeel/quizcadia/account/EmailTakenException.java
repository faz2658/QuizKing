package com.fazeel.quizcadia.account;

public class EmailTakenException extends RuntimeException {

	public EmailTakenException() {
		super("An account with this email already exists");
	}
}
