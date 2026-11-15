package com.fazeel.maverick.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepository extends MongoRepository<AdaptiveSession, String> {

}
