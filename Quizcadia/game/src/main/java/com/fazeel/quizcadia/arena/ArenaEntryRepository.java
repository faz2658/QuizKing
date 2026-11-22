package com.fazeel.quizcadia.arena;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Arena players in MongoDB, one document per player and round. */
public interface ArenaEntryRepository extends MongoRepository<ArenaEntry, String> {

	List<ArenaEntry> findByRoundId(String roundId);
}
