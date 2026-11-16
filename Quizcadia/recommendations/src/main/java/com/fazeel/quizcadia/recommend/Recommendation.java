package com.fazeel.quizcadia.recommend;

public record Recommendation(int gameId, String gameName, String gameImage, int categoryId, GameMode mode,
		Reason reason, double score) {

	public enum Reason {
		/** In a category the user picked. */
		FAVORITE_CATEGORY,
		/** Played by people who play the same games as the user. */
		PLAYERS_LIKE_YOU,
		/** Popular with everyone. */
		POPULAR,
		/** The user played it before. */
		PLAY_AGAIN,
		/** Nothing else to go on (new user, new games). */
		NEW
	}
}
