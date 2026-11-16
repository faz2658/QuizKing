package com.fazeel.quizcadia.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InquiryLimiterTest {

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
	void fivePerSenderPerHourThenRecovers() {
		MovableClock clock = new MovableClock();
		InquiryLimiter limiter = new InquiryLimiter(clock);
		for (int i = 0; i < InquiryLimiter.PER_SENDER; i++) {
			assertThat(limiter.allow("1.2.3.4")).isTrue();
		}
		assertThat(limiter.allow("1.2.3.4")).isFalse();
		assertThat(limiter.allow("5.6.7.8")).isTrue(); // someone else isn't affected

		clock.now = clock.now.plus(Duration.ofMinutes(61));
		assertThat(limiter.allow("1.2.3.4")).isTrue();
	}

	@Test
	void capsEveryoneTogether() {
		InquiryLimiter limiter = new InquiryLimiter(new MovableClock());
		for (int i = 0; i < InquiryLimiter.TOTAL; i++) {
			assertThat(limiter.allow("10.0.0." + i)).isTrue();
		}
		assertThat(limiter.allow("10.0.1.1")).isFalse();
	}
}
