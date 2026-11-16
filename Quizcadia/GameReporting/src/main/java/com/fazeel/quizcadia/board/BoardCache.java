package com.fazeel.quizcadia.board;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Keeps each computed board for a short time and shares it: however many people open the leaderboard at once,
 * it is worked out once, and everyone waiting gets that one result. A board that fails isn't kept.
 */
@Component
public class BoardCache {

	private record Entry(CompletableFuture<Object> value, Instant expires) {
	}

	private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
	private final Clock clock;

	@org.springframework.beans.factory.annotation.Autowired
	public BoardCache(Clock clock) {
		this.clock = clock;
	}

	/** A cache for tests and callers without Spring. */
	public BoardCache() {
		this(Clock.systemUTC());
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, Duration ttl, Supplier<T> compute) {
		Instant now = clock.instant();
		CompletableFuture<Object> mine = new CompletableFuture<>();
		Entry entry = entries.compute(key, (k, old) -> old != null && old.expires().isAfter(now) && !failed(old)
				? old : new Entry(mine, now.plus(ttl)));
		if (entry.value() == mine) {
			try {
				mine.complete(compute.get());
			}
			catch (RuntimeException e) {
				mine.completeExceptionally(e);
				entries.remove(key, entry);
			}
		}
		try {
			return (T) entry.value().join();
		}
		catch (CompletionException e) {
			throw e.getCause() instanceof RuntimeException r ? r : e;
		}
	}

	/** Drops every board whose key starts with {@code prefix}, so the next request works it out afresh. */
	public void evict(String prefix) {
		entries.keySet().removeIf(k -> k.startsWith(prefix));
	}

	private static boolean failed(Entry e) {
		return e.value().isCompletedExceptionally();
	}
}
