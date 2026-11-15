package com.fazeel.maverick.report;

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
		names.save(new PlayerName(userId, trimmed, clock.instant()));
	}

	public Map<Integer, String> lookup(Collection<Integer> userIds) {
		Map<Integer, String> found = names.findAllById(userIds).stream()
				.collect(Collectors.toMap(PlayerName::userId, PlayerName::name, (a, b) -> a));
		return userIds.stream().distinct()
				.collect(Collectors.toMap(Function.identity(), id -> found.getOrDefault(id, "Player " + id)));
	}
}
