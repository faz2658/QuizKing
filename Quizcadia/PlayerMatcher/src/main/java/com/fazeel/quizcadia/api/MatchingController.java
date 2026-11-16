package com.fazeel.quizcadia.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.matching.LobbyStatus;
import com.fazeel.quizcadia.matching.Matchmaker;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

	private final Matchmaker matchmaker;
	private final com.fazeel.quizcadia.matching.Presence presence;

	public MatchingController(Matchmaker matchmaker, com.fazeel.quizcadia.matching.Presence presence) {
		this.matchmaker = matchmaker;
		this.presence = presence;
	}

	/** Joins the lobby of a game. Poll the status endpoint until the state is MATCHED or EXPIRED. */
	@PostMapping("/{gameId}/players/{userId}")
	public LobbyStatus join(@PathVariable int gameId, @PathVariable int userId) {
		presence.seen(userId, com.fazeel.quizcadia.auth.Callers.currentUserName(), null);
		return matchmaker.join(gameId, userId);
	}

	@GetMapping("/{gameId}/players/{userId}")
	public LobbyStatus status(@PathVariable int gameId, @PathVariable int userId) {
		return matchmaker.status(gameId, userId);
	}

	@DeleteMapping("/{gameId}/players/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@PathVariable int gameId, @PathVariable int userId) {
		matchmaker.leave(gameId, userId);
	}
}
