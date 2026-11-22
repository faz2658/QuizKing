package com.fazeel.quizcadia.match;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.domain.MultiPlayerResult;
import com.fazeel.quizcadia.match.MultiplayerMatch.AnswerOutcome;
import com.fazeel.quizcadia.match.MultiplayerMatch.Status;

/**
 * Starts matches from the player matcher's events, serves them to players and screens, records answers, and
 * publishes the result once a match completes.
 */
@Service
public class MatchService {

	private static final Logger log = LoggerFactory.getLogger(MatchService.class);

	private final MatchRepository matches;
	private final GameRepository games;
	private final GameSource gameSource;
	private final MatchFactory factory;
	private final MatchEvents events;
	private final SeenQuestionRepository seen;
	private final Clock clock;
	private final MatchUpdates updates;
	private final TopicPools pools;
	private final RoomQuizQuestions quizQuestions;

	public MatchService(MatchRepository matches, GameRepository games, GameSource gameSource, MatchFactory factory,
			MatchEvents events, SeenQuestionRepository seen, Clock clock) {
		this(matches, games, gameSource, factory, events, seen, clock, new MatchUpdates(), TopicPools.none());
	}

	public MatchService(MatchRepository matches, GameRepository games, GameSource gameSource, MatchFactory factory,
			MatchEvents events, SeenQuestionRepository seen, Clock clock, MatchUpdates updates) {
		this(matches, games, gameSource, factory, events, seen, clock, updates, TopicPools.none());
	}

	public MatchService(MatchRepository matches, GameRepository games, GameSource gameSource, MatchFactory factory,
			MatchEvents events, SeenQuestionRepository seen, Clock clock, MatchUpdates updates, TopicPools pools) {
		this(matches, games, gameSource, factory, events, seen, clock, updates, pools, RoomQuizQuestions.none());
	}

	@Autowired
	public MatchService(MatchRepository matches, GameRepository games, GameSource gameSource, MatchFactory factory,
			MatchEvents events, SeenQuestionRepository seen, Clock clock, MatchUpdates updates, TopicPools pools,
			RoomQuizQuestions quizQuestions) {
		this.quizQuestions = quizQuestions;
		this.pools = pools;
		this.updates = updates;
		this.matches = matches;
		this.games = games;
		this.gameSource = gameSource;
		this.factory = factory;
		this.events = events;
		this.seen = seen;
		this.clock = clock;
	}

	/** Keeps a copy of a multiplayer game the game manager published. */
	public void cacheGame(MultiPlayerGame game) {
		games.save(game);
	}

	/** Forgets a deleted game; running matches keep their questions. */
	public void removeGame(int gameId) {
		games.deleteById(gameId);
	}

	/** Starts the match for players found by the player matcher. Replayed events are ignored. */
	public MultiplayerMatch start(Match match) {
		if (match.quiz() != null) {
			return startRoomQuiz(match);
		}
		return matches.findById(match.matchId()).orElseGet(() -> {
			MultiPlayerGame game = pools.withPool(games.findById(match.gameId())
					.or(() -> gameSource.fetch(match.gameId()).map(games::save))
					.orElseThrow(() -> new NotFoundException("No multiplayer game " + match.gameId())));
			List<Integer> ids = game.autoquestions() == null ? List.of()
					: game.autoquestions().stream().filter(Objects::nonNull)
							.map(MultiPlayerGame.Question::questionId).toList();
			Map<Integer, Integer> seenBy = new HashMap<>();
			seen.findByUserIdInAndQuestionIdIn(match.players(), ids)
					.forEach(s -> seenBy.merge(s.questionId(), 1, Integer::sum));
			Instant now = clock.instant();
			MultiplayerMatch created = factory.create(match, game, now, seenBy);
			seen.saveAll(match.players().stream().filter(p -> p > 0).flatMap(p -> created.getQuestions().stream()
					.map(q -> SeenQuestion.of(p, q.getQuestionId(), now))).toList());
			log.info("Match {} started: game {} with {}", match.matchId(), match.gameId(), match.players());
			return matches.save(created);
		});
	}

