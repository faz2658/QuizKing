package com.fazeel.quizcadia.progress;

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
	public record Streak(int current, int best, boolean playedToday, int shields) {

		public Streak(int current, int best, boolean playedToday) {
			this(current, best, playedToday, 0);
		}
	}

	public static Streak of(Collection<LocalDate> playedDays, LocalDate today) {
		return of(playedDays, today, 0);
	}

	/**
	 * @param shields streak shields (from invites): each covers one missed day inside the current streak.
	 *        {@code shields} in the result is how many are left.
	 */
	public static Streak of(Collection<LocalDate> playedDays, LocalDate today, int shields) {
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
		int left = shields;
		while (true) {
			if (days.contains(cursor)) {
				current++;
			} else if (current > 0 && left > 0 && days.contains(cursor.minusDays(1))) {
				left--; // a shield covers this one missed day
			} else {
				break;
			}
			cursor = cursor.minusDays(1);
		}
		return new Streak(current, Math.max(best, current), playedToday, left);
	}
}
