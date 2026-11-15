package com.fazeel.quizking.ai;

import java.util.List;

/** What the model returns for one request. */
public record GeneratedQuestion(String question, List<String> options, String answer) {
}
