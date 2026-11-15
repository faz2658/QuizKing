package com.fazeel.maverick.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.maverick.auth.Callers;
import com.fazeel.maverick.board.LeaderboardService;
import com.fazeel.maverick.board.LeaderboardService.DailyBoard;
import com.fazeel.maverick.report.PlayerNames;

/** Leaderboards every player may see. Calling them also records the caller's display name. */
@RestController
@RequestMapping("/api/v1/reports")
public class BoardController {

	private final LeaderboardService boards;
	private final PlayerNames names;
	private final Clock clock;

	public BoardController(LeaderboardService boards, PlayerNames names, Clock clock) {
		this.boards = boards;
		this.names = names;
		this.clock = clock;
	}

	/** A day's challenge ranking; today (UTC) by default. */
	@GetMapping("/daily")
	public DailyBoard daily(@RequestParam(required = false) LocalDate date) {
		int me = Callers.currentUserId();
		names.remember(me, Callers.currentUserName());
		return boards.daily(date != null ? date : LocalDate.now(clock.withZone(ZoneOffset.UTC)), me);
	}
}
