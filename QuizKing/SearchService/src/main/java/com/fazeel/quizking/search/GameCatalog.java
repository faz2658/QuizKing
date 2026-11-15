package com.fazeel.quizking.search;

import java.util.List;

public interface GameCatalog {

	/** Games whose name starts with the prefix, ignoring case. */
	List<GameCard> gamesNamedLike(String prefix);
}
