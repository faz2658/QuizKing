package com.fazeel.quizcadia.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.domain.UserRegistered;
import com.fazeel.quizcadia.profile.ProfileService;

/** Creates a profile for every new account. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationListener {

	private final ProfileService service;

	public RegistrationListener(ProfileService service) {
		this.service = service;
	}

	/** Creates the profile, unless it exists already. */
	@KafkaListener(topics = "${quizcadia.topics.user-registered}", properties = {
			"spring.json.value.default.type=com.fazeel.quizcadia.domain.UserRegistered" })
	public void onRegistered(UserRegistered event) {
		service.onRegistered(event);
	}
}
