package com.fazeel.quizking.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.quizking.domain.UserRegistered;
import com.fazeel.quizking.profile.ProfileService;

@Component
@ConditionalOnProperty(name = "quizking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationListener {

	private final ProfileService service;

	public RegistrationListener(ProfileService service) {
		this.service = service;
	}

	@KafkaListener(topics = "${quizking.topics.user-registered}", properties = {
			"spring.json.value.default.type=com.fazeel.quizking.domain.UserRegistered" })
	public void onRegistered(UserRegistered event) {
		service.onRegistered(event);
	}
}
