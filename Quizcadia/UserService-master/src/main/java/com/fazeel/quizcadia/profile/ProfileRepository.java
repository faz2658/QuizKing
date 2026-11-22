package com.fazeel.quizcadia.profile;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Profiles in MongoDB, by user id. */
public interface ProfileRepository extends MongoRepository<UserProfile, Integer> {

	Optional<UserProfile> findByEmail(String email);
}
