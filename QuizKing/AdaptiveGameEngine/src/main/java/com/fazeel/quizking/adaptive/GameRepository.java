package com.fazeel.quizking.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GameRepository extends MongoRepository<com.fazeel.quizking.domain.Game, Integer> {

}
