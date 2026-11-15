package com.fazeel.maverick.adaptive;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** When a player was last asked a question (in any game), so later sessions prefer new ones. */
@Document(collection = "seen_questions")
public record SeenQuestion(@Id String id, @Indexed int userId, int questionId, Instant lastSeenAt) {

	static SeenQuestion of(int userId, int questionId, Instant at) {
		return new SeenQuestion(userId + ":" + questionId, userId, questionId, at);
	}
}
