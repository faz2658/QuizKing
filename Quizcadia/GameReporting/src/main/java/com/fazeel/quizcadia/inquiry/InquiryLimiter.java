package com.fazeel.quizcadia.inquiry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Keeps the public request form from being flooded: at most {@value #PER_SENDER} requests an hour from one
 * address, and {@value #TOTAL} an hour from everyone together. Kept in memory: a restart simply starts afresh.
 */
@Component
public class InquiryLimiter {

	static final int PER_SENDER = 5;
	static final int TOTAL = 60;
	private static final Duration WINDOW = Duration.ofHours(1);

	private final Clock clock;
	private final Map<String, Deque<Instant>> bySender = new HashMap<>();
	private final Deque<Instant> all = new ArrayDeque<>();

	public InquiryLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Records the attempt and says whether it may go ahead. */
	public synchronized boolean allow(String sender) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(WINDOW);
		prune(all, cutoff);
		bySender.values().forEach(q -> prune(q, cutoff));
		bySender.values().removeIf(Deque::isEmpty);
		Deque<Instant> mine = bySender.computeIfAbsent(sender, k -> new ArrayDeque<>());
		if (mine.size() >= PER_SENDER || all.size() >= TOTAL) {
			return false;
		}
		mine.add(now);
		all.add(now);
		return true;
	}

	private static void prune(Deque<Instant> q, Instant cutoff) {
		while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
			q.pollFirst();
		}
	}
}
