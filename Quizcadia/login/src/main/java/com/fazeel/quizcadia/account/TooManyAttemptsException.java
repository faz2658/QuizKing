package com.fazeel.quizcadia.account;

/** Too many wrong passwords for one email recently; try again after {@code retryAfterSeconds}. */
public class TooManyAttemptsException extends RuntimeException {

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super("Too many sign-in attempts for this email. Try again in " + Math.max(1, (retryAfterSeconds + 59) / 60)
				+ " minutes, or reset your password.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
