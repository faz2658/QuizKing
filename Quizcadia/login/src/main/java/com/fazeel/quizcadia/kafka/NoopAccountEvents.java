package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.account.AccountEvents;
import com.fazeel.quizcadia.account.UserRegistered;

/** Stands in for Kafka when it is switched off (local runs, tests): new accounts are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopAccountEvents implements AccountEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopAccountEvents.class);

	/** Logs instead of publishing. */
	@Override
	public void registered(UserRegistered event) {
		log.info("Kafka disabled: user {} registered", event.userId());
	}
}
