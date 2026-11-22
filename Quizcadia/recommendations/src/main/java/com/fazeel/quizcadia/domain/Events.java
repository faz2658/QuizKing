package com.fazeel.quizcadia.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The parts of other services' events that recommendations need. Unknown fields are ignored. */
public final class Events {

	private Events() {
	}

	/** A created game, from the game manager. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GameCard(int gameId, String gameName, String gameDescription, String gameImage, int categoryId,
			Topic topic) {
	}

	/** The topic of a game. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Topic(int topicId, String topicName) {
	}

	/** A finished solo game (only the fields the graph needs). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SinglePlayerResult(String sessionId, int gameId, int userId) {
	}

	/** A finished adaptive game. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AdaptiveResult(String sessionId, int gameId, int userId) {
	}

	/** A finished multiplayer match, with every player in it. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MultiPlayerResult(String matchId, int gameId, List<Standing> standings) {
	}

	/** One player of a match. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Standing(int userId) {
	}

	/** A new account, from the login service. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UserRegistered(int userId, String userName) {
	}
}
