package com.fazeel.quizking.ai;

import java.util.Optional;

/** Something that writes questions: the fine-tuned model behind an OpenAI-compatible server. */
public interface QuestionModel {

	boolean available();

	/** @return the parsed reply, or empty if the model's reply wasn't a usable question */
	Optional<GeneratedQuestion> write(String topic, String difficulty, String format);
}
