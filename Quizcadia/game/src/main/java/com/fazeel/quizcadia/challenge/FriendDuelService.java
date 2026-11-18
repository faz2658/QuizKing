package com.fazeel.quizcadia.challenge;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.challenge.FriendDuel.Side;
import com.fazeel.quizcadia.challenge.FriendDuel.Status;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.repository.SeenQuestionRepository;
import com.fazeel.quizcadia.service.GameSessionService;
import com.fazeel.quizcadia.service.NotFoundException;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizBuilder;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SeenQuestion;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;
import com.fazeel.quizcadia.session.SessionVariant;

/**
 * Challenges between two friends, played asynchronously.
 * <ol>
 * <li>A signed-in player picks a game, or its International version, and gets a link to send.</li>
 * <li>The first other player who opens the link accepts or declines it within {@link #ANSWER_WITHIN}.</li>
 * <li>On accepting, the questions are drawn once for both: from the players' own country (about 75%) and general
 * questions when they share a country, general questions only when their countries differ or one is unknown,
 * and every country's questions alike for International. Nobody is told which.</li>
 * <li>Both then play exactly those questions, in the same order with the same options, within
 * {@link #PLAY_WITHIN}. The higher score wins; if only one played in time, they win.</li>
 * </ol>
 */
@Service
public class FriendDuelService {

	/** How long a challenge waits for a friend to accept it. */
	static final Duration ANSWER_WITHIN = Duration.ofDays(3);
	/** How long both players have to play once it is accepted. */
	static final Duration PLAY_WITHIN = Duration.ofDays(7);
	static final int LIST_LIMIT = 20;

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no look-alikes
	private final SecureRandom random = new SecureRandom();

	private final FriendDuelRepository duels;
	private final GameSessionService games;
	private final GameSessionRepository sessions;
	private final SeenQuestionRepository seen;
	private final QuizBuilder quizBuilder;
	private final Clock clock;

	public FriendDuelService(FriendDuelRepository duels, GameSessionService games, GameSessionRepository sessions,
			SeenQuestionRepository seen, QuizBuilder quizBuilder, Clock clock) {
		this.duels = duels;
		this.games = games;
		this.sessions = sessions;
		this.seen = seen;
		this.quizBuilder = quizBuilder;
		this.clock = clock;
	}

	/** Where a challenge stands, worked out when asked (nothing runs in the background). */
	public enum State {
		/** Waiting for a friend to accept. */
		WAITING,
		DECLINED,
		/** Nobody accepted in time, or nobody played in time. */
		EXPIRED,
		/** Accepted; at least one player still has to finish. */
		PLAYING,
		/** Both played, or the time is up and one of them did. */
		FINISHED
	}

	/** One player's attempt: {@code session} is null until they start. */
	public record Attempt(Side side, GameSession session) {

		public boolean started() {
			return session != null;
		}

		public boolean completed() {
			return session != null && session.getStatus() == SessionStatus.COMPLETED;
		}
	}

	/**
	 * A challenge with both attempts. {@code winnerUserId} is set once it is FINISHED, unless it is a tie;
	 * {@code opponent} is null while nobody has accepted or declined.
	 */
	public record Standing(FriendDuel duel, State state, Attempt challenger, Attempt opponent, Integer winnerUserId,
			boolean tie) {

		public Attempt of(int userId) {
			return duel.isChallenger(userId) ? challenger : duel.isOpponent(userId) ? opponent : null;
		}

		public Attempt against(int userId) {
			return duel.isChallenger(userId) ? opponent : duel.isOpponent(userId) ? challenger : null;
		}
	}

	public FriendDuel create(int userId, String name, int gameId, boolean international, String country) {
		Game game = games.loadGame(gameId, userId);
		boolean playable = Objects.requireNonNullElse(game.getQuestions(), List.<Questions>of()).stream()
				.anyMatch(QuizBuilder::isPlayable);
		if (!playable) {
			throw new QuizRuleException("That game has no questions yet: pick another one");
		}
		Instant now = clock.instant();
		FriendDuel duel = new FriendDuel(newId(), null, gameId, game.getGameName(), game.getCategoryId(), international,
				new Side(userId, name, country(country), null), null, Status.PENDING, List.of(), now, null,
				now.plus(ANSWER_WITHIN));
		return duels.save(duel);
	}

	public FriendDuel get(String id) {
		return duels.findById(id).orElseThrow(() -> new NotFoundException("No challenge " + id));
	}

	/**
	 * Accepts the challenge and draws its questions for both players. Accepting again is harmless.
	 *
	 * @param country the accepting player's country (ISO code, may be null)
	 */
	public FriendDuel accept(String id, int userId, String name, String country) {
		FriendDuel duel = get(id);
		if (duel.isChallenger(userId)) {
			throw new QuizRuleException("That's your own challenge: send the link to a friend");
		}
		if (duel.status() == Status.ACCEPTED && duel.isOpponent(userId)) {
			return duel;
		}
		requireOpen(duel);
		Game game = games.loadGame(duel.gameId(), userId);
		String questionsFrom = duel.international() ? QuizBuilder.EVERYWHERE
				: QuizBuilder.sharedCountry(java.util.Arrays.asList(duel.challenger().country(), country(country)));
		List<SessionQuestion> questions = quizBuilder.build(game, seenByEither(game, duel.challenger().userId(), userId),
				questionsFrom);
		if (questions.isEmpty()) {
			throw new QuizRuleException("That game has no playable questions right now");
		}
		Instant now = clock.instant();
		return duels.save(duel.acceptedBy(new Side(userId, name, country(country), null), questions, now,
				now.plus(PLAY_WITHIN)));
	}

