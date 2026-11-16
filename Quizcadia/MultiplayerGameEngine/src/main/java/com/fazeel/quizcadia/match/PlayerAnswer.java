package com.fazeel.quizcadia.match;

import java.time.Instant;

public record PlayerAnswer(int userId, String answer, boolean correct, Instant answeredAt) {
}
