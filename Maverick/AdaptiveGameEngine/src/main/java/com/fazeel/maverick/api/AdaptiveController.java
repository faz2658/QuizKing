package com.fazeel.maverick.api;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.maverick.adaptive.AdaptiveService;
import com.fazeel.maverick.adaptive.AdaptiveService.AnswerResult;
import com.fazeel.maverick.api.Views.AnswerRequest;
import com.fazeel.maverick.api.Views.AnswerView;
import com.fazeel.maverick.api.Views.SessionView;
import com.fazeel.maverick.api.Views.StartRequest;

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
		return SessionView.of(service.start(request.gameId(), request.userId()), clock.instant());
	}

	@GetMapping("/{sessionId}")
	public SessionView get(@PathVariable String sessionId) {
		return SessionView.of(service.get(sessionId), clock.instant());
	}

	@PostMapping("/{sessionId}/answers")
	public AnswerView answer(@PathVariable String sessionId, @Valid @RequestBody AnswerRequest request) {
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
