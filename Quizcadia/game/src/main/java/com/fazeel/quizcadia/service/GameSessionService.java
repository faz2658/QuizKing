package com.fazeel.quizcadia.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameRequest;
import com.fazeel.quizcadia.domain.PlayedQuestion;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.repository.SeenQuestionRepository;
import com.fazeel.quizcadia.session.SeenQuestion;
import com.fazeel.quizcadia.domain.SinglePlayerResult;
import com.fazeel.quizcadia.kafka.GameEvents;
import com.fazeel.quizcadia.repository.GameRepository;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.session.AnswerOutcome;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.Lifeline;
import com.fazeel.quizcadia.session.QuizBuilder;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;

@Service
public class GameSessionService {

	private final GameRepository games;
	private final GameSessionRepository sessions;
	private final GameEvents events;
	private final SeenQuestionRepository seen;
	private final QuizBuilder quizBuilder;
	private final TopicPools pools;
	private final Clock clock;
	private final java.util.random.RandomGenerator random = new java.security.SecureRandom();

	public GameSessionService(GameRepository games, GameSessionRepository sessions, GameEvents events,
			SeenQuestionRepository seen, QuizBuilder quizBuilder, Clock clock) {
		this(games, sessions, events, seen, quizBuilder, clock, TopicPools.none());
	}

	@org.springframework.beans.factory.annotation.Autowired
	public GameSessionService(GameRepository games, GameSessionRepository sessions, GameEvents events,
			SeenQuestionRepository seen, QuizBuilder quizBuilder, Clock clock, TopicPools pools) {
		this.pools = pools;
		this.games = games;
		this.sessions = sessions;
		this.events = events;
		this.seen = seen;
		this.quizBuilder = quizBuilder;
		this.clock = clock;
	}

	public List<Game> availableGames() {
		return games.findAll();
	}

	public GameSession start(int gameId, int userId) {
		return start(gameId, userId, null);
	}

	public GameSession start(int gameId, int userId, String playerName) {
		return start(gameId, userId, playerName, null);
	}

	/** @param country the player's country (ISO code, may be null): most questions then come from it */
	public GameSession start(int gameId, int userId, String playerName, String country) {
		Game game = pools.withPool(games.findById(gameId).orElseThrow(() -> {
			events.requestGame(new GameRequest(gameId, userId));
			return new GameNotReadyException(gameId);
		}));
		List<Integer> poolIds = game.getQuestions() == null ? List.of()
				: game.getQuestions().stream().filter(java.util.Objects::nonNull).map(Questions::getQuestionId).toList();
		Map<Integer, Instant> lastSeen = seen.findByUserIdAndQuestionIdIn(userId, poolIds).stream()
				.collect(Collectors.toMap(SeenQuestion::questionId, SeenQuestion::lastSeenAt, (a, b) -> a));
		List<SessionQuestion> questions = quizBuilder.build(game, lastSeen, country);
		if (questions.isEmpty()) {
			throw new QuizRuleException("Game " + gameId + " has no playable questions");
		}
		Instant now = clock.instant();
		GameSession session = new GameSession(UUID.randomUUID().toString(), gameId, userId, game.getCategoryId(),
				game.getGameName(), questions, now).playedBy(playerName);
		seen.saveAll(questions.stream().map(q -> SeenQuestion.of(userId, q.getQuestionId(), now)).toList());
		return sessions.save(session);
	}

	/** Difficulty of a custom quiz; MIXED is about 40% easy, 30% medium and 30% advanced. */
	public enum CustomDifficulty {
		EASY, MEDIUM, ADVANCED, MIXED
	}

	static final int CUSTOM_MIN = 5;
	static final int CUSTOM_MAX = 20;
	static final int CUSTOM_MAX_TOPICS = 8;

	/**
	 * A quiz from several games' pools at once. The usual rules apply: unseen questions first, easy before hard,
	 * a level without enough questions borrows from the nearest one.
	 */
	public GameSession startCustom(List<Integer> gameIds, CustomDifficulty difficulty, int count, int userId,
			String playerName) {
		return startCustom(gameIds, difficulty, count, userId, playerName, null);
	}

