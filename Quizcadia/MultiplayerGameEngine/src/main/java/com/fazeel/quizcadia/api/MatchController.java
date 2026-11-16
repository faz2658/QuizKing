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
	private final com.fazeel.quizcadia.match.MatchUpdates updates;

	public MatchController(MatchService service, Clock clock, com.fazeel.quizcadia.match.MatchUpdates updates) {
		this.service = service;
		this.clock = clock;
		this.updates = updates;
	}

	@GetMapping("/{matchId}")
	public MatchView match(@PathVariable String matchId, @RequestParam int userId) {
		Callers.requireSelf(userId);
		return MatchView.of(service.get(matchId, userId), userId, clock.instant());
	}

	/** Longest a request waits for news before answering anyway (well under nginx's 60 s proxy timeout). */
	static final long MAX_WAIT_MS = 20_000;

	/**
	 * The match as soon as it differs from version {@code after}: at once if it already does, otherwise when someone
	 * answers, a bot answers or the question closes (the server knows when), or after {@link #MAX_WAIT_MS}. Screens
	 * call this in a loop instead of asking every second.
	 */
	@GetMapping("/{matchId}/next")
	public org.springframework.web.context.request.async.DeferredResult<MatchView> next(@PathVariable String matchId,
			@RequestParam int userId, @RequestParam(required = false) String after) {
		Callers.requireSelf(userId);
		java.time.Instant now = clock.instant();
		com.fazeel.quizcadia.match.MultiplayerMatch m = service.get(matchId, userId);
		MatchView view = MatchView.of(m, userId, now);
		java.time.Instant nextChange = m.nextChangeAt(now);
		if (after == null || !after.equals(view.version()) || nextChange == null) {
			var done = new org.springframework.web.context.request.async.DeferredResult<MatchView>();
			done.setResult(view);
			return done;
		}
		long wait = Math.clamp(java.time.Duration.between(now, nextChange).toMillis() + 150, 200, MAX_WAIT_MS);
		java.util.function.Supplier<MatchView> fresh = () -> MatchView.of(service.get(matchId, userId), userId, clock.instant());
		var result = new org.springframework.web.context.request.async.DeferredResult<MatchView>(wait,
				(java.util.function.Supplier<Object>) () -> fresh.get());
		Runnable stop = updates.await(matchId, () -> result.setResult(fresh.get()));
		result.onCompletion(stop);
		// a change between reading the match and registering would otherwise wait for the timeout
		MatchView recheck = fresh.get();
		if (!recheck.version().equals(after)) {
			result.setResult(recheck);
		}
		return result;
	}

	@PostMapping("/{matchId}/answers")
	public AnswerView answer(@PathVariable String matchId, @Valid @RequestBody AnswerRequest request) {
		Callers.requireSelf(request.userId());
		AnswerResult r = service.answer(matchId, request.userId(), request.questionId(), request.answer());
		return new AnswerView(r.outcome().correct(), r.outcome().wonQuestion(), r.outcome().score(),
				MatchView.of(r.match(), request.userId(), clock.instant()));
	}
}
