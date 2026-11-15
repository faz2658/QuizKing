package com.stackroute.maverick.account;

import java.time.Instant;

/** Published when an account is created, so the user service can create the profile. No credentials. */
public record UserRegistered(int userId, String userName, String email, String role, Instant registeredAt) {
}
