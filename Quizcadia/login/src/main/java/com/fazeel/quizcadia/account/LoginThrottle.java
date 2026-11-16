package com.fazeel.quizcadia.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Stops password guessing on one account: after {@value #MAX_FAILURES} wrong passwords for an email within
 * {@link #WINDOW}, further attempts for that email are refused until the oldest of those failures is
 * {@link #WINDOW} old. Counted per email whatever address the attempts come from, and the same for emails that
 * don't exist, so it doesn't reveal which accounts are real. Kept in memory: a restart clears it.
 */
@Component
public class LoginThrottle {

	static final int MAX_FAILURES = 10;
	static final Duration WINDOW = Duration.ofMinutes(15);
	/** Stops the map growing without end under a flood of made-up emails. */
	private static final int MAX_TRACKED = 50_000;

	private final Clock clock;
	private final Map<String, Deque<Instant>> failures = new HashMap<>();

	public LoginThrottle(Clock clock) {
		this.clock = clock;
	}

	/** Throws if this email is locked right now. */
	public synchronized void check(String email) {
		Deque<Instant> f = recent(email);
		if (f != null && f.size() >= MAX_FAILURES) {
			long wait = Math.max(1, Duration.between(clock.instant(), f.peekFirst().plus(WINDOW)).toSeconds());
			throw new TooManyAttemptsException(wait);
		}
	}

	public synchronized void failed(String email) {
		if (failures.size() >= MAX_TRACKED && !failures.containsKey(email)) {
			Instant cutoff = clock.instant().minus(WINDOW);
			failures.values().removeIf(q -> q.isEmpty() || q.peekLast().isBefore(cutoff));
		}
		failures.computeIfAbsent(email, k -> new ArrayDeque<>()).addLast(clock.instant());
	}

	public synchronized void succeeded(String email) {
		failures.remove(email);
	}

	private Deque<Instant> recent(String email) {
		Deque<Instant> f = failures.get(email);
		if (f == null) {
			return null;
		}
		Instant cutoff = clock.instant().minus(WINDOW);
		while (!f.isEmpty() && f.peekFirst().isBefore(cutoff)) {
			f.pollFirst();
		}
		if (f.isEmpty()) {
			failures.remove(email);
			return null;
		}
		return f;
	}
}
