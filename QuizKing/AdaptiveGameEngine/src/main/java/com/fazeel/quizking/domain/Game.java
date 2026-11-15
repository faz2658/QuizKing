package com.fazeel.quizking.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Game definition from the game manager (only the fields the adaptive engine needs). */
@Document(collection = "games")
@JsonIgnoreProperties(ignoreUnknown = true)
public record Game(@Id int gameId, String gameName, int categoryId, Topic topic, List<Question> questions) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Topic(int topicId, String topicName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
			String option3, String option4, String correctAnswer) {
	}
}
