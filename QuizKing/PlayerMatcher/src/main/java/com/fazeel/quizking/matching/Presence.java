package com.fazeel.quizking.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fazeel.quizking.domain.Match;

/**
 * Who is online right now: every signed-in page sends a heartbeat about once a minute; anyone heard from in
 * the last {@link #ONLINE_FOR} counts as online. Also remembers names and countries so matches can show them.
 */
public class Presence {

	static final Duration ONLINE_FOR = Duration.ofMinutes(2);

	public record Player(String name, String country, Instant lastSeen) {
	}

	public record CountryCount(String country, int players) {
	}

	private final Clock clock;
	private final Map<Integer, Player> players = new ConcurrentHashMap<>();

	public Presence(Clock clock) {
		this.clock = clock;
	}

	/** A heartbeat; {@code country} is kept from earlier heartbeats when this one has none. */
	public void seen(int userId, String name, String country) {
		Player before = players.get(userId);
		String c = validCountry(country) ? country.toUpperCase(java.util.Locale.ROOT)
				: before == null ? null : before.country();
		players.put(userId, new Player(name, c, clock.instant()));
	}

	public int online() {
		Instant since = clock.instant().minus(ONLINE_FOR);
		return (int) players.values().stream().filter(p -> p.lastSeen().isAfter(since)).count();
	}

	public List<CountryCount> byCountry() {
		Instant since = clock.instant().minus(ONLINE_FOR);
		return players.values().stream().filter(p -> p.lastSeen().isAfter(since) && p.country() != null)
				.collect(Collectors.groupingBy(Player::country, Collectors.counting())).entrySet().stream()
				.map(e -> new CountryCount(e.getKey(), e.getValue().intValue()))
				.sorted(Comparator.comparingInt(CountryCount::players).reversed().thenComparing(CountryCount::country))
				.toList();
	}

	/** Adds the names and countries we know to a match (bots already carry theirs). */
	public Match enrich(Match m) {
		Map<Integer, String> names = new HashMap<>(m.names());
		Map<Integer, String> countries = new HashMap<>(m.countries());
		for (int id : m.players()) {
			Player p = players.get(id);
			if (p != null) {
				names.putIfAbsent(id, p.name());
				if (p.country() != null) {
					countries.putIfAbsent(id, p.country());
				}
			}
		}
		return new Match(m.matchId(), m.gameId(), m.players(), m.createdAt(), names, countries, m.screenId());
	}

	/** Forgets people who haven't been seen for a day. */
	public void tick() {
		Instant cutoff = clock.instant().minus(Duration.ofDays(1));
		players.values().removeIf(p -> p.lastSeen().isBefore(cutoff));
	}

	static boolean validCountry(String c) {
		return c != null && c.matches("[A-Za-z]{2}");
	}
}
