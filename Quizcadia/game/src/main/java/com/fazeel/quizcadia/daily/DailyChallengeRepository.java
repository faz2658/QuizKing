package com.fazeel.quizcadia.daily;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Daily challenges in MongoDB, by date (yyyy-mm-dd). */
public interface DailyChallengeRepository extends MongoRepository<DailyChallenge, String> {
}
