package com.fazeel.quizcadia.account;

import java.time.Instant;

/**
 * Published when an account is created, so the user service can create the profile and reporting can reward
 * the friend who invited them ({@code referredBy}, null without an invite). No credentials.
 */
public record UserRegistered(int userId, String userName, String email, String role, Instant registeredAt,
		Integer referredBy) {
}
