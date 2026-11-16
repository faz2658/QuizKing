package com.fazeel.quizcadia.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GameRepository extends MongoRepository<com.fazeel.quizcadia.domain.Game, Integer> {

}
