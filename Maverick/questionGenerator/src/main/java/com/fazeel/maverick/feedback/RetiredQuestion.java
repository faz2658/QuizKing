package com.fazeel.maverick.feedback;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.maverick.domain.Questions;

/** A question taken out of the bank because players rated or flagged it; kept so an admin can restore it. */
@Document(collection = "retired_questions")
public record RetiredQuestion(@Id int questionId, int categoryId, String topicName, Questions question, String why,
		int ups, int downs, int flags, Instant retiredAt) {
}
