package com.stackroute.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stackroute.maverick.account.AccountEvents;
import com.stackroute.maverick.account.UserRegistered;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "false")
public class NoopAccountEvents implements AccountEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopAccountEvents.class);

	@Override
	public void registered(UserRegistered event) {
		log.info("Kafka disabled: user {} registered", event.userId());
	}
}
