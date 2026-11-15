package com.fazeel.quizking.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.catalog.CatalogEvents;
import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.domain.MultiPlayerGame;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "false")
public class NoopCatalogEvents implements CatalogEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopCatalogEvents.class);

	@Override
	public void gameCreated(Game game) {
		log.info("Kafka disabled: game {} created", game.getGameId());
	}

	@Override
	public void gameDefinition(Game game) {
		log.debug("Kafka disabled: not publishing definition of game {}", game.getGameId());
	}

	@Override
	public void multiPlayerGameCreated(MultiPlayerGame game) {
		log.info("Kafka disabled: multiplayer game {} created", game.getGameId());
	}

	@Override
	public void gameDeleted(int gameId) {
		log.info("Kafka disabled: game {} deleted", gameId);
	}
}
