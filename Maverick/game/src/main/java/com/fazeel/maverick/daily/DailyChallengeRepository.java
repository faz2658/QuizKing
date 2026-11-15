package com.fazeel.maverick.daily;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyChallengeRepository extends MongoRepository<DailyChallenge, String> {
}
