package com.fazeel.quizcadia.match;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.MultiPlayerGame;

public interface GameRepository extends MongoRepository<MultiPlayerGame, Integer> {
}
