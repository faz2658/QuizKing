package com.fazeel.quizking.topicquiz;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** One player's go at a topic quiz; the id "quizId:userId" allows one each. {@code -1} marks a timed-out answer. */
@Document(collection = "topic_quiz_attempts")
@CompoundIndex(name = "quiz_score", def = "{'quizId': 1, 'score': -1}")
public record TopicQuizAttempt(@Id String id, String quizId, int userId, String name, List<Integer> answers, int score,
		Instant completedAt) {

	static String idOf(String quizId, int userId) {
		return quizId + ":" + userId;
	}
}
