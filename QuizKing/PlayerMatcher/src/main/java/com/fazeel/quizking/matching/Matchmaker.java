package com.fazeel.quizking.matching;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.fazeel.quizking.domain.Match;
import com.fazeel.quizking.matching.LobbyStatus.State;

/**
 * Groups players who want to play the same game. There is one lobby per game: players for game A
 * never end up in a match for game B (the original matcher used a single shared set for all games).
 * All methods are synchronized; the expensive part (publishing the match) is a quick handoff.
 */
public class Matchmaker {

	private final MatchmakingRules rules;
	private final Clock clock;
	private final Consumer<Match> onMatch;

	private final Map<Integer, Lobby> lobbies = new HashMap<>();
	/** Last outcome per player, so a polling client can learn its match id or that it expired. */
	private final Map<Integer, LobbyStatus> outcomes = new HashMap<>();

	public Matchmaker(MatchmakingRules rules, Clock clock, Consumer<Match> onMatch) {
		this.rules = rules;
		this.clock = clock;
		this.onMatch = onMatch;
	}

	public synchronized LobbyStatus join(int gameId, int userId) {
		Instant now = clock.instant();
		closeDueLobbies(now);
		// A player can only wait for one game at a time.
		lobbies.values().removeIf(l -> l.gameId() != gameId && l.leave(userId) && l.isEmpty());
		outcomes.remove(userId);

		Lobby lobby = lobbies.computeIfAbsent(gameId, id -> new Lobby(id, now));
		lobby.join(userId);
		if (lobby.isFull(rules.maxPlayers())) {
			return close(lobby, now).get(userId);
		}
		return waiting(lobby);
	}

	public synchronized LobbyStatus status(int gameId, int userId) {
		closeDueLobbies(clock.instant());
		Lobby lobby = lobbies.get(gameId);
		if (lobby != null && lobby.contains(userId)) {
			return waiting(lobby);
		}
		LobbyStatus outcome = outcomes.get(userId);
		if (outcome != null && outcome.gameId() == gameId) {
			return outcome;
		}
		return new LobbyStatus(State.EXPIRED, gameId, List.of(), null, null);
	}

	public synchronized void leave(int gameId, int userId) {
		Lobby lobby = lobbies.get(gameId);
		if (lobby != null && lobby.leave(userId) && lobby.isEmpty()) {
			lobbies.remove(gameId);
		}
	}

	/** Called periodically: starts or abandons lobbies whose waiting time is over. */
	public synchronized void tick() {
		closeDueLobbies(clock.instant());
	}

	private void closeDueLobbies(Instant now) {
		for (Iterator<Lobby> it = lobbies.values().iterator(); it.hasNext();) {
			Lobby lobby = it.next();
			if (!lobby.waitedLongEnough(now, rules.waitTime())) {
				continue;
			}
			it.remove();
			if (lobby.hasEnough(rules.minPlayers())) {
				startMatch(lobby, now);
			} else {
				for (int p : lobby.players()) {
					outcomes.put(p, new LobbyStatus(State.EXPIRED, lobby.gameId(), lobby.players(), null, null));
				}
			}
		}
	}

	private Map<Integer, LobbyStatus> close(Lobby lobby, Instant now) {
		lobbies.remove(lobby.gameId());
		startMatch(lobby, now);
		return outcomes;
	}

	private void startMatch(Lobby lobby, Instant now) {
		Match match = new Match(UUID.randomUUID().toString(), lobby.gameId(), lobby.players(), now);
		for (int p : match.players()) {
			outcomes.put(p, new LobbyStatus(State.MATCHED, match.gameId(), match.players(), now, match.matchId()));
		}
		onMatch.accept(match);
	}

	/** Open lobbies, so players can join one that already has people waiting. */
	public synchronized List<OpenLobby> openLobbies() {
		closeDueLobbies(clock.instant());
		return lobbies.values().stream().map(l -> new OpenLobby(l.gameId(), l.players().size(), l.closesAt(rules.waitTime())))
				.sorted(java.util.Comparator.comparingInt(OpenLobby::players).reversed()).toList();
	}

	public record OpenLobby(int gameId, int players, Instant closesAt) {
	}

	private LobbyStatus waiting(Lobby lobby) {
		return new LobbyStatus(State.WAITING, lobby.gameId(), lobby.players(), lobby.closesAt(rules.waitTime()), null);
	}
}
