package com.fazeel.quizcadia.api;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.api.Views.AnswerRequest;
import com.fazeel.quizcadia.api.Views.AnswerView;
import com.fazeel.quizcadia.api.Views.GameSummary;
import com.fazeel.quizcadia.api.Views.ResultView;
import com.fazeel.quizcadia.api.Views.SessionView;
import com.fazeel.quizcadia.api.Views.CustomRequest;
import com.fazeel.quizcadia.api.Views.LifelineRequest;
import com.fazeel.quizcadia.api.Views.LifelineView;
import com.fazeel.quizcadia.api.Views.StartRequest;
import com.fazeel.quizcadia.service.GameSessionService;
import com.fazeel.quizcadia.service.GameSessionService.AnswerResult;
import com.fazeel.quizcadia.service.GameSessionService.LifelineResult;
import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.session.GameSession;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class GameSessionController {

	private final GameSessionService service;
	private final Clock clock;

	public GameSessionController(GameSessionService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/games")
	public List<GameSummary> games() {
		return service.availableGames().stream().map(GameSummary::of).toList();
	}

	@PostMapping("/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView start(@Valid @RequestBody StartRequest request) {
		Callers.requireSelf(request.userId());
		return SessionView.of(service.start(request.gameId(), request.userId(), Callers.currentUserName(),
				request.country()),
				clock.instant());
	}

	/** A quiz mixed from several topics, at one difficulty (or mixed), 5 to 20 questions. */
	@PostMapping("/sessions/custom")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView startCustom(@Valid @RequestBody CustomRequest request) {
		return SessionView.of(service.startCustom(request.gameIds(), request.difficulty(), request.count(),
				Callers.currentUserId(), Callers.currentUserName(), request.country()), clock.instant());
	}

	@GetMapping("/sessions/{sessionId}")
	public SessionView session(@PathVariable String sessionId) {
		return SessionView.of(owned(sessionId), clock.instant());
	}

	@PostMapping("/sessions/{sessionId}/answers")
	public AnswerView answer(@PathVariable String sessionId, @Valid @RequestBody AnswerRequest request) {
		owned(sessionId);
		AnswerResult result = service.answer(sessionId, request.questionId(), request.answer());
		return AnswerView.of(result.outcome(), SessionView.of(result.session(), clock.instant()));
	}

	/** 50/50, skip or extra time, once each per game. */
	@PostMapping("/sessions/{sessionId}/lifelines")
	public LifelineView lifeline(@PathVariable String sessionId, @Valid @RequestBody LifelineRequest request) {
		owned(sessionId);
		LifelineResult r = service.useLifeline(sessionId, request.lifeline(), request.questionId());
		SessionView view = SessionView.of(r.session(), clock.instant());
		return new LifelineView(request.lifeline(), r.skipped() == null ? null : AnswerView.of(r.skipped(), view), view);
	}

	@GetMapping("/sessions/{sessionId}/result")
	public ResultView result(@PathVariable String sessionId) {
		owned(sessionId);
		return ResultView.of(service.result(sessionId));
	}

	/** Loads a session, refusing anyone but the player who started it. */
	private GameSession owned(String sessionId) {
		GameSession session = service.get(sessionId);
		Callers.requireSelf(session.getUserId());
		return session;
	}
}
