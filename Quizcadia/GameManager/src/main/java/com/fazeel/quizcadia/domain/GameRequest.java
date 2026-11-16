package com.fazeel.quizcadia.domain;

/** A game engine asking for a game's full definition. */
public record GameRequest(int gameId, int userId) {
}
