package com.fazeel.quizking.adaptive;

import java.util.List;

/** A playable question waiting in a session's pool. */
public record PooledQuestion(int questionId, int level, String stem, List<String> options, String correctAnswer) {
}