	/** @param country the player's country (ISO code, may be null): most questions then come from it */
	public GameSession startCustom(List<Integer> gameIds, CustomDifficulty difficulty, int count, int userId,
			String playerName, String country) {
		List<Integer> ids = gameIds == null ? List.of() : gameIds.stream().distinct().toList();
		if (ids.isEmpty() || ids.size() > CUSTOM_MAX_TOPICS) {
			throw new QuizRuleException("Pick between 1 and " + CUSTOM_MAX_TOPICS + " topics");
		}
		if (count < CUSTOM_MIN || count > CUSTOM_MAX) {
			throw new QuizRuleException("A custom quiz has " + CUSTOM_MIN + " to " + CUSTOM_MAX + " questions");
		}
		List<Game> picked = new java.util.ArrayList<>();
		for (int id : ids) {
			picked.add(pools.withPool(games.findById(id).orElseThrow(() -> {
				events.requestGame(new GameRequest(id, userId));
				return new GameNotReadyException(id);
			})));
		}
		Map<Integer, Questions> pool = new java.util.LinkedHashMap<>();
		picked.forEach(g -> java.util.Objects.requireNonNullElse(g.getQuestions(), List.<Questions>of()).stream()
				.filter(java.util.Objects::nonNull).forEach(q -> pool.putIfAbsent(q.getQuestionId(), q)));
		int easy = switch (difficulty) {
			case EASY -> count;
			case MIXED -> Math.round(count * 0.4f);
			default -> 0;
		};
		int medium = switch (difficulty) {
			case MEDIUM -> count;
			case MIXED -> Math.round(count * 0.3f);
			default -> 0;
		};
		int advanced = count - easy - medium;
		Game custom = new Game();
		custom.setGameName(customName(picked));
		custom.setQuestions(List.copyOf(pool.values()));
		custom.setQuestionLevels(new com.fazeel.quizcadia.domain.GameQuestionLevel(0, easy, medium, advanced));
		Map<Integer, Instant> lastSeen = seen.findByUserIdAndQuestionIdIn(userId, List.copyOf(pool.keySet())).stream()
				.collect(Collectors.toMap(SeenQuestion::questionId, SeenQuestion::lastSeenAt, (a, b) -> a));
		List<SessionQuestion> questions = quizBuilder.build(custom, lastSeen, country);
		if (questions.isEmpty()) {
			throw new QuizRuleException("Those topics have no playable questions yet");
		}
		Instant now = clock.instant();
		GameSession session = new GameSession(UUID.randomUUID().toString(), 0, userId, 0, custom.getGameName(), questions,
				now).asVariant(com.fazeel.quizcadia.session.SessionVariant.CUSTOM, null, null).playedBy(playerName);
		seen.saveAll(questions.stream().map(q -> SeenQuestion.of(userId, q.getQuestionId(), now)).toList());
		return sessions.save(session);
	}

	static String customName(List<Game> games) {
		List<String> names = games.stream().map(g -> g.getTopic() != null && g.getTopic().getTopicName() != null
				? g.getTopic().getTopicName() : g.getGameName()).distinct().toList();
		String shown = String.join(", ", names.subList(0, Math.min(3, names.size())));
		return "Custom quiz: " + shown + (names.size() > 3 ? " +" + (names.size() - 3) : "");
	}

	/** Returns the session with any timed-out questions already skipped. */
	public GameSession get(String sessionId) {
		GameSession session = load(sessionId);
		if (!session.expireOverdueQuestions(clock.instant()).isEmpty()) {
			session = save(session);
		}
		return session;
	}

	public AnswerResult answer(String sessionId, int questionId, String answer) {
		GameSession session = load(sessionId);
		AnswerOutcome outcome = session.answer(questionId, answer, clock.instant());
		return new AnswerResult(outcome, save(session));
	}

	/** Uses a lifeline on the current question; a skip also returns the skipped question's outcome. */
	public LifelineResult useLifeline(String sessionId, Lifeline lifeline, int questionId) {
		GameSession session = load(sessionId);
		Optional<AnswerOutcome> skipped = session.useLifeline(lifeline, questionId, clock.instant(), random);
		return new LifelineResult(skipped.orElse(null), save(session));
	}

	public GameSession result(String sessionId) {
		GameSession session = get(sessionId);
		if (session.getStatus() != SessionStatus.COMPLETED) {
			throw new QuizRuleException("The game is still in progress");
		}
		return session;
	}

	private GameSession load(String sessionId) {
		return sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("No session " + sessionId));
	}

	/** Saves the session and publishes its result exactly once, the first time it is seen completed. */
	private GameSession save(GameSession session) {
		boolean publish = session.getStatus() == SessionStatus.COMPLETED && !session.isResultPublished();
		if (publish) {
			session.markResultPublished();
		}
		GameSession saved = sessions.save(session);
		if (publish) {
			events.publishResult(toResult(saved));
		}
		return saved;
	}

	static SinglePlayerResult toResult(GameSession s) {
		List<PlayedQuestion> played = s.getQuestions().stream()
				.map(q -> new PlayedQuestion(q.getQuestionId(), q.getDifficulty().level(), q.getStem(),
						q.getCorrectAnswer(), q.getAnswer(), q.isCorrect(), q.isTimedOut(), q.getPointsAwarded()))
				.toList();
		return new SinglePlayerResult(s.getId(), s.getGameId(), s.getGameName(), s.getCategoryId(), s.getUserId(),
				s.getScore(), s.maxScore(), s.percentage(), (int) s.correctCount(), s.getQuestions().size(),
				s.getCompletedAt(), played, s.getVariant().name(), s.getVariantKey(), s.getOpponentScore(),
				s.getPlayerName());
	}

	public record AnswerResult(AnswerOutcome outcome, GameSession session) {
	}

	public record LifelineResult(AnswerOutcome skipped, GameSession session) {
	}
}
