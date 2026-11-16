package com.fazeel.quizcadia.knowme;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** "How well do you know me?": questions a player wrote about themselves, shared as a link for friends to answer. */
@Document(collection = "know_me_quizzes")
public record KnowMeQuiz(@Id String id, @Indexed int ownerId, String ownerName, List<Question> questions,
		Instant createdAt) {

	/** One question; {@code answer} is the index of the right option. */
	public record Question(String text, List<String> options, int answer) {
	}
}
