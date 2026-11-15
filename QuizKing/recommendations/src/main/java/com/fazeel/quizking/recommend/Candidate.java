package com.fazeel.quizking.recommend;

/**
 * A game with the signals used to rank it for one user.
 *
 * @param totalPlays plays by everyone
 * @param myPlays plays by the user
 * @param favoriteCategory the game is in one of the user's favourite categories
 * @param similarPlayers other players who share a game with the user and played this one
 */
public record Candidate(int gameId, String gameName, String gameImage, int categoryId, GameMode mode,
		long totalPlays, long myPlays, boolean favoriteCategory, long similarPlayers) {
}
