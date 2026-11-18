package com.fazeel.quizcadia.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import com.fazeel.quizcadia.domain.Match;

/**
 * Private multiplayer rooms: the host gets a code like KING-4821, friends join with it, and the host starts the
 * match once at least two players are in. Rooms live in memory (like public lobbies) and close after
 * {@link #IDLE} without a start.
 * <p>
 * A party room is for a TV or classroom screen: the host's device shows the code, a QR code and the live game
 * but doesn't play, and up to {@link #PARTY_MAX_PLAYERS} phones join.
 * <p>
 * A quiz room is a party room for the live room quiz: no game (it asks {@link #QUIZ_QUESTIONS} random medium
 * questions from every topic), up to {@link #QUIZ_MAX_PLAYERS} phones, and one player is enough to start.
 */
public class PrivateRooms {

	public static final int MAX_PLAYERS = 8;
	public static final int PARTY_MAX_PLAYERS = 30;
	public static final int QUIZ_MAX_PLAYERS = 50;
	public static final int QUIZ_QUESTIONS = 20;
	public static final int QUIZ_SECONDS = 20;
	/** How long a quiz's answer shows when the host lets it run by itself. */
	public static final int QUIZ_AUTO_REVEAL_SECONDS = 8;
	static final Duration IDLE = Duration.ofMinutes(30);

	public enum State {
		WAITING, STARTED, CLOSED
	}

	public record Player(int userId, String name) {
	}

	/** @param quiz a live room quiz (always a party room) */
	public record RoomStatus(String code, int gameId, int hostId, List<Player> players, State state, String matchId,
			int maxPlayers, boolean party, boolean quiz) {
	}

	/** A room rule was broken; {@code notFound} means the code doesn't exist (any more). */
	public static class RoomException extends RuntimeException {
		private final boolean notFound;

		RoomException(String message, boolean notFound) {
			super(message);
			this.notFound = notFound;
		}

		public boolean isNotFound() {
			return notFound;
		}
	}

	private static final class Room {
		final String code;
		final int gameId;
		final int hostId;
		final boolean party;
		/** Null unless this is a live room quiz. */
		final Match.RoomQuiz quiz;
		final Map<Integer, String> players = new LinkedHashMap<>();
		Instant lastActivity;
		State state = State.WAITING;
		String matchId;

		Room(String code, int gameId, int hostId, boolean party, Match.RoomQuiz quiz, Instant now) {
			this.code = code;
			this.gameId = gameId;
			this.hostId = hostId;
			this.party = party || quiz != null;
			this.quiz = quiz;
			this.lastActivity = now;
		}
	}

	private final Clock clock;
	private final RandomGenerator random;
	private final Consumer<Match> onMatch;
	private final Map<String, Room> rooms = new HashMap<>();

	public PrivateRooms(Clock clock, RandomGenerator random, Consumer<Match> onMatch) {
		this.clock = clock;
		this.random = random;
		this.onMatch = onMatch;
	}

	public synchronized RoomStatus create(int gameId, int userId, String name) {
		return create(gameId, userId, name, false);
	}

	/** @param party true for a big-screen party: the host only shows the game and doesn't play */
	public synchronized RoomStatus create(int gameId, int userId, String name, boolean party) {
		return create(gameId, userId, name, party, null);
	}

	/**
	 * A live room quiz on the host's big screen: {@value #QUIZ_QUESTIONS} random medium questions from every topic.
	 *
	 * @param autoAdvance the next question opens by itself {@value #QUIZ_AUTO_REVEAL_SECONDS} s after each answer;
	 *        otherwise the host moves on
	 */
	public synchronized RoomStatus createQuiz(int userId, String name, boolean autoAdvance) {
		return create(0, userId, name, true,
				new Match.RoomQuiz(QUIZ_QUESTIONS, QUIZ_SECONDS, autoAdvance ? QUIZ_AUTO_REVEAL_SECONDS : 0));
	}

	private RoomStatus create(int gameId, int userId, String name, boolean party, Match.RoomQuiz quiz) {
		Instant now = clock.instant();
		expire(now);
		String code;
		do {
			code = "KING-" + (1000 + random.nextInt(9000));
		} while (rooms.containsKey(code));
		Room room = new Room(code, gameId, userId, party, quiz, now);
		if (!room.party) {
			room.players.put(userId, name);
		}
		rooms.put(code, room);
		return status(room);
	}

	public synchronized RoomStatus join(String code, int userId, String name) {
		Room room = find(code);
		if (room.players.containsKey(userId) || (room.party && room.hostId == userId)) {
			return status(room); // the party host is the screen, not a player
		}
		if (room.state != State.WAITING) {
			throw new RoomException("That room has already started", false);
		}
		int max = max(room);
		if (room.players.size() >= max) {
			throw new RoomException("That room is full (" + max + " players)", false);
		}
		room.players.put(userId, name);
		room.lastActivity = clock.instant();
		return status(room);
	}

	/** Anyone with the code may look (to see who's in before joining). */
	public synchronized RoomStatus status(String code) {
		return status(find(code));
	}

	public synchronized RoomStatus start(String code, int userId) {
		Room room = find(code);
		if (room.hostId != userId) {
			throw new RoomException("Only the host can start the match", false);
		}
		if (room.state == State.STARTED) {
			return status(room);
		}
		if (room.players.size() < (room.quiz != null ? 1 : 2)) {
			if (room.quiz != null) {
				throw new RoomException("Wait for someone to join", false);
			}
			throw new RoomException(room.party ? "Wait for at least two players to join" : "Wait for at least one friend to join", false);
		}
		Instant now = clock.instant();
		Match match = new Match(UUID.randomUUID().toString(), room.gameId, new ArrayList<>(room.players.keySet()), now,
				Map.copyOf(room.players), Map.of(), room.party ? room.hostId : null, room.quiz);
		room.state = State.STARTED;
		room.matchId = match.matchId();
		room.lastActivity = now;
		onMatch.accept(match);
		return status(room);
	}

	/** A player leaves; when the host leaves a waiting room, the room closes for everyone. */
	public synchronized void leave(String code, int userId) {
		Room room = rooms.get(normalize(code));
		if (room == null || room.state != State.WAITING) {
			return;
		}
		room.players.remove(userId);
		if (userId == room.hostId || room.players.isEmpty()) {
			room.state = State.CLOSED;
		}
		room.lastActivity = clock.instant();
	}

	public synchronized void tick() {
		expire(clock.instant());
	}

	private void expire(Instant now) {
		rooms.values().removeIf(r -> Duration.between(r.lastActivity, now).compareTo(IDLE) > 0);
	}

	private Room find(String code) {
		Room room = rooms.get(normalize(code));
		if (room == null) {
			throw new RoomException("No room with code " + code, true);
		}
		return room;
	}

	static String normalize(String code) {
		String c = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
		return c.matches("\\d{4}") ? "KING-" + c : c; // friends may type just the digits
	}

	private static int max(Room r) {
		return r.quiz != null ? QUIZ_MAX_PLAYERS : r.party ? PARTY_MAX_PLAYERS : MAX_PLAYERS;
	}

	private static RoomStatus status(Room r) {
		List<Player> players = r.players.entrySet().stream().map(e -> new Player(e.getKey(), e.getValue())).toList();
		return new RoomStatus(r.code, r.gameId, r.hostId, players, r.state, r.matchId, max(r), r.party, r.quiz != null);
	}
}
