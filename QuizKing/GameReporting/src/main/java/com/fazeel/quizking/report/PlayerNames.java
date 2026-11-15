package com.fazeel.quizking.report;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** Display names for leaderboards. Writes only when a name changes (cached in memory). */
@Component
public class PlayerNames {

	private final PlayerNameRepository names;
	private final Clock clock;
	private final Map<Integer, String> known = new ConcurrentHashMap<>();

	public PlayerNames(PlayerNameRepository names, Clock clock) {
		this.names = names;
		this.clock = clock;
	}

	public void remember(int userId, String name) {
		if (name == null || name.isBlank() || name.equals(known.get(userId))) {
			return;
		}
		String trimmed = name.trim();
		known.put(userId, trimmed);
		String country = names.findById(userId).map(PlayerName::country).orElse(null);
		names.save(new PlayerName(userId, trimmed, clock.instant(), country));
	}

	/** The player's country (ISO code) for flags and the countries leaderboard; null clears it. */
	public void country(int userId, String name, String country) {
		String c = country == null || !country.matches("[A-Za-z]{2}") ? null : country.toUpperCase(java.util.Locale.ROOT);
		if (name != null && !name.isBlank()) {
			known.put(userId, name.trim());
		}
		String n = name != null && !name.isBlank() ? name.trim()
				: names.findById(userId).map(PlayerName::name).orElse("Player " + userId);
		names.save(new PlayerName(userId, n, clock.instant(), c));
	}

	public Map<Integer, String> countries(Collection<Integer> userIds) {
		Map<Integer, String> found = new java.util.HashMap<>();
		names.findAllById(userIds).forEach(p -> {
			if (p.country() != null) {
				found.put(p.userId(), p.country());
			}
		});
		return found;
	}

	public Map<Integer, String> lookup(Collection<Integer> userIds) {
		Map<Integer, String> found = names.findAllById(userIds).stream()
				.collect(Collectors.toMap(PlayerName::userId, PlayerName::name, (a, b) -> a));
		return userIds.stream().distinct()
				.collect(Collectors.toMap(Function.identity(), id -> found.getOrDefault(id, "Player " + id)));
	}
}
