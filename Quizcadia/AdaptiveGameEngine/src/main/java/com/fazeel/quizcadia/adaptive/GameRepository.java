package com.fazeel.quizcadia.adaptive;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Game;

/** Games cached from the game manager (each with its topic, rules and possibly its own questions). */
public interface GameRepository extends MongoRepository<Game, Integer> {

}
