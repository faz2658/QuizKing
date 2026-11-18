package com.fazeel.quizcadia.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What a Kafka listener does when handling an event fails, in every service that has listeners: try again with
 * exponential backoff (1 s, 2 s, 4 s, 8 s, then every 10 s, each spread a little) for up to a minute, then log the
 * event as lost and move on. Spring's default was nine retries back to back, which a database that is restarting
 * never survives. Events that can't be read at all are skipped at once: retrying them can't help.
 *
 * While a listener retries, the events after it on the same partition wait; at QuizCadia's volume that is a few
 * results or game updates arriving a minute late, which beats losing them. Listeners must tolerate repeats anyway
 * (Kafka delivers at least once), so a retry after a half-done attempt is safe.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.kafka.listener.DefaultErrorHandler")
public class KafkaRetries {

	static final long FIRST_WAIT_MS = 1_000;
	static final double MULTIPLIER = 2.0;
	static final long MAX_WAIT_MS = 10_000;
	static final long GIVE_UP_AFTER_MS = 60_000;
	static final long JITTER_MS = 250;

	static ExponentialBackOff backOff() {
		ExponentialBackOff b = new ExponentialBackOff(FIRST_WAIT_MS, MULTIPLIER);
		b.setMaxInterval(MAX_WAIT_MS);
		b.setMaxElapsedTime(GIVE_UP_AFTER_MS);
		b.setJitter(JITTER_MS);
		return b;
	}

	/** Picked up by Spring Boot for every {@code @KafkaListener}. */
	@Bean
	@ConditionalOnMissingBean(CommonErrorHandler.class)
	DefaultErrorHandler kafkaErrorHandler() {
		return new DefaultErrorHandler(backOff());
	}
}
