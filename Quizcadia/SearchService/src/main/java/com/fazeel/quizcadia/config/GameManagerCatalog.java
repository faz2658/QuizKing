package com.fazeel.quizcadia.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.search.GameCard;
import com.fazeel.quizcadia.search.GameCatalog;
import com.fazeel.quizcadia.web.InternalHttp;
import com.fazeel.quizcadia.web.Breakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** Searches the game manager. The query is passed as an encoded URI variable (it used to be concatenated). */
@Component
public class GameManagerCatalog implements GameCatalog {

	private static final Logger log = LoggerFactory.getLogger(GameManagerCatalog.class);

	private final RestClient http;
	private final CircuitBreaker breaker;

	public GameManagerCatalog(RestClient.Builder builder, @Value("${quizcadia.game-manager-url}") String baseUrl,
			Breakers breakers) {
		this.http = builder.baseUrl(baseUrl).requestFactory(InternalHttp.requestFactory()).build();
		this.breaker = breakers.forService("game-manager");
	}

	/** Games from the game manager whose name starts with the prefix; none when it can't be reached. */
	@Override
	public List<GameCard> gamesNamedLike(String prefix) {
		try {
			GameCard[] cards = Breakers.call(breaker, () -> http.get()
					.uri("/api/game/gamesNameStartsWith/{prefix}", prefix).retrieve().body(GameCard[].class));
			return cards == null ? List.of() : Arrays.asList(cards);
		}
		catch (RestClientException e) {
			log.warn("Game manager search failed: {}", e.getMessage());
			return List.of();
		}
	}
}
