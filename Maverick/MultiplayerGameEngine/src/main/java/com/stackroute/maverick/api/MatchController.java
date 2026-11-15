package com.stackroute.maverick.api;

import java.time.Clock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stackroute.maverick.api.Views.AnswerRequest;
import com.stackroute.maverick.api.Views.AnswerView;
import com.stackroute.maverick.api.Views.MatchView;
import com.stackroute.maverick.match.MatchService;
import com.stackroute.maverick.match.MatchService.AnswerResult;

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
		return MatchView.of(service.get(matchId, userId), userId, clock.instant());
	}

	@PostMapping("/{matchId}/answers")
	public AnswerView answer(@PathVariable String matchId, @Valid @RequestBody AnswerRequest request) {
		AnswerResult r = service.answer(matchId, request.userId(), request.questionId(), request.answer());
		return new AnswerView(r.outcome().correct(), r.outcome().wonQuestion(), r.outcome().score(),
				MatchView.of(r.match(), request.userId(), clock.instant()));
	}
}
