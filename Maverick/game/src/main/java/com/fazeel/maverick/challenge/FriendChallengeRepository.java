package com.fazeel.maverick.challenge;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FriendChallengeRepository extends MongoRepository<FriendChallenge, String> {

	Optional<FriendChallenge> findBySessionId(String sessionId);
}
