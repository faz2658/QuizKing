package com.fazeel.quizcadia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.web.InternalHttp;
import com.fazeel.quizcadia.web.Breakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * A game's question pool. Games built from a topic no longer carry copies of its questions (a big topic would
 * outgrow messages and documents); their questions are loaded from the question service when a game starts and
 * kept for a few minutes. Games that still carry their own questions (hand-made ones, older data) use those.
 */
@Component
public class TopicPools {

	private static final Logger log = LoggerFactory.getLogger(TopicPools.class);

	/** Loads a topic's playable questions; empty when it cannot. */
	public interface Source {
		List<Questions> load(int topicId);
	}

	private record Cached(List<Questions> questions, Instant at) {
	}

	private final Source source;
	private final Clock clock;
	private final Duration keep;
	private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

	@Autowired
	public TopicPools(@Value("${quizcadia.question-generator-url}") String url, Clock clock, Breakers breakers) {
		this(http(InternalHttp.client(url).build(), breakers.forService("question-generator")), clock,
				Duration.ofMinutes(10));
	}

	public TopicPools(Source source, Clock clock, Duration keep) {
		this.source = source;
		this.clock = clock;
		this.keep = keep;
	}

	/** For code and tests that only use games carrying their own questions. */
	public static TopicPools none() {
		return new TopicPools(topicId -> List.of(), Clock.systemUTC(), Duration.ZERO);
	}

	/** The game with its questions filled in from its topic when it does not carry any. Not saved. */
	public Game withPool(Game game) {
		if (game == null || game.getQuestions() != null && !game.getQuestions().isEmpty() || game.getTopic() == null
				|| game.getTopic().getTopicId() <= 0) {
			return game;
		}
		game.setQuestions(questions(game.getTopic().getTopicId()));
		return game;
	}

	public List<Game> withPools(List<Game> games) {
		games.forEach(this::withPool);
		return games;
	}

	/** A topic's questions, from the cache while fresh. Failed loads are not cached, so the next game retries. */
	public List<Questions> questions(int topicId) {
		Instant now = clock.instant();
		Cached c = cache.get(topicId);
		if (c != null && c.at().plus(keep).isAfter(now)) {
			return c.questions();
		}
		List<Questions> loaded = source.load(topicId);
		if (!loaded.isEmpty()) {
			cache.put(topicId, new Cached(loaded, now));
		}
		return loaded;
	}

	private static Source http(RestClient client, CircuitBreaker breaker) {
		return topicId -> {
			try {
				// while the question service is down, fail at once instead of every game start waiting for a timeout
				Questions[] qs = Breakers.call(breaker, () -> client.get()
						.uri("/internal/topics/{id}/questions", topicId).retrieve().body(Questions[].class));
				return qs == null ? List.of() : List.copyOf(Arrays.asList(qs));
			}
			catch (RestClientException e) {
				log.warn("Could not load the questions of topic {}: {}", topicId, e.getMessage());
				return List.of();
			}
		};
	}
}
