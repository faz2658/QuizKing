package com.stackroute.maverick.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.session.GameSession;

public interface GameSessionRepository extends MongoRepository<GameSession, String> {
}
