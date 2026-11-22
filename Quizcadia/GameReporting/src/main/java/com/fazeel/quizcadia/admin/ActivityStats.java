package com.fazeel.quizcadia.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.board.BoardCache;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReportRepository;
import com.fazeel.quizcadia.report.PlayerName;
import com.fazeel.quizcadia.report.PlayerNameRepository;
import com.fazeel.quizcadia.report.Players;

/** Who plays and what, for the admin dashboard. Guests are counted apart from accounts. */
@Service
public class ActivityStats {

	static final int GUEST_ID_BASE = Players.GUEST_ID_BASE;

	private final GameReportRepository reports;
	private final PlayerNameRepository names;
	private final Clock clock;
	private final BoardCache cache;

	@Autowired
	public ActivityStats(GameReportRepository reports, PlayerNameRepository names, Clock clock,
			BoardCache cache) {
		this.reports = reports;
		this.names = names;
		this.clock = clock;
		this.cache = cache;
	}

	public ActivityStats(GameReportRepository reports, PlayerNameRepository names, Clock clock) {
		this(reports, names, clock, new BoardCache(clock));
	}

	public record Day(LocalDate day, int games, int players, int guestGames) {
	}

	public record Count(String name, int count) {
	}

	public record TopPlayer(int userId, String name, String country, int games, int points) {
	}

	public record View(long gamesAllTime, int gamesInPeriod, int activeToday, int active7Days, int active30Days,
			int guestPlayers30Days, List<Day> byDay, List<Count> modes, List<Count> topGames, List<Count> countries,
			List<TopPlayer> topPlayers) {
	}

	static boolean guest(int userId) {
		return Players.isGuest(userId);
	}

	/** Retention cohorts over the last {@code weeks} start weeks (see {@link Retention}); cached for 5 minutes. */
	public Retention.View retention(int weeks) {
		return cache.get("retention:" + weeks, Duration.ofMinutes(5), () -> Retention.compute(
				reports.findAllPlayTimes().stream().map(p -> new Retention.Play(p.userId(), p.completedAt())).toList(),
				LocalDate.now(clock.withZone(ZoneOffset.UTC)), weeks));
	}

	public View view(int days) {
		return cache.get("admin:" + days, Duration.ofSeconds(30), () -> compute(days));
	}

	private View compute(int days) {
		Instant now = clock.instant();
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		int span = Math.max(days, 30);
		Instant since = today.minusDays(span - 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
		List<GameReport> recent = reports.findCompletedBetween(since, now.plusSeconds(1)).stream()
				.filter(r -> r.completedAt() != null).toList();

		Map<LocalDate, List<GameReport>> perDay = new TreeMap<>();
		for (int i = days - 1; i >= 0; i--) {
			perDay.put(today.minusDays(i), new ArrayList<>());
		}
		Set<Integer> d1 = new HashSet<>(), d7 = new HashSet<>(), d30 = new HashSet<>(), guests = new HashSet<>();
		for (GameReport r : recent) {
			LocalDate day = r.completedAt().atZone(ZoneOffset.UTC).toLocalDate();
			List<GameReport> bucket = perDay.get(day);
			if (bucket != null) bucket.add(r);
			if (guest(r.userId())) {
				if (!day.isBefore(today.minusDays(29))) guests.add(r.userId());
				continue;
			}
			if (day.equals(today)) d1.add(r.userId());
			if (!day.isBefore(today.minusDays(6))) d7.add(r.userId());
			if (!day.isBefore(today.minusDays(29))) d30.add(r.userId());
		}
		List<Day> byDay = new ArrayList<>();
		List<GameReport> inPeriod = new ArrayList<>();
		perDay.forEach((day, list) -> {
			inPeriod.addAll(list);
			byDay.add(new Day(day, list.size(), (int) list.stream().map(GameReport::userId).distinct().count(),
					(int) list.stream().filter(r -> guest(r.userId())).count()));
		});

		List<Count> modes = top(inPeriod, ActivityStats::kind, 10);
		List<Count> topGames = top(inPeriod, r -> r.gameName() == null || r.gameName().isBlank() ? "Game " + r.gameId() : r.gameName(), 10);

		Map<Integer, PlayerName> known = new HashMap<>();
		names.findAll().forEach(n -> known.put(n.userId(), n));
		Map<String, Integer> countries = new HashMap<>();
		known.values().stream().filter(n -> !guest(n.userId()) && n.country() != null)
				.forEach(n -> countries.merge(n.country(), 1, Integer::sum));

		Map<Integer, int[]> perPlayer = new LinkedHashMap<>();
		inPeriod.stream().filter(r -> !guest(r.userId()))
				.forEach(r -> {
					int[] gx = perPlayer.computeIfAbsent(r.userId(), k -> new int[2]);
					gx[0]++;
					gx[1] += Math.max(0, r.score());
				});
		List<TopPlayer> topPlayers = perPlayer.entrySet().stream()
				.sorted(Comparator.comparingInt((Map.Entry<Integer, int[]> e) -> e.getValue()[0]).reversed()
						.thenComparing(e -> -e.getValue()[1]))
				.limit(10)
				.map(e -> {
					PlayerName n = known.get(e.getKey());
					return new TopPlayer(e.getKey(), n == null ? "Player " + e.getKey() : n.name(),
							n == null ? null : n.country(), e.getValue()[0], e.getValue()[1]);
				})
				.toList();

		return new View(reports.count(), inPeriod.size(), d1.size(), d7.size(), d30.size(), guests.size(), byDay,
				modes, topGames, sorted(countries, 15), topPlayers);
	}

	/** "Daily", "Challenge", "Arena", or the mode: Solo, Multiplayer, Adaptive. */
	static String kind(GameReport r) {
		if (GameReport.DAILY.equals(r.variant())) return "Daily";
		if (GameReport.CHALLENGE.equals(r.variant())) return "Challenge";
		if (GameReport.ARENA.equals(r.variant())) return "Arena";
		if (r.mode() == null) return "Other";
		return switch (r.mode()) {
			case SINGLE -> "Solo";
			case MULTI -> "Multiplayer";
			case ADAPTIVE -> "Adaptive";
		};
	}

	private static List<Count> top(List<GameReport> list, Function<GameReport, String> key, int limit) {
		Map<String, Integer> counts = list.stream().map(key).filter(Objects::nonNull)
				.collect(Collectors.toMap(Function.identity(), k -> 1, Integer::sum));
		return sorted(counts, limit);
	}

	private static List<Count> sorted(Map<String, Integer> counts, int limit) {
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(limit).map(e -> new Count(e.getKey(), e.getValue())).toList();
	}
}
