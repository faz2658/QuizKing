package com.stackroute.maverick.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.stackroute.maverick.adaptive.Ports.GameSource;
import com.stackroute.maverick.domain.Game;

@Component
public class GameManagerClient implements GameSource {

	private static final Logger log = LoggerFactory.getLogger(GameManagerClient.class);

	private final RestClient http;

	public GameManagerClient(RestClient.Builder builder, @Value("${maverick.game-manager-url}") String baseUrl) {
		this.http = builder.baseUrl(baseUrl).build();
	}

	@Override
	public Optional<Game> fetch(int gameId) {
		try {
			return Optional.ofNullable(http.get().uri("/api/game/game1/{id}", gameId).retrieve().body(Game.class));
		}
		catch (RestClientException e) {
			log.warn("Could not fetch game {}: {}", gameId, e.getMessage());
			return Optional.empty();
		}
	}
}
