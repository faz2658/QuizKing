package com.fazeel.maverick.match;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.domain.MultiPlayerGame;

public interface GameRepository extends MongoRepository<MultiPlayerGame, Integer> {
}
