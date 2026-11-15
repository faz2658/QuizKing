package com.fazeel.quizking.domain;

/** Asks the game manager to publish a game's full definition (questions included). */
public record GameRequest(int gameId, int userId) {
}
