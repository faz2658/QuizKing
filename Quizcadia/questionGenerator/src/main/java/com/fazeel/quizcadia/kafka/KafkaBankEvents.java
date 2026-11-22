package com.fazeel.quizcadia.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.bank.BankEvents;
import com.fazeel.quizcadia.bank.TopicPool;
import com.fazeel.quizcadia.domain.Category;

@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaBankEvents implements BankEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final String topic;

	public KafkaBankEvents(KafkaTemplate<String, Object> kafka, @Value("${quizcadia.topics.questions}") String topic) {
		this.kafka = kafka;
		this.topic = topic;
	}

	@Override
	public void categoryChanged(Category category) {
		// topics travel as counts only: the engines load a topic's questions from this service when a game starts
		kafka.send(topic, String.valueOf(category.getCategoryId()), TopicPool.slim(category));
	}
}
