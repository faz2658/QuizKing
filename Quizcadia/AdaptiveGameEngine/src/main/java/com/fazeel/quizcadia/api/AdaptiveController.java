package com.fazeel.quizcadia.api;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.adaptive.AdaptiveService;
import com.fazeel.quizcadia.adaptive.AdaptiveService.AnswerResult;
import com.fazeel.quizcadia.api.Views.AnswerRequest;
import com.fazeel.quizcadia.api.Views.AnswerView;
import com.fazeel.quizcadia.api.Views.SessionView;
import com.fazeel.quizcadia.api.Views.StartRequest;
import com.fazeel.quizcadia.adaptive.AdaptiveSession;
import com.fazeel.quizcadia.auth.Callers;

import jakarta.validation.Valid;

/**
 * The adaptive game API. Players start, read and answer only their own sessions; the server does all timing and
 * scoring.
 */
@RestController
@RequestMapping("/api/v1/adaptive/sessions")
public class AdaptiveController {

	private final AdaptiveService service;
	private final Clock clock;

	public AdaptiveController(AdaptiveService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	/** Starts a session for the calling player (most questions from their country when one is given). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView start(@Valid @RequestBody StartRequest request) {
		Callers.requireSelf(request.userId());
		return SessionView.of(service.start(request.gameId(), request.userId(), request.country()), clock.instant());
	}

	/** The caller's session, with the open question and the seconds left on it. */
	@GetMapping("/{sessionId}")
	public SessionView get(@PathVariable String sessionId) {
		AdaptiveSession session = service.get(sessionId);
		Callers.requireSelf(session.getUserId());
		return SessionView.of(session, clock.instant());
	}

	/**
	 * Answers the open question; the reply says whether it was right, the points, and whether the level went UP,
	 * DOWN or stayed the SAME.
	 */
	@PostMapping("/{sessionId}/answers")
	public AnswerView answer(@PathVariable String sessionId, @Valid @RequestBody AnswerRequest request) {
		Callers.requireSelf(service.get(sessionId).getUserId());
		AnswerResult r = service.answer(sessionId, request.questionId(), request.answer());
		String change = switch (Integer.signum(r.outcome().levelChange())) {
			case 1 -> "UP";
			case -1 -> "DOWN";
			default -> "SAME";
		};
		return new AnswerView(r.outcome().correct(), r.outcome().correctAnswer(), r.outcome().points(), change,
				SessionView.of(r.session(), clock.instant()));
	}
}
