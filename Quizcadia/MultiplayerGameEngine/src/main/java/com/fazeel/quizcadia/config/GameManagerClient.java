package com.fazeel.quizcadia.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.match.GameSource;

/** Fetches a multiplayer game from the game manager (replaces the hard-coded game URL). */
@Component
public class GameManagerClient implements GameSource {

	private static final Logger log = LoggerFactory.getLogger(GameManagerClient.class);

	private final RestClient http;

	public GameManagerClient(RestClient.Builder builder, @Value("${quizcadia.game-manager-url}") String baseUrl) {
		this.http = builder.baseUrl(baseUrl).build();
	}

	@Override
	public Optional<MultiPlayerGame> fetch(int gameId) {
		try {
			return Optional.ofNullable(http.get().uri("/internal/game/mp/{id}", gameId).retrieve()
					.body(MultiPlayerGame.class));
		}
		catch (RestClientException e) {
			log.warn("Could not fetch multiplayer game {}: {}", gameId, e.getMessage());
			return Optional.empty();
		}
	}
}
