package com.stackroute.maverick.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.GameRequest;
import com.stackroute.maverick.domain.PlayedQuestion;
import com.stackroute.maverick.domain.SinglePlayerResult;
import com.stackroute.maverick.kafka.GameEvents;
import com.stackroute.maverick.repository.GameRepository;
import com.stackroute.maverick.repository.GameSessionRepository;
import com.stackroute.maverick.session.AnswerOutcome;
import com.stackroute.maverick.session.GameSession;
import com.stackroute.maverick.session.QuizBuilder;
import com.stackroute.maverick.session.QuizRuleException;
import com.stackroute.maverick.session.SessionQuestion;
import com.stackroute.maverick.session.SessionStatus;

@Service
public class GameSessionService {

	private final GameRepository games;
	private final GameSessionRepository sessions;
	private final GameEvents events;
	private final QuizBuilder quizBuilder;
	private final Clock clock;

	public GameSessionService(GameRepository games, GameSessionRepository sessions, GameEvents events,
			QuizBuilder quizBuilder, Clock clock) {
		this.games = games;
		this.sessions = sessions;
		this.events = events;
		this.quizBuilder = quizBuilder;
		this.clock = clock;
	}

	public List<Game> availableGames() {
		return games.findAll();
	}

	public GameSession start(int gameId, int userId) {
		Game game = games.findById(gameId).orElseThrow(() -> {
			events.requestGame(new GameRequest(gameId, userId));
			return new GameNotReadyException(gameId);
		});
		List<SessionQuestion> questions = quizBuilder.build(game);
		if (questions.isEmpty()) {
			throw new QuizRuleException("Game " + gameId + " has no playable questions");
		}
		GameSession session = new GameSession(UUID.randomUUID().toString(), gameId, userId, game.getCategoryId(),
				game.getGameName(), questions, clock.instant());
		return sessions.save(session);
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
				s.getCompletedAt(), played);
	}

	public record AnswerResult(AnswerOutcome outcome, GameSession session) {
	}
}
