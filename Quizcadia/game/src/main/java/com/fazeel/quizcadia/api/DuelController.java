package com.fazeel.quizcadia.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fazeel.quizcadia.api.Views.SessionView;
import com.fazeel.quizcadia.auth.Callers;
import com.fazeel.quizcadia.challenge.FriendDuel;
import com.fazeel.quizcadia.challenge.FriendDuelService;
import com.fazeel.quizcadia.challenge.FriendDuelService.Attempt;
import com.fazeel.quizcadia.challenge.FriendDuelService.Standing;
import com.fazeel.quizcadia.challenge.FriendDuelService.State;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * "Challenge a friend": one friend against another on the same questions (see {@link FriendDuelService}).
 * Countries go in but never come out: nobody is told how the questions were picked.
 */
@RestController
@RequestMapping("/api/v1/challenges/duels")
public class DuelController {

	private final FriendDuelService duels;
	private final Clock clock;

	public DuelController(FriendDuelService duels, Clock clock) {
		this.duels = duels;
		this.clock = clock;
	}

	/** @param country the challenger's country (ISO code), optional */
	public record CreateDuel(@Positive int gameId, Boolean international, @Pattern(regexp = "[A-Za-z]{2}") String country) {
	}

	/** @param country the accepting player's country (ISO code), optional */
	public record AcceptDuel(@Pattern(regexp = "[A-Za-z]{2}") String country) {
	}

	/**
	 * One player in a challenge as {@code me} sees them. The other player's score shows once {@code me} has finished
	 * or the challenge is over; {@code sessionId} only for {@code me}'s own attempt.
	 */
	public record DuelPlayer(String name, String status, Integer score, Integer maxScore, Integer correctAnswers,
			String sessionId) {
	}

	/**
	 * A challenge from the caller's side. {@code role} is CHALLENGER, OPPONENT, INVITED (may still accept) or
	 * OUTSIDER; {@code outcome} is WON, LOST or TIE once FINISHED; {@code friend} is null until someone accepts.
	 */
	public record DuelView(String id, String gameName, int gameId, boolean international, int totalQuestions,
			State state, String role, String challengerName, DuelPlayer you, DuelPlayer friend, String outcome,
			Instant expiresAt) {

		static DuelView of(Standing st, int me) {
			FriendDuel d = st.duel();
			boolean inIt = d.sideOf(me) != null;
			String role = d.isChallenger(me) ? "CHALLENGER" : d.isOpponent(me) ? "OPPONENT"
					: st.state() == State.WAITING ? "INVITED" : "OUTSIDER";
			DuelPlayer you = null;
			DuelPlayer friend = null;
			String outcome = null;
			if (inIt) {
				Attempt mine = st.of(me);
				Attempt theirs = st.against(me);
				boolean over = st.state() == State.FINISHED || st.state() == State.EXPIRED;
				boolean declined = d.status() == FriendDuel.Status.DECLINED;
				you = player(mine, true, true, declined && d.isOpponent(me));
				friend = theirs == null ? null : player(theirs, mine.completed() || over, false, declined && d.isChallenger(me));
				if (st.state() == State.FINISHED) {
					outcome = st.tie() ? "TIE" : st.winnerUserId() == me ? "WON" : "LOST";
				}
			}
			return new DuelView(d.id(), d.gameName(), d.gameId(), d.international(), d.questions().size(), st.state(),
					role, d.challenger().name(), you, friend, outcome,
					st.state() == State.WAITING || st.state() == State.PLAYING ? d.expiresAt() : null);
		}

		private static DuelPlayer player(Attempt a, boolean showScore, boolean own, boolean declined) {
			String status = declined ? "DECLINED" : a.completed() ? "COMPLETED" : a.started() ? "IN_PROGRESS" : "NOT_STARTED";
			boolean show = showScore && a.completed();
			return new DuelPlayer(a.side().name(), status, show ? a.session().getScore() : null,
					show ? a.session().maxScore() : null, show ? (int) a.session().correctCount() : null,
					own && a.started() ? a.session().getId() : null);
		}
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DuelView create(@Valid @RequestBody CreateDuel request) {
		if (!Callers.isAccount()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in to challenge a friend");
		}
		int me = Callers.currentUserId();
		FriendDuel d = duels.create(me, Callers.currentUserName(), request.gameId(),
				Boolean.TRUE.equals(request.international()), request.country());
		return DuelView.of(duels.standing(d), me);
	}

	/** The caller's challenges, sent and received, newest first. */
	@GetMapping
	public List<DuelView> mine() {
		int me = Callers.currentUserId();
		return duels.mine(me).stream().map(d -> DuelView.of(duels.standing(d), me)).toList();
	}

	@GetMapping("/{id}")
	public DuelView get(@PathVariable String id) {
		int me = Callers.currentUserId();
		return DuelView.of(duels.standing(duels.get(id)), me);
	}

	@PostMapping("/{id}/accept")
	public DuelView accept(@PathVariable String id, @Valid @RequestBody(required = false) AcceptDuel request) {
		int me = Callers.currentUserId();
		FriendDuel d = duels.accept(id, me, Callers.currentUserName(), request == null ? null : request.country());
		return DuelView.of(duels.standing(d), me);
	}

	@PostMapping("/{id}/decline")
	public DuelView decline(@PathVariable String id) {
		int me = Callers.currentUserId();
		return DuelView.of(duels.standing(duels.decline(id, me, Callers.currentUserName())), me);
	}

	/** Starts (or resumes) the caller's game: then played like any other session. */
	@PostMapping("/{id}/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionView play(@PathVariable String id) {
		return SessionView.of(duels.play(id, Callers.currentUserId(), Callers.currentUserName()), clock.instant());
	}
}
