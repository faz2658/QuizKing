package com.fazeel.quizking.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.domain.Game;

public interface GameRepository extends MongoRepository<Game, Integer> {
}
