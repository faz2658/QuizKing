package com.fazeel.maverick.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.maverick.auth.Callers;
import com.fazeel.maverick.matching.Matchmaker;
import com.fazeel.maverick.matching.Matchmaker.OpenLobby;
import com.fazeel.maverick.matching.Presence;
import com.fazeel.maverick.matching.Presence.CountryCount;
import com.fazeel.maverick.matching.QuickMatch;

/** Playing with the world: who's online, open lobbies, and the global Quick Match queue. */
@RestController
@RequestMapping("/api/v1/matching")
public class WorldController {

	private final Presence presence;
	private final Matchmaker matchmaker;
	private final QuickMatch quick;

	public WorldController(Presence presence, Matchmaker matchmaker, QuickMatch quick) {
		this.presence = presence;
		this.matchmaker = matchmaker;
		this.quick = quick;
	}

	/** {@code country} is an ISO 3166 code like CA; it is optional. */
	public record Heartbeat(String country) {
	}

	public record Online(int online, List<CountryCount> countries, List<OpenLobby> lobbies, int quickQueue) {
	}

	/** Sent by every signed-in page about once a minute. */
	@PostMapping("/presence")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void heartbeat(@RequestBody(required = false) Heartbeat beat) {
		presence.seen(Callers.currentUserId(), Callers.currentUserName(), beat == null ? null : beat.country());
	}

	@GetMapping("/online")
	public Online online() {
		return new Online(presence.online(), presence.byCountry(), matchmaker.openLobbies(), quick.waiting());
	}

	@PostMapping("/quick")
	public QuickMatch.Status joinQuick() {
		presence.seen(Callers.currentUserId(), Callers.currentUserName(), null);
		return quick.join(Callers.currentUserId());
	}

	@GetMapping("/quick")
	public QuickMatch.Status quickStatus() {
		return quick.status(Callers.currentUserId());
	}

	@DeleteMapping("/quick")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leaveQuick() {
		quick.leave(Callers.currentUserId());
	}
}
