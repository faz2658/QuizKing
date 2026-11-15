package com.fazeel.quizking.match;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.domain.MultiPlayerGame;

public interface GameRepository extends MongoRepository<MultiPlayerGame, Integer> {
}
