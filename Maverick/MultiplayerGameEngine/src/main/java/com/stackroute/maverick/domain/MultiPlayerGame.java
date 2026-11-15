package com.stackroute.maverick.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Multiplayer game definition as published by the game manager (only the fields the engine needs). */
@Document(collection = "multiplayer_games")
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultiPlayerGame(@Id int gameId, String gameName, int categoryId, int timePerQuestion,
		int scorePerQuestion, List<Question> autoquestions) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
			String option3, String option4, String correctAnswer) {
	}
}
