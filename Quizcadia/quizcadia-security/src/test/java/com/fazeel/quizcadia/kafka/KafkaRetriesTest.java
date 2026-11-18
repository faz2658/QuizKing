package com.fazeel.quizcadia.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class KafkaRetriesTest {

	@Test
	void waitsLongerEachTimeUpToTenSecondsAndGivesUpAfterAboutAMinute() {
		BackOffExecution run = KafkaRetries.backOff().start();
		List<Long> waits = new ArrayList<>();
		long total = 0;
		long next;
		while ((next = run.nextBackOff()) != BackOffExecution.STOP) {
			waits.add(next);
			total += next;
			assertThat(waits).hasSizeLessThan(20);
		}
		assertThat(waits.get(0)).isBetween(750L, 1_250L);
		assertThat(waits.get(1)).isBetween(1_500L, 2_500L);
		assertThat(waits.get(2)).isBetween(3_000L, 5_000L);
		assertThat(waits).allSatisfy(w -> assertThat(w).isLessThanOrEqualTo(10_000L));
		assertThat(total).isBetween(40_000L, 75_000L);
	}
}
