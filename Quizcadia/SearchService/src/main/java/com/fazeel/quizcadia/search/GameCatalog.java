package com.fazeel.quizcadia.search;

import java.util.List;

/** Where search finds games (the game manager in production). */
public interface GameCatalog {

	/** Games whose name starts with the prefix, ignoring case. */
	List<GameCard> gamesNamedLike(String prefix);
}
