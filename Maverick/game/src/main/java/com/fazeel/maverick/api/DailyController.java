package com.fazeel.maverick.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.maverick.api.Views.DailyView;
import com.fazeel.maverick.api.Views.SessionView;
import com.fazeel.maverick.auth.Callers;
import com.fazeel.maverick.daily.DailyChallenge;
import com.fazeel.maverick.daily.DailyChallengeService;
import com.fazeel.maverick.session.GameSession;

/** Today's challenge. Answers and results go through the regular /sessions endpoints. */
@RestController
@RequestMapping("/api/v1/daily")
public class DailyController {

	private final DailyChallengeService daily;
	private final Clock clock;

	public DailyController(DailyChallengeService daily, Clock clock) {
		this.daily = daily;
		this.clock = clock;
	}

	@GetMapping
	public DailyView today() {
		LocalDate date = daily.today();
		DailyChallenge challenge = daily.challenge(date);
		Optional<GameSession> run = daily.todaysRun(Callers.currentUserId());
		String status = run.map(s -> s.getStatus().name()).orElse("NOT_STARTED");
		return new DailyView(date.toString(), challenge.questions().size(),
				challenge.topics().stream().distinct().toList(), status, run.map(GameSession::getId).orElse(null),
				run.map(GameSession::getScore).orElse(null), run.map(GameSession::maxScore).orElse(null),
				daily.untilNext().toSeconds());
	}

	@PostMapping("/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView start() {
		return SessionView.of(daily.start(Callers.currentUserId(), Callers.currentUserName()), clock.instant());
	}
}
