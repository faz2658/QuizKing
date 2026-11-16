package com.fazeel.quizcadia.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Game;

public interface GameRepository extends MongoRepository<Game, Integer> {
}
