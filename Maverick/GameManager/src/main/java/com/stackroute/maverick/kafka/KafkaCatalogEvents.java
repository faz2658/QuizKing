package com.stackroute.maverick.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.catalog.CatalogEvents;
import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.MultiPlayerGame;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaCatalogEvents implements CatalogEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final Topics topics;

	public KafkaCatalogEvents(KafkaTemplate<String, Object> kafka, Topics topics) {
		this.kafka = kafka;
		this.topics = topics;
	}

	@Override
	public void gameCreated(Game game) {
		kafka.send(topics.gameCreated(), key(game.getGameId()), game);
	}

	@Override
	public void gameDefinition(Game game) {
		kafka.send(topics.gameDefinition(), key(game.getGameId()), game);
	}

	@Override
	public void multiPlayerGameCreated(MultiPlayerGame game) {
		kafka.send(topics.multiPlayerGameCreated(), key(game.getGameId()), game);
	}

	@Override
	public void gameDeleted(int gameId) {
		kafka.send(topics.gameDeleted(), key(gameId), gameId);
	}

	private static String key(int id) {
		return String.valueOf(id);
	}

	@Component
	public record Topics(@Value("${maverick.topics.game-created}") String gameCreated,
			@Value("${maverick.topics.game-definition}") String gameDefinition,
			@Value("${maverick.topics.multiplayer-game-created}") String multiPlayerGameCreated,
			@Value("${maverick.topics.game-deleted}") String gameDeleted) {
	}
}
