package com.fazeel.maverick.session;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** When a player was last asked a question (in any game), so new sessions prefer questions they haven't seen. */
@Document(collection = "seen_questions")
public record SeenQuestion(@Id String id, @Indexed int userId, int questionId, Instant lastSeenAt) {

	public static SeenQuestion of(int userId, int questionId, Instant at) {
		return new SeenQuestion(userId + ":" + questionId, userId, questionId, at);
	}
}
