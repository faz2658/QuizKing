package com.fazeel.maverick.account;

/** Deliberately does not say whether the email or the password was wrong. */
public class BadCredentialsException extends RuntimeException {

	public BadCredentialsException() {
		super("Wrong email or password");
	}
}
