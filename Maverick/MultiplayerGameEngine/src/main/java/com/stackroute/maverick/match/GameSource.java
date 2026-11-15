package com.stackroute.maverick.match;

import java.util.Optional;

import com.stackroute.maverick.domain.MultiPlayerGame;

/** Where game definitions come from when they are not cached yet (the game manager). */
public interface GameSource {

	Optional<MultiPlayerGame> fetch(int gameId);
}
