package com.stackroute.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.adaptive.Ports.ResultEvents;
import com.stackroute.maverick.domain.AdaptiveResult;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "false")
public class NoopResultEvents implements ResultEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopResultEvents.class);

	@Override
	public void sessionCompleted(AdaptiveResult result) {
		log.info("Kafka disabled: adaptive session {} finished with {} points", result.sessionId(), result.score());
	}
}
