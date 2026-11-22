package com.fazeel.quizcadia.challenge;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Friend challenges in MongoDB. */
public interface FriendChallengeRepository extends MongoRepository<FriendChallenge, String> {

	Optional<FriendChallenge> findBySessionId(String sessionId);
}
