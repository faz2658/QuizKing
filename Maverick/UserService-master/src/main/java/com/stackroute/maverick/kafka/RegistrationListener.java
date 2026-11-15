package com.stackroute.maverick.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.domain.UserRegistered;
import com.stackroute.maverick.profile.ProfileService;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationListener {

	private final ProfileService service;

	public RegistrationListener(ProfileService service) {
		this.service = service;
	}

	@KafkaListener(topics = "${maverick.topics.user-registered}", properties = {
			"spring.json.value.default.type=com.stackroute.maverick.domain.UserRegistered" })
	public void onRegistered(UserRegistered event) {
		service.onRegistered(event);
	}
}
