package com.fazeel.quizcadia.opentdb;

/** Where imported trivia comes from (Open Trivia DB in production). */
public interface TriviaSource {

	/** A session token: questions already returned for it are never returned again. */
	String newToken();

	/** Up to amount new questions for the token, with Open Trivia DB's response code. */
	TriviaBatch fetch(String token, int amount);
}
