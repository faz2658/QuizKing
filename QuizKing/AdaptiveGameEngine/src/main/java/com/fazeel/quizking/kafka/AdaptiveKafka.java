package com.fazeel.quizking.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.adaptive.AdaptiveService;
import com.fazeel.quizking.adaptive.Ports.ResultEvents;
import com.fazeel.quizking.domain.AdaptiveResult;
import com.fazeel.quizking.domain.Game;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AdaptiveKafka implements ResultEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final AdaptiveService service;
	private final String resultTopic;

	public AdaptiveKafka(KafkaTemplate<String, Object> kafka, @Lazy AdaptiveService service,
			@Value("${quizking.topics.adaptive-result}") String resultTopic) {
		this.kafka = kafka;
		this.service = service;
		this.resultTopic = resultTopic;
	}

	@KafkaListener(topics = "${quizking.topics.game-definition}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.Game" })
	public void onGame(Game game) {
		service.cacheGame(game);
	}

	/** A deleted game must disappear from the catalog; sessions already running keep their own copy. */
	@KafkaListener(topics = "${quizking.topics.game-deleted}", properties = {
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
