package com.fazeel.quizcadia.domain;

import java.util.List;
import java.util.Objects;

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

	/** @param country ISO code of a country-specific question (e.g. "IN"); blank for general questions */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
			String option3, String option4, String correctAnswer, String country) {

		public Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
				String option3, String option4, String correctAnswer) {
			this(questionId, questionLevel, questionStem, option1, option2, option3, option4, correctAnswer, null);
		}

		boolean general() {
			return country == null || country.isBlank();
		}
	}

	/** The same game with these questions (not saved). */
	public Game withQuestions(List<Question> qs) {
		return new Game(gameId, gameName, categoryId, topic, qs);
	}

	/**
	 * The questions an adaptive run may use for a player from {@code country}: that country's and general ones,
	 * never other countries' (topics are shared by every country). Without a country, general ones. A game with
	 * neither keeps all it has.
	 */
	public Game forCountry(String country) {
		if (questions == null) {
			return this;
		}
		String home = country == null || country.isBlank() ? null : country.trim();
		List<Question> eligible = questions.stream().filter(Objects::nonNull)
				.filter(q -> q.general() || home != null && home.equalsIgnoreCase(q.country())).toList();
		return eligible.isEmpty() ? this : withQuestions(eligible);
	}
}
