package com.fazeel.quizcadia.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.catalog.CatalogEvents;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

/** Kafka out: announces created, changed and deleted games, keyed by game id so one game's events stay in order. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaCatalogEvents implements CatalogEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final Topics topics;

	public KafkaCatalogEvents(KafkaTemplate<String, Object> kafka, Topics topics) {
		this.kafka = kafka;
		this.topics = topics;
	}

	/** A new solo game, for the recommendation graph. */
	@Override
	public void gameCreated(Game game) {
		kafka.send(topics.gameCreated(), key(game.getGameId()), game);
	}

	/** A solo game in full, for the game engines' caches. */
	@Override
	public void gameDefinition(Game game) {
		kafka.send(topics.gameDefinition(), key(game.getGameId()), game);
	}

	/** A new multiplayer game, for the multiplayer engine and the recommendation graph. */
	@Override
	public void multiPlayerGameCreated(MultiPlayerGame game) {
		kafka.send(topics.multiPlayerGameCreated(), key(game.getGameId()), game);
	}

	/** A deleted game's id, so every service drops it. */
	@Override
	public void gameDeleted(int gameId) {
		kafka.send(topics.gameDeleted(), key(gameId), gameId);
	}

	private static String key(int id) {
		return String.valueOf(id);
	}

	/** The Kafka topic names, from configuration. */
	@Component
	public record Topics(@Value("${quizcadia.topics.game-created}") String gameCreated,
			@Value("${quizcadia.topics.game-definition}") String gameDefinition,
			@Value("${quizcadia.topics.multiplayer-game-created}") String multiPlayerGameCreated,
			@Value("${quizcadia.topics.game-deleted}") String gameDeleted) {
	}
}
