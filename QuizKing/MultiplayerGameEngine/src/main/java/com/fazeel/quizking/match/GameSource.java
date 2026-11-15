package com.fazeel.quizking.match;

import java.util.Optional;

import com.fazeel.quizking.domain.MultiPlayerGame;

/** Where game definitions come from when they are not cached yet (the game manager). */
public interface GameSource {

	Optional<MultiPlayerGame> fetch(int gameId);
}
