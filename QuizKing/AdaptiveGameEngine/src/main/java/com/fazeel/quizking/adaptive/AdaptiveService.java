package com.fazeel.quizking.adaptive;

import java.time.Clock;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fazeel.quizking.adaptive.AdaptiveSession.Outcome;
import com.fazeel.quizking.adaptive.AdaptiveSession.Status;
import com.fazeel.quizking.adaptive.Ports.GameSource;
import com.fazeel.quizking.adaptive.Ports.ResultEvents;
import com.fazeel.quizking.adaptive.GameRepository;
import com.fazeel.quizking.adaptive.SeenQuestionRepository;
import com.fazeel.quizking.adaptive.SessionRepository;
import com.fazeel.quizking.domain.AdaptiveResult;
import com.fazeel.quizking.domain.Game;

@Service
public class AdaptiveService {

	private final SessionRepository sessions;
	private final GameRepository games;
	private final SeenQuestionRepository seen;
	private final GameSource gameSource;
	private final ResultEvents events;
	private final PoolBuilder poolBuilder;
	private final AdaptiveRules rules;
	private final Clock clock;

	public AdaptiveService(SessionRepository sessions, GameRepository games, SeenQuestionRepository seen,
			GameSource gameSource, ResultEvents events, PoolBuilder poolBuilder, AdaptiveRules rules, Clock clock) {
		this.sessions = sessions;
		this.games = games;
		this.seen = seen;
		this.gameSource = gameSource;
		this.events = events;
		this.poolBuilder = poolBuilder;
		this.rules = rules;
		this.clock = clock;
	}

	public void cacheGame(Game game) {
		games.save(game);
	}

	public void removeGame(int gameId) {
		games.deleteById(gameId);
	}

	public AdaptiveSession start(int gameId, int userId) {
		Game game = games.findById(gameId).or(() -> gameSource.fetch(gameId).map(games::save))
				.orElseThrow(() -> new NotFoundException("No game " + gameId));
		List<Integer> ids = game.questions() == null ? List.of()
				: game.questions().stream().filter(java.util.Objects::nonNull).map(Game.Question::questionId).toList();
		Map<Integer, Instant> lastSeen = seen.findByUserIdAndQuestionIdIn(userId, ids).stream()
				.collect(Collectors.toMap(SeenQuestion::questionId, SeenQuestion::lastSeenAt, (a, b) -> a));
		List<PooledQuestion> pool = poolBuilder.build(game, lastSeen, rules.totalQuestions());
		AdaptiveSession session = new AdaptiveSession(UUID.randomUUID().toString(), userId, gameId, game.gameName(),
				game.categoryId(), game.topic() == null ? 0 : game.topic().topicId(), pool, rules, clock.instant());
		return save(session);
	}

	public AdaptiveSession get(String sessionId) {
		AdaptiveSession s = load(sessionId);
		return s.expireOverdue(clock.instant()) ? save(s) : s;
	}

	public AnswerResult answer(String sessionId, int questionId, String answer) {
		AdaptiveSession s = load(sessionId);
		Outcome outcome = s.answer(questionId, answer, clock.instant());
		return new AnswerResult(outcome, save(s));
	}

	private AdaptiveSession load(String id) {
		return sessions.findById(id).orElseThrow(() -> new NotFoundException("No session " + id));
	}

	private AdaptiveSession save(AdaptiveSession s) {
		boolean publish = s.getStatus() == Status.COMPLETED && !s.isResultPublished();
		if (publish) {
			s.markResultPublished();
		}
		AdaptiveSession saved = sessions.save(s);
		saved.currentQuestion().ifPresent(
				q -> seen.save(SeenQuestion.of(saved.getUserId(), q.getQuestionId(), clock.instant())));
		if (publish) {
			events.sessionCompleted(toResult(saved));
		}
		return saved;
	}

	static AdaptiveResult toResult(AdaptiveSession s) {
		List<AdaptiveResult.Answer> answers = s.getAsked().stream()
				.map(q -> new AdaptiveResult.Answer(q.getQuestionId(), q.getLevel(), q.getStem(), q.getCorrectAnswer(),
						q.getAnswer(), q.isCorrect(), q.isTimedOut(), q.getPoints()))
				.toList();
		return new AdaptiveResult(s.getId(), s.getUserId(), s.getGameId(), s.getGameName(), s.getCategoryId(),
				s.getTopicId(), s.getScore(), s.getLevel(), s.getHighestLevel(), (int) s.correctCount(),
				s.getAsked().size(), s.getCompletedAt(), answers);
	}

	public record AnswerResult(Outcome outcome, AdaptiveSession session) {
	}
}
