package com.fazeel.quizcadia.match;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<MultiplayerMatch, String> {
}
