package com.fazeel.quizcadia.daily;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.repository.GameRepository;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.session.Difficulty;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizBuilder;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;
import com.fazeel.quizcadia.session.SessionVariant;

/**
 * The Daily Challenge: {@value #EASY} easy, {@value #MEDIUM} medium and {@value #HARD} advanced questions drawn
 * from as many different topics as possible. The pick is seeded by the date (UTC), so it is the same for everyone,
 * and it is stored the first time it is requested so later changes to the games cannot alter it. Each player
 * gets one attempt per day.
 */
@Service
public class DailyChallengeService {

	static final int EASY = 4;
	static final int MEDIUM = 3;
	static final int HARD = 3;
	public static final String NAME = "Daily Challenge";

	private final GameRepository games;
	private final DailyChallengeRepository challenges;
	private final GameSessionRepository sessions;
	private final Clock clock;

	public DailyChallengeService(GameRepository games, DailyChallengeRepository challenges,
			GameSessionRepository sessions, Clock clock) {
		this.games = games;
		this.challenges = challenges;
		this.sessions = sessions;
		this.clock = clock;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(ZoneOffset.UTC));
	}

	/** Time until the next challenge (midnight UTC). */
	public Duration untilNext() {
		Instant next = today().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		return Duration.between(clock.instant(), next);
	}

	public DailyChallenge challenge(LocalDate date) {
		String key = date.toString();
		return challenges.findById(key).orElseGet(() -> {
			DailyChallenge built = build(date, games.findAll());
			try {
				return challenges.insert(built);
			}
			catch (DuplicateKeyException raced) {
				return challenges.findById(key).orElseThrow(); // another request stored it first
			}
		});
	}

	/** The player's run of today's challenge, if they started one. */
	public Optional<GameSession> todaysRun(int userId) {
		return sessions.findFirstByUserIdAndVariantAndVariantKey(userId, SessionVariant.DAILY, today().toString());
	}

	/** Starts today's challenge, or returns the run already in progress; a finished run cannot be replayed. */
	public GameSession start(int userId, String playerName) {
		Optional<GameSession> existing = todaysRun(userId);
		if (existing.isPresent()) {
			if (existing.get().getStatus() == SessionStatus.COMPLETED) {
				throw new QuizRuleException("You've already played today's challenge. A new one starts at midnight UTC.");
			}
			return existing.get();
		}
		LocalDate date = today();
		DailyChallenge challenge = challenge(date);
		if (challenge.questions().isEmpty()) {
			throw new QuizRuleException("There are no questions for today's challenge yet");
		}
		List<SessionQuestion> questions = challenge.questions().stream().map(SessionQuestion::fresh).toList();
		GameSession session = new GameSession(UUID.randomUUID().toString(), 0, userId, 0, NAME, questions,
				clock.instant()).asVariant(SessionVariant.DAILY, date.toString(), null).playedBy(playerName);
		return sessions.save(session);
	}

	/** Picks the day's questions: seeded by the date, one topic at a time so the mix is as varied as possible. */
	static DailyChallenge build(LocalDate date, List<Game> games) {
		Picked p = pick(date.toEpochDay() * 7919L + 17, games, EASY, MEDIUM, HARD);
		return new DailyChallenge(date.toString(), p.questions(), p.topics(), Instant.now());
	}

	/** Questions and their topics, as picked by {@link #pick}. */
	public record Picked(List<SessionQuestion> questions, List<String> topics) {
	}

	/**
	 * Picks {@code easy}, {@code medium} and {@code hard} questions from every game, one topic at a time so the mix
	 * is as varied as possible. The same seed and games always give the same questions and option order.
	 */
	public static Picked pick(long seed, List<Game> games, int easy, int medium, int hard) {
		Random random = new Random(seed);
		QuizBuilder builder = new QuizBuilder(random);
		// question id -> (question, its game); a question shared by several games counts once
		Map<Difficulty, Map<String, List<Pick>>> byLevelAndTopic = new EnumMap<>(Difficulty.class);
		for (Difficulty d : Difficulty.values()) {
			byLevelAndTopic.put(d, new LinkedHashMap<>());
		}
		Set<Integer> seenIds = new HashSet<>();
		List<Game> sorted = new ArrayList<>(games);
		sorted.sort(Comparator.comparingInt(Game::getGameId)); // stable input order, so the seed decides
		for (Game g : sorted) {
			String topic = g.getTopic() == null || g.getTopic().getTopicName() == null ? g.getGameName()
					: g.getTopic().getTopicName();
			for (Questions q : g.getQuestions() == null ? List.<Questions>of() : g.getQuestions()) {
				if (QuizBuilder.isPlayable(q) && seenIds.add(q.getQuestionId())) {
					byLevelAndTopic.get(Difficulty.fromLevel(q.getQuestionLevel()))
							.computeIfAbsent(topic, t -> new ArrayList<>()).add(new Pick(q, g, topic));
				}
			}
		}
		Map<Difficulty, Integer> wanted = Map.of(Difficulty.EASY, easy, Difficulty.MEDIUM, medium,
				Difficulty.ADVANCED, hard);
		List<SessionQuestion> questions = new ArrayList<>();
		List<String> topics = new ArrayList<>();
		Set<String> usedTopics = new HashSet<>();
		for (Difficulty d : Difficulty.values()) {
			Map<String, List<Pick>> pool = byLevelAndTopic.get(d);
			List<String> topicOrder = new ArrayList<>(pool.keySet());
			java.util.Collections.shuffle(topicOrder, random);
			// unused topics first, so ten questions cover up to ten topics
			topicOrder.sort(Comparator.comparing(usedTopics::contains));
			int need = wanted.get(d);
			int round = 0;
			while (need > 0 && !topicOrder.isEmpty() && round < 50) {
				for (String topic : topicOrder) {
					List<Pick> candidates = pool.get(topic);
					if (need == 0 || candidates.isEmpty()) {
						continue;
					}
					Pick pick = candidates.remove(random.nextInt(candidates.size()));
					questions.add(builder.toSessionQuestion(pick.question(), null));
					topics.add(topic);
					usedTopics.add(topic);
					need--;
				}
				round++;
			}
		}
		return new Picked(questions, topics);
	}

	private record Pick(Questions question, Game game, String topic) {
	}
}
