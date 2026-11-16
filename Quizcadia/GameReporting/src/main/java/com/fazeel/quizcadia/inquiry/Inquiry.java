package com.fazeel.quizcadia.inquiry;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A request for a paid offer, sent from the website without an account: a private quiz night (EVENT) or weekly
 * trivia at a bar or café (VENUE). The owner reads them on the admin page and replies by email.
 */
@Document(collection = "inquiries")
public record Inquiry(@Id String id, Kind kind, String name, String email, String organisation, String occasion,
		String date, Integer groupSize, String city, String message, Status status, Instant createdAt, Instant updatedAt) {

	public enum Kind {
		EVENT, VENUE
	}

	/** Where the owner is with it: NEW until they reply, then CONTACTED, and BOOKED or CLOSED. */
	public enum Status {
		NEW, CONTACTED, BOOKED, CLOSED
	}

	public Inquiry withStatus(Status s, Instant now) {
		return new Inquiry(id, kind, name, email, organisation, occasion, date, groupSize, city, message, s, createdAt, now);
	}
}