	public FriendDuel decline(String id, int userId, String name) {
		FriendDuel duel = get(id);
		if (duel.isChallenger(userId)) {
			throw new QuizRuleException("You can't decline your own challenge");
		}
		if (duel.status() == Status.DECLINED && duel.isOpponent(userId)) {
			return duel;
		}
		requireOpen(duel);
		return duels.save(duel.declinedBy(new Side(userId, name, null, null)));
	}

	/** Starts the player's attempt, or returns it to carry on. Each player plays once. */
	public GameSession play(String id, int userId, String name) {
		FriendDuel duel = get(id);
		Side side = duel.sideOf(userId);
		if (side == null) {
			throw new QuizRuleException("This challenge is between two other players");
		}
		if (duel.status() != Status.ACCEPTED) {
			throw new QuizRuleException(duel.status() == Status.DECLINED ? "This challenge was declined"
					: "Your friend hasn't accepted yet");
		}
		if (side.sessionId() != null) {
			GameSession session = games.get(side.sessionId());
			if (session.getStatus() == SessionStatus.COMPLETED) {
				throw new QuizRuleException("You've already played this challenge");
			}
			return session;
		}
		Instant now = clock.instant();
		if (!now.isBefore(duel.expiresAt())) {
			throw new QuizRuleException("This challenge has expired");
		}
		List<SessionQuestion> questions = duel.questions().stream().map(SessionQuestion::fresh).toList();
		GameSession session = new GameSession(UUID.randomUUID().toString(), duel.gameId(), userId, duel.categoryId(),
				duel.gameName(), questions, now).asVariant(SessionVariant.DUEL, duel.id(), null).playedBy(name);
		seen.saveAll(questions.stream().map(q -> SeenQuestion.of(userId, q.getQuestionId(), now)).toList());
		GameSession saved = sessions.save(session);
		duels.save(duel.withSession(userId, saved.getId()));
		return saved;
	}

	/** The player's challenges, sent and received, newest first. */
	public List<FriendDuel> mine(int userId) {
		return duels.findByChallengerUserIdOrOpponentUserIdOrderByCreatedAtDesc(userId, userId,
				PageRequest.of(0, LIST_LIMIT));
	}

	public Standing standing(FriendDuel duel) {
		Attempt challenger = attempt(duel.challenger());
		Attempt opponent = duel.opponent() == null ? null : attempt(duel.opponent()); // also whoever declined
		boolean over = !clock.instant().isBefore(duel.expiresAt());
		State state;
		switch (duel.status()) {
			case DECLINED -> state = State.DECLINED;
			case PENDING -> state = over ? State.EXPIRED : State.WAITING;
			default -> {
				boolean bothDone = challenger.completed() && opponent.completed();
				boolean someonePlaying = challenger.started() && !challenger.completed()
						|| opponent.started() && !opponent.completed();
				if (bothDone) {
					state = State.FINISHED;
				} else if (!over || someonePlaying) {
					state = State.PLAYING; // a game already under way at the deadline still counts
				} else {
					state = challenger.completed() || opponent.completed() ? State.FINISHED : State.EXPIRED;
				}
			}
		}
		Integer winner = null;
		boolean tie = false;
		if (state == State.FINISHED) {
			int a = challenger.completed() ? challenger.session().getScore() : -1;
			int b = opponent.completed() ? opponent.session().getScore() : -1;
			tie = a == b;
			winner = tie ? null : a > b ? duel.challenger().userId() : duel.opponent().userId();
		}
		return new Standing(duel, state, challenger, opponent, winner, tie);
	}

	private Attempt attempt(Side side) {
		return new Attempt(side, side == null || side.sessionId() == null ? null : games.get(side.sessionId()));
	}

	private void requireOpen(FriendDuel duel) {
		if (duel.status() == Status.DECLINED) {
			throw new QuizRuleException("This challenge was declined");
		}
		if (duel.status() == Status.ACCEPTED) {
			throw new QuizRuleException("Someone else already accepted this challenge");
		}
		if (!clock.instant().isBefore(duel.expiresAt())) {
			throw new QuizRuleException("This challenge has expired");
		}
	}

	/** When either player last saw each of the game's questions, so questions new to both come first. */
	private Map<Integer, Instant> seenByEither(Game game, int... userIds) {
		Collection<Integer> pool = Objects.requireNonNullElse(game.getQuestions(), List.<Questions>of()).stream()
				.filter(Objects::nonNull).map(Questions::getQuestionId).toList();
		Map<Integer, Instant> lastSeen = new HashMap<>();
		List<SeenQuestion> sightings = new ArrayList<>();
		for (int userId : userIds) {
			sightings.addAll(seen.findByUserIdAndQuestionIdIn(userId, pool));
		}
		sightings.forEach(s -> lastSeen.merge(s.questionId(), s.lastSeenAt(), (x, y) -> x.isAfter(y) ? x : y));
		return lastSeen;
	}

	private static String country(String country) {
		return country == null || !country.trim().matches("[A-Za-z]{2}") ? null
				: country.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private String newId() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
