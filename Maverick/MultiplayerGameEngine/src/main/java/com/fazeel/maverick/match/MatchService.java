package com.fazeel.maverick.match;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fazeel.maverick.domain.Match;
import com.fazeel.maverick.domain.MultiPlayerGame;
import com.fazeel.maverick.domain.MultiPlayerResult;
import com.fazeel.maverick.match.MultiplayerMatch.AnswerOutcome;
import com.fazeel.maverick.match.MultiplayerMatch.Status;

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

	public MatchService(MatchRepository matches, GameRepository games, GameSource gameSource, MatchFactory factory,
			MatchEvents events, SeenQuestionRepository seen, Clock clock) {
		this.matches = matches;
		this.games = games;
		this.gameSource = gameSource;
		this.factory = factory;
		this.events = events;
		this.seen = seen;
		this.clock = clock;
	}

	public void cacheGame(MultiPlayerGame game) {
		games.save(game);
	}

	public void removeGame(int gameId) {
		games.deleteById(gameId);
	}

	/** Starts the match for players found by the player matcher. Replayed events are ignored. */
	public MultiplayerMatch start(Match match) {
		return matches.findById(match.matchId()).orElseGet(() -> {
			MultiPlayerGame game = games.findById(match.gameId())
					.or(() -> gameSource.fetch(match.gameId()).map(games::save))
					.orElseThrow(() -> new NotFoundException("No multiplayer game " + match.gameId()));
			List<Integer> ids = game.autoquestions() == null ? List.of()
					: game.autoquestions().stream().filter(java.util.Objects::nonNull)
							.map(MultiPlayerGame.Question::questionId).toList();
			java.util.Map<Integer, Integer> seenBy = new java.util.HashMap<>();
			seen.findByUserIdInAndQuestionIdIn(match.players(), ids)
					.forEach(s -> seenBy.merge(s.questionId(), 1, Integer::sum));
			java.time.Instant now = clock.instant();
			MultiplayerMatch created = factory.create(match, game, now, seenBy);
			seen.saveAll(match.players().stream().filter(p -> p > 0).flatMap(p -> created.getQuestions().stream()
					.map(q -> SeenQuestion.of(p, q.getQuestionId(), now))).toList());
			log.info("Match {} started: game {} with {}", match.matchId(), match.gameId(), match.players());
			return matches.save(created);
		});
	}

	public MultiplayerMatch get(String matchId, int userId) {
		MultiplayerMatch match = load(matchId);
		requirePlayer(match, userId);
		if (match.advanceClock(clock.instant())) {
			match = save(match);
		}
		return match;
	}

	public AnswerResult answer(String matchId, int userId, int questionId, String answer) {
		MultiplayerMatch match = load(matchId);
		requirePlayer(match, userId);
		AnswerOutcome outcome = match.answer(userId, questionId, answer, clock.instant());
		return new AnswerResult(outcome, save(match));
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
		}
		MultiplayerMatch saved = matches.save(match);
		if (publish) {
			events.matchCompleted(toResult(saved));
		}
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

	public record AnswerResult(AnswerOutcome outcome, MultiplayerMatch match) {
	}
}
