package com.fazeel.quizcadia.match;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** When a player last got a question in a match, so new matches prefer questions nobody has seen. */
@Document(collection = "seen_questions")
// "which of these questions has this player seen?" is answered from the index, not by reading everything the player
// has ever seen (that grows with every game); the userId prefix still serves lookups by player alone
@CompoundIndex(name = "player_question", def = "{'userId': 1, 'questionId': 1}")
public record SeenQuestion(@Id String id, int userId, int questionId, Instant lastSeenAt) {

	static SeenQuestion of(int userId, int questionId, Instant at) {
		return new SeenQuestion(userId + ":" + questionId, userId, questionId, at);
	}
}
