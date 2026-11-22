package com.fazeel.quizcadia.report;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Players' display names and countries for leaderboards, by user id. */
public interface PlayerNameRepository extends MongoRepository<PlayerName, Integer> {
}
