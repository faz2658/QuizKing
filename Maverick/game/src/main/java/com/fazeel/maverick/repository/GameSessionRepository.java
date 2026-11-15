package com.fazeel.maverick.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.session.GameSession;

public interface GameSessionRepository extends MongoRepository<GameSession, String> {
}
