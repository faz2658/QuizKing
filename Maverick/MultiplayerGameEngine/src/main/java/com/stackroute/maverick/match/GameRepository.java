package com.stackroute.maverick.match;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.domain.MultiPlayerGame;

public interface GameRepository extends MongoRepository<MultiPlayerGame, Integer> {
}
