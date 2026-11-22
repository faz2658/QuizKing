package com.fazeel.quizcadia.match;

import java.time.Instant;

/** One player's answer to a match question, whether it was right, and when the server received it. */
public record PlayerAnswer(int userId, String answer, boolean correct, Instant answeredAt) {
}
