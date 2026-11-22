package com.fazeel.quizcadia.board;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fazeel.quizcadia.progress.Xp;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReportRepository;
import com.fazeel.quizcadia.report.PlayerNames;
import com.fazeel.quizcadia.report.Players;

/** Rankings across players. */
@Service
public class LeaderboardService {

	static final int TOP = 20;

	/** How long a computed board is shared. The daily board is also dropped whenever a daily run arrives. */
	static final Duration DAILY_TTL = Duration.ofSeconds(15);
	static final Duration XP_TTL = Duration.ofSeconds(30);

	private final GameReportRepository reports;
	private final PlayerNames names;
	private final BoardCache cache;

	@Autowired
	public LeaderboardService(GameReportRepository reports, PlayerNames names, BoardCache cache) {
		this.reports = reports;
		this.names = names;
		this.cache = cache;
	}

	public LeaderboardService(GameReportRepository reports, PlayerNames names) {
		this(reports, names, new BoardCache());
	}

	/** Cache key prefix of the daily boards (see {@link BoardCache#evict}). */
	public static final String DAILY_KEY = "daily:";

	/** One day's challenge: highest score first, the earlier finish wins a tie. */
	public DailyBoard daily(LocalDate date, int callerId) {
		List<DailyEntry> entries = cache.get(DAILY_KEY + date, DAILY_TTL, () -> dailyEntries(date));
		DailyEntry you = entries.stream().filter(e -> e.userId() == callerId).findFirst().orElse(null);
		return new DailyBoard(date.toString(), entries.size(), entries.stream().limit(TOP).toList(), you, place(you, entries));
	}

	private List<DailyEntry> dailyEntries(LocalDate date) {
		List<GameReport> runs = new ArrayList<>(reports.findScoresByVariantAndVariantKey(GameReport.DAILY, date.toString())
				.stream().filter(r -> Players.isAccount(r.userId())).toList()); // guests aren't ranked
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
		return List.copyOf(entries);
	}

	/** Brag material for the caller: rank among players from their country, and "top N%" of everyone. */
	static Place place(DailyEntry you, List<DailyEntry> entries) {
		if (you == null) {
			return null;
		}
		int topPercent = Math.max(1, (int) Math.ceil(100.0 * you.rank() / entries.size()));
		if (you.country() == null) {
			return new Place(null, 0, 0, topPercent);
		}
		List<DailyEntry> compatriots = entries.stream().filter(e -> you.country().equals(e.country())).toList();
		return new Place(you.country(), compatriots.indexOf(you) + 1, compatriots.size(), topPercent);
	}

	/** Which stretch a leaderboard covers: today, this week (from Monday, UTC) or all time. */
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
		List<XpEntry> entries = cache.get("xp:" + since + ":" + categoryId, XP_TTL, () -> xpEntries(since, categoryId));
		XpEntry you = entries.stream().filter(e -> e.userId() == callerId).findFirst().orElse(null);
		return new XpBoard(period, categoryId, entries.size(), entries.stream().limit(TOP).toList(), you, entries);
	}

	private List<XpEntry> xpEntries(Instant since, Integer categoryId) {
		Map<Integer, int[]> totals = new HashMap<>(); // userId -> {xp, games}
		for (GameReport r : reports.findScoresCompletedSince(since)) {
			if (!Players.isAccount(r.userId()) || categoryId != null && r.categoryId() != categoryId) {
				continue; // guests and bots aren't ranked
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
		return List.copyOf(entries);
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

	/** A country's rank, total XP and number of players. */
	public record CountryEntry(int rank, String country, int xp, int players) {
	}

	/** The country leaderboard for a period. */
	public record CountryBoard(Period period, List<CountryEntry> countries) {
	}

	/**
	 * The player leaderboard: the top players, how many played, the caller's own entry (even outside the top) and,
	 * for internal use only, everyone.
	 */
	public record XpBoard(Period period, Integer categoryId, int players, List<XpEntry> top, XpEntry you,
			@JsonIgnore List<XpEntry> all) {
	}

	/** The player's rank in one day's challenge, or 0 if they didn't finish it. */
	public int dailyRank(String date, int userId) {
		if (date == null) {
			return 0;
		}
		DailyBoard board = daily(LocalDate.parse(date), userId);
		return board.you() == null ? 0 : board.you().rank();
	}

	/** {@code you}/{@code place} are null if the caller didn't finish that day's challenge. */
	public record DailyBoard(String date, int players, List<DailyEntry> top, DailyEntry you, Place place) {
	}

	/** The caller's {@code countryRank} of {@code countryPlayers} (0 without a country) and "top {@code topPercent}%". */
	public record Place(String country, int countryRank, int countryPlayers, int topPercent) {
	}

	/** One run of today's Daily Challenge on its leaderboard. */
	public record DailyEntry(int rank, int userId, String name, int score, Integer maxScore, int correctAnswers,
			int totalQuestions, String country) {
	}
}
