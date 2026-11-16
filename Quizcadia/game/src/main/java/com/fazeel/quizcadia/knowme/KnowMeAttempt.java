package com.fazeel.quizcadia.knowme;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** A friend's one go at a know-me quiz. The id is "quizId:userId", so each friend gets exactly one. */
@Document(collection = "know_me_attempts")
@CompoundIndex(name = "quiz_score", def = "{'quizId': 1, 'score': -1}")
public record KnowMeAttempt(@Id String id, String quizId, int userId, String name, List<Integer> answers, int score,
		Instant completedAt) {

	static String idOf(String quizId, int userId) {
		return quizId + ":" + userId;
	}
}
