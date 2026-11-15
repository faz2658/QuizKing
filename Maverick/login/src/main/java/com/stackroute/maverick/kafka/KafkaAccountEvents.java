package com.stackroute.maverick.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.account.AccountEvents;
import com.stackroute.maverick.account.UserRegistered;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaAccountEvents implements AccountEvents {

	private final KafkaTemplate<String, Object> kafka;
	private final String topic;

	public KafkaAccountEvents(KafkaTemplate<String, Object> kafka,
			@Value("${maverick.topics.user-registered}") String topic) {
		this.kafka = kafka;
		this.topic = topic;
	}

	@Override
	public void registered(UserRegistered event) {
		kafka.send(topic, String.valueOf(event.userId()), event);
	}
}
