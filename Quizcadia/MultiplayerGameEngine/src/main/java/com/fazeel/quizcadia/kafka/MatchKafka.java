package com.fazeel.quizcadia.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.domain.MultiPlayerResult;
import com.fazeel.quizcadia.match.MatchEvents;
import com.fazeel.quizcadia.match.MatchService;

/**
 * Kafka in and out: matches formed by the player matcher, multiplayer games from the game manager, and finished
 * matches to the reporting service.
 */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MatchKafka implements MatchEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final MatchService service;
	private final String resultTopic;

	public MatchKafka(KafkaTemplate<String, Object> kafka, @Lazy MatchService service,
			@Value("${quizcadia.topics.multiplayer-result}") String resultTopic) {
		this.kafka = kafka;
		this.service = service;
		this.resultTopic = resultTopic;
	}

	/**
	 * The player matcher formed a match (or a room started): create it. A repeated event finds the match already
	 * there.
	 */
	@KafkaListener(topics = "${quizcadia.topics.players-matched}", properties = {
			"spring.json.value.default.type=com.fazeel.quizcadia.domain.Match" })
	public void onMatch(Match match) {
		service.start(match);
	}

	/** A multiplayer game was created or changed: keep a copy. */
	@KafkaListener(topics = "${quizcadia.topics.multiplayer-game-created}", properties = {
			"spring.json.value.default.type=com.fazeel.quizcadia.domain.MultiPlayerGame" })
	public void onGame(MultiPlayerGame game) {
		service.cacheGame(game);
	}

	/** A deleted game must disappear from the catalog; sessions already running keep their own copy. */
	@KafkaListener(topics = "${quizcadia.topics.game-deleted}", properties = {
			"spring.json.value.default.type=java.lang.Integer" })
	public void onGameDeleted(Integer gameId) {
		if (gameId != null) {
			service.removeGame(gameId);
		}
	}

	/** Publishes a finished match, keyed by match id. */
	@Override
	public void matchCompleted(MultiPlayerResult result) {
		kafka.send(resultTopic, result.matchId(), result);
	}
}
