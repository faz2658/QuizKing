package com.fazeel.quizcadia.push;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One browser's permission to get our pushes. The endpoint (the push service's URL for this browser) is the id, so
 * subscribing again just updates it. {@code timeZone} is the browser's (IANA name), for when to remind.
 * {@code lastRemindedDay}: the UTC day of the last reminder, so there is at most one a day.
 */
@Document(collection = "push_subscriptions")
public record PushSubscription(@Id String endpoint, @Indexed int userId, String p256dh, String auth, String timeZone,
		Instant createdAt, LocalDate lastRemindedDay) {

	public PushSubscription remindedOn(LocalDate day) {
		return new PushSubscription(endpoint, userId, p256dh, auth, timeZone, createdAt, day);
	}
}
