package com.fazeel.quizking.match;

import java.time.Instant;

public record PlayerAnswer(int userId, String answer, boolean correct, Instant answeredAt) {
}
