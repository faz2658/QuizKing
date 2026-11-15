package com.fazeel.maverick.progress;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/** Days in a row with at least one finished game (days are UTC, like the Daily Challenge). */
public final class Streaks {

	private Streaks() {
	}

	/**
	 * @param current days in a row up to today, or up to yesterday when today has no game yet (the streak is
	 *        still alive until midnight)
	 */
	public record Streak(int current, int best, boolean playedToday) {
	}

	public static Streak of(Collection<LocalDate> playedDays, LocalDate today) {
		TreeSet<LocalDate> days = new TreeSet<>(playedDays);
		int best = 0;
		int run = 0;
		LocalDate previous = null;
		for (LocalDate d : days) {
			run = previous != null && previous.plusDays(1).equals(d) ? run + 1 : 1;
			best = Math.max(best, run);
			previous = d;
		}
		boolean playedToday = days.contains(today);
		LocalDate cursor = playedToday ? today : today.minusDays(1);
		int current = 0;
		while (days.contains(cursor)) {
			current++;
			cursor = cursor.minusDays(1);
		}
		return new Streak(current, best, playedToday);
	}
}
