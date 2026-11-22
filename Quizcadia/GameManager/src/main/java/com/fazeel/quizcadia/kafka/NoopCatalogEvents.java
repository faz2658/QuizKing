package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.catalog.CatalogEvents;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

/** Stands in for Kafka when it is switched off (local runs, tests): catalog changes are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopCatalogEvents implements CatalogEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopCatalogEvents.class);

	/** Logs instead of publishing. */
	@Override
	public void gameCreated(Game game) {
		log.info("Kafka disabled: game {} created", game.getGameId());
	}

	/** Logs (at debug) instead of publishing. */
	@Override
	public void gameDefinition(Game game) {
		log.debug("Kafka disabled: not publishing definition of game {}", game.getGameId());
	}

	/** Logs instead of publishing. */
	@Override
	public void multiPlayerGameCreated(MultiPlayerGame game) {
		log.info("Kafka disabled: multiplayer game {} created", game.getGameId());
	}

	/** Logs instead of publishing. */
	@Override
	public void gameDeleted(int gameId) {
		log.info("Kafka disabled: game {} deleted", gameId);
	}
}
