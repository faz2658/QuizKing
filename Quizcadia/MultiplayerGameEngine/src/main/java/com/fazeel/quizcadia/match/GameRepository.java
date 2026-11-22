package com.fazeel.quizcadia.match;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.MultiPlayerGame;

/** Multiplayer games cached from the game manager. */
public interface GameRepository extends MongoRepository<MultiPlayerGame, Integer> {
}
