package com.fazeel.quizcadia.api;

import java.time.Clock;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.api.Views.SessionView;
import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.challenge.FriendChallenge;
import com.fazeel.quizcadia.challenge.FriendChallengeService;
import com.fazeel.quizcadia.service.GameSessionService;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.SessionStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Friend challenges. Questions are never shown here: only through a session, one at a time. */
@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

	private final FriendChallengeService challenges;
	private final GameSessionService sessions;
	private final Clock clock;

	public ChallengeController(FriendChallengeService challenges, GameSessionService sessions, Clock clock) {
		this.challenges = challenges;
		this.sessions = sessions;
		this.clock = clock;
	}

	/** The finished session to turn into a challenge. */
	public record CreateChallenge(@NotBlank String sessionId) {
	}

	/** {@code yourStatus} is NOT_STARTED, IN_PROGRESS, COMPLETED, or OWN for the challenger. */
	public record ChallengeView(String id, String challengerName, String gameName, int score, int maxScore,
			int correctAnswers, int totalQuestions, String yourStatus, String yourSessionId, Integer yourScore) {

		static ChallengeView of(FriendChallenge c, int me, Optional<GameSession> attempt) {
			String status = c.challengerId() == me ? "OWN"
					: attempt.map(s -> s.getStatus().name()).orElse("NOT_STARTED");
			return new ChallengeView(c.id(), c.challengerName(), c.gameName(), c.score(), c.maxScore(), c.correctAnswers(),
					c.questions().size(), status, attempt.map(GameSession::getId).orElse(null),
					attempt.filter(s -> s.getStatus() == SessionStatus.COMPLETED)
							.map(GameSession::getScore).orElse(null));
		}
	}

	/** Turns the caller's own finished session into a challenge link. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ChallengeView create(@Valid @RequestBody CreateChallenge request) {
		GameSession session = sessions.get(request.sessionId());
		Callers.requireSelf(session.getUserId());
		FriendChallenge c = challenges.create(session, Callers.currentUserName());
		return ChallengeView.of(c, Callers.currentUserId(), Optional.empty());
	}

	/** The challenge, with the caller's status in it. */
	@GetMapping("/{id}")
	public ChallengeView get(@PathVariable String id) {
		int me = Callers.currentUserId();
		return ChallengeView.of(challenges.get(id), me, challenges.attempt(id, me));
	}

	/** Starts the caller's attempt, or returns the unfinished one. */
	@PostMapping("/{id}/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView accept(@PathVariable String id) {
		return SessionView.of(challenges.accept(id, Callers.currentUserId(), Callers.currentUserName()), clock.instant());
	}
}
