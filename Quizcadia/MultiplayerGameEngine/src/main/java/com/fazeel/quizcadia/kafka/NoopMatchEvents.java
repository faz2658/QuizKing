package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.MultiPlayerResult;
import com.fazeel.quizcadia.match.MatchEvents;

/** Stands in for Kafka when it is switched off (local runs, tests): finished matches are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopMatchEvents implements MatchEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopMatchEvents.class);

	/** Logs instead of publishing. */
	@Override
	public void matchCompleted(MultiPlayerResult result) {
		log.info("Kafka disabled: match {} finished", result.matchId());
	}
}
