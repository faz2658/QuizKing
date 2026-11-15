package com.stackroute.maverick.adaptive;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Remembers that a player was asked a question, so later sessions prefer new ones. */
@Document(collection = "seen_questions")
public record SeenQuestion(@Id String id, int userId, int gameId, int questionId) {

	static SeenQuestion of(int userId, int gameId, int questionId) {
		return new SeenQuestion(userId + ":" + gameId + ":" + questionId, userId, gameId, questionId);
	}
}
