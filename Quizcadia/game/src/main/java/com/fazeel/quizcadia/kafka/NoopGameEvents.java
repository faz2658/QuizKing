package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.GameRequest;
import com.fazeel.quizcadia.domain.SinglePlayerResult;

/** Used when Kafka is switched off (local development): events are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopGameEvents implements GameEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopGameEvents.class);

	/** Only logs the request. */
	@Override
	public void requestGame(GameRequest request) {
		log.info("Kafka disabled: not requesting game {}", request.gameId());
	}

	/** Only logs the result. */
	@Override
	public void publishResult(SinglePlayerResult result) {
		log.info("Kafka disabled: result of session {} not published (score {})", result.sessionId(), result.score());
	}
}
