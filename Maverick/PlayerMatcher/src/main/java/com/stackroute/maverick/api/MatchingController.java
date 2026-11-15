package com.stackroute.maverick.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stackroute.maverick.matching.LobbyStatus;
import com.stackroute.maverick.matching.Matchmaker;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

	private final Matchmaker matchmaker;

	public MatchingController(Matchmaker matchmaker) {
		this.matchmaker = matchmaker;
	}

	/** Joins the lobby of a game. Poll the status endpoint until the state is MATCHED or EXPIRED. */
	@PostMapping("/{gameId}/players/{userId}")
	public LobbyStatus join(@PathVariable int gameId, @PathVariable int userId) {
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
