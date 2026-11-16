package com.fazeel.quizcadia.report;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlayerNameRepository extends MongoRepository<PlayerName, Integer> {
}
