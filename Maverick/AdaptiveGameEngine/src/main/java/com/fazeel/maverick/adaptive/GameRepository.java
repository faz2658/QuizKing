package com.fazeel.maverick.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GameRepository extends MongoRepository<com.fazeel.maverick.domain.Game, Integer> {

}
