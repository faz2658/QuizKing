package com.fazeel.quizcadia.board;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.progress.Xp;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReportRepository;
import com.fazeel.quizcadia.report.PlayerNames;
import com.fazeel.quizcadia.report.Players;

/**
 * The weekly Country Cup: Monday 00:00 to Monday 00:00 UTC, a country scores the XP its best {@link #SQUAD} players
 * earned that week. Every extra player (up to the squad size) helps, so it pays to bring friends.
 */
@Service
public class CountryCup {

	static final int SQUAD = 20;
	static final int SHOWN = 30;

	private final GameReportRepository reports;
	private final PlayerNames names;
	private final Clock clock;
	private final BoardCache cache;

	/** Cache key prefix of the cup standings (dropped when someone changes country). */
	public static final String KEY = "cup:";
	static final Duration TTL = Duration.ofSeconds(30);

	@Autowired
	public CountryCup(GameReportRepository reports, PlayerNames names, Clock clock, BoardCache cache) {
		this.reports = reports;
		this.names = names;
		this.clock = clock;
		this.cache = cache;
	}

	public CountryCup(GameReportRepository reports, PlayerNames names, Clock clock) {
		this(reports, names, clock, new BoardCache(clock));
	}

	/** A country's place in this week's Cup: its points, how many players scored, and its best player (star). */
	public record Entry(int rank, String country, int points, int players, String star) {
	}

	/** The caller's country: its place, the points to catch the country above, and the caller's own part. */
	public record Yours(String country, int rank, int points, int players, int gapToNext, int yourXp, int yourRank) {
	}

	/** Last week's winner and its points. */
	public record Champion(String country, int points) {
	}

	/** {@code yours} is null for players without a country (they're asked to pick one). */
	public record CupView(Instant weekStart, Instant endsAt, List<Entry> standings, int countries, Yours yours,
			Champion lastWeek) {
	}

	/** This week's Cup standings, last week's champion, and the caller's own contribution. */
	public CupView view(int callerId) {
		Instant now = clock.instant();
		Instant start = LocalDate.ofInstant(now, ZoneOffset.UTC).with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant end = start.plus(7, ChronoUnit.DAYS);
		Standings week = standings(start, end);
		Standings last = standings(start.minus(7, ChronoUnit.DAYS), start);

		String myCountry = names.countries(List.of(callerId)).get(callerId);
		Yours yours = null;
		if (myCountry != null) {
			Entry mine = week.entries.stream().filter(e -> e.country().equals(myCountry)).findFirst().orElse(null);
			List<Integer> squad = week.squads.getOrDefault(myCountry, List.of());
			int myXp = week.xp.getOrDefault(callerId, 0);
			int myRank = squad.indexOf(callerId) + 1;
			if (mine == null) {
				int rank = week.entries.size() + 1;
				int gap = week.entries.isEmpty() ? 0 : week.entries.get(week.entries.size() - 1).points() + 1;
				yours = new Yours(myCountry, rank, 0, 0, gap, myXp, 0);
			} else {
				int gap = mine.rank() == 1 ? 0 : week.entries.get(mine.rank() - 2).points() - mine.points() + 1;
				yours = new Yours(myCountry, mine.rank(), mine.points(), mine.players(), gap, myXp, myRank);
			}
		}
		Champion champion = last.entries.isEmpty() ? null
				: new Champion(last.entries.get(0).country(), last.entries.get(0).points());
		return new CupView(start, end, week.entries.stream().limit(SHOWN).toList(), week.entries.size(), yours, champion);
	}

	/** The week worked out: the ranked countries, each player's XP and which players count for each country. */
	private record Standings(List<Entry> entries, Map<Integer, Integer> xp, Map<String, List<Integer>> squads) {
	}

	private Standings standings(Instant since, Instant until) {
		return cache.get(KEY + since + ":" + until, TTL, () -> computeStandings(since, until));
	}

	private Standings computeStandings(Instant since, Instant until) {
		Map<Integer, Integer> xp = new HashMap<>();
		for (GameReport r : reports.findCompletedBetween(since, until)) {
			if (Players.isAccount(r.userId())) { // bots and guests never score for a country
				xp.merge(r.userId(), Xp.of(r), Integer::sum);
			}
		}
		Map<Integer, String> where = names.countries(xp.keySet());
		Map<String, List<Integer>> byCountry = new HashMap<>();
		xp.forEach((id, points) -> {
			String c = where.get(id);
			if (c != null) {
				byCountry.computeIfAbsent(c, k -> new ArrayList<>()).add(id);
			}
		});
		Map<Integer, String> who = names.lookup(xp.keySet());
		Map<String, List<Integer>> squads = new HashMap<>();
		List<Entry> entries = new ArrayList<>();
		byCountry.forEach((country, ids) -> {
			ids.sort(Comparator.comparingInt((Integer id) -> xp.get(id)).reversed().thenComparing(id -> id));
			List<Integer> squad = ids.subList(0, Math.min(SQUAD, ids.size()));
			squads.put(country, ids);
			int points = squad.stream().mapToInt(xp::get).sum();
			entries.add(new Entry(0, country, points, ids.size(), who.get(squad.get(0))));
		});
		entries.sort(Comparator.comparingInt(Entry::points).reversed().thenComparing(Entry::country));
		List<Entry> ranked = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			ranked.add(new Entry(i + 1, e.country(), e.points(), e.players(), e.star()));
		}
		return new Standings(ranked, xp, squads);
	}
}
