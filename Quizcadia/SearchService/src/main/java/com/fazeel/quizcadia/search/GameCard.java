package com.fazeel.quizcadia.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What search returns about a game. Questions (and their answers) are never included. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameCard(int gameId, String gameName, String gameDescription, String gameImage, int categoryId,
		Topic topic, GameType gameType) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Topic(int topicId, String topicName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GameType(int gameTypeId, String gameTypeName) {
	}
}
