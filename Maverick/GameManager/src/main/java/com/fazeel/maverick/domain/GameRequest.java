package com.fazeel.maverick.domain;

/** A game engine asking for a game's full definition. */
public record GameRequest(int gameId, int userId) {
}
