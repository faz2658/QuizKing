package com.stackroute.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.domain.AdaptiveResult;
import com.stackroute.maverick.domain.MultiPlayerResult;
import com.stackroute.maverick.domain.SinglePlayerResult;
import com.stackroute.maverick.report.ReportService;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ResultListeners {

	private static final Logger log = LoggerFactory.getLogger(ResultListeners.class);

	private final ReportService service;

	public ResultListeners(ReportService service) {
		this.service = service;
	}

	@KafkaListener(topics = "${maverick.topics.single-player-result}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.SinglePlayerResult" })
	public void single(SinglePlayerResult r) {
		log.info("Single-player result {} for user {}", r.sessionId(), r.userId());
		service.record(r);
	}

	@KafkaListener(topics = "${maverick.topics.multiplayer-result}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.MultiPlayerResult" })
	public void multi(MultiPlayerResult r) {
		log.info("Multiplayer result {}", r.matchId());
		service.record(r);
	}

	@KafkaListener(topics = "${maverick.topics.adaptive-result}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.AdaptiveResult" })
	public void adaptive(AdaptiveResult r) {
		log.info("Adaptive result {} for user {}", r.sessionId(), r.userId());
		service.record(r);
	}
}
