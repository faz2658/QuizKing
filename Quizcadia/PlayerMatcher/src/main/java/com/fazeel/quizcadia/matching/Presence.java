package com.fazeel.quizcadia.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fazeel.quizcadia.domain.Match;

/**
 * Who is online right now: every signed-in page sends a heartbeat about once a minute; anyone heard from in
 * the last {@link #ONLINE_FOR} counts as online. Also remembers names and countries so matches can show them.
 */
public class Presence {

	static final Duration ONLINE_FOR = Duration.ofMinutes(2);

	/** {@code since}: when this stretch online began (a gap longer than {@link #ONLINE_FOR} starts a new one). */
	public record Player(String name, String country, Instant lastSeen, Instant since) {
	}

	/** One person online now, for admins. Guests have ids from {@link #GUEST_ID_BASE} up (see the login service). */
	public record OnlinePlayer(int userId, String name, String country, boolean guest, Instant onlineSince,
			Instant lastSeen) {
	}

	static final int GUEST_ID_BASE = 900_000_000;

	/** How many players from one country are online. */
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
		String c = validCountry(country) ? country.toUpperCase(Locale.ROOT)
				: before == null ? null : before.country();
		Instant now = clock.instant();
		boolean stillOnline = before != null && before.lastSeen().isAfter(now.minus(ONLINE_FOR));
		players.put(userId, new Player(name, c, now, stillOnline ? before.since() : now));
	}

	/** How many players were seen in the last 2 minutes. */
	public int online() {
		Instant since = clock.instant().minus(ONLINE_FOR);
		return (int) players.values().stream().filter(p -> p.lastSeen().isAfter(since)).count();
	}

	/** Who is online now: accounts first, then guests, each by name. */
	public List<OnlinePlayer> whoIsOnline() {
		Instant since = clock.instant().minus(ONLINE_FOR);
		return players.entrySet().stream().filter(e -> e.getValue().lastSeen().isAfter(since))
				.map(e -> new OnlinePlayer(e.getKey(), e.getValue().name(), e.getValue().country(),
						e.getKey() >= GUEST_ID_BASE, e.getValue().since(), e.getValue().lastSeen()))
				.sorted(Comparator.comparing(OnlinePlayer::guest)
						.thenComparing(p -> p.name() == null ? "" : p.name(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/** Online players counted per country (those who set one). */
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
		return new Match(m.matchId(), m.gameId(), m.players(), m.createdAt(), names, countries, m.screenId(), m.quiz());
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
