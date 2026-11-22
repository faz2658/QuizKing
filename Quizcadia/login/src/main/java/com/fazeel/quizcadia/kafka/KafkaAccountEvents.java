package com.fazeel.quizcadia.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.account.AccountEvents;
import com.fazeel.quizcadia.account.UserRegistered;

/** Kafka out: announces new accounts (for invite rewards and recommendations), keyed by user id. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaAccountEvents implements AccountEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final String topic;

	public KafkaAccountEvents(KafkaTemplate<String, Object> kafka,
			@Value("${quizcadia.topics.user-registered}") String topic) {
		this.kafka = kafka;
		this.topic = topic;
	}

	/** Publishes a new account. */
	@Override
	public void registered(UserRegistered event) {
		kafka.send(topic, String.valueOf(event.userId()), event);
	}
}
