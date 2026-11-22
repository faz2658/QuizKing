package com.fazeel.quizcadia.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.json.JsonMapper;

/**
 * The prompt format the question model was trained on (ai/question-model/prompt.json, copied into
 * resources). Serving must use exactly the same wording, or a fine-tuned model performs badly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiPrompt(String system, String user, @JsonProperty("no_think_suffix") String noThinkSuffix) {

	/** Reads the prompt from the classpath (ai/prompt.json). */
	public static AiPrompt load() {
		try (InputStream in = new ClassPathResource("ai/prompt.json").getInputStream()) {
			return JsonMapper.builder().build().readValue(in, AiPrompt.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The user message for one question, in the training format. */
	public String userTurn(String topic, String difficulty, String format) {
		return user.replace("{topic}", topic).replace("{difficulty}", difficulty).replace("{format}", format)
				+ noThinkSuffix;
	}
}
