package com.fazeel.quizcadia.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.adaptive.Ports.GameSource;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.web.InternalHttp;
import com.fazeel.quizcadia.web.Breakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

@Component
public class GameManagerClient implements GameSource {

	private static final Logger log = LoggerFactory.getLogger(GameManagerClient.class);

	private final RestClient http;
	private final CircuitBreaker breaker;

	public GameManagerClient(RestClient.Builder builder, @Value("${quizcadia.game-manager-url}") String baseUrl,
			Breakers breakers) {
		this.http = builder.baseUrl(baseUrl).requestFactory(InternalHttp.requestFactory()).build();
		this.breaker = breakers.forService("game-manager");
	}

	@Override
	public Optional<Game> fetch(int gameId) {
		try {
			return Optional.ofNullable(Breakers.call(breaker, () -> http.get().uri("/internal/game/{id}", gameId).retrieve().body(Game.class)));
		}
		catch (RestClientException e) {
			log.warn("Could not fetch game {}: {}", gameId, e.getMessage());
			return Optional.empty();
		}
	}
}
