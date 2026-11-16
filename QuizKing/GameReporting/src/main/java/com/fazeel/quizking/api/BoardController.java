package com.fazeel.quizking.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizking.auth.Callers;
import com.fazeel.quizking.board.LeaderboardService;
import com.fazeel.quizking.board.LeaderboardService.DailyBoard;
import com.fazeel.quizking.board.LeaderboardService.Period;
import com.fazeel.quizking.board.LeaderboardService.XpBoard;
import com.fazeel.quizking.report.PlayerNames;

/** Leaderboards every player may see. Calling them also records the caller's display name. */
@RestController
@RequestMapping("/api/v1/reports")
public class BoardController {

	private final LeaderboardService boards;
	private final PlayerNames names;
	private final Clock clock;
	private final com.fazeel.quizking.board.CountryCup cup;
	private final com.fazeel.quizking.invite.Invites invites;
	private final com.fazeel.quizking.board.BoardCache cache;

	public BoardController(LeaderboardService boards, PlayerNames names, Clock clock,
			com.fazeel.quizking.board.CountryCup cup, com.fazeel.quizking.invite.Invites invites,
			com.fazeel.quizking.board.BoardCache cache) {
		this.cache = cache;
		this.boards = boards;
		this.names = names;
		this.clock = clock;
		this.cup = cup;
		this.invites = invites;
	}

	/** XP rankings: today, this week or all time, optionally for one category. */
	@GetMapping("/leaderboards")
	public XpBoard leaderboard(@RequestParam(defaultValue = "WEEK") Period period,
			@RequestParam(required = false) Integer categoryId) {
		int me = Callers.currentUserId();
		names.remember(me, Callers.currentUserName());
		return boards.xp(period, categoryId, me, clock.instant());
	}

	/** Countries ranked by their players' XP. */
	public record InviteView(String code, int invited, int shields) {
	}

	/** The caller's invite code, how many friends joined with it, and their streak shields. */
	@GetMapping("/invites/me")
	public InviteView invites() {
		int me = com.fazeel.quizking.auth.Callers.currentUserId();
		return new InviteView(com.fazeel.quizking.invite.Invites.code(me), invites.invited(me).size(), invites.shields(me));
	}

	/** This week's Country Cup, with the caller's country's place. */
	@GetMapping("/cup")
	public com.fazeel.quizking.board.CountryCup.CupView cup() {
		return cup.view(com.fazeel.quizking.auth.Callers.currentUserId());
	}

	@GetMapping("/leaderboards/countries")
	public com.fazeel.quizking.board.LeaderboardService.CountryBoard countries(@RequestParam(defaultValue = "WEEK") Period period) {
		return boards.countries(period, clock.instant());
	}

	public record MyCountry(String country) {
	}

	/** Sets (or clears, with null) the caller's country for flags and the countries leaderboard. */
	@org.springframework.web.bind.annotation.PostMapping("/me")
	@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	public void me(@org.springframework.web.bind.annotation.RequestBody MyCountry body) {
		names.country(Callers.currentUserId(), Callers.currentUserName(), body == null ? null : body.country());
		cache.evict(com.fazeel.quizking.board.CountryCup.KEY); // the cup page reloads right after "Join the cup
	}

	/** A day's challenge ranking; today (UTC) by default. */
	@GetMapping("/daily")
	public DailyBoard daily(@RequestParam(required = false) LocalDate date) {
		int me = Callers.currentUserId();
		names.remember(me, Callers.currentUserName());
		return boards.daily(date != null ? date : LocalDate.now(clock.withZone(ZoneOffset.UTC)), me);
	}
}
