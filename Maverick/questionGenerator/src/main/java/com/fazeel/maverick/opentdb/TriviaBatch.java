package com.fazeel.maverick.opentdb;

import java.util.List;

/**
 * Result of one request. Open Trivia DB response codes: 0 ok, 1 not enough questions left for the
 * requested amount, 2 invalid parameter, 3 unknown token, 4 token has seen every question, 5 rate limited.
 */
public record TriviaBatch(int responseCode, List<TriviaQuestion> questions) {

	public static final int OK = 0;
	public static final int NO_RESULTS = 1;
	public static final int TOKEN_NOT_FOUND = 3;
	public static final int TOKEN_EMPTY = 4;
	public static final int RATE_LIMITED = 5;
}
