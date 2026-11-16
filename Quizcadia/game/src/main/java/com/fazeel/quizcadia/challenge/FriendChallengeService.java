package com.fazeel.quizcadia.challenge;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.service.NotFoundException;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;
import com.fazeel.quizcadia.session.SessionVariant;

/**
 * Friend challenges. Any finished game except a Daily Challenge (its questions stay secret until the day is over)
 * can become a challenge; each friend gets one attempt at the same questions, in the same order with the same
 * options, and the challenger's score to beat.
 */
@Service
public class FriendChallengeService {

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no look-alikes
	private final SecureRandom random = new SecureRandom();

	private final FriendChallengeRepository challenges;
	private final GameSessionRepository sessions;
	private final Clock clock;

	public FriendChallengeService(FriendChallengeRepository challenges, GameSessionRepository sessions, Clock clock) {
		this.challenges = challenges;
		this.sessions = sessions;
		this.clock = clock;
	}

	/** Turns the caller's finished session into a challenge (the same challenge if asked twice). */
	public FriendChallenge create(GameSession session, String challengerName) {
		if (session.getStatus() != SessionStatus.COMPLETED) {
			throw new QuizRuleException("Finish the game before challenging friends");
		}
		if (session.getVariant() == SessionVariant.DAILY) {
			throw new QuizRuleException("Daily Challenge questions stay secret until tomorrow: share your result card instead");
		}
		Optional<FriendChallenge> existing = challenges.findBySessionId(session.getId());
		if (existing.isPresent()) {
			return existing.get();
		}
		List<SessionQuestion> questions = session.getQuestions().stream().map(SessionQuestion::fresh).toList();
		FriendChallenge challenge = new FriendChallenge(newId(), session.getId(), session.getUserId(), challengerName,
				session.getGameId(), session.getGameName(), session.getCategoryId(), session.getScore(),
				session.maxScore(), (int) session.correctCount(), questions, clock.instant());
		return challenges.save(challenge);
	}

	public FriendChallenge get(String id) {
		return challenges.findById(id).orElseThrow(() -> new NotFoundException("No challenge " + id));
	}

	/** The player's attempt at a challenge, if they started one. */
	public Optional<GameSession> attempt(String challengeId, int userId) {
		return sessions.findFirstByUserIdAndVariantAndVariantKey(userId, SessionVariant.CHALLENGE, challengeId);
	}

	public GameSession accept(String challengeId, int userId, String playerName) {
		FriendChallenge c = get(challengeId);
		if (c.challengerId() == userId) {
			throw new QuizRuleException("That's your own challenge: send the link to a friend");
		}
		Optional<GameSession> existing = attempt(challengeId, userId);
		if (existing.isPresent()) {
			if (existing.get().getStatus() == SessionStatus.COMPLETED) {
				throw new QuizRuleException("You've already taken this challenge");
			}
			return existing.get();
		}
		List<SessionQuestion> questions = c.questions().stream().map(SessionQuestion::fresh).toList();
		GameSession session = new GameSession(UUID.randomUUID().toString(), c.gameId(), userId, c.categoryId(),
				c.gameName(), questions, clock.instant())
				.asVariant(SessionVariant.CHALLENGE, c.id(), c.score()).playedBy(playerName);
		return sessions.save(session);
	}

	private String newId() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}
}
