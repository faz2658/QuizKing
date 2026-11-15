package com.fazeel.maverick.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.maverick.adaptive.AdaptiveService;
import com.fazeel.maverick.adaptive.Ports.ResultEvents;
import com.fazeel.maverick.domain.AdaptiveResult;
import com.fazeel.maverick.domain.Game;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AdaptiveKafka implements ResultEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final AdaptiveService service;
	private final String resultTopic;

	public AdaptiveKafka(KafkaTemplate<String, Object> kafka, @Lazy AdaptiveService service,
			@Value("${maverick.topics.adaptive-result}") String resultTopic) {
		this.kafka = kafka;
		this.service = service;
		this.resultTopic = resultTopic;
	}

	@KafkaListener(topics = "${maverick.topics.game-definition}", properties = {
			"spring.json.value.default.type=com.fazeel.maverick.domain.Game" })
	public void onGame(Game game) {
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
	public void sessionCompleted(AdaptiveResult result) {
		kafka.send(resultTopic, result.sessionId(), result);
	}
}
