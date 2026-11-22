package com.fazeel.quizcadia.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.fazeel.quizcadia.api.Views.AnswerRequest;
import com.fazeel.quizcadia.api.Views.AnswerView;
import com.fazeel.quizcadia.api.Views.MatchView;
import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.match.MatchService;
import com.fazeel.quizcadia.match.MatchService.AnswerResult;
import com.fazeel.quizcadia.match.MatchUpdates;
import com.fazeel.quizcadia.match.MultiplayerMatch;

import jakarta.validation.Valid;

/** Players poll the match (about once a second) and post their answers. */
@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

	private final MatchService service;
	private final Clock clock;
	private final MatchUpdates updates;

	public MatchController(MatchService service, Clock clock, MatchUpdates updates) {
		this.service = service;
		this.clock = clock;
		this.updates = updates;
	}

	/**
	 * The match as the caller sees it now (players see only their own answers; the open question never shows its
	 * answer).
	 */
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
	public DeferredResult<MatchView> next(@PathVariable String matchId,
			@RequestParam int userId, @RequestParam(required = false) String after) {
		Callers.requireSelf(userId);
		Instant now = clock.instant();
		MultiplayerMatch m = service.get(matchId, userId);
		MatchView view = MatchView.of(m, userId, now);
		Instant nextChange = m.nextChangeAt(now);
		if (after == null || !after.equals(view.version()) || nextChange == null) {
			var done = new DeferredResult<MatchView>();
			done.setResult(view);
			return done;
		}
		long wait = Math.clamp(Duration.between(now, nextChange).toMillis() + 150, 200, MAX_WAIT_MS);
		Supplier<MatchView> fresh = () -> MatchView.of(service.get(matchId, userId), userId, clock.instant());
		var result = new DeferredResult<MatchView>(wait,
				(Supplier<Object>) () -> fresh.get());
		AtomicReference<Runnable> stop = new AtomicReference<>(() -> {
		});
		result.onCompletion(() -> stop.get().run());
		awaitChange(matchId, after, fresh, result, stop);
		// a change between reading the match and registering would otherwise wait for the timeout
		MatchView recheck = fresh.get();
		if (!recheck.version().equals(after)) {
			result.setResult(recheck);
		}
		return result;
	}

	/** The host's big screen paces a room quiz: REVEAL the answer, NEXT question, or END it. */
	@PostMapping("/{matchId}/host")
	public MatchView host(@PathVariable String matchId, @Valid @RequestBody Views.HostRequest request) {
		int userId = Callers.currentUserId();
		return MatchView.of(service.host(matchId, userId, request.action()), userId, clock.instant());
	}

	/**
	 * Answers {@code result} at the next change that this viewer would see. A change they wouldn't (on a phone in a
	 * room quiz, someone else's answer) waits again instead of sending the same view back.
	 */
	private void awaitChange(String matchId, String after, Supplier<MatchView> fresh,
			DeferredResult<MatchView> result,
			AtomicReference<Runnable> stop) {
		stop.set(updates.await(matchId, () -> {
			if (result.isSetOrExpired()) {
				return;
			}
			try {
				MatchView view = fresh.get();
				if (!view.version().equals(after)) {
					result.setResult(view);
				} else {
					awaitChange(matchId, after, fresh, result, stop);
				}
			}
			catch (RuntimeException e) {
				result.setErrorResult(e); // never fail the request whose save woke us
			}
		}));
	}

	/** Answers the open question for the caller and returns how it went with the updated match. */
	@PostMapping("/{matchId}/answers")
	public AnswerView answer(@PathVariable String matchId, @Valid @RequestBody AnswerRequest request) {
		Callers.requireSelf(request.userId());
		AnswerResult r = service.answer(matchId, request.userId(), request.questionId(), request.answer());
		return new AnswerView(r.outcome().correct(), r.outcome().wonQuestion(), r.outcome().score(),
				MatchView.of(r.match(), request.userId(), clock.instant()));
	}
}
