package com.fazeel.quizking.api;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizking.adaptive.AdaptiveService;
import com.fazeel.quizking.adaptive.AdaptiveService.AnswerResult;
import com.fazeel.quizking.api.Views.AnswerRequest;
import com.fazeel.quizking.api.Views.AnswerView;
import com.fazeel.quizking.api.Views.SessionView;
import com.fazeel.quizking.api.Views.StartRequest;
import com.fazeel.quizking.adaptive.AdaptiveSession;
import com.fazeel.quizking.auth.Callers;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/adaptive/sessions")
public class AdaptiveController {

	private final AdaptiveService service;
	private final Clock clock;

	public AdaptiveController(AdaptiveService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView start(@Valid @RequestBody StartRequest request) {
		Callers.requireSelf(request.userId());
		return SessionView.of(service.start(request.gameId(), request.userId()), clock.instant());
	}

	@GetMapping("/{sessionId}")
	public SessionView get(@PathVariable String sessionId) {
		AdaptiveSession session = service.get(sessionId);
		Callers.requireSelf(session.getUserId());
		return SessionView.of(session, clock.instant());
	}

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
