package com.fazeel.quizking.arena;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ArenaEntryRepository extends MongoRepository<ArenaEntry, String> {

	List<ArenaEntry> findByRoundId(String roundId);
}
