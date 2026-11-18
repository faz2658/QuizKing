package com.fazeel.quizcadia.match;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.web.Breakers;
import com.fazeel.quizcadia.web.InternalHttp;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * A room quiz's questions: random medium questions from every topic, picked by the question service (it holds the
 * bank) at {@code /internal/questions/mix}.
 */
@Component
public class RoomQuizQuestions {

	private static final Logger log = LoggerFactory.getLogger(RoomQuizQuestions.class);

	/** Medium, in the bank's levels (1 easy, 2 medium, 3 advanced). */
	public static final int MEDIUM = 2;

	/** Loads {@code count} questions of a level; empty when it cannot. */
	public interface Source {
		List<MultiPlayerGame.Question> load(int level, int count);
	}

	private final Source source;

	@Autowired
	public RoomQuizQuestions(@Value("${quizcadia.question-generator-url}") String url, Breakers breakers) {
		this(http(InternalHttp.client(url).build(), breakers.forService("question-generator")));
	}

	public RoomQuizQuestions(Source source) {
		this.source = source;
	}

	public static RoomQuizQuestions none() {
		return new RoomQuizQuestions((level, count) -> List.of());
	}

	public List<MultiPlayerGame.Question> medium(int count) {
		return source.load(MEDIUM, count);
	}

	private static Source http(RestClient client, CircuitBreaker breaker) {
		return (level, count) -> {
			try {
				MultiPlayerGame.Question[] qs = Breakers.call(breaker, () -> client.get()
						.uri("/internal/questions/mix?level={level}&count={count}", level, count).retrieve()
						.body(MultiPlayerGame.Question[].class));
				return qs == null ? List.of() : List.copyOf(Arrays.asList(qs));
			}
			catch (RestClientException e) {
				log.warn("Could not load room quiz questions: {}", e.getMessage());
				return List.of();
			}
		};
	}
}
