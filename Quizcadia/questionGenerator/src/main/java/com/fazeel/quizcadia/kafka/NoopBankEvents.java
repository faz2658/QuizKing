package com.fazeel.quizcadia.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.quizcadia.bank.BankEvents;
import com.fazeel.quizcadia.domain.Category;

/** Used when Kafka is switched off (quizcadia.kafka.enabled=false): changes are only logged. */
@Component
@ConditionalOnProperty(name = "quizcadia.kafka.enabled", havingValue = "false")
public class NoopBankEvents implements BankEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopBankEvents.class);

	@Override
	public void categoryChanged(Category category) {
		log.info("Kafka disabled: category {} changed", category.getCategoryId());
	}
}
