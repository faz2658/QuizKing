package com.fazeel.quizcadia.bank;

/** No such category, topic, question or draft; answered with 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}
