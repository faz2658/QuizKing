package com.fazeel.quizcadia.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Multiplayer game definition as published by the game manager (only the fields the engine needs). */
@Document(collection = "multiplayer_games")
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultiPlayerGame(@Id int gameId, String gameName, int categoryId, int timePerQuestion,
		int scorePerQuestion, List<Question> autoquestions, int numberOfQuestions, Level questionLevel, TopicRef topic) {

	public MultiPlayerGame(int gameId, String gameName, int categoryId, int timePerQuestion, int scorePerQuestion,
			List<Question> autoquestions, int numberOfQuestions, Level questionLevel) {
		this(gameId, gameName, categoryId, timePerQuestion, scorePerQuestion, autoquestions, numberOfQuestions,
				questionLevel, null);
	}

	/** The topic a game draws from; its questions are loaded from the question service when the game carries none. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TopicRef(int topicId, String topicName) {
	}

	/** The same game with these questions (not saved). */
	public MultiPlayerGame withQuestions(List<Question> questions) {
		return new MultiPlayerGame(gameId, gameName, categoryId, timePerQuestion, scorePerQuestion, questions,
				numberOfQuestions, questionLevel, topic);
	}

	/** How many easy, medium and advanced questions a match asks. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Level(int easyLevel, int mediumLevel, int advancedLevel) {

		/** How many questions the mix asks in all (negative counts count as none). */
		public int total() {
			return Math.max(0, easyLevel) + Math.max(0, mediumLevel) + Math.max(0, advancedLevel);
		}

		/** How many questions of a level (1 easy, 2 medium, 3 advanced) the mix asks. */
		public int of(int level) {
			return Math.max(0, switch (level) {
				case 1 -> easyLevel;
				case 2 -> mediumLevel;
				default -> advancedLevel;
			});
		}
	}

	/** @param country ISO code of a country-specific question (e.g. "IN"); blank for general questions */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
			String option3, String option4, String correctAnswer, String country) {

		public Question(int questionId, int questionLevel, String questionStem, String option1, String option2,
				String option3, String option4, String correctAnswer) {
			this(questionId, questionLevel, questionStem, option1, option2, option3, option4, correctAnswer, null);
		}
	}
}
