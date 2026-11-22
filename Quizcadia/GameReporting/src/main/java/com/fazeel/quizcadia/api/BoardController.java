package com.fazeel.quizcadia.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.board.BoardCache;
import com.fazeel.quizcadia.board.CountryCup;
import com.fazeel.quizcadia.board.LeaderboardService;
import com.fazeel.quizcadia.board.LeaderboardService.DailyBoard;
import com.fazeel.quizcadia.board.LeaderboardService.Period;
import com.fazeel.quizcadia.board.LeaderboardService.XpBoard;
import com.fazeel.quizcadia.board.NameMask;
import com.fazeel.quizcadia.invite.Invites;
import com.fazeel.quizcadia.report.PlayerNames;

/**
 * Leaderboards every player may see. Calling them also records the caller's display name. Other players' names
 * are masked ("Faz***") for privacy; callers see their own name, and admins see everyone's (see {@link NameMask}).
 */
@RestController
@RequestMapping("/api/v1/reports")
public class BoardController {

	private final LeaderboardService boards;
	private final PlayerNames names;
	private final Clock clock;
	private final CountryCup cup;
	private final Invites invites;
	private final BoardCache cache;

	public BoardController(LeaderboardService boards, PlayerNames names, Clock clock,
			CountryCup cup, Invites invites,
			BoardCache cache) {
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
		return NameMask.xp(boards.xp(period, categoryId, me, clock.instant()), me, Callers.isAdmin());
	}

	/** Countries ranked by their players' XP. */
	public record InviteView(String code, int invited, int shields) {
	}

	/** The caller's invite code, how many friends joined with it, and their streak shields. */
	@GetMapping("/invites/me")
	public InviteView invites() {
		int me = Callers.currentUserId();
		return new InviteView(Invites.code(me), invites.invited(me).size(), invites.shields(me));
	}

	/** This week's Country Cup, with the caller's country's place. */
	@GetMapping("/cup")
	public CountryCup.CupView cup() {
		return NameMask.cup(cup.view(Callers.currentUserId()), Callers.isAdmin());
	}

	/** Countries ranked by the XP their players earned in the period (day, week or all time). */
	@GetMapping("/leaderboards/countries")
	public LeaderboardService.CountryBoard countries(@RequestParam(defaultValue = "WEEK") Period period) {
		return boards.countries(period, clock.instant());
	}

	/** A player's country (ISO code), as they set it on the World page. */
	public record MyCountry(String country) {
	}

	/** Sets (or clears, with null) the caller's country for flags and the countries leaderboard. */
	@PostMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void me(@RequestBody MyCountry body) {
		names.country(Callers.currentUserId(), Callers.currentUserName(), body == null ? null : body.country());
		cache.evict(CountryCup.KEY); // the cup page reloads right after "Join the cup
	}

	/** A day's challenge ranking; today (UTC) by default. */
	@GetMapping("/daily")
	public DailyBoard daily(@RequestParam(required = false) LocalDate date) {
		int me = Callers.currentUserId();
		names.remember(me, Callers.currentUserName());
		return NameMask.daily(boards.daily(date != null ? date : LocalDate.now(clock.withZone(ZoneOffset.UTC)), me), me,
				Callers.isAdmin());
	}
}
