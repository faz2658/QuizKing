package com.fazeel.quizcadia.arena;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Arena rounds and their questions, by round id (the start time). */
public interface ArenaRoundRepository extends MongoRepository<ArenaRound, String> {
}
