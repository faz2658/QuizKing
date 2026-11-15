package com.stackroute.maverick.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.adaptive.AdaptiveService;
import com.stackroute.maverick.adaptive.Ports.ResultEvents;
import com.stackroute.maverick.domain.AdaptiveResult;
import com.stackroute.maverick.domain.Game;

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
			"spring.json.value.default.type=com.stackroute.maverick.domain.Game" })
	public void onGame(Game game) {
		service.cacheGame(game);
	}

	@Override
	public void sessionCompleted(AdaptiveResult result) {
		kafka.send(resultTopic, result.sessionId(), result);
	}
}
