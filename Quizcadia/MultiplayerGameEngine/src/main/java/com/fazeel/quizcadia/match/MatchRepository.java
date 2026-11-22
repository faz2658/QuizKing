package com.fazeel.quizcadia.match;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Matches, saved after every change (optimistic locking via the match's version). */
public interface MatchRepository extends MongoRepository<MultiplayerMatch, String> {
}
