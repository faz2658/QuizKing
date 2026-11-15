package com.fazeel.quizking.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.domain.GameRequest;
import com.fazeel.quizking.domain.SinglePlayerResult;

/** Used when Kafka is switched off (local development): events are only logged. */
@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "false")
public class NoopGameEvents implements GameEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopGameEvents.class);

	@Override
	public void requestGame(GameRequest request) {
		log.info("Kafka disabled: not requesting game {}", request.gameId());
	}

	@Override
	public void publishResult(SinglePlayerResult result) {
		log.info("Kafka disabled: result of session {} not published (score {})", result.sessionId(), result.score());
	}
}
