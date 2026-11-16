package com.fazeel.quizcadia.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginThrottleTest {

	static class MovableClock extends Clock {
		Instant now = Instant.parse("2026-11-16T12:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Test
	void locksAnEmailAfterTenFailuresUntilTheWindowPasses() {
		MovableClock clock = new MovableClock();
		LoginThrottle t = new LoginThrottle(clock);
		for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
			assertThatCode(() -> t.check("ada@example.test")).doesNotThrowAnyException();
			t.failed("ada@example.test");
			clock.now = clock.now.plusSeconds(10);
		}
		assertThatThrownBy(() -> t.check("ada@example.test")).isInstanceOf(TooManyAttemptsException.class)
				.satisfies(e -> assertThat(((TooManyAttemptsException) e).retryAfterSeconds()).isBetween(1L, 900L));
		assertThatCode(() -> t.check("bob@example.test")).doesNotThrowAnyException(); // other accounts unaffected

		clock.now = clock.now.plus(LoginThrottle.WINDOW);
		assertThatCode(() -> t.check("ada@example.test")).doesNotThrowAnyException();
	}

	@Test
	void aSuccessfulSignInClearsTheCount() {
		LoginThrottle t = new LoginThrottle(new MovableClock());
		for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
			t.failed("ada@example.test");
		}
		t.succeeded("ada@example.test");
		t.failed("ada@example.test");
		assertThatCode(() -> t.check("ada@example.test")).doesNotThrowAnyException();
		assertThat(Duration.ofMinutes(15)).isEqualTo(LoginThrottle.WINDOW);
	}
}
