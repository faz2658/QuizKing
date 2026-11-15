package com.stackroute.maverick.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.domain.Match;
import com.stackroute.maverick.domain.MultiPlayerGame;
import com.stackroute.maverick.domain.MultiPlayerResult;
import com.stackroute.maverick.match.MatchEvents;
import com.stackroute.maverick.match.MatchService;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MatchKafka implements MatchEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final MatchService service;
	private final String resultTopic;

	public MatchKafka(KafkaTemplate<String, Object> kafka, @org.springframework.context.annotation.Lazy MatchService service,
			@Value("${maverick.topics.multiplayer-result}") String resultTopic) {
		this.kafka = kafka;
		this.service = service;
		this.resultTopic = resultTopic;
	}

	@KafkaListener(topics = "${maverick.topics.players-matched}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.Match" })
	public void onMatch(Match match) {
		service.start(match);
	}

	@KafkaListener(topics = "${maverick.topics.multiplayer-game-created}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.MultiPlayerGame" })
	public void onGame(MultiPlayerGame game) {
		service.cacheGame(game);
	}

	/** A deleted game must disappear from the catalog; sessions already running keep their own copy. */
	@KafkaListener(topics = "${maverick.topics.game-deleted}", properties = {
			"spring.json.value.default.type=java.lang.Integer" })
	public void onGameDeleted(Integer gameId) {
		if (gameId != null) {
			service.removeGame(gameId);
		}
	}

	@Override
	public void matchCompleted(MultiPlayerResult result) {
		kafka.send(resultTopic, result.matchId(), result);
	}
}
