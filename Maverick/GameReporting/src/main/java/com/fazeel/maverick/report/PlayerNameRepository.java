package com.fazeel.maverick.report;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlayerNameRepository extends MongoRepository<PlayerName, Integer> {
}
