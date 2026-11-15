package com.fazeel.quizking.domain;

import java.time.Instant;

/** From the login service when an account is created; {@code referredBy} is the friend who invited them, or null. */
public record UserRegistered(int userId, String userName, String email, String role, Instant registeredAt,
		Integer referredBy) {
}
