package com.fazeel.quizcadia.service;

/** No such session, challenge or quiz; answered with 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