	/** A live room quiz: random medium questions from every topic, the same for everyone, in the same order. */
	private MultiplayerMatch startRoomQuiz(Match match) {
		return matches.findById(match.matchId()).orElseGet(() -> {
			List<MultiPlayerGame.Question> pool = quizQuestions.medium(match.quiz().questions());
			if (pool.isEmpty()) {
				throw new NotFoundException("No questions for room quiz " + match.matchId());
			}
			MultiplayerMatch created = factory.roomQuiz(match, pool, clock.instant());
			log.info("Room quiz {} started: {} questions for {}", match.matchId(), created.getQuestions().size(),
					match.players());
			return matches.save(created);
		});
	}

	/** The host's big screen paces a room quiz (reveal the answer, next question, end). */
	public MultiplayerMatch host(String matchId, int userId, MultiplayerMatch.HostAction action) {
		return retrying(() -> {
			MultiplayerMatch match = load(matchId);
			return match.host(userId, action, clock.instant()) ? save(match) : match;
		});
	}

	/** Tries this many times when another save of the same match got in first (a room full of phones answering). */
	static final int ATTEMPTS = 6;

	/** Runs a load-change-save again when another request saved the match in between. */
	private static <T> T retrying(Supplier<T> work) {
		for (int attempt = 1;; attempt++) {
			try {
				return work.get();
			}
			catch (OptimisticLockingFailureException e) {
				if (attempt >= ATTEMPTS) {
					throw e;
				}
				try {
					Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20L * attempt));
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	/** The match for a player or the party screen, with questions whose time ran out closed first (and saved). */
	public MultiplayerMatch get(String matchId, int userId) {
		return retrying(() -> {
			MultiplayerMatch match = load(matchId);
			if (!match.isScreen(userId)) {
				requirePlayer(match, userId);
			}
			if (match.advanceClock(clock.instant())) {
				match = save(match);
			}
			return match;
		});
	}

	/** Records a player's answer; retried when another answer was saved at the same moment. */
	public AnswerResult answer(String matchId, int userId, int questionId, String answer) {
		return retrying(() -> {
			MultiplayerMatch match = load(matchId);
			requirePlayer(match, userId);
			AnswerOutcome outcome = match.answer(userId, questionId, answer, clock.instant());
			return new AnswerResult(outcome, save(match));
		});
	}

	private void requirePlayer(MultiplayerMatch match, int userId) {
		if (!match.hasPlayer(userId)) {
			throw new MatchRuleException("You are not in this match");
		}
	}

	private MultiplayerMatch load(String matchId) {
		return matches.findById(matchId).orElseThrow(() -> new NotFoundException("No match " + matchId));
	}

	private MultiplayerMatch save(MultiplayerMatch match) {
		boolean publish = match.getStatus() == Status.COMPLETED && !match.isResultPublished();
		if (publish) {
			match.markResultPublished();
			// room quizzes belong to no game, so there is nothing to report or recommend from them (yet)
			publish = !match.isRoomQuiz();
		}
		MultiplayerMatch saved = matches.save(match);
		if (publish) {
			events.matchCompleted(toResult(saved));
		}
		updates.changed(saved.getMatchId()); // answer anyone waiting for this match (see MatchController#next)
		return saved;
	}

	static MultiPlayerResult toResult(MultiplayerMatch m) {
		List<MultiPlayerResult.Standing> standings = new ArrayList<>();
		List<PlayerScore> ordered = m.standings();
		for (int i = 0; i < ordered.size(); i++) {
			PlayerScore p = ordered.get(i);
			if (p.isBot()) {
				continue; // ranks count the bot, but bots get no reports or recommendations
			}
			standings.add(new MultiPlayerResult.Standing(i + 1, p.getUserId(), p.getScore(), p.getQuestionsWon(),
					p.getWrongAnswers()));
		}
		List<MultiPlayerResult.QuestionResult> questions = m.getQuestions().stream()
				.map(q -> new MultiPlayerResult.QuestionResult(q.getQuestionId(), q.getStem(), q.getCorrectAnswer(),
						q.getWinnerUserId(),
						q.getAnswers().stream().map(a -> new MultiPlayerResult.PlayerAnswer(a.userId(), a.answer(),
								a.correct(), Duration.between(q.getOpenedAt(), a.answeredAt()).toMillis())).toList()))
				.toList();
		return new MultiPlayerResult(m.getMatchId(), m.getGameId(), m.getGameName(), m.getCategoryId(),
				m.getCompletedAt(), standings, questions);
	}

	/** What answering returned: the outcome and the match after it. */
	public record AnswerResult(AnswerOutcome outcome, MultiplayerMatch match) {
	}
}
