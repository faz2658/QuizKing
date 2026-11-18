package com.fazeel.quizcadia.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.fazeel.quizcadia.web.InternalHttp;
import com.fazeel.quizcadia.web.Breakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Calls the question model through the OpenAI-compatible chat API that llama.cpp's llama-server
 * (and Ollama, vLLM, ...) expose. Sampling follows Unsloth's recommendation for Qwen3 without
 * thinking: temperature 0.7, top_p 0.8, top_k 20.
 */
@Component
public class OpenAiCompatibleQuestionModel implements QuestionModel {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleQuestionModel.class);

	private final RestClient http;
	private final CircuitBreaker breaker;
	private final boolean configured;
	private final String modelName;
	private final AiPrompt prompt = AiPrompt.load();
	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	public OpenAiCompatibleQuestionModel(RestClient.Builder builder, @Value("${quizcadia.ai.url:}") String url,
			@Value("${quizcadia.ai.model:quizcadia}") String modelName, Breakers breakers) {
		// A small model on the server's CPU takes a while to write a question, but one that never answers must not
		// hold the request for good: give up after a few minutes (AiUnavailableException, like an unreachable model).
		// After a few failures in a row the breaker answers "not available" at once for two minutes.
		this(builder, url, modelName, InternalHttp.requestFactory(Duration.ofMinutes(3)),
				breakers.forSlowService("ai-model"));
	}

	OpenAiCompatibleQuestionModel(RestClient.Builder builder, String url, String modelName) {
		this(builder, url, modelName, InternalHttp.requestFactory(Duration.ofMinutes(3)),
				new Breakers().forSlowService("ai-model"));
	}

	/** {@code requestFactory} null keeps the builder's own (tests bind a mock server to it). */
	OpenAiCompatibleQuestionModel(RestClient.Builder builder, String url, String modelName,
			ClientHttpRequestFactory requestFactory) {
		this(builder, url, modelName, requestFactory, new Breakers().forSlowService("ai-model"));
	}

	OpenAiCompatibleQuestionModel(RestClient.Builder builder, String url, String modelName,
			ClientHttpRequestFactory requestFactory, CircuitBreaker breaker) {
		this.breaker = breaker;
		this.configured = url != null && !url.isBlank();
		if (requestFactory != null) {
			builder = builder.requestFactory(requestFactory);
		}
		this.http = builder.baseUrl(configured ? url : "http://localhost").build();
		this.modelName = modelName;
	}

	@Override
	public boolean available() {
		return configured;
	}

	@Override
	public Optional<GeneratedQuestion> write(String topic, String difficulty, String format) {
		Map<String, Object> body = Map.of("model", modelName, "temperature", 0.7, "top_p", 0.8, "top_k", 20,
				"max_tokens", 300, "messages", List.of(Map.of("role", "system", "content", prompt.system()),
						Map.of("role", "user", "content", prompt.userTurn(topic, difficulty, format))));
		ChatResponse response;
		try {
			// Serialised up front so the request carries a Content-Length (some servers reject chunked bodies)
			String payload = json.writeValueAsString(body);
			response = Breakers.call(breaker, () -> http.post().uri("/v1/chat/completions")
					.contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(ChatResponse.class));
		}
		catch (RestClientException e) {
			throw new AiUnavailableException("The question model is not reachable: " + e.getMessage());
		}
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			return Optional.empty();
		}
		return parse(response.choices().get(0).message().content());
	}

	/** Accepts a bare JSON object, one wrapped in ```json fences, or one after an empty think block. */
	Optional<GeneratedQuestion> parse(String reply) {
		if (reply == null) {
			return Optional.empty();
		}
		String text = reply.replaceAll("(?s)<think>.*?</think>", "");
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Optional.empty();
		}
		try {
			GeneratedQuestion q = json.readValue(text.substring(start, end + 1), GeneratedQuestion.class);
			return q.question() == null || q.options() == null || q.answer() == null ? Optional.empty() : Optional.of(q);
		}
		catch (JacksonException e) {
			log.debug("Unusable model reply: {}", reply);
			return Optional.empty();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String content) {
	}
}
