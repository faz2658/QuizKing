package com.fazeel.quizking.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.bank.BankEvents;
import com.fazeel.quizking.domain.Category;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaBankEvents implements BankEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final String topic;

	public KafkaBankEvents(KafkaTemplate<String, Object> kafka, @Value("${quizking.topics.questions}") String topic) {
		this.kafka = kafka;
		this.topic = topic;
	}

	@Override
	public void categoryChanged(Category category) {
		kafka.send(topic, String.valueOf(category.getCategoryId()), category);
	}
}
