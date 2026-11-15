package com.fazeel.maverick.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fazeel.maverick.bank.BankEvents;
import com.fazeel.maverick.domain.Category;

@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "false")
public class NoopBankEvents implements BankEvents {

	private static final Logger log = LoggerFactory.getLogger(NoopBankEvents.class);

	@Override
	public void categoryChanged(Category category) {
		log.info("Kafka disabled: category {} changed", category.getCategoryId());
	}
}
