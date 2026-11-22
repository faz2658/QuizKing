package com.fazeel.quizcadia.session;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** When a player was last asked a question (in any game), so new sessions prefer questions they haven't seen. */
@Document(collection = "seen_questions")
// "which of these questions has this player seen?" is answered from the index, not by reading everything the player
// has ever seen (that grows with every game); the userId prefix still serves lookups by player alone
@CompoundIndex(name = "player_question", def = "{'userId': 1, 'questionId': 1}")
public record SeenQuestion(@Id String id, int userId, int questionId, Instant lastSeenAt) {

	public static SeenQuestion of(int userId, int questionId, Instant at) {
		return new SeenQuestion(userId + ":" + questionId, userId, questionId, at);
	}
}
