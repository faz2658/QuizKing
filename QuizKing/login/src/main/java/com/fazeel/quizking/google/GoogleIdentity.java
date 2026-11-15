package com.fazeel.quizking.google;

/** Who Google says signed in, taken from a verified ID token. */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
