package com.fazeel.quizcadia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BreakersTest {

	final Breakers breakers = new Breakers();

	static void failWith(CircuitBreaker b, RuntimeException e) {
		try {
			Breakers.call(b, () -> {
				throw e;
			});
		}
		catch (RuntimeException ignored) {
			// expected
		}
	}

	@Test
	void opensAfterRepeatedFailuresAndThenFailsAtOnceWithoutCalling() {
		CircuitBreaker b = breakers.forService("game-manager");
		for (int i = 0; i < 5; i++) {
			failWith(b, new ResourceAccessException("Connection refused"));
		}
		assertThat(b.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> Breakers.call(b, calls::incrementAndGet))
				.isInstanceOf(ResourceAccessException.class) // what the callers already handle
				.hasMessageContaining("circuit open");
		assertThat(calls).hasValue(0);
	}

	@Test
	void serverErrorsCountButClientErrorsAreNormalAnswers() {
		CircuitBreaker five = breakers.forService("a");
		for (int i = 0; i < 5; i++) {
			failWith(five, HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "x", null, null, null));
		}
		assertThat(five.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		CircuitBreaker four = breakers.forService("b");
		for (int i = 0; i < 10; i++) {
			failWith(four, HttpClientErrorException.create(HttpStatus.NOT_FOUND, "x", null, null, null));
		}
		assertThat(four.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void aFewFailuresAmongSuccessesDoNotOpenIt() {
		CircuitBreaker b = breakers.forService("c");
		for (int i = 0; i < 20; i++) {
			if (i % 4 == 0) {
				failWith(b, new ResourceAccessException("timeout"));
			}
			else {
				Breakers.call(b, () -> "ok");
			}
		}
		assertThat(b.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void theSlowServiceBreakerOpensSoonerAndStaysOpenLonger() {
		CircuitBreaker ai = breakers.forSlowService("ai-model");
		for (int i = 0; i < 3; i++) {
			failWith(ai, new ResourceAccessException("timeout"));
		}
		assertThat(ai.getState()).isEqualTo(CircuitBreaker.State.OPEN);
		assertThat(ai.getCircuitBreakerConfig().getMaxWaitDurationInHalfOpenState()).isNotNull();
		assertThat(ai.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(120_000L);
	}

	@Test
	void theStateIsInTheMetrics() {
		SimpleMeterRegistry meters = new SimpleMeterRegistry();
		var withMetrics = new Breakers(new StaticListableBeanFactory(
				Map.of("meters", meters)).getBeanProvider(MeterRegistry.class));
		withMetrics.forService("question-generator");
		assertThat(meters.find("resilience4j.circuitbreaker.state").tag("name", "question-generator").gauges())
				.isNotEmpty();
	}
}
