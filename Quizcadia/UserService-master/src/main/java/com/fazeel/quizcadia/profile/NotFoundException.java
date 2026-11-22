package com.fazeel.quizcadia.profile;

/** No such profile; answered with 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
