package com.fazeel.quizking.opentdb;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * HTTP client for https://opentdb.com. Questions are requested base64-encoded so they arrive as plain
 * text (the default encoding is HTML entities).
 */
@Component
public class OpenTriviaClient implements TriviaSource {

	private final RestClient http;

	public OpenTriviaClient(RestClient.Builder builder,
			@Value("${quizking.opentdb.url:https://opentdb.com}") String baseUrl) {
		this.http = builder.baseUrl(baseUrl).defaultHeader("User-Agent", "QuizKing/2.0 (question importer)").build();
	}

	@Override
	public String newToken() {
		TokenResponse r = http.get().uri("/api_token.php?command=request").retrieve().body(TokenResponse.class);
		if (r == null || r.token() == null) {
			throw new IllegalStateException("Open Trivia DB did not return a session token");
		}
		return r.token();
	}

	@Override
	public TriviaBatch fetch(String token, int amount) {
		RawResponse r = http.get().uri("/api.php?amount={amount}&token={token}&encode=base64", amount, token)
				.retrieve().body(RawResponse.class);
		if (r == null) {
			return new TriviaBatch(TriviaBatch.NO_RESULTS, List.of());
		}
		List<TriviaQuestion> questions = r.results() == null ? List.of()
				: r.results().stream().map(OpenTriviaClient::decode).toList();
		return new TriviaBatch(r.responseCode(), questions);
	}

	private static TriviaQuestion decode(RawQuestion q) {
		return new TriviaQuestion(b64(q.type()), b64(q.difficulty()), b64(q.category()), b64(q.question()),
				b64(q.correctAnswer()),
				q.incorrectAnswers() == null ? List.of() : q.incorrectAnswers().stream().map(OpenTriviaClient::b64).toList());
	}

	private static String b64(String s) {
		return s == null ? null : new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@JsonProperty("response_code") int responseCode, String token) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawResponse(@JsonProperty("response_code") int responseCode, List<RawQuestion> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawQuestion(String type, String difficulty, String category, String question,
			@JsonProperty("correct_answer") String correctAnswer,
			@JsonProperty("incorrect_answers") List<String> incorrectAnswers) {
	}
}
