package com.fazeel.quizking.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The parts of other services' events that recommendations need. Unknown fields are ignored. */
public final class Events {

	private Events() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GameCard(int gameId, String gameName, String gameDescription, String gameImage, int categoryId,
			Topic topic) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Topic(int topicId, String topicName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SinglePlayerResult(int gameId, int userId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AdaptiveResult(int gameId, int userId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MultiPlayerResult(int gameId, List<Standing> standings) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Standing(int userId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UserRegistered(int userId, String userName) {
	}
}
