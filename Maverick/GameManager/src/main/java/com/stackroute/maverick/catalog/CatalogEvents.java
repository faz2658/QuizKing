package com.stackroute.maverick.catalog;

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.MultiPlayerGame;

/** Outgoing events of the game manager. */
public interface CatalogEvents {

	/** A new single-player game card exists (recommendations, search). */
	void gameCreated(Game game);

	/** Full game definition for the game engine. */
	void gameDefinition(Game game);

	void multiPlayerGameCreated(MultiPlayerGame game);

	void gameDeleted(int gameId);
}
