package com.fazeel.quizcadia.ai;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.quizcadia.domain.Questions;

/** An AI-written question waiting for an admin. Drafts never reach games until approved. */
@Document(collection = "draft_questions")
public record DraftQuestion(@Id String id, int categoryId, String topicName, @Indexed Status status,
		Questions question, Instant createdAt, Instant reviewedAt) {

	/** PENDING until an admin approves or rejects the draft. */
	public enum Status {
		PENDING, APPROVED, REJECTED
	}

	DraftQuestion reviewed(Status s, Instant at, Questions q) {
		return new DraftQuestion(id, categoryId, topicName, s, q, createdAt, at);
	}
}
