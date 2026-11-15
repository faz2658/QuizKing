package com.fazeel.maverick.board;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fazeel.maverick.progress.Xp;
import com.fazeel.maverick.report.GameReport;
import com.fazeel.maverick.report.GameReportRepository;
import com.fazeel.maverick.report.PlayerNames;

/** Rankings across players. */
@Service
public class LeaderboardService {

	static final int TOP = 20;

	private final GameReportRepository reports;
	private final PlayerNames names;

	public LeaderboardService(GameReportRepository reports, PlayerNames names) {
		this.reports = reports;
		this.names = names;
	}

	/** One day's challenge: highest score first, the earlier finish wins a tie. */
	public DailyBoard daily(LocalDate date, int callerId) {
		List<GameReport> runs = new ArrayList<>(reports.findByVariantAndVariantKey(GameReport.DAILY, date.toString()));
		runs.sort(Comparator.comparingInt(GameReport::score).reversed()
				.thenComparing(GameReport::completedAt, Comparator.nullsLast(Comparator.naturalOrder())));
		Map<Integer, String> who = names.lookup(runs.stream().map(GameReport::userId).toList());
		Map<Integer, String> where = names.countries(runs.stream().map(GameReport::userId).toList());
		List<DailyEntry> entries = new ArrayList<>();
		for (int i = 0; i < runs.size(); i++) {
			GameReport r = runs.get(i);
			entries.add(new DailyEntry(i + 1, r.userId(), who.get(r.userId()), r.score(), r.maxScore(),
					r.correctAnswers(), r.totalQuestions(), where.get(r.userId())));
		}
		DailyEntry you = entries.stream().filter(e -> e.userId() == callerId).findFirst().orElse(null);
		return new DailyBoard(date.toString(), entries.size(), entries.stream().limit(TOP).toList(), you);
	}

	public enum Period {
		DAY, WEEK, ALL
	}

	/**
	 * Players ranked by XP earned in the period (today, this week from Monday, or all time), optionally in one
	 * category only. Ties go to the player with more games.
	 */
	public XpBoard xp(Period period, Integer categoryId, int callerId, Instant now) {
		LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
		Instant since = switch (period) {
			case DAY -> today.atStartOfDay(ZoneOffset.UTC).toInstant();
			case WEEK -> today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
			case ALL -> Instant.EPOCH;
		};
		Map<Integer, int[]> totals = new HashMap<>(); // userId -> {xp, games}
		for (GameReport r : reports.findByCompletedAtGreaterThanEqual(since)) {
			if (categoryId != null && r.categoryId() != categoryId) {
				continue;
			}
			int[] t = totals.computeIfAbsent(r.userId(), id -> new int[2]);
			t[0] += Xp.of(r);
			t[1]++;
		}
		List<Map.Entry<Integer, int[]>> ranked = new ArrayList<>(totals.entrySet());
		ranked.sort(Comparator.comparingInt((Map.Entry<Integer, int[]> e) -> e.getValue()[0]).reversed()
				.thenComparing(e -> e.getValue()[1], Comparator.reverseOrder()).thenComparing(Map.Entry::getKey));
		Map<Integer, String> who = names.lookup(ranked.stream().map(Map.Entry::getKey).toList());
		Map<Integer, String> where = names.countries(ranked.stream().map(Map.Entry::getKey).toList());
		List<XpEntry> entries = new ArrayList<>();
		for (int i = 0; i < ranked.size(); i++) {
			var e = ranked.get(i);
			int xp = e.getValue()[0];
			entries.add(new XpEntry(i + 1, e.getKey(), who.get(e.getKey()), xp, e.getValue()[1], Xp.title(Xp.level(xp)),
					where.get(e.getKey())));
		}
		XpEntry you = entries.stream().filter(e -> e.userId() == callerId).findFirst().orElse(null);
		return new XpBoard(period, categoryId, entries.size(), entries.stream().limit(TOP).toList(), you, entries);
	}

	/** {@code title} is the rank the period's XP alone would earn, a fun label rather than the player's level. */
	public record XpEntry(int rank, int userId, String name, int xp, int games, String title, String country) {
	}

	/** Countries ranked by the XP their players earned in the period (players without a country don't count). */
	public CountryBoard countries(Period period, Instant now) {
		XpBoard players = xp(period, null, 0, now);
		Map<Integer, String> where = names.countries(players.all().stream().map(XpEntry::userId).toList());
		Map<String, int[]> totals = new HashMap<>(); // country -> {xp, players}
		for (XpEntry e : players.all()) {
			String c = where.get(e.userId());
			if (c != null) {
				int[] t = totals.computeIfAbsent(c, k -> new int[2]);
				t[0] += e.xp();
				t[1]++;
			}
		}
		List<Map.Entry<String, int[]>> ranked = new ArrayList<>(totals.entrySet());
		ranked.sort(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0]).reversed()
				.thenComparing(Map.Entry::getKey));
		List<CountryEntry> entries = new ArrayList<>();
		for (int i = 0; i < ranked.size(); i++) {
			entries.add(new CountryEntry(i + 1, ranked.get(i).getKey(), ranked.get(i).getValue()[0], ranked.get(i).getValue()[1]));
		}
		return new CountryBoard(period, entries);
	}

	public record CountryEntry(int rank, String country, int xp, int players) {
	}

	public record CountryBoard(Period period, List<CountryEntry> countries) {
	}

	public record XpBoard(Period period, Integer categoryId, int players, List<XpEntry> top, XpEntry you,
			@com.fasterxml.jackson.annotation.JsonIgnore List<XpEntry> all) {
	}

	/** The player's rank in one day's challenge, or 0 if they didn't finish it. */
	public int dailyRank(String date, int userId) {
		if (date == null) {
			return 0;
		}
		DailyBoard board = daily(LocalDate.parse(date), userId);
		return board.you() == null ? 0 : board.you().rank();
	}

	public record DailyBoard(String date, int players, List<DailyEntry> top, DailyEntry you) {
	}

	public record DailyEntry(int rank, int userId, String name, int score, Integer maxScore, int correctAnswers,
			int totalQuestions, String country) {
	}
}
