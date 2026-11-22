package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.adaptive.Ports.ResultEvents;
import com.fazeel.quizcadia.domain.AdaptiveResult;

/** Stands in for Kafka when it is switched off (local runs, tests): finished sessions are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopResultEvents implements ResultEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopResultEvents.class);

	/** Logs the finished session instead of publishing it. */
	@Override
	public void sessionCompleted(AdaptiveResult result) {
		log.info("Kafka disabled: adaptive session {} finished with {} points", result.sessionId(), result.score());
	}
}
