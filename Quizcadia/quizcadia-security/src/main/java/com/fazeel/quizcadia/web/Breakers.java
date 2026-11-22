package com.fazeel.quizcadia.web;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Circuit breakers for calls to other services. Once most recent calls to a service have failed (it is down, or
 * hangs until the {@link InternalHttp} timeout), the breaker opens: for the next 30 seconds calls fail at once
 * ({@link CallNotPermittedException}) and the caller uses its fallback straight away, instead of every player
 * waiting for a timeout and the sick service being hammered while it restarts. Then a couple of trial calls go
 * through; if they work the breaker closes again, otherwise it waits another 30 seconds.
 *
 * <p>Only signs that the service is unwell count: no answer, a timeout, or a 5xx. A 4xx (a game that doesn't exist)
 * is a normal answer. Each breaker's state is in the metrics ({@code resilience4j_circuitbreaker_state}), and every
 * change of state is logged.
 */
@Component
public class Breakers {

	private static final Logger log = LoggerFactory.getLogger(Breakers.class);

	/** Looks at the last 20 calls, once there were 5; opens at half of them failing or all being very slow. */
	static final CircuitBreakerConfig SERVICE = CircuitBreakerConfig.custom()
			.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
			.slidingWindowSize(20)
			.minimumNumberOfCalls(5)
			.failureRateThreshold(50)
			.slowCallDurationThreshold(Duration.ofSeconds(8))
			.slowCallRateThreshold(100)
			.waitDurationInOpenState(Duration.ofSeconds(30))
			.permittedNumberOfCallsInHalfOpenState(2)
			.recordException(Breakers::unwell)
			.build();

	/**
	 * For the AI model: few calls, each up to minutes long. Opens after 3 failures out of the last 5, and waits two
	 * minutes before trying again (a model that is loading takes a while).
	 */
	static final CircuitBreakerConfig SLOW_SERVICE = CircuitBreakerConfig.from(SERVICE)
			.slidingWindowSize(5)
			.minimumNumberOfCalls(3)
			.slowCallRateThreshold(100)
			.slowCallDurationThreshold(Duration.ofMinutes(5)) // its own timeout ends calls before this
			.waitDurationInOpenState(Duration.ofMinutes(2))
			.permittedNumberOfCallsInHalfOpenState(1)
			.build();

	private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(SERVICE);

	@Autowired
	public Breakers(ObjectProvider<MeterRegistry> meters) {
		meters.ifAvailable(m -> TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(m));
		registry.getEventPublisher().onEntryAdded(e -> e.getAddedEntry().getEventPublisher()
				.onStateTransition(t -> log.warn("Circuit breaker '{}': {}", t.getCircuitBreakerName(),
						t.getStateTransition())));
	}

	/** Without metrics, for tests and code outside Spring. */
	public Breakers() {
		this(new StaticListableBeanFactory()
				.getBeanProvider(MeterRegistry.class));
	}

	/** The breaker for calls to another QuizCadia service, e.g. {@code "game-manager"}. */
	public CircuitBreaker forService(String name) {
		return registry.circuitBreaker(name);
	}

	/** The breaker for a slow dependency such as the AI model. */
	public CircuitBreaker forSlowService(String name) {
		return registry.circuitBreaker(name, SLOW_SERVICE);
	}

	/**
	 * Runs {@code call} through {@code breaker}; while the breaker is open, or when the call fails in a way that
	 * counts, the caller's own exception handling sees a {@link ResourceAccessException} as before.
	 */
	public static <T> T call(CircuitBreaker breaker, Supplier<T> call) {
		try {
			return breaker.executeSupplier(call);
		}
		catch (CallNotPermittedException open) {
			throw new ResourceAccessException(breaker.getName() + " is not answering right now (circuit open)");
		}
	}

	static boolean unwell(Throwable e) {
		return e instanceof ResourceAccessException || e instanceof HttpServerErrorException;
	}
}
