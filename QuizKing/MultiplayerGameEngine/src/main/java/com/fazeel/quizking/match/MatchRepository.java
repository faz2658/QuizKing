package com.fazeel.quizking.match;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<MultiplayerMatch, String> {
}
