package com.fazeel.quizking.feedback;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One player's feedback on one question: a rating (-1, 0 or +1) and/or a flag with a reason. One document per
 * player and question (id "userId:questionId"), so voting again changes the vote instead of adding one.
 * {@code account} is false for guests, whose votes are shown but never retire a question on their own.
 */
@Document(collection = "question_feedback")
public record QuestionFeedback(@Id String id, @Indexed int questionId, int userId, boolean account, int rating,
		boolean flagged, FlagReason reason, String note, Instant updatedAt) {

	public enum FlagReason {
		WRONG_ANSWER, UNCLEAR, TYPO, OFFENSIVE, OTHER
	}

	public static String id(int userId, int questionId) {
		return userId + ":" + questionId;
	}
}
