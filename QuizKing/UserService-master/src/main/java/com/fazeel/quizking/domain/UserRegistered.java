package com.fazeel.quizking.domain;

import java.time.Instant;

/** Published by the login service when an account is created. */
public record UserRegistered(int userId, String userName, String email, String role, Instant registeredAt) {
}
