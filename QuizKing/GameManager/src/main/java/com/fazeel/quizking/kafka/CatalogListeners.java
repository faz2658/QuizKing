package com.fazeel.quizking.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.catalog.GameCatalogService;
import com.fazeel.quizking.domain.Category;
import com.fazeel.quizking.domain.GameRequest;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogListeners {

	private static final Logger log = LoggerFactory.getLogger(CatalogListeners.class);

	private final GameCatalogService catalog;

	public CatalogListeners(GameCatalogService catalog) {
		this.catalog = catalog;
	}

	/** New questions from the question generator. */
	@KafkaListener(topics = "${quizking.topics.questions}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.Category" })
	public void onQuestions(Category category) {
		log.info("Merging questions for category {}", category.getCategoryId());
		catalog.mergeCategory(category);
	}

	/** A game engine wants a game definition. */
	@KafkaListener(topics = "${quizking.topics.game-request}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.GameRequest" })
	public void onGameRequest(GameRequest request) {
		log.info("Game engine requested game {}", request.gameId());
		catalog.publishDefinition(request.gameId());
	}
}
