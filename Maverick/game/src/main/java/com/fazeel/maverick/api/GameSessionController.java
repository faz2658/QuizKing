package com.fazeel.maverick.api;

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

import com.fazeel.maverick.api.Views.AnswerRequest;
import com.fazeel.maverick.api.Views.AnswerView;
import com.fazeel.maverick.api.Views.GameSummary;
import com.fazeel.maverick.api.Views.ResultView;
import com.fazeel.maverick.api.Views.SessionView;
import com.fazeel.maverick.api.Views.StartRequest;
import com.fazeel.maverick.service.GameSessionService;
import com.fazeel.maverick.service.GameSessionService.AnswerResult;
import com.fazeel.maverick.auth.Callers;
import com.fazeel.maverick.session.GameSession;

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
		return SessionView.of(service.start(request.gameId(), request.userId()), clock.instant());
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
