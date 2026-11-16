package com.fazeel.quizcadia.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BoardCacheTest {

	static class MovableClock extends Clock {
		Instant now = Instant.parse("2026-11-16T03:00:00Z");

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Test
	void manySimultaneousRequestsComputeTheBoardOnce() throws Exception {
		BoardCache cache = new BoardCache();
		AtomicInteger computed = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(25);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return cache.get("xp:week", Duration.ofSeconds(30), () -> {
					computed.incrementAndGet();
					sleep(200); // a slow board: everyone else arrives while it's being worked out
					return "board";
				});
			}));
		}
		start.countDown();
		for (Future<String> f : results) {
			assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo("board");
		}
		pool.shutdown();
		assertThat(computed).hasValue(1);
	}

	@Test
	void boardsExpireAndCanBeDropped() {
		MovableClock clock = new MovableClock();
		BoardCache cache = new BoardCache(clock);
		AtomicInteger n = new AtomicInteger();

		assertThat(cache.get("daily:2026-11-16", Duration.ofSeconds(15), n::incrementAndGet)).isEqualTo(1);
		assertThat(cache.get("daily:2026-11-16", Duration.ofSeconds(15), n::incrementAndGet)).isEqualTo(1);
		clock.now = clock.now.plusSeconds(16);
		assertThat(cache.get("daily:2026-11-16", Duration.ofSeconds(15), n::incrementAndGet)).isEqualTo(2);

		cache.evict("daily:");
		assertThat(cache.get("daily:2026-11-16", Duration.ofSeconds(15), n::incrementAndGet)).isEqualTo(3);
	}

	@Test
	void aFailedBoardIsNotKept() {
		BoardCache cache = new BoardCache();
		assertThatThrownBy(() -> cache.get("cup:x", Duration.ofSeconds(30), () -> {
			throw new IllegalStateException("database down");
		})).isInstanceOf(IllegalStateException.class).hasMessage("database down");
		assertThat(cache.<String>get("cup:x", Duration.ofSeconds(30), () -> "ok")).isEqualTo("ok");
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
