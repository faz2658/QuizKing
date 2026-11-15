package com.fazeel.quizking.profile;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileRepository extends MongoRepository<UserProfile, Integer> {

	Optional<UserProfile> findByEmail(String email);
}
