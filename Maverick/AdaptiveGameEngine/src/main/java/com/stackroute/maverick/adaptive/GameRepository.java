package com.stackroute.maverick.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GameRepository extends MongoRepository<com.stackroute.maverick.domain.Game, Integer> {

}
