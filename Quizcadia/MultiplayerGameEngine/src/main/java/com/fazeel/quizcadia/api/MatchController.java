package com.fazeel.quizcadia.api;

import java.time.Clock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.api.Views.AnswerRequest;
import com.fazeel.quizcadia.api.Views.AnswerView;
import com.fazeel.quizcadia.api.Views.MatchView;
import com.fazeel.quizcadia.match.MatchService;
import com.fazeel.quizcadia.match.MatchService.AnswerResult;
import com.fazeel.quizcadia.auth.Callers;

import jakarta.validation.Valid;

/** Players poll the match (about once a second) and post their answers. */
@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

	private final MatchService service;
	private final Clock clock;

	public MatchController(MatchService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping("/{matchId}")
	public MatchView match(@PathVariable String matchId, @RequestParam int userId) {
		Callers.requireSelf(userId);
		return MatchView.of(service.get(matchId, userId), userId, clock.instant());
	}

	@PostMapping("/{matchId}/answers")
	public AnswerView answer(@PathVariable String matchId, @Valid @RequestBody AnswerRequest request) {
		Callers.requireSelf(request.userId());
		AnswerResult r = service.answer(matchId, request.userId(), request.questionId(), request.answer());
		return new AnswerView(r.outcome().correct(), r.outcome().wonQuestion(), r.outcome().score(),
				MatchView.of(r.match(), request.userId(), clock.instant()));
	}
}
