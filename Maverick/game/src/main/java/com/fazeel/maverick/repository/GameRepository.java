package com.fazeel.maverick.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.domain.Game;

public interface GameRepository extends MongoRepository<Game, Integer> {
}
