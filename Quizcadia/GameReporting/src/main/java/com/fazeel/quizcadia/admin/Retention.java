package com.fazeel.quizcadia.admin;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.fazeel.quizcadia.report.Players;

/**
 * How many players come back. A player is "active" on a day (UTC) when they finish a game that day, and starts on the
 * day of their first finished game.
 * <ul>
 * <li>Day-N retention: of the players whose day N (N days after their first) is over, the share active that day
 * (the usual D1 / D7 / D30). Players too new for that are left out, not counted as gone.</li>
 * <li>Weekly cohorts: players grouped by the week (Monday to Sunday) they started; for each later week, the share of
 * the group active at least once that week. Weeks still to come are null.</li>
 * </ul>
 * Guests are counted too (their ids are {@link Players#GUEST_ID_BASE} and up); the "accounts" figures leave them out.
 */
public final class Retention {

	/** The day-N figures shown. */
	public static final int[] DAYS = { 1, 7, 30 };

	private Retention() {
	}

	/** One finished game: who and when. */
	public record Play(int userId, Instant at) {
	}

	/** Day-N retention: {@code eligible} players had reached day N, {@code returned} of them played that day. */
	public record DayN(int day, int eligible, int returned, Double rate) {
	}

	/** One week's new players and the share active in each following week (index 0 is the start week: 1.0). */
	public record Cohort(LocalDate weekStart, int players, int guests, List<Double> weeks) {
	}

	public record View(LocalDate asOf, int players, List<DayN> everyone, List<DayN> accounts, List<Cohort> cohorts) {
	}

	/**
	 * @param weeks how many recent start weeks to show as cohorts (and the day-N figures cover players who started
	 *        in that span, so old early adopters don't dominate today's numbers)
	 */
	public static View compute(Collection<Play> plays, LocalDate today, int weeks) {
		Map<Integer, TreeSet<LocalDate>> days = new HashMap<>();
		for (Play p : plays) {
			if (p.at() != null && p.userId() > 0) {
				days.computeIfAbsent(p.userId(), k -> new TreeSet<>()).add(p.at().atZone(ZoneOffset.UTC).toLocalDate());
			}
		}
		LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate firstWeek = thisWeek.minusWeeks(weeks - 1L);

		Map<Integer, TreeSet<LocalDate>> recent = new HashMap<>();
		days.forEach((user, d) -> {
			if (!d.first().isBefore(firstWeek)) {
				recent.put(user, d);
			}
		});

		List<DayN> everyone = new ArrayList<>();
		List<DayN> accounts = new ArrayList<>();
		for (int n : DAYS) {
			everyone.add(dayN(recent, today, n, false));
			accounts.add(dayN(recent, today, n, true));
		}

		List<Cohort> cohorts = new ArrayList<>();
		for (int w = 0; w < weeks; w++) {
			LocalDate start = firstWeek.plusWeeks(w);
			List<TreeSet<LocalDate>> group = new ArrayList<>();
			int guests = 0;
			for (Map.Entry<Integer, TreeSet<LocalDate>> e : recent.entrySet()) {
				LocalDate first = e.getValue().first();
				if (!first.isBefore(start) && first.isBefore(start.plusWeeks(1))) {
					group.add(e.getValue());
					guests += Players.isGuest(e.getKey()) ? 1 : 0;
				}
			}
			List<Double> shares = new ArrayList<>();
			for (int later = 0; later < weeks - w; later++) {
				LocalDate from = start.plusWeeks(later);
				LocalDate to = from.plusWeeks(1);
				long active = group.stream().filter(d -> !d.subSet(from, to).isEmpty()).count();
				shares.add(group.isEmpty() ? null : (double) active / group.size());
			}
			cohorts.add(new Cohort(start, group.size(), guests, shares));
		}
		return new View(today, recent.size(), everyone, accounts, cohorts);
	}

	private static DayN dayN(Map<Integer, TreeSet<LocalDate>> players, LocalDate today, int n, boolean accountsOnly) {
		int eligible = 0;
		int returned = 0;
		for (Map.Entry<Integer, TreeSet<LocalDate>> e : players.entrySet()) {
			if (accountsOnly && !Players.isAccount(e.getKey())) {
				continue;
			}
			LocalDate first = e.getValue().first();
			if (ChronoUnit.DAYS.between(first, today) <= n) {
				continue; // day n isn't over yet (or hasn't come): too early to count them as gone
			}
			eligible++;
			if (e.getValue().contains(first.plusDays(n))) {
				returned++;
			}
		}
		return new DayN(n, eligible, returned, eligible == 0 ? null : (double) returned / eligible);
	}
}
