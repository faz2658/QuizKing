package com.fazeel.quizcadia.google;

/** Google sign-in can't go ahead (not set up, a bad or expired token, an unverified email); the API answers 401. */
public class GoogleSignInException extends RuntimeException {

	public GoogleSignInException(String message) {
		super(message);
	}
}
