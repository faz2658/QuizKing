package com.fazeel.quizcadia.adaptive;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Adaptive sessions, saved after every change (optimistic locking via the session's version). */
public interface SessionRepository extends MongoRepository<AdaptiveSession, String> {

}
