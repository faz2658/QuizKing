package com.fazeel.quizcadia.knowme;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Know-me quizzes in MongoDB. */
public interface KnowMeQuizRepository extends MongoRepository<KnowMeQuiz, String> {

	List<KnowMeQuiz> findByOwnerIdOrderByCreatedAtDesc(int ownerId);

	long countByOwnerId(int ownerId);
}
