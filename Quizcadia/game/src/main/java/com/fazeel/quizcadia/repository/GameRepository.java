package com.fazeel.quizcadia.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Game;

/** Games cached from the game manager. */
public interface GameRepository extends MongoRepository<Game, Integer> {
}
