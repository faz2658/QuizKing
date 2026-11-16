package com.fazeel.quizcadia.search;

import java.util.List;

public interface GameCatalog {

	/** Games whose name starts with the prefix, ignoring case. */
	List<GameCard> gamesNamedLike(String prefix);
}
