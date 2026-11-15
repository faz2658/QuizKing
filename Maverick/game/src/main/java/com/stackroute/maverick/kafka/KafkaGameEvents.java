package com.stackroute.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.domain.GameRequest;
import com.stackroute.maverick.domain.SinglePlayerResult;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaGameEvents implements GameEvents {

	private static final Logger log = LoggerFactory.getLogger(KafkaGameEvents.class);

	private final KafkaTemplate<String, Object> kafka;
	private final String gameRequestTopic;
	private final String resultTopic;

	public KafkaGameEvents(KafkaTemplate<String, Object> kafka,
			@Value("${maverick.topics.game-request}") String gameRequestTopic,
			@Value("${maverick.topics.single-player-result}") String resultTopic) {
		this.kafka = kafka;
		this.gameRequestTopic = gameRequestTopic;
		this.resultTopic = resultTopic;
	}

	@Override
	public void requestGame(GameRequest request) {
		log.info("Requesting game {} from the game manager", request.gameId());
		kafka.send(gameRequestTopic, String.valueOf(request.gameId()), request);
	}

	@Override
	public void publishResult(SinglePlayerResult result) {
		log.info("Publishing result of session {} (score {})", result.sessionId(), result.score());
		kafka.send(resultTopic, result.sessionId(), result);
	}
}
