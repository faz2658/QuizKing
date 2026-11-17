package com.fazeel.quizcadia.adaptive;

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

/**
 * An adaptive game's question pool. Games built from a topic no longer carry copies of its questions; they are
 * loaded from the question service when a run starts and kept for a few minutes. Games that still carry their
 * own questions (hand-made ones, older data) use those.
 */
@Component
public class TopicPools {

	private static final Logger log = LoggerFactory.getLogger(TopicPools.class);

	/** Loads a topic's playable questions; empty when it cannot. */
	public interface Source {
		List<Game.Question> load(int topicId);
	}

	private record Cached(List<Game.Question> questions, Instant at) {
	}

	private final Source source;
	private final Clock clock;
	private final Duration keep;
	private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

	@Autowired
	public TopicPools(@Value("${quizcadia.question-generator-url}") String url, Clock clock) {
		this(http(RestClient.builder().baseUrl(url).build()), clock, Duration.ofMinutes(10));
	}

	public TopicPools(Source source, Clock clock, Duration keep) {
		this.source = source;
		this.clock = clock;
		this.keep = keep;
	}

	public static TopicPools none() {
		return new TopicPools(topicId -> List.of(), Clock.systemUTC(), Duration.ZERO);
	}

	/** The game with its topic's questions when it carries none. Not saved. */
	public Game withPool(Game game) {
		if (game == null || game.questions() != null && !game.questions().isEmpty() || game.topic() == null
				|| game.topic().topicId() <= 0) {
			return game;
		}
		return game.withQuestions(questions(game.topic().topicId()));
	}

	/** A topic's questions, from the cache while fresh. Failed loads are not cached. */
	public List<Game.Question> questions(int topicId) {
		Instant now = clock.instant();
		Cached c = cache.get(topicId);
		if (c != null && c.at().plus(keep).isAfter(now)) {
			return c.questions();
		}
		List<Game.Question> loaded = source.load(topicId);
		if (!loaded.isEmpty()) {
			cache.put(topicId, new Cached(loaded, now));
		}
		return loaded;
	}

	private static Source http(RestClient client) {
		return topicId -> {
			try {
				Game.Question[] qs = client.get().uri("/internal/topics/{id}/questions", topicId).retrieve()
						.body(Game.Question[].class);
				return qs == null ? List.of() : List.copyOf(Arrays.asList(qs));
			}
			catch (RestClientException e) {
				log.warn("Could not load the questions of topic {}: {}", topicId, e.getMessage());
				return List.of();
			}
		};
	}
}
