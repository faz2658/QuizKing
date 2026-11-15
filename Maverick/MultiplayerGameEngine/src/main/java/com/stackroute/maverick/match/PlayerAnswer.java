package com.stackroute.maverick.match;

import java.time.Instant;

public record PlayerAnswer(int userId, String answer, boolean correct, Instant answeredAt) {
}
