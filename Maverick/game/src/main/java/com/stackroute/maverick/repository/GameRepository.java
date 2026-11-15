package com.stackroute.maverick.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.domain.Game;

public interface GameRepository extends MongoRepository<Game, Integer> {
}
