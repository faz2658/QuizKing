package com.stackroute.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.repository.GameRepository;

/**
 * Caches game definitions published by the game manager. Each game is stored under its own id, so
 * any number of players can play different games at the same time.
 */
@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class GameDefinitionListener {

	private static final Logger log = LoggerFactory.getLogger(GameDefinitionListener.class);

	private final GameRepository games;

	public GameDefinitionListener(GameRepository games) {
		this.games = games;
	}

	@KafkaListener(topics = "${maverick.topics.game-definition}")
	public void onGame(Game game) {
		if (game == null || game.getGameId() == 0) {
			log.warn("Ignoring game definition without an id");
			return;
		}
		log.info("Caching game {} ({} questions)", game.getGameId(),
				game.getQuestions() == null ? 0 : game.getQuestions().size());
		games.save(game);
	}

	/** A deleted game must disappear from the catalog; sessions already running keep their own copy. */
	@KafkaListener(topics = "${maverick.topics.game-deleted}", properties = {
			"spring.json.value.default.type=java.lang.Integer" })
	public void onGameDeleted(Integer gameId) {
		if (gameId != null) {
			log.info("Removing deleted game {}", gameId);
			games.deleteById(gameId);
		}
	}
}
