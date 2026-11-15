package com.fazeel.quizking.opentdb;

public interface TriviaSource {

	/** A session token: questions already returned for it are never returned again. */
	String newToken();

	TriviaBatch fetch(String token, int amount);
}
