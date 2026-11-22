package com.fazeel.quizcadia.match;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Who is waiting for news about a match. Whenever a match is saved (an answer, a question closing), everyone waiting
 * for it is told at once, so players' screens update without asking every second. In memory: this engine runs as one
 * instance.
 */
@Component
public class MatchUpdates {

	private final ConcurrentHashMap<String, Set<Runnable>> waiting = new ConcurrentHashMap<>();

	/** Runs {@code onChange} once, at the next change to the match. @return call it to stop waiting */
	public Runnable await(String matchId, Runnable onChange) {
		waiting.computeIfAbsent(matchId, k -> ConcurrentHashMap.newKeySet()).add(onChange);
		return () -> waiting.computeIfPresent(matchId, (k, set) -> {
			set.remove(onChange);
			return set.isEmpty() ? null : set;
		});
	}

	/** Wakes everyone waiting for news about this match (each waiter runs once). */
	public void changed(String matchId) {
		Set<Runnable> set = waiting.remove(matchId);
		if (set != null) {
			set.forEach(Runnable::run);
		}
	}

	/** How many requests wait for this match (for tests and diagnostics). */
	public int waitingFor(String matchId) {
		Set<Runnable> set = waiting.get(matchId);
		return set == null ? 0 : set.size();
	}
}
