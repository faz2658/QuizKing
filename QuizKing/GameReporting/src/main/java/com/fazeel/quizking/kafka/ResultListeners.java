package com.fazeel.quizking.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.domain.AdaptiveResult;
import com.fazeel.quizking.domain.MultiPlayerResult;
import com.fazeel.quizking.domain.SinglePlayerResult;
import com.fazeel.quizking.domain.UserRegistered;
import com.fazeel.quizking.invite.Invites;
import com.fazeel.quizking.report.ReportService;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ResultListeners {

	private static final Logger log = LoggerFactory.getLogger(ResultListeners.class);

	private final ReportService service;
	private final Invites invites;

	public ResultListeners(ReportService service, Invites invites) {
		this.service = service;
		this.invites = invites;
	}

	@KafkaListener(topics = "${quizking.topics.single-player-result}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.SinglePlayerResult" })
	public void single(SinglePlayerResult r) {
		log.info("Single-player result {} for user {}", r.sessionId(), r.userId());
		service.record(r);
	}

	@KafkaListener(topics = "${quizking.topics.multiplayer-result}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.MultiPlayerResult" })
	public void multi(MultiPlayerResult r) {
		log.info("Multiplayer result {}", r.matchId());
		service.record(r);
	}

	/** New accounts, for invite rewards. */
	@KafkaListener(topics = "${quizking.topics.user-registered}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.UserRegistered" })
	public void registered(UserRegistered e) {
		invites.registered(e);
	}

	@KafkaListener(topics = "${quizking.topics.adaptive-result}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.AdaptiveResult" })
	public void adaptive(AdaptiveResult r) {
		log.info("Adaptive result {} for user {}", r.sessionId(), r.userId());
		service.record(r);
	}
}
