package com.fazeel.quizking.challenge;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.quizking.session.SessionQuestion;

/** A finished game turned into a link: friends replay exactly these questions to beat the challenger's score. */
@Document(collection = "friend_challenges")
public record FriendChallenge(@Id String id, @Indexed(unique = true) String sessionId, int challengerId,
		String challengerName, int gameId, String gameName, int categoryId, int score, int maxScore, int correctAnswers,
		List<SessionQuestion> questions, Instant createdAt) {
}
